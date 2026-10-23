package ao.kupila.gestaonegocios.dados.remoto

import ao.kupila.gestaonegocios.dominio.anuncio.AnuncioPublico
import ao.kupila.gestaonegocios.dominio.anuncio.DetalhesCategoria
import ao.kupila.gestaonegocios.dominio.anuncio.Diferenciais
import ao.kupila.gestaonegocios.dominio.anuncio.EstadoAnuncio
import ao.kupila.gestaonegocios.dominio.anuncio.EstadoVerificacao
import ao.kupila.gestaonegocios.dominio.anuncio.Fotografia
import ao.kupila.gestaonegocios.dominio.catalogo.Categoria
import ao.kupila.gestaonegocios.dominio.catalogo.Envolvimento
import ao.kupila.gestaonegocios.dominio.catalogo.PromocaoActiva
import ao.kupila.gestaonegocios.dominio.financeiro.CondicoesAluguer
import ao.kupila.gestaonegocios.dominio.financeiro.Dinheiro
import ao.kupila.gestaonegocios.dominio.financeiro.Modalidade
import ao.kupila.gestaonegocios.dominio.financeiro.PrazoMinimo
import ao.kupila.gestaonegocios.dominio.financeiro.Promocao
import ao.kupila.gestaonegocios.dominio.financeiro.UnidadeDuracao
import ao.kupila.gestaonegocios.dominio.financeiro.UnidadePreco
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.Instant

/**
 * Projecção pública de `products/{id}` com os NOMES DE CAMPOS DA ORIGEM (decisão D-20).
 * É o que vai para o cache público Room — por construção não tem campos privados (D-22): qualquer campo
 * desconhecido do documento remoto (incluindo `otp`, `doc`, `ownerSnapshot` legados) é descartado aqui.
 */
@Serializable
data class ProdutoDto(
    val id: String,
    val ownerId: String = "",
    val title: String = "",
    val category: String = "",
    val subcategory: String = "",
    val intent: String = "",
    val priceText: String = "0",
    val rentalTerms: TermosAluguerDto? = null,
    val province: String = "",
    val municipality: String = "",
    val categoryDetails: Map<String, String> = emptyMap(),
    val qualities: List<String> = emptyList(),
    val images: List<ImagemDto> = emptyList(),
    val status: String = "",
    val createdAtMs: Long = 0,
    val updatedAtMs: Long = 0,
    val version: Long = 0,
    val engagement: EnvolvimentoDto = EnvolvimentoDto(),
    val promotionServices: List<String> = emptyList(),
    val promotionPaidAtMs: Long? = null,
    val promotionExpiresAtMs: Long? = null,
    val publisherRole: String = "",
    val financialSnapshotId: String? = null,
    val verification: String = "PENDENTE",
)

@Serializable data class ImagemDto(val url: String, val thumb: String? = null, val synthetic: Boolean = false)
@Serializable data class EnvolvimentoDto(val qualifiedViews: Int = 0, val interest24h: Int = 0, val reservationCount: Int = 0,
    val completedDeals: Int = 0, val lastInterestAtMs: Long? = null, val lastReservedAtMs: Long? = null)
@Serializable data class TermosAluguerDto(val priceUnit: String = "MONTHLY", val otherPriceUnit: String = "", val minimumPreset: String = "3_MONTHS",
    val minimumCount: Int = 0, val minimumUnit: String = "MONTH", val monthlyUnitsEquivalent: String? = null)

object MapeamentoProduto {

    /** Converte timestamps da origem (ISO texto), do Central (ms) ou Timestamp do Firestore. */
    fun ms(v: Any?): Long? = when (v) {
        null -> null
        is Number -> v.toLong()
        is com.google.firebase.Timestamp -> v.toDate().time
        is String -> runCatching { Instant.parse(v).toEpochMilli() }.getOrNull()
        else -> null
    }

    private fun texto(v: Any?) = when (v) { null -> ""; is String -> v; is Number -> BigDecimal(v.toString()).stripTrailingZeros().toPlainString(); else -> v.toString() }

