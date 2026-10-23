package ao.kupila.gestaonegocios.dominio.rascunhos

import java.time.Instant

/** Resumo de um cadastro inacabado (`modal-product-drafts`). */
data class ResumoRascunho(
    val id: String,
    val titulo: String,
    val actualizadoEm: Instant,
    val percentagemConclusao: Int,
    /** Rascunho associado a cobrança/pagamento em curso ou evidência: nunca é removido automaticamente. */
    val protegido: Boolean = false,
    val edicaoDeAnuncioId: String? = null,
)

/**
 * Até cinco cadastros inacabados por utilizador (`KUPILA_PRODUCT_DRAFT_LIMIT`, HTML 9386); ao exceder,
 * substitui o mais antigo (`pruneProductDrafts`, 9576) — com aviso prévio e nunca um rascunho protegido.
 */
object PoliticaRascunhos {
    const val LIMITE = 5

    /** Rascunho que sairá da lista ao criar um novo (para avisar antes), ou null se houver espaço. */
    fun aSubstituir(existentes: List<ResumoRascunho>): ResumoRascunho? {
        val livres = existentes.filter { it.edicaoDeAnuncioId == null }
        if (livres.size < LIMITE) return null
        return livres.filterNot { it.protegido }.minByOrNull { it.actualizadoEm }
    }

    /** Sem candidato removível (todos protegidos) → não se pode criar sem o utilizador resolver um. */
    fun podeCriarNovo(existentes: List<ResumoRascunho>) =
        existentes.count { it.edicaoDeAnuncioId == null } < LIMITE || aSubstituir(existentes) != null
}

/** Conteúdo útil vs ficha vazia (`productDraftHasContent`, 9567) e % de conclusão (`productDraftCompletion`, 9572). */
object ConclusaoRascunho {
    data class Campos(
        val titulo: Boolean, val preco: Boolean, val localizacao: Boolean, val bairroReferencia: Boolean,
        val caracteristicas: Boolean, val fotos: Boolean, val documentos: Boolean, val recebimento: Boolean, val aceite: Boolean,
    )

    fun temConteudo(c: Campos) = c.titulo || c.preco || c.caracteristicas || c.fotos || c.documentos

    fun percentagem(c: Campos): Int {
        val v = listOf(c.titulo, c.preco, c.localizacao, c.bairroReferencia, c.caracteristicas, c.fotos, c.documentos, c.recebimento, c.aceite)
        return v.count { it } * 100 / v.size
    }
}
