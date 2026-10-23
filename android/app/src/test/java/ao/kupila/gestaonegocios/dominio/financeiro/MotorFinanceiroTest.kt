package ao.kupila.gestaonegocios.dominio.financeiro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/** Casos de referência da secção 37 do prompt mestre (sintéticos). */
class MotorFinanceiroTest {

    private val base = ParametrosFinanceiros.inicial
    private fun aoa(v: Long) = Dinheiro.de(v)

    @Test fun venda_referencia() {
        val r = MotorFinanceiro.calcular(EntradaCalculo(Modalidade.VENDA, aoa(1_000_000), parametros = base))
        assertTrue(r.problemas.toString(), r.valido)
        assertEquals(aoa(50_000), r.sinal)
        assertEquals(aoa(7_500), r.comissao)
        assertEquals(BaseComissao.SINAL, r.baseComissao)
        assertEquals(aoa(42_500), r.liquidoCustodia)
        assertEquals(aoa(950_000), r.saldoRestante)
        assertEquals(aoa(10_000), r.taxaAnuncio)
        assertEquals(aoa(982_500), r.liquidoPrevistoProprietario)
        assertEquals(aoa(42_500), r.reembolsoElegivel)
        assertEquals(aoa(17_500), r.receitaContratualEstimada)
    }

    @Test fun aluguer_mensal_referencia() {
        val c = CondicoesAluguer(aoa(100_000), UnidadePreco.MES, PrazoMinimo.TresMeses)
        val r = MotorFinanceiro.calcular(EntradaCalculo(Modalidade.ARRENDAMENTO, aoa(100_000), c, base))
        assertTrue(r.problemas.toString(), r.valido)
        assertEquals(aoa(300_000), r.baseNegocio)
        assertEquals(aoa(100_000), r.sinal)
        assertEquals(aoa(45_000), r.comissao)          // não 15.000: comissão sobre o contrato mínimo
        assertEquals(BaseComissao.CONTRATO_MINIMO, r.baseComissao)
        assertEquals(aoa(55_000), r.liquidoCustodia)
        assertEquals(aoa(200_000), r.saldoRestante)
        assertEquals(aoa(1_000), r.taxaAnuncio)        // sobre o preço por unidade, não sobre B
        assertEquals(aoa(55_000), r.reembolsoElegivel)
    }

    @Test fun promocoes_venda_referencia() {
        val r = MotorFinanceiro.calcular(EntradaCalculo(Modalidade.VENDA, aoa(1_000_000), parametros = base, promocoes = Promocao.entries.toSet()))
        assertEquals(aoa(2_500), r.promocoes[Promocao.DESTAQUE])
        assertEquals(aoa(5_000), r.promocoes[Promocao.TOP])
        assertEquals(aoa(3_500), r.promocoes[Promocao.PRIORIDADE])
        assertEquals(aoa(11_000), r.totalPromocoes)
        assertEquals(aoa(21_000), r.totalPublicacao)
    }

    @Test fun aluguer_comissao_superior_ao_sinal_e_bloqueado() {
        val c = CondicoesAluguer(aoa(100_000), UnidadePreco.MES, PrazoMinimo.TresMeses)
        val p = base.copy(comissao = Percentagem.de(40))
        val r = MotorFinanceiro.calcular(EntradaCalculo(Modalidade.ARRENDAMENTO, aoa(100_000), c, p))
        assertEquals(aoa(120_000), r.comissao)
        assertFalse(r.valido)
        assertTrue(r.problemas.any { it is Problema.Configuracao })
        assertEquals(aoa(-20_000), r.liquidoCustodia)   // mostrado como está, nunca zerado
    }

    @Test fun conservacao_do_sinal() {
        listOf(1_000_000L, 1_234_567L, 999L, 15_000_000L).forEach { preco ->
            val r = MotorFinanceiro.calcular(EntradaCalculo(Modalidade.VENDA, aoa(preco), parametros = base))
            assertEquals(r.sinal, r.comissao + r.liquidoCustodia)
            assertEquals(r.baseNegocio, r.sinal + r.saldoRestante)
        }
    }

    @Test fun isencao_admin_separada_e_com_motivo() {
        val sem = MotorFinanceiro.calcular(EntradaCalculo(Modalidade.VENDA, aoa(1_000_000), parametros = base,
            isencoes = Isencoes(comissao = true, taxaAnuncio = true)))
        assertFalse("isenção sem motivo deve ser recusada", sem.valido)
        val com = MotorFinanceiro.calcular(EntradaCalculo(Modalidade.VENDA, aoa(1_000_000), parametros = base,
            isencoes = Isencoes(comissao = true, taxaAnuncio = true, motivo = "Anúncio publicado pelo Administrador")))
        assertTrue(com.valido)
        assertEquals(aoa(50_000), com.sinal)             // o Cliente continua a pagar o sinal
        assertEquals(Dinheiro.ZERO, com.comissao)
        assertEquals(Dinheiro.ZERO, com.taxaAnuncio)
        assertEquals(aoa(50_000), com.liquidoCustodia)
    }

