package ao.kupila.gestaonegocios.dominio.financeiro

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Montante em kwanzas (AOA) com 2 casas decimais. Nunca Double/Float (secção 16, decisão D-10).
 * Os cálculos intermédios usam [ESCALA_INTERNA]; cada conceito é fixado uma única vez com [fixar].
 */
@JvmInline
value class Dinheiro private constructor(val valor: BigDecimal) : Comparable<Dinheiro> {

    operator fun plus(outro: Dinheiro) = Dinheiro(valor + outro.valor)
    operator fun minus(outro: Dinheiro) = Dinheiro(valor - outro.valor)
    override fun compareTo(other: Dinheiro) = valor.compareTo(other.valor)

    val positivo get() = valor.signum() > 0
    val negativo get() = valor.signum() < 0
    val zero get() = valor.signum() == 0

    /** Aplica uma percentagem (5 = 5%) e fixa o resultado em cêntimos. */
    fun percentagem(p: Percentagem): Dinheiro =
        fixar(valor.multiply(p.valor).divide(CEM, ESCALA_INTERNA, RoundingMode.HALF_UP))

    /** Multiplica por um factor exacto (ex.: número de unidades, meses) e fixa em cêntimos. */
    fun vezes(factor: BigDecimal): Dinheiro = fixar(valor.multiply(factor))

    override fun toString() = "${valor.toPlainString()} AOA"

    companion object {
        const val ESCALA = 2
        const val ESCALA_INTERNA = 10
        private val CEM = BigDecimal(100)
        val ZERO = Dinheiro(BigDecimal.ZERO.setScale(ESCALA))

        fun fixar(v: BigDecimal) = Dinheiro(v.setScale(ESCALA, RoundingMode.HALF_UP))
        fun de(v: Long) = fixar(BigDecimal.valueOf(v))
        fun de(v: String) = fixar(BigDecimal(v.trim()))
        fun de(v: BigDecimal) = fixar(v)
        fun soma(itens: Iterable<Dinheiro>) = itens.fold(ZERO) { a, b -> a + b }
    }
}

/** Percentagem expressa em pontos percentuais: 5 = 5%, 0.25 = 0,25% (não um factor decimal). */
@JvmInline
value class Percentagem private constructor(val valor: BigDecimal) : Comparable<Percentagem> {
    override fun compareTo(other: Percentagem) = valor.compareTo(other.valor)
    val dentroDe0a100 get() = valor.signum() >= 0 && valor <= CEM
    override fun toString() = "${valor.stripTrailingZeros().toPlainString()}%"

    companion object {
        private val CEM = BigDecimal(100)
        val ZERO = Percentagem(BigDecimal.ZERO)
        fun de(v: String) = Percentagem(BigDecimal(v.trim()))
        fun de(v: Long) = Percentagem(BigDecimal.valueOf(v))
        fun de(v: BigDecimal) = Percentagem(v)
    }
}
