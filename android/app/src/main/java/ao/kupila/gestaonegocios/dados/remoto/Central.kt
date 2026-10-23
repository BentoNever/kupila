package ao.kupila.gestaonegocios.dados.remoto

import ao.kupila.gestaonegocios.BuildConfig
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Erro devolvido pelo Central (ou pela rede) — sempre explicável ao utilizador.
 * [desconhecido] = timeout/sem resposta: o pedido PODE ter sido aceite; consultar antes de repetir (secção 20).
 */
class ErroCentral(
    val codigo: String,
    mensagem: String,
    val motivo: String? = null,
    val detalhes: Map<*, *>? = null,
    val desconhecido: Boolean = false,
) : Exception(mensagem) {
    val semRede get() = codigo == "unavailable" || desconhecido
    val sessaoSubstituida get() = motivo == "SESSAO_SUBSTITUIDA" || motivo == "SESSAO_EM_FALTA"
    val conflitoVersao get() = motivo == "CONFLITO_VERSAO" || codigo == "aborted"
}

/** Cliente dos comandos do Central (Callable Functions). Nunca envia segredos de provedor; só dados do pedido. */
@Singleton
class Central @Inject constructor() {

    private val funcoes by lazy { FirebaseFunctions.getInstance(BuildConfig.FUNCTIONS_REGIAO) }

    suspend fun chamar(nome: String, dados: Map<String, Any?> = emptyMap(), timeoutSegundos: Long = 30): Map<*, *> {
        if (!ArranqueFirebase.disponivel) throw ErroCentral("unavailable", "Serviço central não configurado neste build.")
        try {
            val r = funcoes.getHttpsCallable(nome).apply { setTimeout(timeoutSegundos, TimeUnit.SECONDS) }.call(dados).await()
            return r.getData() as? Map<*, *> ?: emptyMap<String, Any?>()
        } catch (e: FirebaseFunctionsException) {
            val det = e.details as? Map<*, *>
            val codigo = e.code.name.lowercase().replace('_', '-')
            val desconhecido = e.code == FirebaseFunctionsException.Code.DEADLINE_EXCEEDED || e.code == FirebaseFunctionsException.Code.UNAVAILABLE
            val msg = when {
                e.code == FirebaseFunctionsException.Code.DEADLINE_EXCEEDED -> "O serviço demorou a responder. O resultado ainda não é conhecido; vamos confirmar antes de repetir."
                e.code == FirebaseFunctionsException.Code.UNAVAILABLE -> "Sem ligação ao serviço. Verifique a internet e tente novamente."
                e.code == FirebaseFunctionsException.Code.INTERNAL && e.message.isNullOrBlank() -> "O serviço não conseguiu concluir a operação. Tente novamente."
                else -> e.message ?: "Operação não concluída."
            }
            throw ErroCentral(codigo, msg, det?.get("motivo") as? String, det, desconhecido)
        } catch (e: java.io.IOException) {
            throw ErroCentral("unavailable", "Sem ligação ao serviço. Verifique a internet e tente novamente.", desconhecido = true)
        }
    }
}
