package ao.kupila.gestaonegocios.dominio.financeiro

import java.math.BigDecimal

enum class Modalidade(val codigoOrigem: String) {
    VENDA("VENDA"), ARRENDAMENTO("ARRENDAMENTO");
    companion object { fun daOrigem(c: String?) = entries.firstOrNull { it.codigoOrigem == c } }
}

/** Serviços promocionais (HTML 15831, códigos da origem). */
enum class Promocao(val codigoOrigem: String, val rotulo: String) {
    DESTAQUE("FEATURED", "Destaque Premium"),
    TOP("TOP_SPONSORED", "Top Patrocinados"),
    PRIORIDADE("PRIORITY", "Prioridade na Vitrine"),
}

/**
 * Parâmetros financeiros de uma versão de ajustes. Valores [inicial] = fallback versionado da origem
 * (`appSettings` 9087, payments-v2 15817) apenas para ambiente novo; nunca substituem o servidor.
 */
data class ParametrosFinanceiros(
    val versao: String,
    val sinalVenda: Percentagem,
    val mesesSinalAluguer: Int,
    val comissao: Percentagem,
    val taxaAnuncio: Percentagem,
    val promocoes: Map<Promocao, Percentagem>,
    val duracaoPromocaoDias: Int,
) {
    companion object {
        val inicial = ParametrosFinanceiros(
            versao = "origem-1.0.239",
            sinalVenda = Percentagem.de(5),
            mesesSinalAluguer = 1,
            comissao = Percentagem.de(15),
            taxaAnuncio = Percentagem.de(1),
            promocoes = mapOf(
                Promocao.DESTAQUE to Percentagem.de("0.25"),
                Promocao.TOP to Percentagem.de("0.50"),
                Promocao.PRIORIDADE to Percentagem.de("0.35"),
            ),
            duracaoPromocaoDias = 7,
        )
    }
}

/**
 * Isenções explícitas e separadas (incons. D, decisão D-05). Nenhuma isenta o Cliente do sinal.
 * @param motivo obrigatório quando alguma isenção está activa; fica no snapshot/recibo.
 */
data class Isencoes(
    val comissao: Boolean = false,
    val taxaAnuncio: Boolean = false,
    val promocoes: Boolean = false,
    val motivo: String? = null,
) {
    val alguma get() = comissao || taxaAnuncio || promocoes
    companion object { val NENHUMA = Isencoes() }
}

/** Entrada do motor: o mesmo objecto serve cadastro, detalhes, checkout, simulador, recibo e painéis. */
data class EntradaCalculo(
    val modalidade: Modalidade,
    /** Venda: preço do bem. Aluguer: preço por unidade (deve coincidir com [aluguer].precoUnidade). */
    val preco: Dinheiro,
    val aluguer: CondicoesAluguer? = null,
    val parametros: ParametrosFinanceiros,
    val promocoes: Set<Promocao> = emptySet(),
    val isencoes: Isencoes = Isencoes.NENHUMA,
)

enum class BaseComissao(val rotulo: String) { SINAL("sobre o sinal"), CONTRATO_MINIMO("sobre o contrato mínimo") }

sealed interface Problema {
    val mensagem: String
    data class Parametro(override val mensagem: String) : Problema
    data class Aluguer(override val mensagem: String) : Problema
    /** Configuração global incoerente: bloquear só a operação afectada e encaminhar ao Administrador. */
    data class Configuracao(override val mensagem: String) : Problema
}

/**
 * Resultado do motor. Os montantes são sempre calculados (para explicar ao utilizador), mas uma
 * operação financeira só pode prosseguir se [valido]. Nunca se esconde um negativo com max(0, …).
 */
data class ResumoFinanceiro(
    val entrada: EntradaCalculo,
    val termosAluguer: TermosAluguer?,
    /** Venda: P. Aluguer: B (contrato mínimo). */
    val baseNegocio: Dinheiro,
    val sinal: Dinheiro,
    val comissao: Dinheiro,
    val baseComissao: BaseComissao,
    val liquidoCustodia: Dinheiro,
    val saldoRestante: Dinheiro,
    val taxaAnuncio: Dinheiro,
    val promocoes: Map<Promocao, Dinheiro>,
    val totalPublicacao: Dinheiro,
    val problemas: List<Problema>,
) {
    val valido get() = problemas.isEmpty()
    val totalPromocoes get() = Dinheiro.soma(promocoes.values)
    /** Receita contratual estimada (C + Q): previsão, não receita confirmada. */
    val receitaContratualEstimada get() = comissao + totalPublicacao
    /** N + R − Q: Q é pago à parte na publicação; não se desconta outra vez do saldo a transferir. */
    val liquidoPrevistoProprietario get() = liquidoCustodia + saldoRestante - totalPublicacao
    /** Reembolso elegível inicial = N (secção 23), sujeito ao fluxo e à confirmação financeira. */
    val reembolsoElegivel get() = liquidoCustodia
}

object MotorFinanceiro {

