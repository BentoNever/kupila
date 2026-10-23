package ao.kupila.gestaonegocios.ui.negocios

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ao.kupila.gestaonegocios.dados.ContaActiva
import ao.kupila.gestaonegocios.dados.ajustes.AjustesRepositorio
import ao.kupila.gestaonegocios.dados.anuncios.AnunciosRepositorio
import ao.kupila.gestaonegocios.dados.eventos.Conjunto
import ao.kupila.gestaonegocios.dados.eventos.Registo
import ao.kupila.gestaonegocios.dados.media.Documentos
import ao.kupila.gestaonegocios.dados.media.Imagens
import ao.kupila.gestaonegocios.dados.remoto.MensagensErro
import ao.kupila.gestaonegocios.dados.remoto.Origem
import ao.kupila.gestaonegocios.dados.transaccoes.INTENCAO_ABERTA
import ao.kupila.gestaonegocios.dados.transaccoes.TransaccoesRepositorio
import ao.kupila.gestaonegocios.dominio.anuncio.ValidacaoAnuncio
import ao.kupila.gestaonegocios.dominio.perfis.Papel
import ao.kupila.gestaonegocios.dominio.transaccoes.MotivoReembolso
import ao.kupila.gestaonegocios.dominio.transaccoes.ResultadoOtp
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/** Reserva do Cliente com o código já resolvido (do comprovativo confirmado) — o Cliente nunca tem de o procurar. */
data class ReservaCliente(val r: Registo, val otp: String?)

/** Pagamento à espera do Administrador, com o comprovativo (lido só quando o cartão é aberto). */
data class Pendencia(val intent: Registo, val coincide: Boolean)

