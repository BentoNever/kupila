package ao.kupila.gestaonegocios.dominio.anuncio

import ao.kupila.gestaonegocios.dominio.catalogo.Categoria
import ao.kupila.gestaonegocios.dominio.duplicados.IdentidadeBem
import ao.kupila.gestaonegocios.dominio.financeiro.Modalidade
import java.math.BigDecimal

/** O que o título permite deduzir (tudo opcional; a pessoa confirma ou corrige). */
data class Sugestao(
    val categoria: Categoria? = null,
    val subcategoria: String? = null,
    val modalidade: Modalidade? = null,
    val marca: String? = null,
    val modelo: String? = null,
    val ano: Int? = null,
    val areaM2: BigDecimal? = null,
    val quartos: Int? = null,
)

/**
 * Preenchimento inteligente a partir do título (princípio "zero burocracia", docs/principios-produto.md). Determinístico e local:
 * nenhuma chamada de rede, nenhum dado sai do aparelho.
 */
object Inteligencia {
    private val MARCAS_VIATURA = listOf("toyota", "hyundai", "kia", "nissan", "mitsubishi", "mercedes", "bmw", "audi", "volkswagen", "vw", "ford", "chevrolet",
        "honda", "mazda", "suzuki", "isuzu", "land rover", "range rover", "jeep", "peugeot", "renault", "citroen", "fiat", "volvo", "lexus", "subaru", "jac",
        "geely", "chery", "haval", "great wall", "mahindra", "tata", "opel", "seat", "skoda", "dodge", "ram", "gmc", "cadillac", "infiniti", "porsche", "byd")
    private val MARCAS_MOTO = listOf("yamaha", "kawasaki", "ktm", "ducati", "harley", "bajaj", "tvs", "keeway", "lifan", "haojue", "zongshen", "loncin", "vespa", "piaggio", "benelli")
    private val MODELO_PARA_SUB = mapOf("hilux" to "Pick-up", "ranger" to "Pick-up", "navara" to "Pick-up", "l200" to "Pick-up", "d-max" to "Pick-up", "dmax" to "Pick-up",
        "amarok" to "Pick-up", "land cruiser" to "SUV / Todo-terreno", "prado" to "SUV / Todo-terreno", "fortuner" to "SUV / Todo-terreno", "rav4" to "SUV / Todo-terreno",
        "santa fe" to "SUV / Todo-terreno", "tucson" to "SUV / Todo-terreno", "sportage" to "SUV / Todo-terreno", "pajero" to "SUV / Todo-terreno", "x-trail" to "SUV / Todo-terreno",
        "hiace" to "Carrinha", "sprinter" to "Carrinha", "h1" to "Carrinha", "coaster" to "Autocarro", "corolla" to "Turismo", "yaris" to "Turismo", "accent" to "Turismo",
        "elantra" to "Turismo", "picanto" to "Turismo", "i10" to "Turismo", "civic" to "Turismo", "camry" to "Turismo")

    private fun tem(t: String, vararg p: String) = p.any { Regex("\\b$it\\b").containsMatchIn(t) }

