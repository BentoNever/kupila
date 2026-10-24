package ao.kupila.gestaonegocios.ui.simulador

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ao.kupila.gestaonegocios.dominio.financeiro.BaseComissao
import ao.kupila.gestaonegocios.dominio.financeiro.Dinheiro
import ao.kupila.gestaonegocios.dominio.financeiro.FormatoAoa
import ao.kupila.gestaonegocios.dominio.financeiro.Promocao
import ao.kupila.gestaonegocios.dominio.financeiro.UnidadeDuracao
import ao.kupila.gestaonegocios.dominio.financeiro.UnidadePreco
import ao.kupila.gestaonegocios.ui.comum.Fa
import ao.kupila.gestaonegocios.ui.kp.FaIcone
import ao.kupila.gestaonegocios.ui.kp.Kp
import ao.kupila.gestaonegocios.ui.kp.KpEntrada
import ao.kupila.gestaonegocios.ui.kp.KpFechar
import ao.kupila.gestaonegocios.ui.kp.KpSelect
import ao.kupila.gestaonegocios.ui.kp.LocalMarca
import ao.kupila.gestaonegocios.ui.kp.T
import ao.kupila.gestaonegocios.ui.kp.gradiente
import ao.kupila.gestaonegocios.ui.kp.sup
import ao.kupila.gestaonegocios.ui.kp.toque

