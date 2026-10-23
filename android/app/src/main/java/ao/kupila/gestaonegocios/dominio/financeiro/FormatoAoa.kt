package ao.kupila.gestaonegocios.dominio.financeiro

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Formato português de Angola, equivalente a `toLocaleString('pt-AO', {maximumFractionDigits: 2})`
 * da origem (`formatMoney`, HTML 12479): milhares com espaço fino, decimais com vírgula, sem ",00".
 */
object FormatoAoa {
    private const val SEPARADOR_MILHAR = ' '

    fun formatar(d: Dinheiro, moeda: Boolean = true): String = formatarNumero(d.valor) + if (moeda) " AOA" else ""

    fun formatarNumero(v: BigDecimal, casas: Int = 2): String {
        val n = v.setScale(casas, RoundingMode.HALF_UP).stripTrailingZeros().let { if (it.scale() < 0) it.setScale(0) else it }
        val negativo = n.signum() < 0
        val texto = n.abs().toPlainString()
        val inteiro = texto.substringBefore('.')
        val decimal = texto.substringAfter('.', "")
        val agrupado = inteiro.reversed().chunked(3).joinToString(SEPARADOR_MILHAR.toString()).reversed()
        return (if (negativo) "-" else "") + agrupado + if (decimal.isNotEmpty()) ",$decimal" else ""
    }

    fun formatar(p: Percentagem): String = formatarNumero(p.valor) + "%"
}
