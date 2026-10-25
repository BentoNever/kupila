package ao.kupila.gestaonegocios.dados.transaccoes

import ao.kupila.gestaonegocios.dados.ContaActiva
import ao.kupila.gestaonegocios.dados.ajustes.AjustesRepositorio
import ao.kupila.gestaonegocios.dados.anuncios.AnunciosRepositorio
import ao.kupila.gestaonegocios.dados.eventos.Conjunto
import ao.kupila.gestaonegocios.dados.eventos.Registo
import ao.kupila.gestaonegocios.dominio.perfis.Papel
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ZERO BUROCRACIA no lado do Administrador. Corre na app do Administrador sempre que os dados sincronizam, sem toques:
 *
 *  - Pagamentos: confirma sozinho quando a referência lida no comprovativo COINCIDE com a da intenção, o MONTANTE aparece no
 *    texto do comprovativo e o mesmo comprovativo (hash) não foi usado noutro pagamento. O resto fica para decisão humana.
 *  - Reembolsos: quando o Proprietário confirmou que o negócio não foi fechado, verifica a integridade da evidência (hash) e
 *    aprova; a ordem de pagamento segue para a confirmação financeira (marcar como pago continua humano).
 *
 * Cada acção fica na auditoria com o motivo "automático". Pode ser desligada nos Pendentes (preferência deste aparelho).
 */
@Singleton
class AutomacaoAdmin @Inject constructor(
    private val conta: ContaActiva,
    private val transaccoes: TransaccoesRepositorio,
    private val anuncios: AnunciosRepositorio,
    private val ajustes: AjustesRepositorio,
) {
    private val mutex = Mutex()
    /** Já tentados nesta sessão (nunca repete uma tentativa falhada em ciclo). */
    private val tentados = ConcurrentHashMap.newKeySet<String>()

    suspend fun executar(intencoes: List<Registo>, reembolsos: List<Registo>): Int = mutex.withLock {
        val r = conta.recursos.value ?: return 0
        val admin = r.perfil
        if (admin.papel != Papel.ADMINISTRADOR || r.offline || !ajustes.autoConfirmar.value) return 0
        var feitos = 0
        intencoes.filter { it.s("status") == "RECEIPT_MATCHED" && tentados.add("pay:${it.id}") }.take(10).forEach { i ->
            if (runCatching { pagamentoSeguro(i) }.getOrDefault(false)) runCatching {
                if (i.s("type") == "RESERVATION") transaccoes.confirmarSinal(i.id, admin, ajustes.ajustes.value.prazoReservaMinutos, modo = "AUTO_REFERENCE_AMOUNT")
                else anuncios.publicarRascunhoPago(i.s("listingId"), i.id, admin.uid)
                feitos++
            }
        }
        reembolsos.filter { it.s("status") == "OWNER_CONFIRMED" && tentados.add("rfd:${it.id}") }.take(10).forEach { rf ->
            runCatching {
                val (_, _, integra) = transaccoes.abrirEvidencia(rf.id, admin)
                if (integra) { transaccoes.decidirReembolso(rf.id, true, admin, fundamento = "Aprovação automática: o Proprietário confirmou que o negócio não foi fechado e a evidência está íntegra."); feitos++ }
            }
        }
        if (feitos > 0) r.eventos.actualizar(listOf(Conjunto.INTENCOES_TODAS, Conjunto.REEMBOLSOS_TODOS, Conjunto.RESERVAS_TODAS))
        feitos
    }

    /** Referência coincide + montante no comprovativo + comprovativo nunca usado noutro pagamento. */
    private suspend fun pagamentoSeguro(i: Registo): Boolean {
        val sid = i.s("lastSubmissionId").ifBlank { return false }
        val db = FirebaseFirestore.getInstance()
        val s = db.document("paymentSubmissions/$sid").get().await()
        if (!s.exists()) return false
        val esperada = i.s("reference").filter(Char::isDigit)
        if (esperada.isBlank() || s.getString("receiptReference").orEmpty().filter(Char::isDigit).takeLast(9) != esperada) return false
        val montante = i.n("amount").toLong()
        val digitos = s.getString("receiptText").orEmpty().filter(Char::isDigit)
        if (montante <= 0 || !digitos.contains(montante.toString())) return false
        val hash = s.getString("receiptHash").orEmpty().ifBlank { return false }
        val usos = db.collection("paymentSubmissions").whereEqualTo("receiptHash", hash).limit(3).get().await()
        return usos.documents.all { it.getString("paymentIntentId") == i.id }
    }
}
