package ao.kupila.gestaonegocios.dominio.financeiro

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Unidades de cobrança do aluguer. Dias comerciais da origem (`RENTAL_PRICE_UNIT_DAYS`, HTML 12448):
 * mês = 30 e ano = 360 são convenção comercial, não calendário civil (decisão D-09).
 */
enum class UnidadePreco(val codigoOrigem: String, val dias: Int?, val rotulo: String) {
    DIA("DAILY", 1, "dia"),
    SEMANA("WEEKLY", 7, "semana"),
    QUINZENA("BIWEEKLY", 15, "quinzena"),
    MES("MONTHLY", 30, "mês"),
    ANO("ANNUAL", 360, "ano"),
    PERSONALIZADA("OTHER", null, "unidade personalizada");

    companion object {
        fun daOrigem(codigo: String?) = entries.firstOrNull { it.codigoOrigem == codigo }
    }
}

/** Unidade do tempo mínimo (`RENTAL_DURATION_UNIT_DAYS`, HTML 12449). */
enum class UnidadeDuracao(val codigoOrigem: String, val dias: Int?, val rotulo: String) {
    DIA("DAY", 1, "dia(s)"),
    SEMANA("WEEK", 7, "semana(s)"),
    QUINZENA("BIWEEK", 15, "quinzena(s)"),
    MES("MONTH", 30, "mês(es)"),
    ANO("YEAR", 360, "ano(s)"),
    UNIDADE_PERSONALIZADA("CUSTOM_UNIT", null, "unidade(s) personalizada(s)");

    companion object {
        fun daOrigem(codigo: String?) = entries.firstOrNull { it.codigoOrigem == codigo }
    }
}

/** Tempo mínimo exigido (`rentalDurationPreset`, HTML 12451). */
sealed interface PrazoMinimo {
    val codigoOrigem: String
    data object TresMeses : PrazoMinimo { override val codigoOrigem = "3_MONTHS" }
    data object SeisMeses : PrazoMinimo { override val codigoOrigem = "6_MONTHS" }
    data object Anual : PrazoMinimo { override val codigoOrigem = "12_MONTHS" }
    data class Personalizado(val quantidade: Int, val unidade: UnidadeDuracao) : PrazoMinimo {
        override val codigoOrigem = "CUSTOM"
    }

    /** (quantidade, unidade, dias comerciais ou null se a unidade for personalizada). */
    fun normalizado(): Triple<Int, UnidadeDuracao, Int?> = when (this) {
        TresMeses -> Triple(3, UnidadeDuracao.MES, 90)
        SeisMeses -> Triple(6, UnidadeDuracao.MES, 180)
        Anual -> Triple(12, UnidadeDuracao.MES, 360)
        is Personalizado -> Triple(quantidade, unidade, unidade.dias?.let { it * quantidade })
    }

    fun rotulo(): String = when (this) {
        TresMeses -> "3 meses"
        SeisMeses -> "6 meses"
        Anual -> "12 meses (anual)"
        is Personalizado -> "$quantidade ${unidade.rotulo}"
    }
}

/**
 * Condições de aluguer de um anúncio.
 * @param unidadesPersonalizadasPorMes para [UnidadePreco.PERSONALIZADA]: quantas unidades equivalem
 *        contratualmente a um mês. Sem ela não existe sinal em meses (incons. B; a origem presumia 1 = mês).
 */
data class CondicoesAluguer(
    val precoUnidade: Dinheiro,
    val unidade: UnidadePreco,
    val prazo: PrazoMinimo,
    val nomeUnidadePersonalizada: String = "",
    val unidadesPersonalizadasPorMes: BigDecimal? = null,
)

/** Resultado validado das condições: base mínima B e equivalente mensal. */
data class TermosAluguer(
    val condicoes: CondicoesAluguer,
    val unidadesCobranca: Int,
    val baseMinima: Dinheiro,
    /** Preço mensal equivalente; null quando a unidade personalizada não tem equivalência declarada. */
    val precoMensalEquivalente: Dinheiro?,
    val diasMinimos: Int?,
)

object RegrasAluguer {

    /**
     * Valida e calcula B = preço × unidades de cobrança, com as mesmas mensagens e regras de
     * `collectRentalTerms` (HTML 12455-12467). Devolve a lista de problemas se for incoerente.
     */
    fun termos(c: CondicoesAluguer): Result<TermosAluguer> {
        val (qtd, unidadeDur, dias) = c.prazo.normalizado()
        val personalizado = c.prazo is PrazoMinimo.Personalizado
        val erro: String? = when {
            !c.precoUnidade.positivo -> "Informe um preço por unidade superior a zero."
            c.unidade == UnidadePreco.PERSONALIZADA && c.nomeUnidadePersonalizada.isBlank() ->
                "Informe o nome da unidade personalizada do preço."
            c.unidade == UnidadePreco.PERSONALIZADA && (!personalizado || unidadeDur != UnidadeDuracao.UNIDADE_PERSONALIZADA) ->
                "Para outra unidade de preço, use “Outro período específico” e “Mesma unidade personalizada”."
            c.unidade != UnidadePreco.PERSONALIZADA && personalizado && unidadeDur == UnidadeDuracao.UNIDADE_PERSONALIZADA ->
                "A unidade personalizada do período só pode ser usada com outra unidade de preço."
            personalizado && qtd < 1 -> "Informe uma quantidade mínima inteira e superior a zero."
            c.unidade != UnidadePreco.PERSONALIZADA && (dias == null || dias < c.unidade.dias!!) ->
                "O tempo mínimo não pode ser inferior à unidade usada para cobrar o preço."
            c.unidade == UnidadePreco.PERSONALIZADA && c.unidadesPersonalizadasPorMes != null &&
                c.unidadesPersonalizadasPorMes.signum() <= 0 -> "A equivalência mensal da unidade personalizada deve ser positiva."
            else -> null
        }
        if (erro != null) return Result.failure(CondicaoAluguerIncoerente(erro))

        val unidades = if (c.unidade == UnidadePreco.PERSONALIZADA) qtd
        else (dias!! + c.unidade.dias!! - 1) / c.unidade.dias   // arredondamento para cima da origem
        val base = c.precoUnidade.vezes(BigDecimal(unidades))
        return Result.success(TermosAluguer(c, unidades, base, precoMensal(c), dias))
    }

    /**
     * Equivalente mensal (HTML 16707), reescrito pela convenção de 30 dias: preço × 30 / dias da unidade.
     * Dá os mesmos valores da origem (dia ×30, semana ×30/7, quinzena ×2, ano ÷12), excepto a unidade
     * personalizada, que exige equivalência declarada.
     */
    fun precoMensal(c: CondicoesAluguer): Dinheiro? {
        val p = c.precoUnidade.valor
        return when (val d = c.unidade.dias) {
            null -> c.unidadesPersonalizadasPorMes?.let { Dinheiro.fixar(p.multiply(it)) }
            else -> Dinheiro.fixar(p.multiply(BigDecimal(30)).divide(BigDecimal(d), Dinheiro.ESCALA_INTERNA, RoundingMode.HALF_UP))
        }
    }
}

class CondicaoAluguerIncoerente(mensagem: String) : IllegalArgumentException(mensagem)
