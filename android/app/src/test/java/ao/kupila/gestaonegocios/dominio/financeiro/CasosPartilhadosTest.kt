package ao.kupila.gestaonegocios.dominio.financeiro

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.math.BigDecimal

/** Mesmos casos que o Central (backend/functions) testa: `contratos/casos-financeiros.json`. */
class CasosPartilhadosTest {

    private fun ficheiro(): File = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "contratos/casos-financeiros.json") }.first { it.exists() }

    private fun JsonObject.s(k: String) = this[k]?.jsonPrimitive?.content

    private fun entrada(e: JsonObject): EntradaCalculo {
        val pctPromo = e["percentagensPromocao"]?.jsonObject
        val base = ParametrosFinanceiros.inicial
        val params = base.copy(
            sinalVenda = Percentagem.de(e.s("sinalVenda")!!),
            mesesSinalAluguer = e["mesesSinal"]!!.jsonPrimitive.int,
            comissao = Percentagem.de(e.s("comissao")!!),
            taxaAnuncio = Percentagem.de(e.s("taxaAnuncio")!!),
            promocoes = if (pctPromo == null) base.promocoes else Promocao.entries.associateWith { Percentagem.de(pctPromo.s(it.codigoOrigem)!!) },
        )
        val preco = Dinheiro.de(e.s("preco")!!)
        val modalidade = Modalidade.daOrigem(e.s("modalidade"))!!
        val aluguer = if (modalidade == Modalidade.ARRENDAMENTO) {
            val prazo = when (e.s("prazo")) {
                "3_MONTHS" -> PrazoMinimo.TresMeses; "6_MONTHS" -> PrazoMinimo.SeisMeses; "12_MONTHS" -> PrazoMinimo.Anual
                else -> PrazoMinimo.Personalizado(e["prazoQuantidade"]!!.jsonPrimitive.int, UnidadeDuracao.daOrigem(e.s("prazoUnidade"))!!)
            }
            CondicoesAluguer(preco, UnidadePreco.daOrigem(e.s("unidade"))!!, prazo, e.s("nomeUnidade").orEmpty(), e.s("unidadesPorMes")?.let(::BigDecimal))
        } else null
        val promos = e["promocoes"]!!.jsonArray.map { p -> Promocao.entries.first { it.codigoOrigem == p.jsonPrimitive.content } }.toSet()
        return EntradaCalculo(modalidade, preco, aluguer, params, promos)
    }

    @Test fun todos_os_casos_coincidem() {
        val raiz = Json.parseToJsonElement(ficheiro().readText()).jsonObject
        val casos = raiz["casos"]!!.jsonArray
        check(casos.size >= 9)
        casos.forEach { c ->
            val o = c.jsonObject
            val id = o.s("id")
            val r = MotorFinanceiro.calcular(entrada(o["entrada"]!!.jsonObject))
            val esp = o["esperado"]!!.jsonObject
            fun igual(k: String, v: Dinheiro) { esp.s(k)?.let { assertEquals("$id.$k", Dinheiro.de(it), v) } }
            assertEquals("$id.valido (${r.problemas})", esp["valido"]!!.jsonPrimitive.boolean, r.valido)
            igual("base", r.baseNegocio); igual("sinal", r.sinal); igual("comissao", r.comissao); igual("liquido", r.liquidoCustodia)
            igual("saldo", r.saldoRestante); igual("taxa", r.taxaAnuncio); igual("publicacao", r.totalPublicacao)
            igual("liquidoPrevisto", r.liquidoPrevistoProprietario); igual("reembolso", r.reembolsoElegivel)
            esp.s("baseComissao")?.let { assertEquals("$id.baseComissao", it, r.baseComissao.name) }
            esp["promocoes"]?.jsonObject?.forEach { (k, v) ->
                assertEquals("$id.promo.$k", Dinheiro.de(v.jsonPrimitive.content), r.promocoes[Promocao.entries.first { it.codigoOrigem == k }])
            }
        }
    }
}
