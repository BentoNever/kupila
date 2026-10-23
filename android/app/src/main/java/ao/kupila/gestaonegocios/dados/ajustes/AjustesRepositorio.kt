package ao.kupila.gestaonegocios.dados.ajustes

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ao.kupila.gestaonegocios.dados.remoto.ArranqueFirebase
import ao.kupila.gestaonegocios.dados.remoto.MapeamentoProduto
import ao.kupila.gestaonegocios.dominio.ajustes.AjustesAlertasProprietario
import ao.kupila.gestaonegocios.dominio.ajustes.AjustesPlataforma
import ao.kupila.gestaonegocios.dominio.aparencia.IdentidadeVisual
import ao.kupila.gestaonegocios.dominio.aparencia.ModoAparencia
import ao.kupila.gestaonegocios.dominio.aparencia.PaletaVitrine
import ao.kupila.gestaonegocios.dominio.aparencia.TemaVisual
import ao.kupila.gestaonegocios.dominio.financeiro.ParametrosFinanceiros
import ao.kupila.gestaonegocios.dominio.financeiro.Percentagem
import ao.kupila.gestaonegocios.dominio.financeiro.Promocao
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dadosAjustes by preferencesDataStore("kupila_ajustes")

@Serializable
private data class IdentidadeGuardada(val versao: Long = 0, val tema: String = "EMERALD", val primaria: Long = TemaVisual.KUPILA.primaria,
    val destaque: Long = TemaVisual.KUPILA.destaque, val vitrine: String = "AURORA", val sp: Long = PaletaVitrine.AURORA.pesquisaPrimaria,
    val sa: Long = PaletaVitrine.AURORA.pesquisaDestaque, val hp: Long = PaletaVitrine.AURORA.vitrinePrimaria, val ha: Long = PaletaVitrine.AURORA.vitrineDestaque,
    val modo: String = "LIGHT", val logo: String? = null)

/**
 * Ajustes da plataforma: o servidor é a fonte; a última versão autorizada fica guardada localmente com versão
 * para aplicar a identidade visual já no login e offline, sem flash de tema errado (secção 6). O cliente só lê.
 */
@Singleton
class AjustesRepositorio @Inject constructor(@param:ApplicationContext private val contexto: Context) {
    private val ambito = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val chaveIdentidade = stringPreferencesKey("identidade")
    private val chaveModoLocal = stringPreferencesKey("modo_local")

    /** Lido de forma síncrona UMA vez no arranque (antes do 1.º frame); depois actualizado por eventos. */
    private val _identidade = MutableStateFlow(runBlocking { lerIdentidade() })
    val identidade: StateFlow<IdentidadeVisual> = _identidade.asStateFlow()
    private val _modoLocal = MutableStateFlow(runBlocking { contexto.dadosAjustes.data.first()[chaveModoLocal]?.let { runCatching { ModoAparencia.valueOf(it) }.getOrNull() } })
    /** Preferência do dispositivo (claro/escuro/automático), sobrepõe a da plataforma. */
    val modoLocal: StateFlow<ModoAparencia?> = _modoLocal.asStateFlow()

    private val _ajustes = MutableStateFlow(AjustesPlataforma())
    val ajustes: StateFlow<AjustesPlataforma> = _ajustes.asStateFlow()
    private var escutas = mutableListOf<ListenerRegistration>()

    private suspend fun lerIdentidade(): IdentidadeVisual = contexto.dadosAjustes.data.first()[chaveIdentidade]
        ?.let { runCatching { json.decodeFromString(IdentidadeGuardada.serializer(), it) }.getOrNull() }?.paraDominio() ?: IdentidadeVisual()

    private fun IdentidadeGuardada.paraDominio() = IdentidadeVisual(versao, TemaVisual.daOrigem(tema), primaria, destaque, PaletaVitrine.daOrigem(vitrine),
        sp, sa, hp, ha, when (modo) { "DARK" -> ModoAparencia.ESCURO; "AUTO" -> ModoAparencia.AUTOMATICO; else -> ModoAparencia.CLARO }, logo)

    fun definirModoLocal(m: ModoAparencia?) = ambito.launch {
        contexto.dadosAjustes.edit { if (m == null) it.remove(chaveModoLocal) else it[chaveModoLocal] = m.name }
        _modoLocal.value = m
    }