/**
 * Centro "Negócios": tudo o que exige acção, agrupado por papel, a partir do cache local (zero leituras ao abrir de novo).
 * Automatiza o que não é decisão humana: reservas vencidas são expiradas sozinhas; o OTP aparece sem procurar; o pedido de reembolso
 * usa o OTP da própria reserva. Decisões financeiras (confirmar, reembolsar, liberar) continuam humanas e explícitas.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NegociosViewModel @Inject constructor(
    private val conta: ContaActiva,
    private val ajustes: AjustesRepositorio,
    private val transaccoes: TransaccoesRepositorio,
    private val anuncios: AnunciosRepositorio,
    private val imagens: Imagens,
    private val documentos: Documentos,
    private val testemunhos: ao.kupila.gestaonegocios.dados.admin.TestemunhosRepositorio,
    @param:ApplicationContext private val contexto: Context,
) : ViewModel() {
    private val perfil get() = conta.recursos.value?.perfil
    val papel: Papel? get() = perfil?.papel
    private fun lista(c: Conjunto) = conta.recursos.flatMapLatest { it?.eventos?.observar(c) ?: flowOf(emptyList()) }
    private fun <T> st(f: kotlinx.coroutines.flow.Flow<T>, inicial: T) = f.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), inicial)

    val aSincronizar = st(conta.recursos.flatMapLatest { it?.eventos?.aSincronizar ?: flowOf(false) }, false)

    val reservasCliente: StateFlow<List<ReservaCliente>> = st(combine(lista(Conjunto.RESERVAS_CLIENTE), lista(Conjunto.SUBMISSOES)) { rs, ss ->
        val otps = ss.mapNotNull { s -> s.s("reservationId").takeIf { it.isNotBlank() }?.let { it to s.s("otpForClient") } }.toMap()
        rs.sortedByDescending { it.ms("createdAt") ?: 0 }.map { r -> ReservaCliente(r, otps[r.id]?.takeIf { it.isNotBlank() } ?: r.s("otp").takeIf { it.isNotBlank() }) }
    }, emptyList())

    val pagamentosAbertos: StateFlow<List<Registo>> = st(lista(Conjunto.INTENCOES).map { l ->
        l.filter { it.s("status") in INTENCAO_ABERTA && ((it.ms("expiresAt") ?: Long.MAX_VALUE) > System.currentTimeMillis() || it.s("status") !in setOf("AWAITING_PAYMENT", "AWAITING_PROVIDER_CONFIGURATION")) }
            .sortedByDescending { it.ms("createdAt") ?: 0 }
    }, emptyList())

    val reembolsosCliente = st(lista(Conjunto.REEMBOLSOS_CLIENTE), emptyList())
    val reservasDono = st(lista(Conjunto.RESERVAS_DONO), emptyList())
    val reembolsosDono = st(lista(Conjunto.REEMBOLSOS_DONO), emptyList())

    val pendencias: StateFlow<List<Pendencia>> = st(lista(Conjunto.INTENCOES_TODAS).map { l ->
        l.filter { it.s("status") in setOf("VERIFYING", "RECEIPT_MATCHED") }.map { Pendencia(it, it.s("status") == "RECEIPT_MATCHED") }
            .sortedWith(compareByDescending<Pendencia> { it.coincide }.thenBy { it.intent.ms("receiptSubmittedAt") ?: 0 })
    }, emptyList())
    val reembolsosAdmin = st(lista(Conjunto.REEMBOLSOS_TODOS), emptyList())
    val intencoesTodas = st(lista(Conjunto.INTENCOES_TODAS), emptyList())
    val reservasTodas = st(lista(Conjunto.RESERVAS_TODAS), emptyList())

    private val _mensagem = MutableStateFlow<String?>(null); val mensagem: StateFlow<String?> = _mensagem.asStateFlow()
    private val _ocupado = MutableStateFlow<String?>(null); val ocupado: StateFlow<String?> = _ocupado.asStateFlow()
    fun limparMensagem() { _mensagem.value = null }

    init { actualizar(automatica = true) }

    fun actualizar(automatica: Boolean = false) = viewModelScope.launch {
        val ev = conta.recursos.value?.eventos ?: return@launch
        ev.actualizar(ev.essenciais + ev.administracao, automatica)
        expirarVencidas()
    }

    /** Reservas vencidas passam a EXPIRED sozinhas (Proprietário/Administrador), libertando o produto — sem esperar por ninguém. */
    private suspend fun expirarVencidas() {
        val p = perfil ?: return
        if (p.papel == Papel.CLIENTE) return
        val agora = System.currentTimeMillis()
        val vencidas = (reservasDono.value + reservasTodas.value).distinctBy { it.id }.filter { it.s("status") == "ACTIVE" && (it.ms("expiresAt") ?: Long.MAX_VALUE) < agora }
        if (vencidas.isEmpty()) return
        vencidas.take(20).forEach { r -> runCatching { transaccoes.expirarSeNecessario(FirebaseFirestore.getInstance().document("reservations/${r.id}").get().await(), p) } }
        conta.recursos.value?.eventos?.actualizar(listOf(Conjunto.RESERVAS_DONO, Conjunto.RESERVAS_TODAS))
    }

    private fun accao(chave: String, bloco: suspend () -> String?) = viewModelScope.launch {
        if (_ocupado.value != null) return@launch
        _ocupado.value = chave
        runCatching { bloco() }.onSuccess { m -> m?.let { _mensagem.value = it } }.onFailure { _mensagem.value = MensagensErro.de(it).takeIf { m -> m != MensagensErro.GENERICA } ?: it.message }
        _ocupado.value = null
        conta.recursos.value?.eventos?.let { it.actualizar(it.essenciais + it.administracao) }
    }

    // ------------------------------------------------------------------ Proprietário
    fun validarOtp(reservaId: String, codigo: String) = accao("otp:$reservaId") {
        val p = perfil ?: return@accao null
        val r = transaccoes.validarOtp(reservaId, codigo.filter(Char::isDigit), p)
        if (r == ResultadoOtp.VALIDADO || r == ResultadoOtp.JA_VALIDADO) r.mensagem else throw IllegalStateException(r.mensagem)
    }

    /** Abre a evidência protegida (regista a consulta exigida antes de confirmar). */
    fun abrirEvidencia(pedidoId: String, aoAbrir: (Intent) -> Unit) = accao("ev:$pedidoId") {
        val p = perfil ?: return@accao null
        val (mime, bytes, integra) = transaccoes.abrirEvidencia(pedidoId, p)
        val f = documentos.ficheiro("evidencia-$pedidoId.${if (mime == "application/pdf") "pdf" else "jpg"}", bytes)
        aoAbrir(documentos.abrir(f, mime))
        if (!integra) "Atenção: a evidência não confere com o hash registado." else null
    }

    fun reverReembolso(pedidoId: String, confirmado: Boolean) = accao("rev:$pedidoId") {
        transaccoes.reverReembolsoProprietario(pedidoId, confirmado, perfil ?: return@accao null)
        if (confirmado) "Confirmação registada; o Administrador conclui o reembolso." else "Contestação registada para análise do Administrador."
    }

    // ------------------------------------------------------------------ Cliente
    fun pedirReembolso(reservaId: String, otp: String, motivo: MotivoReembolso, descricao: String, evidencia: Uri) = accao("rfd:$reservaId") {
        val p = perfil ?: return@accao null
        require(descricao.trim().length >= 20) { "Descreva o que aconteceu (pelo menos 20 caracteres)." }
        val mime = contexto.contentResolver.getType(evidencia).orEmpty()
        val bytes = if (mime == "application/pdf") contexto.contentResolver.openInputStream(evidencia)!!.use { it.readBytes() }
            .also { require(it.size <= ValidacaoAnuncio.MAX_BYTES_EVIDENCIA_REEMBOLSO) { "O PDF excede 600 KB." } }
            else imagens.comprovativo(evidencia, ValidacaoAnuncio.MAX_BYTES_EVIDENCIA_REEMBOLSO)
        val doc = FirebaseFirestore.getInstance().document("reservations/$reservaId").get().await()
        transaccoes.pedirReembolso(doc, p, otp, motivo, descricao, "evidencia.${if (mime == "application/pdf") "pdf" else "jpg"}",
            if (mime == "application/pdf") "application/pdf" else "image/jpeg", bytes)
        "Pedido de reembolso enviado. O Proprietário confirma e o Administrador conclui."
    }

    fun testemunho(r: Registo, estrelas: Int, texto: String, modo: ao.kupila.gestaonegocios.dominio.testemunhos.ModoNome) = accao("tst:${r.id}") {
        require(texto.trim().length in 20..600) { "Escreva entre 20 e 600 caracteres." }
        testemunhos.submeter(r.d, r.id, perfil ?: return@accao null, estrelas, texto, modo); "Obrigado! O seu testemunho segue para moderação."
    }

    // ------------------------------------------------------------------ Administrador
    /** Comprovativo da última submissão (1 leitura, só quando o Administrador abre o cartão). */
    suspend fun comprovativo(intent: Registo): Pair<ByteArray?, String> = runCatching {
        val sid = intent.s("lastSubmissionId")
        val d = conta.recursos.value!!.eventos.ler("paymentSubmissions/$sid")
        Origem.bytesDe(d.getString("receiptDataUrl").orEmpty()) to (d.getString("receiptReference").orEmpty())
    }.getOrDefault(null to "")

    fun confirmarPagamento(intent: Registo) = accao("pay:${intent.id}") { confirmar(intent) }

    private suspend fun confirmar(intent: Registo): String {
        val p = perfil ?: error("Sessão terminada.")
        return if (intent.s("type") == "RESERVATION") {
            transaccoes.confirmarSinal(intent.id, p, ajustes.ajustes.value.prazoReservaMinutos)
            "Sinal confirmado: reserva activa e OTP entregue ao Cliente."
        } else { anuncios.publicarRascunhoPago(intent.s("listingId"), intent.id, p.uid); "Pagamento confirmado: anúncio publicado." }
    }

    /** Confirma de uma vez os comprovativos cuja referência coincide (cada um continua auditado individualmente). */
    fun confirmarCoincidentes() = accao("lote") {
        val l = pendencias.value.filter { it.coincide }
        var ok = 0; val falhas = mutableListOf<String>()
        l.forEach { runCatching { confirmar(it.intent) }.onSuccess { ok++ }.onFailure { e -> falhas += "${it.intent.s("listingTitle")}: ${MensagensErro.de(e)}" } }
        "$ok confirmado(s)." + if (falhas.isNotEmpty()) " Falharam: " + falhas.joinToString("; ") else ""
    }

    fun rejeitar(intent: Registo, motivo: String, referencia: Boolean) = accao("rej:${intent.id}") {
        transaccoes.rejeitarPagamento(intent.id, motivo.ifBlank { if (referencia) "A referência do comprovativo não corresponde." else "Pagamento não recebido." }, referencia, perfil?.uid ?: "")
        "Comprovativo rejeitado; o pagador foi informado."
    }

    fun decidirReembolso(pedidoId: String, aprovado: Boolean, fundamento: String) = accao("dec:$pedidoId") {
        transaccoes.decidirReembolso(pedidoId, aprovado, perfil ?: return@accao null, fundamento = fundamento)
        if (aprovado) "Reembolso aprovado; aguarda a confirmação da transferência." else "Reembolso recusado."
    }

    fun confirmarReembolsoPago(pedidoId: String, ref: String) = accao("rpg:$pedidoId") {
        require(ref.isNotBlank()) { "Indique a referência da transferência." }
        transaccoes.confirmarReembolsoPago(pedidoId, ref.trim(), perfil ?: return@accao null); "Reembolso registado como pago."
    }

    fun confirmarLiberacao(reservaId: String, ref: String) = accao("lib:$reservaId") {
        require(ref.isNotBlank()) { "Indique a referência da transferência." }
        transaccoes.confirmarLiberacao(reservaId, ref.trim(), perfil ?: return@accao null); "Liberação ao Proprietário confirmada."
    }

    fun cancelarReserva(reservaId: String, motivo: String) = accao("can:$reservaId") {
        require(motivo.isNotBlank()) { "Indique o motivo do cancelamento." }
        transaccoes.cancelarReserva(reservaId, motivo.trim(), perfil ?: return@accao null); "Reserva cancelada; produto disponível de novo."
    }
}
