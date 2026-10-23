package ao.kupila.gestaonegocios.dados.remoto

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FirebaseFirestoreException

/** Mensagens compreensíveis, portadas de `firebaseErrorMessage` (HTML 10987-11016). Nunca mostram detalhes técnicos. */
object MensagensErro {
    const val CREDENCIAIS = "E-mail ou palavra-passe incorretos. Confirme os dados e tente novamente ou use “Esqueceu as credenciais?”."
    const val SEM_REDE = "Não foi possível comunicar com o serviço agora. Verifique a ligação à Internet e tente novamente."
    const val GENERICA = "Não foi possível concluir esta ação agora. Os dados preenchidos foram preservados. Tente novamente; se o problema continuar, contacte o Administrador."

    private val auth = mapOf(
        "ERROR_EMAIL_ALREADY_IN_USE" to "Este e-mail já está associado a uma conta. Entre normalmente ou recupere as credenciais.",
        "ERROR_INVALID_EMAIL" to "Informe um e-mail válido.",
        "ERROR_WEAK_PASSWORD" to "Escolha uma palavra-passe mais segura, com pelo menos 6 caracteres.",
        "ERROR_OPERATION_NOT_ALLOWED" to "Este método de acesso está temporariamente indisponível. Contacte o Administrador.",
        "ERROR_USER_DISABLED" to "Esta conta está temporariamente indisponível. Contacte o Administrador.",
        "ERROR_TOO_MANY_REQUESTS" to "Demasiadas tentativas. Aguarde alguns minutos antes de tentar novamente.",
    )

    fun de(e: Throwable): String = when (e) {
        is ErroCentral -> e.message ?: GENERICA
        is FirebaseAuthInvalidCredentialsException -> if (e.errorCode == "ERROR_INVALID_EMAIL") auth.getValue("ERROR_INVALID_EMAIL") else CREDENCIAIS
        is FirebaseAuthInvalidUserException -> if (e.errorCode == "ERROR_USER_DISABLED") auth.getValue("ERROR_USER_DISABLED") else CREDENCIAIS
        is FirebaseAuthException -> auth[e.errorCode] ?: GENERICA
        is FirebaseNetworkException -> SEM_REDE
        is FirebaseFirestoreException -> when (e.code) {
            FirebaseFirestoreException.Code.UNAVAILABLE -> "Não foi possível sincronizar os dados agora. O app manterá as informações já guardadas e tentará novamente quando a ligação estabilizar."
            FirebaseFirestoreException.Code.PERMISSION_DENIED -> "Sem permissão para consultar esta informação."
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> "A operação demorou mais do que o esperado. Os dados foram preservados; tente novamente."
            FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED -> "Este recurso está temporariamente indisponível. Os dados já preenchidos foram preservados. Tente novamente mais tarde."
            else -> GENERICA
        }
        is java.io.IOException -> SEM_REDE
        else -> GENERICA
    }
}
