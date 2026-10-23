package ao.kupila.gestaonegocios.dominio.duplicados

import java.security.MessageDigest
import java.text.Normalizer

/**
 * Identidade do bem para prevenção de duplicados, portada de `buildDuplicateIdentity` (HTML 11839-11953, versão 8).
 * A verificação local só antecipa feedback; a garantia final é a reivindicação central de claims (secção 15).
 */
object IdentidadeBem {
    const val VERSAO = 8

    private val PALAVRAS_VAZIAS = setOf("de", "da", "do", "das", "dos", "em", "no", "na", "nos", "nas", "para", "por", "com", "e", "a", "o", "um", "uma",
        "venda", "vende", "arrendamento", "arrenda", "aluga", "aluguer", "imovel", "casa", "vivenda", "apartamento", "terreno", "lote",
        "viatura", "carro", "veiculo", "motorizada", "moto", "produto", "equipamento", "electronico", "mobiliario")

    fun normalizar(v: String?): String = Normalizer.normalize(v.orEmpty(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "").lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

    fun tokensTitulo(v: String?): List<String> = normalizar(v).split(Regex("\\s+"))
        .filter { it.length > 1 && it !in PALAVRAS_VAZIAS }.distinct().sorted()

    fun sha256(texto: String): String = MessageDigest.getInstance("SHA-256").digest(texto.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    fun jaccard(a: Collection<String>, b: Collection<String>): Double {
        val sa = a.toSet(); val sb = b.toSet()
        if (sa.isEmpty() && sb.isEmpty()) return 1.0
        return (sa intersect sb).size.toDouble() / maxOf(1, (sa union sb).size)
    }

    /** Distância de Hamming entre dois dHash hexadecimais (999 se incomparáveis, como na origem). */
    fun hamming(a: String, b: String): Int {
        if (a.isEmpty() || b.isEmpty() || a.length != b.length) return 999
        return a.indices.sumOf { Integer.bitCount(Character.digit(a[it], 16) xor Character.digit(b[it], 16)) }
    }

    /**
     * dHash 9×8 a partir de luminâncias já reduzidas (linha a linha, 72 valores) — a redução da imagem
     * é feita na camada de dados (Bitmap). Luminância = 0,299R + 0,587G + 0,114B.
     */
    fun dHash(luminancias9x8: DoubleArray): String {
        require(luminancias9x8.size == 72)
        val bits = StringBuilder(64)
        for (y in 0 until 8) for (x in 0 until 8) bits.append(if (luminancias9x8[y * 9 + x] > luminancias9x8[y * 9 + x + 1]) '1' else '0')
        return (0 until 64 step 4).joinToString("") { Integer.toHexString(bits.substring(it, it + 4).toInt(2)) }.padStart(16, '0')
    }
}

/** Descritor estável do bem (`duplicateCoreDescriptor`). */
data class DescritorBem(
    val categoria: String,
    val subcategoria: String,
    val provincia: String,
    val municipio: String,
    val bairro: String,
    val tokensTitulo: List<String>,
    /** Características normalizadas, ordenadas por chave. */
    val detalhes: Map<String, String>,
) {
    fun textoCanonico(): String = buildString {
        append(categoria).append('|').append(subcategoria).append('|').append(provincia).append('|').append(municipio).append('|').append(bairro)
        append('|').append(tokensTitulo.joinToString(","))
        detalhes.toSortedMap().forEach { (k, v) -> append('|').append(k).append('=').append(v) }
    }
}

data class Identidade(
    val docHash: String,
    val hashesDocumentos: List<String>,
    val hashesVisuais: List<String>,
    val coreHash: String,
    val descritor: DescritorBem,
    val claims: List<String>,
)

enum class Sinal(val texto: String) {
    REFERENCIA_DOCUMENTO("A mesma referência/número do documento de titularidade já está associada a outro anúncio."),
    DOCUMENTO_TITULARIDADE("Um ou mais comprovativos de titularidade possuem conteúdo digital exactamente igual ao de outro anúncio."),
    MULTIPLAS_IMAGENS("Várias fotografias apresentam assinatura visual igual ou praticamente igual às fotografias de outro anúncio."),
    IMAGEM_E_CARACTERISTICAS("Existe fotografia fortemente semelhante e, ao mesmo tempo, coincidem localização e características relevantes do produto."),
    CARACTERISTICAS_E_IMAGEM("As características estruturadas do produto coincidem e existe também correspondência visual de fotografia."),
    MESMA_LOCALIZACAO("Província, Município e Bairro coincidem com o anúncio comparado."),
    TITULO_SEMELHANTE("O título/descrição principal possui termos relevantes em comum."),
    MESMO_NUCLEO("Categoria, subcategoria, localização e características estruturadas coincidem."),
}

/** Resultado da comparação: duplicado comprovado (≥ 90) vs semelhança provável (sinais sem pontuação). */
data class Correspondencia(val anuncioId: String, val pontuacao: Int, val motivo: Sinal?, val sinais: List<Sinal>, val semelhancaTitulo: Double) {
    val duplicadoComprovado get() = pontuacao >= 90
}

object AvaliacaoDuplicados {

    fun construir(descritor: DescritorBem, referenciaDocumento: String, hashesDocumentos: List<String>, hashesVisuais: List<String>): Identidade {
        val coreHash = IdentidadeBem.sha256("CORE|" + descritor.textoCanonico())
        val ref = IdentidadeBem.normalizar(referenciaDocumento)
        val docHash = if (ref.isNotEmpty()) IdentidadeBem.sha256("DOC|${descritor.categoria}|$ref") else ""
        val claims = buildList {
            if (docHash.isNotEmpty()) add("D_$docHash")
            hashesDocumentos.distinct().forEach { add("F_$it") }
            if (hashesVisuais.distinct().size >= 2) add("VS_" + IdentidadeBem.sha256("VSET|" + hashesVisuais.distinct().sorted().joinToString("|")))
            hashesVisuais.distinct().forEach { add("VC_" + IdentidadeBem.sha256("VC|$coreHash|$it")) }
        }.distinct()
        return Identidade(docHash, hashesDocumentos.distinct(), hashesVisuais.distinct(), coreHash, descritor, claims)
    }

    /** Pontuação de `evaluateLocalDuplicate` (11936-11952). */
    fun comparar(a: Identidade, b: Identidade, idB: String): Correspondencia {
        var pontos = 0
        var motivo: Sinal? = null
        val sinais = linkedSetOf<Sinal>()
        if (a.docHash.isNotEmpty() && a.docHash == b.docHash) { pontos = 100; motivo = Sinal.REFERENCIA_DOCUMENTO; sinais += Sinal.REFERENCIA_DOCUMENTO }
        if (a.hashesDocumentos.any { it in b.hashesDocumentos }) { sinais += Sinal.DOCUMENTO_TITULARIDADE; if (pontos < 100) { pontos = 100; motivo = Sinal.DOCUMENTO_TITULARIDADE } }
        val pares = a.hashesVisuais.flatMap { x -> b.hashesVisuais.map { y -> IdentidadeBem.hamming(x, y) } }.filter { it <= 8 }.sorted()
        if (pares.size >= 2) { sinais += Sinal.MULTIPLAS_IMAGENS; if (pontos < 100) { pontos = maxOf(pontos, 98); motivo = Sinal.MULTIPLAS_IMAGENS } }
        val sim = IdentidadeBem.jaccard(a.descritor.tokensTitulo, b.descritor.tokensTitulo)
        val mesmoLocal = a.descritor.provincia == b.descritor.provincia && a.descritor.municipio == b.descritor.municipio && a.descritor.bairro == b.descritor.bairro
        if (mesmoLocal) sinais += Sinal.MESMA_LOCALIZACAO
        if (sim >= 0.45) sinais += Sinal.TITULO_SEMELHANTE
        if (pares.isNotEmpty() && pares[0] <= 5 && mesmoLocal && sim >= 0.45) {
            sinais += Sinal.IMAGEM_E_CARACTERISTICAS; if (pontos < 98) { pontos = maxOf(pontos, 94); motivo = Sinal.IMAGEM_E_CARACTERISTICAS }
        }
        val mesmoNucleo = a.coreHash.isNotEmpty() && a.coreHash == b.coreHash
        if (mesmoNucleo) sinais += Sinal.MESMO_NUCLEO
        if (mesmoNucleo && pares.isNotEmpty()) { sinais += Sinal.CARACTERISTICAS_E_IMAGEM; if (pontos < 94) { pontos = maxOf(pontos, 92); motivo = Sinal.CARACTERISTICAS_E_IMAGEM } }
        return Correspondencia(idB, pontos, motivo, sinais.toList(), sim)
    }

    /** Melhor correspondência ≥ 90 entre os anúncios existentes (excluindo o próprio). */
    fun melhor(candidato: Identidade, existentes: Map<String, Identidade>): Correspondencia? =
        existentes.map { (id, idt) -> comparar(candidato, idt, id) }.maxByOrNull { it.pontuacao }?.takeIf { it.duplicadoComprovado }
}