/** Simulador de Intermediação (`#modal-intermediation-simulator`, HTML 8253-8322): local, não cria pagamentos nem altera os Ajustes. */
@Composable
fun SimuladorEcra(vm: SimuladorViewModel, aoVoltar: () -> Unit) {
    val marca = LocalMarca.current
    val c by vm.cenario.collectAsStateWithLifecycle()
    val r by vm.resultado.collectAsStateWithLifecycle()
    val aluguer = c.modalidade == "ARRENDAMENTO"
    val modo = ModoSimulacao.valueOf(c.modo)
    val s = r?.getOrNull()
    val f: (Dinheiro?) -> String = { it?.let(FormatoAoa::formatar) ?: "0 AOA" }
    Column(Modifier.background(Kp.slate50)) {
        Row(Modifier.fillMaxWidth().background(gradiente(90f, 0f to Kp.slate950, 1f to marca.c900)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                T("Planeamento financeiro", 9f, marca.c200, 700, espacamento = 1.4f, maiusculas = true)
                Row(verticalAlignment = Alignment.CenterVertically) { FaIcone(Fa.CALCULATOR, 11f, Kp.yellow400); Spacer(Modifier.width(6.dp)); T("Simulador de Intermediação", 14f, Color.White, 700) }
                T("Cálculos instantâneos · não cria pagamentos nem altera os Ajustes", 8f, Kp.slate300, Modifier.padding(top = 2.dp))
            }
            KpFechar(aoVoltar)
        }
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.fillMaxWidth().sup(16.dp, fundo = Color.White, borda = Kp.slate200).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Rotulo("Tipo de simulação"); KpSelect(ModoSimulacao.entries.map { it.name to it.rotulo }, c.modo, { m -> vm.mudar { it.copy(modo = m) } }, altura = 40.dp, raio = 12.dp, tamanho = 16f, borda = Kp.slate200, peso = 700)
                Rotulo("Modalidade do negócio"); KpSelect(listOf("VENDA" to "Venda definitiva", "ARRENDAMENTO" to "Renda / Aluguer"), c.modalidade, { m -> vm.mudar { it.copy(modalidade = m) } }, altura = 40.dp, raio = 12.dp, tamanho = 16f, borda = Kp.slate200, peso = 700)
                Rotulo(if (aluguer) "Preço por unidade (AOA)" else "Preço do produto (AOA)")
                KpEntrada(c.preco, { v -> vm.mudar { it.copy(preco = v.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }) } }, altura = 41.dp, raio = 12.dp, teclado = KeyboardType.Decimal)
                if (aluguer) Column(Modifier.fillMaxWidth().sup(12.dp, fundo = Color(0xFFF0F9FF), borda = Color(0xFFE0F2FE)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) { T("Preço por", 7f, Color(0xFF075985), 700)
                            KpSelect(UnidadePreco.entries.map { it.codigoOrigem to it.rotulo.replaceFirstChar(Char::uppercase) }, c.unidade, { u -> vm.mudar { it.copy(unidade = u) } }, altura = 33.dp, raio = 8.dp, tamanho = 12f, borda = Color(0xFFBAE6FD)) }
                        Column(Modifier.weight(1f)) { T("Tempo mínimo", 7f, Color(0xFF075985), 700)
                            KpSelect(listOf("3_MONTHS" to "3 meses", "6_MONTHS" to "6 meses", "12_MONTHS" to "Anual", "CUSTOM" to "Outro período"), c.prazo, { p -> vm.mudar { it.copy(prazo = p) } }, altura = 33.dp, raio = 8.dp, tamanho = 12f, borda = Color(0xFFBAE6FD)) }
                    }
                    if (c.prazo == "CUSTOM") Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KpEntrada(c.prazoQtd, { v -> vm.mudar { it.copy(prazoQtd = v.filter(Char::isDigit)) } }, Modifier.weight(1f), altura = 33.dp, raio = 8.dp, tamanhoTexto = 12f, teclado = KeyboardType.Number)
                        KpSelect(UnidadeDuracao.entries.map { it.codigoOrigem to it.rotulo }, c.prazoUnidade, { u -> vm.mudar { it.copy(prazoUnidade = u) } }, Modifier.weight(1f), altura = 33.dp, raio = 8.dp, tamanho = 12f, borda = Color(0xFFBAE6FD))
                    }
                    if (c.unidade == "OTHER") {
                        KpEntrada(c.outraUnidade, { v -> vm.mudar { it.copy(outraUnidade = v.take(40)) } }, dica = "Nome da outra unidade", altura = 33.dp, raio = 8.dp, tamanhoTexto = 12f)
                        KpEntrada(c.unidadesPorMes, { v -> vm.mudar { it.copy(unidadesPorMes = v) } }, dica = "Unidades equivalentes a um mês", altura = 33.dp, raio = 8.dp, tamanhoTexto = 12f, teclado = KeyboardType.Decimal)
                    }
                    s?.termosAluguer?.let { T("Contrato mínimo: ${it.unidadesCobranca} unidade(s) · base ${f(s.baseNegocio)}.", 8f, Color(0xFF0C4A6E), linha = 13f) }
                }
                Campo(if (aluguer) "Meses de sinal" else "Sinal (%)", if (aluguer) c.meses else c.sinal) { v -> vm.mudar { if (aluguer) it.copy(meses = v) else it.copy(sinal = v) } }
                Campo("Comissão (%)", c.comissao) { v -> vm.mudar { it.copy(comissao = v) } }
                Campo("Taxa anúncio (%)", c.taxa) { v -> vm.mudar { it.copy(taxa = v) } }
                Row(Modifier.fillMaxWidth().sup(12.dp, fundo = marca.c50, borda = marca.c100).toque(aoTocar = vm::usarActuais).padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    FaIcone(Fa.ROTATE, 9f, marca.c800); Spacer(Modifier.width(6.dp)); T("Usar percentagens actuais do Kupila", 9f, marca.c800, 700)
                }
                Row(Modifier.fillMaxWidth().sup(12.dp, fundo = Kp.slate50, borda = Kp.slate200).padding(10.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FaIcone(Fa.FLOPPY_DISK, 9f, Kp.emerald600); Spacer(Modifier.width(8.dp)); T("Guardado automaticamente neste dispositivo", 8f, Kp.slate500, Modifier.weight(1f), max = 1)
                    Row(Modifier.toque(aoTocar = vm::limpar).padding(8.dp, 4.dp), verticalAlignment = Alignment.CenterVertically) { FaIcone(Fa.ARROW_ROTATE_LEFT, 8f, Kp.slate600); Spacer(Modifier.width(4.dp)); T("Limpar cenário", 8f, Kp.slate600, 700) }
                }
                r?.exceptionOrNull()?.let { T(it.message ?: "Valores inválidos.", 9f, Kp.red700, Modifier.fillMaxWidth().sup(12.dp, fundo = Kp.red50).padding(10.dp)) }
                if (s != null && !s.valido) T(s.problemas.joinToString(" ") { it.mensagem }, 9f, Kp.amber800, Modifier.fillMaxWidth().sup(12.dp, fundo = Kp.amber50, borda = Kp.amber100).padding(10.dp), linha = 14f)
            }
            if (modo != ModoSimulacao.PUBLICACAO) Column(Modifier.fillMaxWidth().sup(16.dp, fundo = Color.White, borda = Kp.slate200).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Cabeca("Reserva / Intermediação", "Fluxo do Cliente e Proprietário", Fa.HANDSHAKE, marca.c600)
                listOf(listOf(Bloco("Base do negócio", f(s?.baseNegocio), Color(0xFFF0F9FF), Color(0xFFE0F2FE), Color(0xFF0369A1), Color(0xFF0C4A6E)), Bloco("Sinal pago pelo Cliente", f(s?.sinal), marca.c50, marca.c100, marca.c600, marca.c900)),
                    listOf(Bloco("Comissão Kupila", f(s?.comissao), Kp.amber50, Kp.amber100, Kp.amber700, Kp.amber900), Bloco("Líquido após negócio confirmado", f(s?.liquidoCustodia), Kp.emerald50, Kp.emerald100, Kp.emerald700, Kp.emerald900)),
                    listOf(Bloco("Saldo restante do preço", f(s?.saldoRestante), Kp.slate50, Kp.slate100, Kp.slate500, Kp.slate900), Bloco("Reembolso se não fechar", f(s?.reembolsoElegivel), Kp.violet50, Color(0xFFEDE9FE), Kp.violet700, Color(0xFF4C1D95)))).forEach { linha ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { linha.forEach { CartaoBloco(it, Modifier.weight(1f)) } }
                }
                if (s != null) T(if (s.baseComissao == BaseComissao.SINAL) "Sinal: ${c.sinal}% de ${f(s.baseNegocio)} = ${f(s.sinal)}. Comissão: ${c.comissao}% do sinal = ${f(s.comissao)}. Se a vistoria não resultar em negócio e a prova for validada, reembolso estimado = ${f(s.reembolsoElegivel)}."
                    else "Contrato mínimo = ${f(s.baseNegocio)}. Sinal = ${c.meses} mês(es) = ${f(s.sinal)}. Comissão: ${c.comissao}% do contrato mínimo = ${f(s.comissao)}. Reembolso estimado = ${f(s.reembolsoElegivel)}.", 8f, Kp.slate500, linha = 13f)
                Row(Modifier.fillMaxWidth().sup(12.dp, fundo = Kp.violet50, borda = Color(0xFFEDE9FE)).padding(10.dp)) {
                    FaIcone(Fa.SHIELD_HEART, 8f, Color(0xFF5B21B6), Modifier.padding(top = 2.dp, end = 4.dp))
                    T("Após vistoria presencial, se o negócio não for fechado, o Cliente pode pedir no app o reembolso do sinal menos a comissão. Exige OTP da vistoria, motivo/evidência e confirmação do Proprietário; o pagamento só é marcado como reembolsado após validação financeira.", 8f, Color(0xFF5B21B6), linha = 13f)
                }
            }
            if (modo != ModoSimulacao.RESERVA) Column(Modifier.fillMaxWidth().sup(16.dp, fundo = Color.White, borda = Kp.slate200).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Cabeca("Publicação e promoção", "Custos do Proprietário", Fa.BULLHORN, Kp.violet600)
                Row(Modifier.fillMaxWidth().sup(12.dp, fundo = Kp.slate50, borda = Kp.slate100).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    T("Taxa normal do anúncio", 9f, Kp.slate600, Modifier.weight(1f)); T(f(s?.taxaAnuncio), 10f, Kp.slate900, 700)
                }
                listOf(Triple(Promocao.DESTAQUE, c.pDestaque, Triple(Kp.violet50, Color(0xFFEDE9FE), Color(0xFF4C1D95))), Triple(Promocao.TOP, c.pTop, Triple(Kp.amber50, Kp.amber100, Kp.amber900)),
                    Triple(Promocao.PRIORIDADE, c.pPrioridade, Triple(Color(0xFFF0F9FF), Color(0xFFE0F2FE), Color(0xFF0C4A6E)))).forEach { (p, pct, cores) ->
                    val activo = p.codigoOrigem in c.promos
                    Row(Modifier.fillMaxWidth().sup(12.dp, fundo = cores.first, borda = cores.second).toque { vm.mudar { it.copy(promos = if (activo) it.promos - p.codigoOrigem else it.promos + p.codigoOrigem) } }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(18.dp).sup(4.dp, fundo = if (activo) cores.third else Color.White, borda = if (activo) cores.third else Kp.slate500), contentAlignment = Alignment.Center) { if (activo) FaIcone(Fa.CHECK, 9f, Color.White) }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) { T(when (p) { Promocao.DESTAQUE -> "Destaque Premium"; Promocao.TOP -> "Top Patrocinados"; else -> "Prioridade na Vitrine" }, 9f, cores.third, 700)
                            T(f(s?.promocoes?.get(p)), 9f, cores.third, 700, Modifier.padding(top = 6.dp)) }
                        KpEntrada(pct, { v -> vm.mudar { when (p) { Promocao.DESTAQUE -> it.copy(pDestaque = v); Promocao.TOP -> it.copy(pTop = v); else -> it.copy(pPrioridade = v) } } }, Modifier.width(64.dp), altura = 40.dp, raio = 8.dp, tamanhoTexto = 12f, teclado = KeyboardType.Decimal)
                        Spacer(Modifier.width(6.dp)); T("%", 8f, Kp.slate900)
                    }
                }
                Row(Modifier.fillMaxWidth().sup(12.dp, fundo = Kp.slate950).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    T("Total para publicar + promoções seleccionadas", 9f, Color.White, Modifier.weight(1f)); Spacer(Modifier.width(12.dp)); T(f(s?.totalPublicacao), 14f, Kp.yellow300, 700)
                }
            }
            if (modo == ModoSimulacao.COMPLETA) Column(Modifier.fillMaxWidth().sup(16.dp, pincel = gradiente(135f, 0f to marca.c900, 1f to Kp.slate950)).padding(14.dp)) {
                T("Resumo consolidado da simulação", 8f, marca.c200, 700, espacamento = 1.2f, maiusculas = true)
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(listOf("Cliente paga no sinal" to f(s?.sinal), "Proprietário recebe do sinal" to f(s?.liquidoCustodia)),
                        listOf("Custo para publicar" to f(s?.totalPublicacao), "Receita potencial Kupila" to f(s?.receitaContratualEstimada))).forEachIndexed { i, linha ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            linha.forEachIndexed { j, (r2, v) -> Column(Modifier.weight(1f).sup(12.dp, fundo = Color.White.copy(.1f)).padding(10.dp)) {
                                T(r2, 7f, Kp.slate300, 700, maiusculas = true); T(v, 14f, if (i == 1 && j == 1) Kp.yellow300 else Color.White, 700, Modifier.padding(top = 4.dp)) } }
                        }
                    }
                }
                Row(Modifier.padding(top = 12.dp)) {
                    FaIcone(Fa.CIRCLE_INFO, 8f, Kp.slate300, Modifier.padding(top = 2.dp, end = 4.dp))
                    T("A simulação é estimativa. O pagamento real utiliza as condições congeladas no anúncio/checkout e só é confirmado pelo backend.", 8f, Kp.slate300, linha = 13f)
                }
            }
        }
    }
}

