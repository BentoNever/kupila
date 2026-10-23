package ao.kupila.gestaonegocios.dominio.autorizacoes

import ao.kupila.gestaonegocios.dominio.anuncio.EstadoAnuncio

/** Acções sujeitas a autorização (`requestListingAuthorization`, HTML 14613). */
enum class AccaoAutorizada(val codigo: String, val verbo: String, val substantivo: String) {
    EXCLUIR("DELETE", "excluir", "exclusão"), DESANUNCIAR("UNPUBLISH", "desanunciar", "desanúncio");
    companion object { fun de(c: String?) = entries.firstOrNull { it.codigo == c } }
}

enum class EstadoAutorizacao(val codigo: String, val rotulo: String) {
    PENDENTE_ADMIN("PENDING_ADMIN", "Aguarda o Administrador"),
    CODIGO_ENVIADO("CODE_SENT", "Código enviado"),
    USADO("USED", "Utilizado"),
    EXPIRADO("EXPIRED", "Expirado"),
    RECUSADO("DENIED", "Recusado");
    companion object { fun de(c: String?) = entries.firstOrNull { it.codigo == c } }
    val emCurso get() = this == PENDENTE_ADMIN || this == CODIGO_ENVIADO
}

object RegrasAutorizacao {
    /** Validade do código (30 min, `generateAuthorizationCode`, 14651). */
    const val VALIDADE_CODIGO_MIN = 30L

    /** Motivo pelo qual o Proprietário não pode pedir, ou null se pode. Mensagens da origem. */
    fun impedimento(accao: AccaoAutorizada, estado: EstadoAnuncio, eTitular: Boolean, pedidoEmCurso: Boolean, emailValido: Boolean): String? = when {
        !eTitular -> "Sem permissão para solicitar esta operação."
        estado == EstadoAnuncio.RESERVADO || estado == EstadoAnuncio.CONCLUIDO -> "A operação não pode ser solicitada no estado actual do produto."
        accao == AccaoAutorizada.DESANUNCIAR && estado != EstadoAnuncio.ACTIVO -> "Este produto não está activo."
        pedidoEmCurso -> "Já existe um pedido de autorização em curso."
        !emailValido -> "Actualize primeiro o e-mail funcional do seu cadastro."
        else -> null
    }

    fun confirmacao(accao: AccaoAutorizada, titulo: String) =
        "Solicitar ao Administrador autorização para ${accao.verbo} “$titulo”? Não haverá reembolso de taxas/comissões pagas."

    /** Destino mascarado (`maskDestination`, 14646). */
    fun mascarar(destino: String): String = if ('@' in destino) {
        val (a, b) = destino.split('@', limit = 2); (a.take(2).ifEmpty { "*" }) + "***@" + b
    } else if (destino.length >= 9) "+244 *** *** " + destino.takeLast(3) else destino
}
