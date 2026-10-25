package ao.kupila.gestaonegocios.ui.checkout

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ao.kupila.gestaonegocios.dados.ContaActiva
import ao.kupila.gestaonegocios.dados.media.Documentos
import ao.kupila.gestaonegocios.dados.media.Imagens
import ao.kupila.gestaonegocios.dados.remoto.MensagensErro
import ao.kupila.gestaonegocios.dados.remoto.Origem
import ao.kupila.gestaonegocios.dados.transaccoes.TransaccoesRepositorio
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Fase do checkout derivada do estado real da intenção (nunca de um sucesso presumido — secção 20). */
enum class FaseCheckout { CARREGAR, PAGAR, VERIFICACAO, REJEITADO, CONCLUIDO, EXPIRADO, INEXISTENTE }

data class EstadoCheckout(
    val intent: Map<String, Any?> = emptyMap(),
    val fase: FaseCheckout = FaseCheckout.CARREGAR,
    val metodo: String = "REF",
    val telefoneExpress: String = "",
    val comprovativo: ByteArray? = null,
    val referenciaLida: String = "",
    val textoOcr: String = "",
    val aLer: Boolean = false,
    val aEnviar: Boolean = false,
    val erro: String? = null,
) {
    val id get() = intent["id"] as? String ?: ""
    val tipo get() = intent["type"] as? String ?: ""
    val referencia get() = intent["reference"] as? String ?: ""
    val confere get() = referenciaLida.isNotBlank() && referenciaLida == referencia
}

/**
 * Checkout em 2 passos (secção 19): pagar com a referência e mostrar o comprovativo. O OCR lê a referência no aparelho; se coincide,
 * o comprovativo segue sozinho (zero toques extra). A confirmação final é sempre humana (Administrador) até haver provedor (D-02).
 * Escuta só o documento desta intenção e só enquanto o ecrã está aberto.
 */
@HiltViewModel
class CheckoutViewModel @Inject constructor(
    private val conta: ContaActiva,
    private val transaccoes: TransaccoesRepositorio,
    private val imagens: Imagens,
    private val documentos: Documentos,
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val contexto: android.content.Context,
    guardado: SavedStateHandle,
) : ViewModel() {
    private val intentId: String = checkNotNull(guardado["id"])
    private val _e = MutableStateFlow(EstadoCheckout(telefoneExpress = conta.recursos.value?.perfil?.telefonePrincipal.orEmpty()))
    val estado: StateFlow<EstadoCheckout> = _e.asStateFlow()
    private val parar: () -> Unit

    init {
        parar = conta.recursos.value?.eventos?.seguir("paymentIntents/$intentId") { r ->
            if (r == null) { _e.update { it.copy(fase = FaseCheckout.INEXISTENTE) }; return@seguir }
            val d = r.d + ("id" to r.id)
            // Método lembrado: sem método escolhido nesta intenção, usa o da última vez (um passo a menos).
            val lembrado = ao.kupila.gestaonegocios.dados.rascunhos.RascunhoAuto.ler(contexto, "checkout.metodo")
            val semMetodo = (d["method"] as? String).isNullOrBlank()
            _e.update { it.copy(intent = d, fase = fase(d), metodo = (d["method"] as? String)?.takeIf { m -> m.isNotBlank() } ?: lembrado ?: it.metodo) }
            if (semMetodo && lembrado != null && fase(d) == FaseCheckout.PAGAR) metodo(lembrado)
        } ?: {}
    }

    private fun fase(d: Map<String, Any?>): FaseCheckout {
        val s = d["status"] as? String ?: ""
        return when {
            s == "COMPLETED" || s == "PAID" || s == "PUBLISHED" -> FaseCheckout.CONCLUIDO
            s == "VERIFYING" || s == "RECEIPT_MATCHED" || s == "PROCESSING" || s == "AWAITING_PROVIDER_CONFIRMATION" -> FaseCheckout.VERIFICACAO
            s.startsWith("REJECTED") -> FaseCheckout.REJEITADO
            (Origem.ms(d["expiresAt"]) ?: Long.MAX_VALUE) < System.currentTimeMillis() || s == "EXPIRED" || s == "CANCELLED" -> FaseCheckout.EXPIRADO
            else -> FaseCheckout.PAGAR
        }
    }

    fun metodo(m: String) {
        _e.update { it.copy(metodo = m) }
        ao.kupila.gestaonegocios.dados.rascunhos.RascunhoAuto.escrever(contexto, "checkout.metodo", m)
        viewModelScope.launch { runCatching { transaccoes.metodo(intentId, m, _e.value.telefoneExpress) } }
    }
    fun telefone(t: String) = _e.update { it.copy(telefoneExpress = t.filter(Char::isDigit).take(9)) }

    /** Lê o comprovativo; se a referência coincidir, envia de imediato. */
    fun comprovativo(uri: Uri) = viewModelScope.launch {
        _e.update { it.copy(aLer = true, erro = null) }
        runCatching {
            val jpeg = imagens.comprovativo(uri, LIMITE_INLINE)
            val (texto, ref) = documentos.lerReferencia(jpeg, _e.value.referencia)
            _e.update { it.copy(comprovativo = jpeg, textoOcr = texto, referenciaLida = ref, aLer = false) }
        }.onFailure { x -> _e.update { it.copy(aLer = false, erro = x.message ?: MensagensErro.GENERICA) } }
        if (_e.value.confere) enviar(manual = false)
    }

    fun enviar(manual: Boolean = true) = viewModelScope.launch {
        val e = _e.value
        val p = conta.recursos.value?.perfil ?: return@launch
        val jpeg = e.comprovativo ?: return@launch
        if (e.aEnviar) return@launch
        if (e.metodo == "EXPRESS" && e.telefoneExpress.length != 9) { _e.update { it.copy(erro = "Indique o número Multicaixa Express usado (9 dígitos).") }; return@launch }
        _e.update { it.copy(aEnviar = true, erro = null) }
        runCatching { transaccoes.submeterComprovativo(e.intent, p, jpeg, e.textoOcr, e.referenciaLida, manual, e.telefoneExpress) }
            .onSuccess { _e.update { it.copy(aEnviar = false, comprovativo = null) } }
            .onFailure { x -> _e.update { it.copy(aEnviar = false, erro = MensagensErro.de(x)) } }
    }

    fun descartar() = _e.update { it.copy(comprovativo = null, referenciaLida = "", textoOcr = "") }

    override fun onCleared() { parar(); super.onCleared() }

    companion object { const val LIMITE_INLINE = 600L * 1024 }   // documento Firestore ≤ 1 MiB com o resto dos campos
}