    fun deTitulo(titulo: String): Sugestao {
        val t = IdentidadeBem.normalizar(titulo)
        if (t.isBlank()) return Sugestao()
        val modalidade = when {
            tem(t, "arrenda", "arrendamento", "aluga", "aluguer", "renda", "alugo", "arrendo", "mensal") -> Modalidade.ARRENDAMENTO
            tem(t, "vende", "venda", "vendo", "vendese") -> Modalidade.VENDA
            else -> null
        }
        val ano = Regex("\\b(19[5-9]\\d|20[0-4]\\d)\\b").find(t)?.value?.toInt()
        val area = Regex("(\\d+(?:[.,]\\d+)?)\\s*(m2|m²|metros quadrados|mts|m\\b)").find(titulo.lowercase())?.groupValues?.get(1)?.replace(',', '.')?.toBigDecimalOrNull()
            ?: Regex("\\b(\\d{2,4})\\s*x\\s*(\\d{2,4})\\b").find(t)?.let { m -> BigDecimal(m.groupValues[1]).multiply(BigDecimal(m.groupValues[2])) }
        val quartos = Regex("\\bt\\s?(\\d)\\b").find(t)?.groupValues?.get(1)?.toInt() ?: Regex("\\b(\\d)\\s*quartos?\\b").find(t)?.groupValues?.get(1)?.toInt()
        val marcaMoto = MARCAS_MOTO.firstOrNull { tem(t, it) }
        val marcaCarro = MARCAS_VIATURA.firstOrNull { tem(t, it) }
        val moto = marcaMoto != null || tem(t, "mota", "moto", "motorizada", "scooter", "motociclo", "acelera", "kupapata")
        val categoria = when {
            moto -> Categoria.MOTORIZADA
            tem(t, "terreno", "lote", "parcela", "talhao", "hectares", "quinta", "fazenda") && !tem(t, "vivenda", "casa", "apartamento") -> Categoria.TERRENO
            marcaCarro != null || tem(t, "carro", "viatura", "jipe", "jeep", "carrinha", "camiao", "autocarro", "pick up", "pickup", "suv", "minibus", "hiace") -> Categoria.VIATURA
            tem(t, "vivenda", "casa", "apartamento", "moradia", "vivenda", "escritorio", "loja", "armazem", "anexo", "condominio", "predio", "flat", "kitnet", "estudio") || quartos != null -> Categoria.IMOVEL
            tem(t, "telemovel", "iphone", "samsung", "portatil", "laptop", "computador", "televisor", "tv", "geladeira", "frigorifico", "fogao", "sofa", "cama", "mesa",
                "gerador", "maquina", "ferramenta", "pecas", "pneu", "motor", "painel solar", "ar condicionado", "playstation") -> Categoria.OUTRO
            else -> null
        }
        val sub = when (categoria) {
            Categoria.IMOVEL -> when {
                tem(t, "apartamento", "flat", "kitnet", "estudio") -> "Apartamento"; tem(t, "escritorio") -> "Escritório"
                tem(t, "loja", "espaco comercial") -> "Loja / Espaço comercial"; tem(t, "armazem") -> "Armazém"
                tem(t, "vivenda", "casa", "moradia") -> "Vivenda / Moradia"; else -> null
            }
            Categoria.TERRENO -> when { tem(t, "agricola", "fazenda", "quinta", "hectares") -> "Agrícola"; tem(t, "industrial") -> "Industrial"; tem(t, "comercial") -> "Comercial"; tem(t, "residencial", "habitacional", "lote") -> "Residencial"; else -> null }
            Categoria.VIATURA -> MODELO_PARA_SUB.entries.firstOrNull { tem(t, it.key) }?.value
                ?: when { tem(t, "camiao") -> "Camião"; tem(t, "autocarro", "minibus") -> "Autocarro"; tem(t, "carrinha", "hiace") -> "Carrinha"; tem(t, "pick up", "pickup") -> "Pick-up"; tem(t, "jipe", "jeep", "suv") -> "SUV / Todo-terreno"; else -> null }
            Categoria.MOTORIZADA -> when { tem(t, "scooter") -> "Scooter"; tem(t, "trail", "cross", "enduro") -> "Trail / Off-road"; tem(t, "entrega", "trabalho", "kupapata") -> "Trabalho / Entrega"; else -> null }
            Categoria.OUTRO -> when {
                tem(t, "telemovel", "iphone", "samsung", "portatil", "laptop", "computador", "televisor", "tv", "playstation") -> "Eletrónicos"
                tem(t, "sofa", "cama", "mesa", "armario", "cadeira") -> "Mobiliário"; tem(t, "gerador", "maquina", "ferramenta") -> "Máquinas / Ferramentas"
                tem(t, "pecas", "pneu", "motor") -> "Peças / Acessórios"; else -> null
            }
            null -> null
        }
        val marca = (marcaMoto ?: marcaCarro)?.let { m -> m.split(' ').joinToString(" ") { p -> if (p.length <= 3) p.uppercase() else p.replaceFirstChar(Char::uppercase) } }
        val modelo = marca?.let { m ->
            val depois = t.substringAfter(m.lowercase(), "").trim().split(' ').filter { it.isNotBlank() && !it.matches(Regex("\\d{4}")) && it !in setOf("de", "em", "com", "a", "venda", "vende", "aluga") }
            depois.take(2).joinToString(" ").takeIf { it.isNotBlank() }?.replaceFirstChar(Char::uppercase)
        }
        return Sugestao(categoria, sub, modalidade, marca, modelo, ano, area, quartos)
    }
}