    /** Identidade pública (sem sessão) — `settings/publicIdentity`. */
    fun escutarIdentidade() {
        if (!ArranqueFirebase.disponivel) return
        escutas += FirebaseFirestore.getInstance().document("settings/publicIdentity").addSnapshotListener { s, _ ->
            val d = s?.data ?: return@addSnapshotListener
            val v = (d["version"] as? Number)?.toLong() ?: 0
            if (v < _identidade.value.versao) return@addSnapshotListener
            fun cor(k: String, padrao: Long) = (d[k] as? String)?.removePrefix("#")?.takeIf { it.length == 6 }?.toLongOrNull(16)?.let { 0xFF000000 or it } ?: padrao
            val g = IdentidadeGuardada(v, d["visualTheme"] as? String ?: "EMERALD", cor("visualPrimary", TemaVisual.KUPILA.primaria), cor("visualAccent", TemaVisual.KUPILA.destaque),
                d["homeVisualPreset"] as? String ?: "AURORA", cor("homeSearchPrimary", PaletaVitrine.AURORA.pesquisaPrimaria), cor("homeSearchAccent", PaletaVitrine.AURORA.pesquisaDestaque),
                cor("homeShowcasePrimary", PaletaVitrine.AURORA.vitrinePrimaria), cor("homeShowcaseAccent", PaletaVitrine.AURORA.vitrineDestaque),
                d["visualAppearance"] as? String ?: "LIGHT", d["logoUrl"] as? String)
            ambito.launch {
                contexto.dadosAjustes.edit { it[chaveIdentidade] = json.encodeToString(IdentidadeGuardada.serializer(), g) }
                _identidade.value = g.paraDominio()
            }
        }
    }

    /** Ajustes completos (`settings/platform`) — só depois da autenticação. */
    fun escutarAjustes() {
        if (!ArranqueFirebase.disponivel) return
        escutas += FirebaseFirestore.getInstance().document("settings/platform").addSnapshotListener { s, _ ->
            val d = s?.data ?: return@addSnapshotListener
            fun pct(k: String, padrao: Percentagem) = (d[k])?.toString()?.toBigDecimalOrNull()?.let(Percentagem::de) ?: padrao
            fun int(k: String, padrao: Int) = (d[k] as? Number)?.toInt() ?: padrao
            val base = ParametrosFinanceiros.inicial
            val f = ParametrosFinanceiros(
                versao = (d["version"] as? Number)?.toString() ?: "servidor",
                sinalVenda = pct("depositPercent", base.sinalVenda), mesesSinalAluguer = int("rentalDepositMonths", base.mesesSinalAluguer),
                comissao = pct("commissionPercent", base.comissao), taxaAnuncio = pct("listingFeePercent", base.taxaAnuncio),
                promocoes = mapOf(Promocao.DESTAQUE to pct("promoFeaturedPercent", base.promocoes.getValue(Promocao.DESTAQUE)),
                    Promocao.TOP to pct("promoTopPercent", base.promocoes.getValue(Promocao.TOP)),
                    Promocao.PRIORIDADE to pct("promoPriorityPercent", base.promocoes.getValue(Promocao.PRIORIDADE))),
                duracaoPromocaoDias = int("promoDurationDays", base.duracaoPromocaoDias))
            _ajustes.value = AjustesPlataforma(
                versao = (d["version"] as? Number)?.toLong() ?: 0, financeiro = f,
                prazoReservaMinutos = (d["reservationDurationMinutes"] as? Number)?.toLong() ?: 1440,
                inactividadeMinutos = int("sessionInactivityMinutes", 10), entidadePagamento = d["paymentEntity"] as? String ?: "",
                alertas = AjustesAlertasProprietario(d["ownerAlertReservationEnabled"] != false, d["ownerAlertInterestEnabled"] != false,
                    d["ownerAlertEmailEnabled"] != false, d["ownerAlertSmsEnabled"] != false, int("ownerInterestThreshold", 3),
                    int("ownerInterestWindowHours", 24), int("ownerInterestCooldownHours", 12)),
                actualizadoPor = d["updatedBy"] as? String, actualizadoEm = MapeamentoProduto.ms(d["updatedAt"])?.let(java.time.Instant::ofEpochMilli))
        }
    }

    fun pararAjustes() { escutas.forEach { it.remove() }; escutas.clear(); escutarIdentidade() }
}