    /** Documento Firestore → DTO público (lista branca de campos). */
    fun deFirestore(id: String, d: Map<String, Any?>): ProdutoDto {
        val location = d["location"] as? String ?: ""
        val province = d["province"] as? String ?: ""
        val municipality = (d["municipality"] as? String)?.takeIf { it.isNotBlank() }
            ?: location.removeSuffix(", $province").takeIf { it.isNotBlank() } ?: ""
        val det = (d["categoryDetails"] as? Map<*, *>) ?: (d["propertyDetails"] as? Map<*, *>) ?: emptyMap<String, Any?>()
        val qualidades = (det["qualities"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        val detalhes = det.entries.filter { it.key != "qualities" && it.value != null && it.value !is Map<*, *> && it.value !is List<*> }
            .associate { it.key.toString() to texto(it.value) }
            .filterKeys { it !in CAMPOS_PRIVADOS }
        val imgsMeta = (d["imagesMeta"] as? List<*>)?.mapNotNull { m -> (m as? Map<*, *>)?.let { ImagemDto(it["url"] as? String ?: return@mapNotNull null, it["thumb"] as? String, it["synthetic"] == true) } }
        // Legado: a origem guardava as fotos como data:image/… no próprio documento (`assetStorageMode: FIRESTORE_FREE`, 12275).
        val imgs = imgsMeta ?: (d["images"] as? List<*>)?.mapNotNull { (it as? String)?.takeIf { u -> u.startsWith("https://") || u.startsWith("data:image/") }?.let(::ImagemDto) } ?: emptyList()
        val rt = d["rentalTerms"] as? Map<*, *>
        val e = d["engagementStats"] as? Map<*, *> ?: emptyMap<String, Any?>()
        val preco = d["priceText"] as? String ?: (d["priceCents"] as? Number)?.let { BigDecimal(it.toLong()).movePointLeft(2).toPlainString() } ?: texto(d["price"])
        return ProdutoDto(
            id = id, ownerId = d["ownerId"] as? String ?: "", title = d["title"] as? String ?: "", category = d["category"] as? String ?: "",
            subcategory = (d["subcategory"] as? String)?.ifBlank { null } ?: (det["subcategory"] as? String ?: ""),
            intent = d["intent"] as? String ?: "", priceText = preco.ifBlank { "0" },
            rentalTerms = rt?.let { TermosAluguerDto(it["priceUnit"] as? String ?: "MONTHLY", it["otherPriceUnit"] as? String ?: "",
                it["minimumPreset"] as? String ?: "3_MONTHS", (it["minimumCount"] as? Number)?.toInt() ?: 0, it["minimumUnit"] as? String ?: "MONTH",
                (it["monthlyUnitsEquivalent"])?.let(::texto)) },
            province = province, municipality = municipality, categoryDetails = detalhes, qualities = qualidades, images = imgs,
            status = d["status"] as? String ?: "", createdAtMs = ms(d["createdAtMs"] ?: d["createdAt"]) ?: 0, updatedAtMs = ms(d["updatedAtMs"] ?: d["updatedAt"]) ?: 0,
            version = (d["version"] as? Number)?.toLong() ?: 0,
            engagement = EnvolvimentoDto((e["qualifiedViews"] as? Number)?.toInt() ?: 0, (e["interest24h"] as? Number)?.toInt() ?: 0,
                (e["reservationCount"] as? Number)?.toInt() ?: 0, (e["completedDeals"] as? Number)?.toInt() ?: 0, ms(e["lastInterestAt"]), ms(e["lastReservedAt"])),
            promotionServices = (d["promotionServices"] as? List<*>)?.mapNotNull { (it as? String) ?: ((it as? Map<*, *>)?.get("code") as? String) } ?: emptyList(),
            promotionPaidAtMs = ms(d["promotionPaidAt"]), promotionExpiresAtMs = ms(d["promotionExpiresAt"]),
            publisherRole = d["publisherRole"] as? String ?: "", financialSnapshotId = d["financialSnapshotId"] as? String,
            verification = d["verification"] as? String ?: "PENDENTE",
        )
    }

    /** Campos que nunca podem constar do público mesmo se um registo legado os tiver dentro de categoryDetails. */
    private val CAMPOS_PRIVADOS = setOf("neighborhood", "referencePoint", "doc", "otp", "ownerIban", "payoutExpressPhone", "ownerPhone", "ownerEmail")

    fun paraDominio(d: ProdutoDto): AnuncioPublico? {
        val categoria = Categoria.de(d.category) ?: return null
        val modalidade = Modalidade.daOrigem(d.intent) ?: return null
        val preco = runCatching { Dinheiro.de(d.priceText) }.getOrNull() ?: return null
        val det = d.categoryDetails
        fun s(k: String) = det[k].orEmpty()
        fun i(k: String) = det[k]?.toBigDecimalOrNull()?.toInt()
        fun l(k: String) = det[k]?.toBigDecimalOrNull()?.toLong()
        val sub = d.subcategory
        val detalhes: DetalhesCategoria = when (categoria) {
            Categoria.IMOVEL -> DetalhesCategoria.Imovel(sub, i("compartments"), d.qualities.toSet(), s("repairStatus"), s("notes"))
            Categoria.TERRENO -> DetalhesCategoria.Terreno(sub, det["areaM2"]?.toBigDecimalOrNull(), s("purpose"), s("topography"), s("access"), s("notes"))
            Categoria.VIATURA -> DetalhesCategoria.Viatura(sub, s("brand"), s("model"), i("year"), l("mileage"), s("fuel"), s("transmission"), s("notes"))
            Categoria.MOTORIZADA -> DetalhesCategoria.Motorizada(sub, s("brand"), s("model"), i("year"), i("cylinderCc"), l("mileage"), s("notes"))
            Categoria.OUTRO -> DetalhesCategoria.Outro(sub, s("condition"), i("quantity") ?: 1, s("brand"), s("model"), s("notes"))
        }
        val aluguer = if (modalidade == Modalidade.ARRENDAMENTO) d.rentalTerms?.let { t ->
            val prazo = when (t.minimumPreset) {
                "3_MONTHS" -> PrazoMinimo.TresMeses; "6_MONTHS" -> PrazoMinimo.SeisMeses; "12_MONTHS" -> PrazoMinimo.Anual
                else -> PrazoMinimo.Personalizado(t.minimumCount, UnidadeDuracao.daOrigem(t.minimumUnit) ?: UnidadeDuracao.MES)
            }
            CondicoesAluguer(preco, UnidadePreco.daOrigem(t.priceUnit) ?: UnidadePreco.MES, prazo, t.otherPriceUnit, t.monthlyUnitsEquivalent?.toBigDecimalOrNull())
        } ?: CondicoesAluguer(preco, UnidadePreco.MES, PrazoMinimo.Personalizado(1, UnidadeDuracao.MES)) /* legado sem termos: 1 mês (12456) */ else null
        val promo = if (d.promotionServices.isNotEmpty() && d.promotionPaidAtMs != null) PromocaoActiva(
            d.promotionServices.mapNotNull { c -> Promocao.entries.firstOrNull { it.codigoOrigem == c } }.toSet(),
            Instant.ofEpochMilli(d.promotionPaidAtMs), d.promotionExpiresAtMs?.let(Instant::ofEpochMilli)) else null
        val e = d.engagement
        return AnuncioPublico(
            id = d.id, donoId = d.ownerId, titulo = d.title, categoria = categoria, modalidade = modalidade, preco = preco, aluguer = aluguer,
            provincia = d.province, municipio = d.municipality, detalhes = detalhes,
            diferenciais = Diferenciais(s("locationProfile"), s("condominiumName"), s("interestHighlights")),
            fotos = d.images.mapIndexed { idx, im -> Fotografia(im.url, im.thumb, im.synthetic, idx) },
            estado = EstadoAnuncio.de(d.status) ?: EstadoAnuncio.ARQUIVADO,
            criadoEm = Instant.ofEpochMilli(d.createdAtMs), actualizadoEm = Instant.ofEpochMilli(d.updatedAtMs), versao = d.version,
            envolvimento = Envolvimento(e.qualifiedViews, e.interest24h, e.reservationCount, e.completedDeals,
                e.lastInterestAtMs?.let(Instant::ofEpochMilli), e.lastReservedAtMs?.let(Instant::ofEpochMilli)),
            promocao = promo, snapshotFinanceiroId = d.financialSnapshotId,
            publicadoPorAdministrador = d.publisherRole.uppercase() in setOf("GESTOR", "ADMINISTRADOR", "ADMIN"),
            verificacaoTitularidade = runCatching { EstadoVerificacao.valueOf(d.verification) }.getOrDefault(EstadoVerificacao.PENDENTE),
        )
    }
}
