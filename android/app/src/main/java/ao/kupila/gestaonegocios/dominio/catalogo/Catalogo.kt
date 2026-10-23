package ao.kupila.gestaonegocios.dominio.catalogo

import ao.kupila.gestaonegocios.dominio.financeiro.Promocao
import java.time.Duration
import java.time.Instant

/** Categorias e subcategorias exactas da origem (`PRODUCT_SUBCATEGORIES`, HTML 13631-13637). */
enum class Categoria(val codigo: String, val rotulo: String, val rotuloPlural: String, val subcategorias: List<String>) {
    IMOVEL("IMOVEL", "Imóvel", "Imóveis", listOf("Vivenda / Moradia", "Apartamento", "Escritório", "Loja / Espaço comercial", "Armazém", "Outro imóvel")),
    TERRENO("TERRENO", "Terreno", "Terrenos", listOf("Residencial", "Comercial", "Agrícola", "Industrial", "Misto", "Outro terreno")),
    VIATURA("VIATURA", "Viatura", "Viaturas", listOf("Turismo", "SUV / Todo-terreno", "Pick-up", "Carrinha", "Camião", "Autocarro", "Outro veículo")),
    MOTORIZADA("MOTORIZADA", "Motorizada", "Motorizadas", listOf("Scooter", "Street / Urbana", "Trail / Off-road", "Desportiva", "Trabalho / Entrega", "Outra motorizada")),
    OUTRO("OUTRO", "Outros Produtos", "Outros Produtos", listOf("Eletrónicos", "Equipamentos", "Mobiliário", "Máquinas / Ferramentas", "Peças / Acessórios", "Materiais", "Outros produtos"));

    companion object { fun de(c: String?) = entries.firstOrNull { it.codigo == c } }
}

/** Vitrines (`setFeaturedMode`, 13120 / 15855). */
enum class Vitrine(val codigo: String, val titulo: String, val subtitulo: String, val rotuloCurto: String) {
    DESTAQUES("FEATURED", "Mais Destacados", "Selecção inteligente e transparente", "Destaques"),
    RECENTES("RECENT", "Mais Recentes", "Novos anúncios publicados na plataforma", "Recentes"),
    EM_ALTA("TRENDING", "Em Alta", "Interesse orgânico recente", "Em alta"),
    PROCURADOS("POPULAR", "Mais Procurados", "Procura e reservas reais", "Procurados"),
    PATROCINADOS("SPONSORED", "Top Patrocinados", "Promoções pagas, claramente identificadas", "Patrocinados"),
}

/** Métricas de envolvimento calculadas pelo servidor (deduplicadas; auto-visualização excluída). */
data class Envolvimento(
    val visitasQualificadas: Int = 0,
    val interesse24h: Int = 0,
    val reservas: Int = 0,
    val negociosConcluidos: Int = 0,
    val ultimoInteresse: Instant? = null,
    val ultimaReserva: Instant? = null,
)

/** Promoção paga confirmada e ainda vigente. */
data class PromocaoActiva(val servicos: Set<Promocao>, val pagaEm: Instant, val expiraEm: Instant?) {
    fun vigente(agora: Instant) = expiraEm == null || expiraEm.isAfter(agora)
    /** Peso de ordenação dos Patrocinados (`promotionMeta`, HTML 15852): o maior serviço contratado. */
    val impulso get() = when {
        Promocao.TOP in servicos -> 30
        Promocao.DESTAQUE in servicos -> 18
        Promocao.PRIORIDADE in servicos -> 10
        else -> 0
    }
}

/** Dados mínimos para ordenar vitrines (projecção pública). */
data class ItemVitrine(
    val id: String,
    val categoria: Categoria,
    val criadoEm: Instant,
    val envolvimento: Envolvimento,
    val promocao: PromocaoActiva?,
)

enum class Selo(val rotulo: String) { TOP_PATROCINADO("Top Patrocinado"), PATROCINADO("Patrocinado"), NOVO("Novo"), EM_ALTA("Em Alta"), MAIS_PROCURADO("Mais Procurado"), DESTAQUE("Destaque"), CATEGORIA("") }

/** Ordenação e selos das vitrines, portados de `listingFeaturedScore`/`rankFeaturedItems`/`listingHighlightMeta`. */
object RegrasVitrine {
    private fun dias(de: Instant, ate: Instant) = Duration.between(de, ate).toMillis().coerceAtLeast(0) / 86_400_000.0

    fun frescura(i: ItemVitrine, agora: Instant) = (28 - minOf(28.0, dias(i.criadoEm, agora) * 2.2)).coerceAtLeast(0.0)

    fun pontuacao(i: ItemVitrine, agora: Instant): Double {
        val e = i.envolvimento
        val interesseRecente = e.ultimoInteresse?.let { Duration.between(it, agora).toMillis() < 86_400_000 } == true
        return frescura(i, agora) + minOf(30.0, e.interesse24h * 8.0) + minOf(22.0, e.visitasQualificadas * 1.8) +
            minOf(28.0, e.reservas * 9.0) + if (interesseRecente) 8.0 else 0.0
    }

    fun ordenar(itens: List<ItemVitrine>, v: Vitrine, agora: Instant): List<ItemVitrine> = when (v) {
        Vitrine.RECENTES -> itens.sortedByDescending { it.criadoEm }
        Vitrine.EM_ALTA -> itens.sortedWith(compareByDescending<ItemVitrine> { it.envolvimento.interesse24h }
            .thenByDescending { it.envolvimento.ultimoInteresse ?: Instant.EPOCH }.thenByDescending { pontuacao(it, agora) })
        Vitrine.PROCURADOS -> itens.sortedWith(compareByDescending<ItemVitrine> { it.envolvimento.reservas * 10 + it.envolvimento.visitasQualificadas }
            .thenByDescending { it.envolvimento.ultimaReserva ?: Instant.EPOCH })
        // Só conteúdo pago vigente, sempre identificado.
        Vitrine.PATROCINADOS -> itens.filter { it.promocao?.vigente(agora) == true }
            .sortedWith(compareByDescending<ItemVitrine> { it.promocao!!.impulso }.thenByDescending { it.promocao!!.pagaEm })
        Vitrine.DESTAQUES -> itens.sortedByDescending { pontuacao(it, agora) }
    }

    fun selo(i: ItemVitrine, agora: Instant): Selo {
        val p = i.promocao
        if (p != null && p.vigente(agora)) return if (Promocao.TOP in p.servicos) Selo.TOP_PATROCINADO else Selo.PATROCINADO
        val e = i.envolvimento
        return when {
            dias(i.criadoEm, agora) <= 3 -> Selo.NOVO
            e.interesse24h >= 3 -> Selo.EM_ALTA
            e.reservas >= 2 || e.visitasQualificadas >= 8 -> Selo.MAIS_PROCURADO
            pontuacao(i, agora) >= 32 -> Selo.DESTAQUE
            else -> Selo.CATEGORIA
        }
    }
}

/** Filtros combináveis da Home (`applyFilters`, 13150). */
data class FiltroCatalogo(
    val texto: String = "",
    val categoria: Categoria? = null,
    val modalidade: ao.kupila.gestaonegocios.dominio.financeiro.Modalidade? = null,
    val provincia: String? = null,
    val vitrine: Vitrine = Vitrine.DESTAQUES,
) {
    val activo get() = texto.isNotBlank() || categoria != null || modalidade != null || provincia != null
}