    fun calcular(e: EntradaCalculo): ResumoFinanceiro {
        val p = e.parametros
        val problemas = mutableListOf<Problema>()
        if (!e.preco.positivo) problemas += Problema.Parametro("O preço deve ser superior a zero.")
        if (!p.comissao.dentroDe0a100) problemas += Problema.Configuracao("Comissão fora do intervalo 0–100%.")
        if (!p.taxaAnuncio.dentroDe0a100) problemas += Problema.Configuracao("Taxa de anúncio fora do intervalo 0–100%.")
        p.promocoes.forEach { (k, v) -> if (!v.dentroDe0a100) problemas += Problema.Configuracao("Percentagem de ${k.rotulo} fora de 0–100%.") }

        var termos: TermosAluguer? = null
        val base: Dinheiro
        val sinal: Dinheiro
        val baseComissao: BaseComissao
        when (e.modalidade) {
            Modalidade.VENDA -> {
                if (p.sinalVenda.valor.signum() <= 0 || !p.sinalVenda.dentroDe0a100)
                    problemas += Problema.Configuracao("Sinal de venda deve estar entre 0% (exclusive) e 100%.")
                base = e.preco
                sinal = e.preco.percentagem(p.sinalVenda)
                baseComissao = BaseComissao.SINAL
            }
            Modalidade.ARRENDAMENTO -> {
                baseComissao = BaseComissao.CONTRATO_MINIMO
                if (p.mesesSinalAluguer !in 1..12) problemas += Problema.Configuracao("Meses de sinal devem estar entre 1 e 12.")
                val c = e.aluguer
                if (c == null) {
                    problemas += Problema.Aluguer("Condições de aluguer em falta.")
                    base = Dinheiro.ZERO; sinal = Dinheiro.ZERO
                } else {
                    if (c.precoUnidade != e.preco) problemas += Problema.Parametro("O preço por unidade não coincide com as condições de aluguer.")
                    val t = RegrasAluguer.termos(c)
                    termos = t.getOrNull()
                    if (termos == null) {
                        problemas += Problema.Aluguer(t.exceptionOrNull()?.message ?: "Condição de aluguer incoerente.")
                        base = Dinheiro.ZERO; sinal = Dinheiro.ZERO
                    } else {
                        base = termos.baseMinima
                        val mensal = termos.precoMensalEquivalente
                        if (mensal == null) {
                            problemas += Problema.Aluguer("A unidade “${c.nomeUnidadePersonalizada}” não tem equivalência mensal declarada; o sinal em meses não pode ser calculado. A configuração foi encaminhada para revisão.")
                            sinal = Dinheiro.ZERO
                        } else {
                            sinal = mensal.vezes(BigDecimal(p.mesesSinalAluguer))
                        }
                    }
                }
            }
        }

        val comissao = if (e.isencoes.comissao) Dinheiro.ZERO else when (baseComissao) {
            BaseComissao.SINAL -> sinal.percentagem(p.comissao)
            BaseComissao.CONTRATO_MINIMO -> base.percentagem(p.comissao)
        }
        if (e.isencoes.alguma && e.isencoes.motivo.isNullOrBlank())
            problemas += Problema.Parametro("Isenção sem motivo registado.")

        // Validações da secção 17 (sem esconder com max(0, …)).
        val estruturaOk = problemas.none { it is Problema.Aluguer } && e.preco.positivo
        if (estruturaOk) {
            if (!sinal.positivo) problemas += Problema.Configuracao("O sinal calculado deve ser superior a zero.")
            if (sinal > base) problemas += Problema.Configuracao("O sinal (${FormatoAoa.formatar(sinal)}) excede a base do negócio (${FormatoAoa.formatar(base)}).")
            if (comissao.negativo || comissao > sinal) problemas += Problema.Configuracao(
                "A comissão (${FormatoAoa.formatar(comissao)}) excede o sinal (${FormatoAoa.formatar(sinal)}). A regra administrativa precisa de revisão; o cadastro foi preservado.")
        }

        // Taxa e promoções incidem sobre o preço informado (por unidade no aluguer), não sobre B.
        val taxa = if (e.isencoes.taxaAnuncio) Dinheiro.ZERO else e.preco.percentagem(p.taxaAnuncio)
        val promos = e.promocoes.associateWith { pr ->
            if (e.isencoes.promocoes) Dinheiro.ZERO else e.preco.percentagem(p.promocoes[pr] ?: Percentagem.ZERO)
        }
        val totalPublicacao = taxa + Dinheiro.soma(promos.values)

        return ResumoFinanceiro(
            entrada = e,
            termosAluguer = termos,
            baseNegocio = base,
            sinal = sinal,
            comissao = comissao,
            baseComissao = baseComissao,
            liquidoCustodia = sinal - comissao,
            saldoRestante = base - sinal,
            taxaAnuncio = taxa,
            promocoes = promos,
            totalPublicacao = totalPublicacao,
            problemas = problemas.toList(),
        )
    }
}
