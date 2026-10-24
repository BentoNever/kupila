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
import ao.kupila.gestaonegocios.dominio.notificacoes.AccaoCampanha
import ao.kupila.gestaonegocios.dominio.notificacoes.Campanha
import ao.kupila.gestaonegocios.dominio.notificacoes.PrioridadeCampanha
import ao.kupila.gestaonegocios.dominio.notificacoes.PublicoCampanha
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
    private val chavePaletaLocal = stringPreferencesKey("paleta_local")

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

    private val _spotec = MutableStateFlow(runBlocking { contexto.dadosAjustes.data.first()[chavePaletaLocal] != "plataforma" })
    /** Identidade visual Spotec (igual ao PDV Digital) neste aparelho; false = a paleta definida pelo Administrador. */
    val spotec: StateFlow<Boolean> = _spotec.asStateFlow()
    fun definirSpotec(s: Boolean) = ambito.launch {
        contexto.dadosAjustes.edit { it[chavePaletaLocal] = if (s) "spotec" else "plataforma" }
        _spotec.value = s
    }

    fun definirModoLocal(m: ModoAparencia?) = ambito.launch {
        contexto.dadosAjustes.edit { if (m == null) it.remove(chaveModoLocal) else it[chaveModoLocal] = m.name }
        _modoLocal.value = m
    }

    /**
     * Em produção as regras só deixam ler `settings` com sessão iniciada (D-29): antes do login aplica-se a última identidade
     * guardada neste aparelho; depois do login, a identidade de `settings/platform` actualiza essa cópia.
     */
    fun escutarIdentidade() { /* identidade pré-login vem da cópia local (DataStore) */ }

    private fun aplicarIdentidade(d: Map<String, Any?>) {
        val v = (d["settingsRevision"] as? Number)?.toLong() ?: 0
        fun cor(k: String, padrao: Long) = (d[k] as? String)?.removePrefix("#")?.takeIf { it.length == 6 }?.toLongOrNull(16)?.let { 0xFF000000 or it } ?: padrao
        val logo = (d["appLogoDataUrl"] as? String)?.takeIf { it.startsWith("data:image") || it.startsWith("http") }
        val g = IdentidadeGuardada(v, d["visualTheme"] as? String ?: "EMERALD", cor("visualPrimary", TemaVisual.KUPILA.primaria), cor("visualAccent", TemaVisual.KUPILA.destaque),
            d["homeVisualPreset"] as? String ?: "AURORA", cor("homeSearchPrimary", PaletaVitrine.AURORA.pesquisaPrimaria), cor("homeSearchAccent", PaletaVitrine.AURORA.pesquisaDestaque),
            cor("homeShowcasePrimary", PaletaVitrine.AURORA.vitrinePrimaria), cor("homeShowcaseAccent", PaletaVitrine.AURORA.vitrineDestaque),
            d["visualAppearance"] as? String ?: "LIGHT", logo)
        val nova = g.paraDominio()
        if (nova == _identidade.value) return
        ambito.launch {
            contexto.dadosAjustes.edit { it[chaveIdentidade] = json.encodeToString(IdentidadeGuardada.serializer(), g) }
            _identidade.value = nova
        }
    }

    private val _campanhas = MutableStateFlow<List<Campanha>>(emptyList())
    /** `settings.marketingMessages` (HTML 14578). */
    val campanhas: StateFlow<List<Campanha>> = _campanhas.asStateFlow()

    private fun aplicarCampanhas(d: Map<String, Any?>) {
        _campanhas.value = (d["marketingMessages"] as? List<*>).orEmpty().mapNotNull { x ->
            val m = x as? Map<*, *> ?: return@mapNotNull null
            fun s(k: String) = m[k] as? String ?: ""
            fun inst(k: String) = MapeamentoProduto.ms(m[k])?.let(java.time.Instant::ofEpochMilli) ?: java.time.Instant.EPOCH
            Campanha(s("id").ifEmpty { return@mapNotNull null }, s("title"), s("message"),
                PublicoCampanha.entries.firstOrNull { it.codigo == s("audience") } ?: PublicoCampanha.TODOS,
                if (s("priority") == "HIGH") PrioridadeCampanha.ALTA else PrioridadeCampanha.NORMAL,
                AccaoCampanha.entries.firstOrNull { it.codigo == s("action") } ?: AccaoCampanha.NENHUMA,
                s("cta"), inst("startAt"), inst("endAt"), m["disabled"] == true, inst("createdAt"))
        }
    }

    /** Ajustes completos (`settings/platform`) — só depois da autenticação. */
    fun escutarAjustes() {
        if (!ArranqueFirebase.disponivel) return
        escutas += FirebaseFirestore.getInstance().document("settings/platform").addSnapshotListener { s, _ ->
            val d = s?.data ?: return@addSnapshotListener
            aplicarIdentidade(d); aplicarCampanhas(d)
            fun pct(k: String, padrao: Percentagem) = (d[k])?.toString()?.toBigDecimalOrNull()?.let(Percentagem::de) ?: padrao
            fun int(k: String, padrao: Int) = (d[k] as? Number)?.toInt() ?: padrao
            val base = ParametrosFinanceiros.inicial
            val f = ParametrosFinanceiros(
                versao = (d["settingsRevision"] as? Number)?.toString() ?: "servidor",
                sinalVenda = pct("depositPercent", base.sinalVenda), mesesSinalAluguer = int("rentalDepositMonths", base.mesesSinalAluguer),
                comissao = pct("commissionPercent", base.comissao), taxaAnuncio = pct("listingFeePercent", base.taxaAnuncio),
                promocoes = mapOf(Promocao.DESTAQUE to pct("promoFeaturedPercent", base.promocoes.getValue(Promocao.DESTAQUE)),
                    Promocao.TOP to pct("promoTopPercent", base.promocoes.getValue(Promocao.TOP)),
                    Promocao.PRIORIDADE to pct("promoPriorityPercent", base.promocoes.getValue(Promocao.PRIORIDADE))),
                duracaoPromocaoDias = int("promoDurationDays", base.duracaoPromocaoDias))
            _ajustes.value = AjustesPlataforma(
                versao = (d["settingsRevision"] as? Number)?.toLong() ?: 0, financeiro = f,
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