private class Bloco(val rotulo: String, val valor: String, val fundo: Color, val borda: Color, val corRotulo: Color, val corValor: Color)

@Composable
private fun CartaoBloco(b: Bloco, modifier: Modifier) = Column(modifier.sup(12.dp, fundo = b.fundo, borda = b.borda).padding(10.dp)) {
    T(b.rotulo, 7f, b.corRotulo, 700, maiusculas = true); T(b.valor, 14f, b.corValor, 700, Modifier.padding(top = 4.dp), max = 1)
}

@Composable
private fun Rotulo(t: String) = T(t, 8f, Kp.slate500, 700, maiusculas = true)

@Composable
private fun Campo(rotulo: String, valor: String, aoMudar: (String) -> Unit) = Column {
    T(rotulo, 7f, Kp.slate500, 700, Modifier.padding(bottom = 4.dp))
    KpEntrada(valor, { aoMudar(it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }) }, altura = 40.dp, raio = 8.dp, tamanhoTexto = 12f, teclado = KeyboardType.Decimal)
}

@Composable
private fun Cabeca(kicker: String, titulo: String, icone: String, cor: Color) = Row(verticalAlignment = Alignment.CenterVertically) {
    Column(Modifier.weight(1f)) { T(kicker, 8f, Kp.slate400, 700, espacamento = 1.2f, maiusculas = true); T(titulo, 12f, Kp.slate900, 900) }
    FaIcone(icone, 14f, cor)
}
