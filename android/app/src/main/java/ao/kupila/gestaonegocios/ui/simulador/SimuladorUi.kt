package ao.kupila.gestaonegocios.ui.simulador

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ao.kupila.gestaonegocios.dominio.financeiro.BaseComissao
import ao.kupila.gestaonegocios.dominio.financeiro.FormatoAoa
import ao.kupila.gestaonegocios.dominio.financeiro.Promocao
import ao.kupila.gestaonegocios.dominio.financeiro.UnidadeDuracao
import ao.kupila.gestaonegocios.dominio.financeiro.UnidadePreco
import ao.kupila.gestaonegocios.ui.ajuda.EcraComTopo
import ao.kupila.gestaonegocios.ui.comum.Aviso
import ao.kupila.gestaonegocios.ui.comum.Campo
import ao.kupila.gestaonegocios.ui.comum.LinhaInfo
import ao.kupila.gestaonegocios.ui.comum.Seccao
import ao.kupila.gestaonegocios.ui.comum.Seletor
import ao.kupila.gestaonegocios.ui.comum.TipoAviso

@Composable
fun SimuladorEcra(vm: SimuladorViewModel, aoVoltar: () -> Unit) {
    val c by vm.cenario.collectAsStateWithLifecycle()
    val r by vm.resultado.collectAsStateWithLifecycle()
    val aluguer = c.modalidade == "ARRENDAMENTO"
    val modo = ModoSimulacao.valueOf(c.modo)
    EcraComTopo("Simulador de Intermediação", aoVoltar) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Aviso("Simulação local e informativa: não cria pagamento, reserva, anúncio nem altera os ajustes da plataforma.", TipoAviso.INFO)
            Seletor("Tipo de simulação", ModoSimulacao.entries.map { it.name to it.rotulo }, c.modo, { m -> vm.mudar { it.copy(modo = m) } })
            Seletor("Modalidade do negócio", listOf("VENDA" to "Venda definitiva", "ARRENDAMENTO" to "Renda / Aluguer"), c.modalidade, { m -> vm.mudar { it.copy(modalidade = m) } })
            Campo(c.preco, { v -> vm.mudar { it.copy(preco = v) } }, if (aluguer) "Preço por unidade (AOA)" else "Preço do produto (AOA)", teclado = KeyboardType.Decimal)
            if (aluguer) {
                Seletor("Preço por", UnidadePreco.entries.map { it.codigoOrigem to it.rotulo.replaceFirstChar(Char::uppercase) }, c.unidade, { u -> vm.mudar { it.copy(unidade = u) } })
                Seletor("Tempo mínimo", listOf("3_MONTHS" to "3 meses", "6_MONTHS" to "6 meses", "12_MONTHS" to "Anual", "CUSTOM" to "Outro período"), c.prazo, { p -> vm.mudar { it.copy(prazo = p) } })
                if (c.prazo == "CUSTOM") Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Campo(c.prazoQtd, { v -> vm.mudar { it.copy(prazoQtd = v) } }, "Quantidade", Modifier.weight(1f), teclado = KeyboardType.Number)
                    Seletor("Unidade", UnidadeDuracao.entries.map { it.codigoOrigem to it.rotulo }, c.prazoUnidade, { u -> vm.mudar { it.copy(prazoUnidade = u) } }, Modifier.weight(1f))
                }
                if (c.unidade == "OTHER") {
                    Campo(c.outraUnidade, { v -> vm.mudar { it.copy(outraUnidade = v) } }, "Nome da outra unidade", maxCaracteres = 40)
                    Campo(c.unidadesPorMes, { v -> vm.mudar { it.copy(unidadesPorMes = v) } }, "Unidades equivalentes a um mês", teclado = KeyboardType.Decimal,
                        ajuda = "Sem esta equivalência, o sinal em meses não pode ser calculado.")
                }
                Campo(c.meses, { v -> vm.mudar { it.copy(meses = v) } }, "Meses de sinal", teclado = KeyboardType.Number)
            } else Campo(c.sinal, { v -> vm.mudar { it.copy(sinal = v) } }, "Sinal (%)", teclado = KeyboardType.Decimal)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Campo(c.comissao, { v -> vm.mudar { it.copy(comissao = v) } }, "Comissão (%)", Modifier.weight(1f), teclado = KeyboardType.Decimal)
                Campo(c.taxa, { v -> vm.mudar { it.copy(taxa = v) } }, "Taxa anúncio (%)", Modifier.weight(1f), teclado = KeyboardType.Decimal)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = vm::usarActuais) { Text("Usar percentagens actuais") }
                TextButton(onClick = vm::limpar) { Text("Limpar cenário") }
            }
            val res = r
            when {
                res == null -> {}
                res.isFailure -> Aviso(res.exceptionOrNull()?.message ?: "Valores inválidos.", TipoAviso.ERRO)
                else -> {
                    val s = res.getOrThrow()
                    val f: (ao.kupila.gestaonegocios.dominio.financeiro.Dinheiro) -> String = { FormatoAoa.formatar(it) }
                    if (!s.valido) Aviso(s.problemas.joinToString(" ") { it.mensagem }, TipoAviso.AVISO, titulo = "Configuração inválida para este cenário")
                    if (modo != ModoSimulacao.PUBLICACAO) Seccao("Fluxo do Cliente e Proprietário") {
                        if (aluguer) {
                            LinhaInfo("Contrato mínimo", f(s.baseNegocio))
                            s.termosAluguer?.let { LinhaInfo("Unidades cobradas", it.unidadesCobranca.toString()) }
                        }
                        LinhaInfo("Sinal pago pelo Cliente", f(s.sinal), destaque = true)
                        LinhaInfo("Comissão (${s.baseComissao.rotulo})", f(s.comissao))
                        LinhaInfo("Líquido em custódia até à vistoria", f(s.liquidoCustodia))
                        LinhaInfo("Saldo a acertar entre as partes", f(s.saldoRestante))
                        LinhaInfo("Reembolso elegível (se aprovado)", f(s.reembolsoElegivel))
                        Text(if (s.baseComissao == BaseComissao.SINAL) "Sinal = preço × ${c.sinal}%. Comissão = sinal × ${c.comissao}%."
                            else "Contrato mínimo = preço × unidades. Sinal = preço mensal equivalente × ${c.meses}. Comissão = contrato mínimo × ${c.comissao}%.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    if (modo != ModoSimulacao.RESERVA) Seccao("Custos do Proprietário") {
                        LinhaInfo("Taxa de anúncio", f(s.taxaAnuncio))
                        listOf(Triple(Promocao.DESTAQUE, c.pDestaque, "pDestaque"), Triple(Promocao.TOP, c.pTop, "pTop"), Triple(Promocao.PRIORIDADE, c.pPrioridade, "pPrioridade")).forEach { (p, pct, campo) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(p.codigoOrigem in c.promos, { on -> vm.mudar { it.copy(promos = if (on) it.promos + p.codigoOrigem else it.promos - p.codigoOrigem) } })
                                Text(p.rotulo, Modifier.weight(1f))
                                Campo(pct, { v -> vm.mudar { when (campo) { "pDestaque" -> it.copy(pDestaque = v); "pTop" -> it.copy(pTop = v); else -> it.copy(pPrioridade = v) } } },
                                    "%", Modifier.weight(0.7f), teclado = KeyboardType.Decimal)
                            }
                        }
                        LinhaInfo("Total da publicação", f(s.totalPublicacao), destaque = true)
                    }
                    if (modo == ModoSimulacao.COMPLETA) Seccao("Plataforma") {
                        LinhaInfo("Receita contratual estimada (comissão + publicação)", f(s.receitaContratualEstimada))
                        LinhaInfo("Líquido previsto ao Proprietário", f(s.liquidoPrevistoProprietario), destaque = true)
                        Text("Estimativa: receita confirmada depende dos eventos financeiros efectivamente confirmados.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