    @Test fun aluguer_unidades_comerciais() {
        fun termos(p: Long, u: UnidadePreco, prazo: PrazoMinimo) = RegrasAluguer.termos(CondicoesAluguer(aoa(p), u, prazo)).getOrThrow()
        assertEquals(90, termos(10_000, UnidadePreco.DIA, PrazoMinimo.TresMeses).unidadesCobranca)
        assertEquals(13, termos(10_000, UnidadePreco.SEMANA, PrazoMinimo.TresMeses).unidadesCobranca) // ceil(90/7)
        assertEquals(6, termos(10_000, UnidadePreco.QUINZENA, PrazoMinimo.TresMeses).unidadesCobranca)
        assertEquals(1, termos(10_000, UnidadePreco.ANO, PrazoMinimo.Anual).unidadesCobranca)
        assertTrue(RegrasAluguer.termos(CondicoesAluguer(aoa(10_000), UnidadePreco.ANO, PrazoMinimo.TresMeses)).isFailure)
        // Equivalente mensal igual ao da origem.
        assertEquals(aoa(300_000), termos(10_000, UnidadePreco.DIA, PrazoMinimo.TresMeses).precoMensalEquivalente)
        assertEquals(aoa(30_000), termos(7_000, UnidadePreco.SEMANA, PrazoMinimo.TresMeses).precoMensalEquivalente)
        assertEquals(Dinheiro.de("42857.14"), termos(10_000, UnidadePreco.SEMANA, PrazoMinimo.TresMeses).precoMensalEquivalente)
        assertEquals(aoa(100_000), termos(1_200_000, UnidadePreco.ANO, PrazoMinimo.Anual).precoMensalEquivalente)
    }

    @Test fun unidade_personalizada_sem_equivalencia_nao_presume_mes() {
        val c = CondicoesAluguer(aoa(50_000), UnidadePreco.PERSONALIZADA, PrazoMinimo.Personalizado(4, UnidadeDuracao.UNIDADE_PERSONALIZADA), "fim-de-semana")
        val r = MotorFinanceiro.calcular(EntradaCalculo(Modalidade.ARRENDAMENTO, aoa(50_000), c, base))
        assertEquals(aoa(200_000), r.baseNegocio)
        assertNull(r.termosAluguer!!.precoMensalEquivalente)
        assertFalse(r.valido)
        val ok = MotorFinanceiro.calcular(EntradaCalculo(Modalidade.ARRENDAMENTO, aoa(50_000), c.copy(unidadesPersonalizadasPorMes = BigDecimal(4)), base))
        assertTrue(ok.problemas.toString(), ok.valido)
        assertEquals(aoa(200_000), ok.sinal)
    }

    @Test fun mensagens_de_coerencia_da_origem() {
        assertEquals("Informe o nome da unidade personalizada do preço.",
            RegrasAluguer.termos(CondicoesAluguer(aoa(1), UnidadePreco.PERSONALIZADA, PrazoMinimo.Personalizado(1, UnidadeDuracao.UNIDADE_PERSONALIZADA))).exceptionOrNull()?.message)
        assertTrue(RegrasAluguer.termos(CondicoesAluguer(aoa(1), UnidadePreco.PERSONALIZADA, PrazoMinimo.TresMeses, "pacote")).isFailure)
        assertTrue(RegrasAluguer.termos(CondicoesAluguer(aoa(1), UnidadePreco.MES, PrazoMinimo.Personalizado(2, UnidadeDuracao.UNIDADE_PERSONALIZADA))).isFailure)
        assertTrue(RegrasAluguer.termos(CondicoesAluguer(aoa(1), UnidadePreco.MES, PrazoMinimo.Personalizado(0, UnidadeDuracao.MES))).isFailure)
    }

    @Test fun formato_aoa() {
        assertEquals("1 000 000 AOA", FormatoAoa.formatar(aoa(1_000_000)))
        assertEquals("42 500,5 AOA", FormatoAoa.formatar(Dinheiro.de("42500.50")))
        assertEquals("0,25%", FormatoAoa.formatar(Percentagem.de("0.25")))
    }
}
