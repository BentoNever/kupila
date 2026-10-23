package ao.kupila.gestaonegocios.ui.detalhe

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ao.kupila.gestaonegocios.dominio.anuncio.AnuncioPublico
import ao.kupila.gestaonegocios.dominio.anuncio.DetalhesCategoria
import ao.kupila.gestaonegocios.dominio.anuncio.EstadoAnuncio
import ao.kupila.gestaonegocios.dominio.anuncio.Fotografia
import ao.kupila.gestaonegocios.dominio.financeiro.Dinheiro
import ao.kupila.gestaonegocios.dominio.financeiro.FormatoAoa
import ao.kupila.gestaonegocios.dominio.financeiro.Modalidade
import ao.kupila.gestaonegocios.dominio.financeiro.ResumoFinanceiro
import ao.kupila.gestaonegocios.ui.comum.Fa
import ao.kupila.gestaonegocios.ui.comum.modeloImagem
import ao.kupila.gestaonegocios.ui.explorar.precoComUnidade
import ao.kupila.gestaonegocios.ui.kp.FaIcone
import ao.kupila.gestaonegocios.ui.kp.Kp
import ao.kupila.gestaonegocios.ui.kp.KpBotao
import ao.kupila.gestaonegocios.ui.kp.LocalMarca
import ao.kupila.gestaonegocios.ui.kp.T
import ao.kupila.gestaonegocios.ui.kp.sup
import ao.kupila.gestaonegocios.ui.kp.toque
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

private fun aoa(d: Dinheiro) = FormatoAoa.formatar(d)

/** MODAL DE DETALHES (`#modal-details`, HTML 7313-7461): galeria, etiquetas, resumo do negócio, fluxo financeiro, reserva protegida e CTA fixo. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetalheEcra(vm: DetalheViewModel, aoVoltar: () -> Unit, aoReservar: ((String) -> Unit)?, aoEditar: ((String) -> Unit)?, aoPedirAutorizacao: ((String) -> Unit)?) {
    val a by vm.anuncio.collectAsStateWithLifecycle()
    val resumo by vm.resumo.collectAsStateWithLifecycle()
    val privado by vm.privado.collectAsStateWithLifecycle()
    val accoes by vm.accoes.collectAsStateWithLifecycle()
    val erro by vm.erroActualizacao.collectAsStateWithLifecycle()
    val ocupado by vm.aReservar.collectAsStateWithLifecycle()
    val marca = LocalMarca.current
    var visor by rememberSaveable { mutableStateOf<Int?>(null) }
    val an = a
    Column(Modifier.fillMaxSize().background(Kp.slate50)) {
        if (an == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { T("A carregar os detalhes do produto…", 10f, Kp.slate400, 600) }
            return@Column
        }
        val pager = rememberPagerState { an.fotos.size.coerceAtLeast(1) }
        val ambito = rememberCoroutineScope()
        // Hero / Galeria principal
        Box(Modifier.fillMaxWidth().height(172.dp).background(Kp.slate900)) {
            if (an.fotos.isNotEmpty()) HorizontalPager(pager, Modifier.fillMaxSize()) { i ->
                AsyncImage(modeloImagem(an.fotos[i].url), "Fotografia ${i + 1}", Modifier.fillMaxSize().pointerInput(i) { detectTapGestures(onTap = { visor = i }) }, contentScale = ContentScale.Fit)
            }
            if (an.fotos.size > 1) T("${pager.currentPage + 1}/${an.fotos.size}", 9f, Color.White, 800,
                Modifier.padding(12.dp).sup(999.dp, fundo = Kp.slate950.copy(.6f), borda = Color.White.copy(.1f)).padding(horizontal = 4.dp, vertical = 1.dp))
            Vidro(Fa.XMARK, 32f, 12f, Modifier.align(Alignment.TopEnd).padding(12.dp), aoVoltar)
            if (an.fotos.size > 1) {
                Vidro(Fa.CHEVRON_LEFT, 32f, 10f, Modifier.align(Alignment.CenterStart).padding(start = 8.dp)) { ambito.launch { pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0)) } }
                Vidro(Fa.CHEVRON_RIGHT, 32f, 10f, Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)) { ambito.launch { pager.animateScrollToPage((pager.currentPage + 1).coerceAtMost(an.fotos.size - 1)) } }
            }
            if (an.fotos.isNotEmpty()) Vidro(Fa.EXPAND, 29f, 9f, Modifier.align(Alignment.BottomEnd).padding(10.dp)) { visor = pager.currentPage }
            if (an.fotos.getOrNull(pager.currentPage)?.sintetica == true) T("Ilustração gerada · não é fotografia real", 7f, Color.White, 800,
                Modifier.align(Alignment.BottomStart).padding(10.dp).sup(999.dp, fundo = Kp.slate950.copy(.7f)).padding(horizontal = 8.dp, vertical = 3.dp))
        }
        // Identificação visual do produto
        FlowRow(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 8.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Etiqueta(an.categoria.rotuloPlural, marca.c50, marca.c100, marca.c800)
            Etiqueta(if (an.modalidade == Modalidade.VENDA) "Venda" else "Aluguer", Kp.amber50, Kp.amber100, Kp.amber900)
            when (an.estado) {
                EstadoAnuncio.ACTIVO -> Etiqueta("Disponível", marca.c50, marca.c100, marca.c800, Fa.CIRCLE_CHECK)
                EstadoAnuncio.RESERVADO -> Etiqueta("Reservado", Kp.amber50, Kp.amber200, Kp.amber900, Fa.LOCK)
                else -> Etiqueta(an.estado.rotulo, Kp.slate100, Kp.slate200, Kp.slate600)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Kp.slate100))
        // Mini carrossel
        if (an.fotos.size > 1) {
            LazyRow(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 8.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(an.fotos) { i, f ->
                    AsyncImage(modeloImagem(f.miniatura ?: f.url), "Ver fotografia ${i + 1}", Modifier.size(54.dp, 42.dp)
                        .sup(8.dp, fundo = Kp.slate100, borda = if (i == pager.currentPage) marca.c600 else Color.Transparent, bordaLargura = 2.dp)
                        .toque { ambito.launch { pager.animateScrollToPage(i) } }, contentScale = ContentScale.Crop)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Kp.slate100))
        }
        // Conteúdo
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(11.dp, 10.dp, 11.dp, 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            erro?.let { Caixa(Kp.amber50, Kp.amber200) { T("Não foi possível confirmar a versão mais recente: $it", 9f, Kp.amber800, linha = 13f) } }
            Column {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        T("Produto Kupila · ${an.id}", 7f, Kp.slate400, 800, espacamento = .63f, maiusculas = true)
                        T(an.titulo, 16f, Kp.slate900, 900, Modifier.padding(top = 2.dp), linha = 19f)
                    }
                    Box(Modifier.size(30.dp).sup(10.dp, fundo = marca.c50, borda = marca.c100), contentAlignment = Alignment.Center) { FaIcone(Fa.SHIELD_HALVED, 10f, marca.c700) }
                }
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    FaIcone(Fa.LOCATION_DOT, 9f, marca.c600); Spacer(Modifier.width(6.dp))
                    T(an.municipio.ifBlank { an.provincia }, 9f, Kp.slate500)
                }
            }
            resumo?.let { r ->
                ResumoNegocio(an, r)
                ResumoFinanceiroSeccao(an, r)
                if (!r.valido) Caixa(Kp.amber50, Kp.amber200) {
                    T("Este anúncio tem uma condição financeira inválida com os ajustes actuais (${r.problemas.first().mensagem}). A reserva fica bloqueada até revisão administrativa.", 9f, Kp.amber800, linha = 13f)
                }
            }
            if (accoes?.podeEditar == true) GestaoProduto(an.id, aoEditar, if (accoes?.podePedirAutorizacao == true) aoPedirAutorizacao else null)
            ReservaProtegida()
            DadosProtegidos(privado)
            Caracteristicas(an, resumo)
            Titularidade()
            Caixa(Kp.amber50, Kp.amber200, raio = 13.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) { FaIcone(Fa.CLOCK, 9f, Kp.amber900, regular = true); Spacer(Modifier.width(6.dp)); T("Prazo após confirmação", 9f, Kp.amber900, 700) }
                T("Após o pagamento ser validado, a reserva terá validade de ${horas(vm)}.", 9f, Kp.amber900, Modifier.padding(top = 4.dp), linha = 13f)
                T("O prazo só começa quando o backend confirma o pagamento integral do sinal.", 9f, Kp.amber700, Modifier.padding(top = 4.dp), linha = 13f)
            }
        }
        // CTA fixo
        val ac = accoes
        if (ac != null) Column(Modifier.fillMaxWidth().background(Color.White.copy(.95f)).padding(top = 1.dp).padding(14.dp)) {
            when {
                ac.podeEditar && aoEditar != null -> KpBotao("Editar anúncio", { aoEditar(vm.id) }, icone = Fa.PEN, altura = 38.dp, raio = 12.dp, tamanho = 10f)
                ac.podeReservar && aoReservar != null -> KpBotao("Gerar referência do sinal", { vm.reservar(aoReservar) }, icone = Fa.BARCODE, ocupado = ocupado,
                    altura = 38.dp, raio = 12.dp, tamanho = 10f)
                else -> KpBotao(ac.motivoSemReserva ?: "Indisponível", {}, activo = false, altura = 38.dp, raio = 12.dp, tamanho = 10f)
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                T("Gerar referência não reserva o produto. A reserva nasce somente após comprovativo + pagamento serem validados.", 7f, Kp.slate600, centro = true)
            }
        }
    }
    visor?.let { i -> a?.let { VisorFotos(it.fotos, i) { visor = null } } }
}

private fun horas(vm: DetalheViewModel): String = vm.prazoReservaHoras().let { h -> if (h == 1L) "1 hora" else "$h horas" }

@Composable
private fun Vidro(glifo: String, tamanho: Float, icone: Float, modifier: Modifier, aoTocar: () -> Unit) {
    Box(modifier.size(tamanho.dp).sup(tamanho.dp, fundo = Kp.slate950.copy(.55f), borda = Color.White.copy(.1f), forma = CircleShape).toque(aoTocar = aoTocar),
        contentAlignment = Alignment.Center) { FaIcone(glifo, icone, Color.White) }
}

@Composable
private fun Etiqueta(texto: String, fundo: Color, borda: Color, cor: Color, icone: String? = null) {
    Row(Modifier.height(22.dp).sup(8.dp, fundo = fundo, borda = borda).padding(horizontal = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        icone?.let { FaIcone(it, 7f, cor); Spacer(Modifier.width(4.dp)) }
        T(texto, 7f, cor, 800, espacamento = .14f, maiusculas = true)
    }
}

@Composable
private fun Caixa(fundo: Color, borda: Color, raio: androidx.compose.ui.unit.Dp = 13.dp, conteudo: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().sup(raio, fundo = fundo, borda = borda).padding(9.dp), content = conteudo)
}

/** Sub-cartão com o botão (i) que expande a explicação (`kupila-detail-info-btn` / `kupila-detail-info-box`). */
@Composable
private fun CartaoInfo(rotulo: String, valor: String, fundo: Color, borda: Color, corRotulo: Color, corValor: Color, tamValor: Float, modifier: Modifier = Modifier,
                       tamRotulo: Float = 6f, pesoValor: Int = 900, explicacao: String? = null) {
    var aberto by remember { mutableStateOf(false) }
    Box(modifier.sup(8.dp, fundo = fundo, borda = borda).toque { aberto = !aberto }.padding(start = 7.dp, top = 6.dp, end = 28.dp, bottom = 6.dp)) {
        Column {
            T(rotulo, tamRotulo, corRotulo, 700, maiusculas = true, max = 1)
            T(valor, tamValor, corValor, pesoValor, Modifier.padding(top = 2.dp), max = 1)
            AnimatedVisibility(aberto) {
                Text2(rotulo, valor, explicacao)
            }
        }
        Box(Modifier.align(Alignment.TopEnd).size(18.dp).sup(18.dp, fundo = Color.White, borda = Kp.slate200, forma = CircleShape),
            contentAlignment = Alignment.Center) { FaIcone(Fa.CIRCLE_INFO, 8f, Kp.slate500) }
    }
}

@Composable
private fun Text2(rotulo: String, valor: String, explicacao: String?) {
    androidx.compose.material3.Text(buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("$rotulo: ") }
        append(explicacao ?: "$valor. Este valor/dado vem do anúncio actual e é actualizado quando a informação do produto muda.")
    }, Modifier.padding(top = 4.dp), color = Kp.slate600, fontSize = 7.sp, lineHeight = 10.sp)
}

/** Resumo comercial (`#modal-summary-section`). */
@Composable
private fun ResumoNegocio(a: AnuncioPublico, r: ResumoFinanceiro) {
    val marca = LocalMarca.current
    val p = r.entrada.parametros
    Column(Modifier.fillMaxWidth().sup(11.dp, fundo = Color.White, borda = Kp.slate200, sombra = Color.Black.copy(.1f), desfoque = 3.dp).padding(7.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                T("Resumo do negócio", 6.5f, Kp.slate400, 800, espacamento = .52f, maiusculas = true)
                T("Valores antes da reserva", 8f, Kp.slate500, Modifier.padding(top = 2.dp))
            }
            FaIcone(Fa.RECEIPT, 10f, marca.c600)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            CartaoInfo(if (a.modalidade == Modalidade.VENDA) "Preço do produto" else "Preço do aluguer", precoComUnidade(a), marca.c50, marca.c100, marca.c700, marca.c800, 14f, Modifier.weight(1f))
            CartaoInfo(if (a.modalidade == Modalidade.VENDA) "Sinal (${FormatoAoa.formatar(p.sinalVenda)})" else "Sinal (${p.mesesSinalAluguer} mês)", aoa(r.sinal),
                Kp.slate50, Kp.slate100, Kp.slate500, Kp.slate900, 9f, Modifier.weight(1f))
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            CartaoInfo("Comissão Kupila", aoa(r.comissao), Kp.slate50, Kp.slate100, Kp.slate500, Kp.slate900, 9f, Modifier.weight(1f),
                explicacao = if (a.modalidade == Modalidade.VENDA) "${FormatoAoa.formatar(p.comissao)} do sinal; é deduzida do sinal e não aumenta o valor pago pelo Cliente."
                else "${FormatoAoa.formatar(p.comissao)} do valor mínimo do contrato (${aoa(r.baseNegocio)}), descontada do sinal.")
            Spacer(Modifier.weight(1f))
        }
        Column(Modifier.padding(top = 6.dp).fillMaxWidth().sup(9.dp, fundo = Color(0xFFF1F9F4).copy(alpha = .7f), borda = marca.c100).padding(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    T("Proprietário", 7f, Color(0xFF065F46), 900, espacamento = .56f, maiusculas = true)
                    T("Recebimentos previstos do negócio", 7f, Kp.emerald700, Modifier.padding(top = 2.dp))
                }
                FaIcone(Fa.USER_CHECK, 10f, Kp.emerald700)
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CartaoInfo("Líquido após sinal", aoa(r.liquidoCustodia), Color.White.copy(.74f), marca.c100, marca.c700, marca.c900, 8.5f, Modifier.weight(1f), pesoValor = 700)
                CartaoInfo("Recebe no fecho", aoa(r.saldoRestante), Color.White.copy(.74f), marca.c100, marca.c700, marca.c900, 8.5f, Modifier.weight(1f), pesoValor = 700)
            }
            Box(Modifier.padding(top = 5.dp).fillMaxWidth().sup(8.dp, fundo = Color.White.copy(.68f), borda = marca.c100).padding(start = 6.dp, top = 5.dp, end = 28.dp, bottom = 5.dp)) {
                androidx.compose.material3.Text(buildAnnotatedString {
                    append("Total líquido previsto: "); withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(aoa(r.liquidoCustodia + r.saldoRestante)) }
                }, color = marca.c700, fontSize = 6.5.sp, lineHeight = 9.sp)
            }
        }
        Row(Modifier.padding(top = 6.dp).fillMaxWidth().sup(12.dp, fundo = Kp.emerald50, borda = Kp.emerald100).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FaIcone(Fa.ARROW_RIGHT_ARROW_LEFT, 9f, Kp.emerald700, Modifier.padding(top = 2.dp))
            androidx.compose.material3.Text(buildAnnotatedString {
                append("Após a validação do sinal, a comissão é deduzida e "); withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(aoa(r.liquidoCustodia)) }
                append(" permanece em custódia para o Proprietário até à decisão da vistoria.")
            }, color = Kp.emerald900, fontSize = 9.sp, lineHeight = 14.sp)
        }
    }
}

/** "RESUMO FINANCEIRO CLARO" (`#modal-financial-summary-section`, 1.0.66). */
@Composable
private fun ResumoFinanceiroSeccao(a: AnuncioPublico, r: ResumoFinanceiro) {
    val marca = LocalMarca.current
    val p = r.entrada.parametros
    Column(Modifier.fillMaxWidth().sup(13.dp, fundo = Color.White, borda = marca.c200, sombra = Color.Black.copy(.1f), desfoque = 3.dp).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                T("Resumo Financeiro", 8f, Kp.emerald700, 900, espacamento = 1.12f, maiusculas = true)
                T("Fluxo completo deste negócio", 12f, Kp.slate900, 900, Modifier.padding(top = 2.dp))
                T("${if (a.modalidade == Modalidade.VENDA) "Venda" else "Aluguer"} · parâmetros globais actuais aplicados instantaneamente.", 8f, Kp.slate500, Modifier.padding(top = 4.dp))
            }
            FaIcone(Fa.MONEY_BILL_TRANSFER, 10f, Kp.emerald700)
        }
        val chips = listOf("Sinal venda" to FormatoAoa.formatar(p.sinalVenda), "Sinal renda" to "${p.mesesSinalAluguer} mês(es)",
            "Comissão" to FormatoAoa.formatar(p.comissao), "Taxa anúncio" to FormatoAoa.formatar(p.taxaAnuncio))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            chips.chunked(2).forEach { par ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    par.forEach { (k, v) -> CartaoInfo(k, v, Kp.slate50, Kp.slate100, Kp.slate400, Kp.slate800, 8f, Modifier.weight(1f), pesoValor = 700) }
                }
            }
        }
        Bloco("Cliente", Fa.USER, Color(0xFFDBEAFE), Kp.blue50.copy(.6f), Kp.blue700, listOf("Base do negócio" to aoa(r.baseNegocio), "Sinal / reserva" to aoa(r.sinal)), "Saldo depois" to aoa(r.saldoRestante), Color(0xFF1E40AF))
        Bloco("Proprietário", Fa.HOUSE_USER, Kp.emerald100, Kp.emerald50.copy(.6f), Kp.emerald700, listOf("Líquido do sinal" to aoa(r.liquidoCustodia), "Recebe depois" to aoa(r.saldoRestante)),
            "Líquido após custos" to aoa(r.liquidoPrevistoProprietario), null)
        Bloco("Plataforma", Fa.BUILDING_COLUMNS, Kp.violet100, Kp.violet50.copy(.6f), Kp.violet700, listOf("Comissão" to aoa(r.comissao), "Taxa / promoção" to aoa(r.totalPublicacao)),
            "Receita estimada" to aoa(r.comissao + r.totalPublicacao), Kp.violet700)
        Bloco("Custos do Proprietário", Fa.WALLET, Kp.amber200, Color(0xFFFFFDF5), Kp.amber800, listOf("Taxa de anúncio" to aoa(r.taxaAnuncio), "Promoções opcionais" to aoa(r.totalPromocoes)),
            "Total de publicação" to aoa(r.totalPublicacao), null, info = true)
        Column(Modifier.fillMaxWidth().sup(12.dp, fundo = Color.White, borda = Kp.slate900, bordaLargura = 1.5.dp).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { FaIcone(Fa.ROTATE_LEFT, 8f, Kp.slate900); Spacer(Modifier.width(4.dp)); T("Protecção do Cliente", 8f, Kp.slate900, 900, maiusculas = true) }
            Linha("Valor retido do sinal", aoa(r.liquidoCustodia), info = true)
            Linha("Reembolso possível*", aoa(r.reembolsoElegivel), info = true)
            T("*Se o negócio não for concluído e o reembolso for aprovado: sinal menos comissão, conforme as regras da plataforma.", 7f, Kp.teal700, linha = 11f)
        }
        Box(Modifier.fillMaxWidth().sup(9.dp, fundo = Kp.slate50, borda = Kp.slate100).padding(7.dp)) {
            androidx.compose.material3.Text(buildAnnotatedString {
                val b = SpanStyle(fontWeight = FontWeight.Bold, color = Kp.slate800)
                if (a.modalidade == Modalidade.VENDA) {
                    withStyle(b) { append("Cliente:") }; append(" sinal de ${FormatoAoa.formatar(p.sinalVenda)} do preço. ")
                    withStyle(b) { append("Proprietário:") }; append(" comissão de ${FormatoAoa.formatar(p.comissao)} incide sobre o sinal. ")
                } else {
                    withStyle(b) { append("Cliente:") }; append(" sinal de ${p.mesesSinalAluguer} mês(es) do aluguer. ")
                    withStyle(b) { append("Proprietário:") }; append(" comissão de ${FormatoAoa.formatar(p.comissao)} sobre o valor mínimo do contrato. ")
                }
                withStyle(b) { append("Plataforma:") }; append(" taxa de anúncio de ${FormatoAoa.formatar(p.taxaAnuncio)} é custo do Proprietário e não aumenta o sinal do Cliente.")
            }, color = Kp.slate600, fontSize = 8.sp, lineHeight = 13.sp)
        }
    }
}

@Composable
private fun Bloco(titulo: String, icone: String, borda: Color, fundo: Color, cor: Color, linhas: List<Pair<String, String>>, total: Pair<String, String>, corTotal: Color?, info: Boolean = false) {
    Column(Modifier.fillMaxWidth().sup(12.dp, fundo = fundo, borda = borda).padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { FaIcone(icone, 8f, cor); Spacer(Modifier.width(4.dp)); T(titulo, 8f, cor, 900, maiusculas = true) }
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            linhas.forEach { (k, v) -> Linha(k, v, info = info) }
            Box(Modifier.fillMaxWidth().height(1.dp).background(borda))
            Linha(total.first, total.second, corValor = corTotal ?: Kp.slate900, info = info)
        }
    }
}

@Composable
private fun Linha(k: String, v: String, corValor: Color = Kp.slate900, info: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        T(k, 9f, Kp.slate700); if (info) { Spacer(Modifier.width(6.dp)); FaIcone(Fa.CIRCLE_INFO, 8f, Kp.slate500) }
        Spacer(Modifier.weight(1f)); T(v, 9f, corValor, 700)
    }
}

/** CRUD do produto (`#modal-crud-section`): visível só a quem pode gerir o anúncio. */
@Composable
private fun GestaoProduto(id: String, aoEditar: ((String) -> Unit)?, aoAutorizacao: ((String) -> Unit)?) {
    Column(Modifier.fillMaxWidth().sup(11.dp, fundo = Color.White, borda = Kp.slate200).padding(10.dp)) {
        Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                T("Gestão do produto", 7f, Kp.slate500, 900, espacamento = .7f, maiusculas = true)
                T("CRUD do anúncio actual", 7f, Kp.slate400, Modifier.padding(top = 2.dp))
            }
            T("GESTÃO", 6f, Kp.slate600, 900, Modifier.sup(999.dp, fundo = Kp.slate100).padding(horizontal = 6.dp, vertical = 2.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOfNotNull(
                Triple(Fa.PEN, "Editar", aoEditar?.let { { it(id) } }),
                Triple(Fa.EYE_SLASH, "Desanunciar", aoAutorizacao?.let { { it(id) } }),
                Triple(Fa.TRASH_CAN, "Excluir", aoAutorizacao?.let { { it(id) } }),
            ).forEach { (ic, r, f) ->
                Column(Modifier.weight(1f).sup(10.dp, fundo = if (r == "Excluir") Kp.red50 else Kp.slate50, borda = if (r == "Excluir") Color(0xFFFEE2E2) else Kp.slate100)
                    .toque(f != null) { f?.invoke() }.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    FaIcone(ic, 10f, if (r == "Excluir") Kp.red600 else Kp.slate700, regular = ic == Fa.TRASH_CAN)
                    T(r, 7f, if (r == "Excluir") Kp.red600 else Kp.slate700, 800, Modifier.padding(top = 3.dp))
                }
            }
        }
    }
}

/** "Processo seguro em 2 etapas" (`#modal-reservation-section`). */
@Composable
private fun ReservaProtegida() {
    val marca = LocalMarca.current
    Column(Modifier.fillMaxWidth().sup(11.dp, fundo = Kp.slate950).padding(horizontal = 8.dp, vertical = 7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                T("Reserva protegida", 8f, Kp.slate400, 800, espacamento = 1.12f, maiusculas = true)
                T("Validação em 2 etapas", 12f, Color.White, 700, Modifier.padding(top = 2.dp))
            }
            T("Automática", 8f, Kp.emerald300, 700, Modifier.sup(999.dp, fundo = Color(0xFF10B981).copy(.15f), borda = Color(0xFF34D399).copy(.2f)).padding(horizontal = 8.dp, vertical = 3.dp))
        }
        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("Pagar o sinal" to "Gerar a referência não reserva o produto.", "Enviar comprovativo" to "A reserva activa após validação integral pelo backend.")
                .forEachIndexed { i, (t, s) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(32.dp).sup(32.dp, fundo = marca.c600, forma = CircleShape), contentAlignment = Alignment.Center) { T("${i + 1}", 10f, Color.White, 900) }
                        Column { T(t, 10f, Color.White, 700); T(s, 8f, Kp.slate400, Modifier.padding(top = 2.dp)) }
                    }
                }
        }
    }
}

/** Privacidade (`#modal-private-location-status` + `#modal-private-location-details`). */
@Composable
private fun DadosProtegidos(p: AcessoPrivado) {
    val d = when (p) { is AcessoPrivado.Titular -> p.dados; is AcessoPrivado.Administrador -> p.dados; is AcessoPrivado.ReservaConfirmada -> p.dados; else -> null }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val (fundo, borda, cor, icone, negrito, resto) = when (p) {
            AcessoPrivado.ACarregar -> Sextuplo(Kp.slate50, Kp.slate200, Kp.slate600, Fa.SPINNER, "A carregar dados protegidos…", "")
            AcessoPrivado.Protegido -> Sextuplo(Kp.amber50, Kp.amber200, Kp.amber800, Fa.SHIELD_HALVED, "Dados protegidos até à validação do pagamento.",
                " Bairro, Ponto de Referência, Proprietário e Contacto só serão revelados depois que o comprovativo e o pagamento integral do sinal forem confirmados.")
            is AcessoPrivado.Falhou -> Sextuplo(Kp.red50, Color(0xFFFEE2E2), Kp.red700, Fa.TRIANGLE_EXCLAMATION, "Não foi possível carregar os dados protegidos.", " ${p.mensagem}")
            is AcessoPrivado.Titular -> Sextuplo(Kp.emerald50, Kp.emerald100, Kp.emerald900, Fa.CIRCLE_CHECK, "Produto da sua tutela.", " Localização e contacto disponíveis ao titular.")
            is AcessoPrivado.Administrador -> Sextuplo(Kp.emerald50, Kp.emerald100, Kp.emerald900, Fa.CIRCLE_CHECK, "Acesso administrativo completo.", " Os dados protegidos deste anúncio estão disponíveis.")
            is AcessoPrivado.ReservaConfirmada -> Sextuplo(Kp.emerald50, Kp.emerald100, Kp.emerald900, Fa.CIRCLE_CHECK, "Reserva paga e confirmada.", " Dados de encontro desbloqueados para acompanhar o negócio.")
        }
        Row(Modifier.fillMaxWidth().sup(13.dp, fundo = fundo, borda = borda).padding(9.dp)) {
            FaIcone(icone, 9f, cor, Modifier.padding(top = 2.dp, end = 8.dp))
            androidx.compose.material3.Text(buildAnnotatedString { withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(negrito) }; append(resto) },
                color = cor, fontSize = 9.sp, lineHeight = 14.sp)
        }
        if (d != null) {
            listOf("Bairro" to d.bairro, "Ponto de Referência" to d.pontoReferencia, "Proprietário" to d.nomeProprietario, "Contacto" to d.contactoProprietario).chunked(2).forEach { par ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    par.forEach { (k, v) ->
                        Column(Modifier.weight(1f).sup(12.dp, fundo = Kp.emerald50, borda = Kp.emerald100).padding(10.dp)) {
                            T(k, 8f, Kp.emerald700, 700, maiusculas = true); T(v.ifBlank { "—" }, 12f, Kp.slate800, 700, Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
    }
}

private data class Sextuplo(val a: Color, val b: Color, val c: Color, val d: String, val e: String, val f: String)

/** Características (`#modal-property-section` / `kupila-property-grid`). */
@Composable
private fun Caracteristicas(a: AnuncioPublico, r: ResumoFinanceiro?) {
    val marca = LocalMarca.current
    val ni = "Não informado"
    fun km(v: Long?) = v?.let { FormatoAoa.formatarNumero(java.math.BigDecimal(it)) + " km" } ?: ni
    val linhas = buildList {
        add("Categoria" to a.categoria.rotuloPlural); add("Subcategoria" to a.detalhes.subcategoria.ifBlank { ni })
        a.aluguer?.let { c -> add("Tempo mínimo" to c.prazo.rotulo()); r?.termosAluguer?.let { add("Unidades cobradas" to it.unidadesCobranca.toString()) } }
        when (val d = a.detalhes) {
            is DetalhesCategoria.Imovel -> { add("Compartimentos" to (d.compartimentos?.toString() ?: ni)); add("Estado / Reparos" to d.estadoReparos.ifBlank { ni })
                if (d.qualidades.isNotEmpty()) add("Acabamentos" to d.qualidades.joinToString(", ")) }
            is DetalhesCategoria.Terreno -> { add("Área" to (d.areaM2?.let { FormatoAoa.formatarNumero(it) + " m²" } ?: ni)); add("Finalidade" to d.finalidade.ifBlank { ni })
                add("Topografia" to d.topografia.ifBlank { ni }); add("Acesso" to d.acesso.ifBlank { ni }) }
            is DetalhesCategoria.Viatura -> { add("Marca" to d.marca.ifBlank { ni }); add("Modelo" to d.modelo.ifBlank { ni }); add("Ano" to (d.ano?.toString() ?: ni))
                add("Quilometragem" to km(d.quilometragem)); add("Combustível" to d.combustivel.ifBlank { ni }); add("Transmissão" to d.transmissao.ifBlank { ni }) }
            is DetalhesCategoria.Motorizada -> { add("Marca" to d.marca.ifBlank { ni }); add("Modelo" to d.modelo.ifBlank { ni }); add("Ano" to (d.ano?.toString() ?: ni))
                add("Cilindrada" to (d.cilindrada?.let { "$it cc" } ?: ni)); add("Quilometragem" to km(d.quilometragem)) }
            is DetalhesCategoria.Outro -> { add("Estado" to d.condicao.ifBlank { ni }); add("Marca" to d.marca.ifBlank { ni }); add("Modelo / Referência" to d.modelo.ifBlank { ni })
                add("Quantidade" to d.quantidade.toString()) }
        }
        if (a.diferenciais.perfilLocalizacao.isNotBlank()) add("Perfil da localização" to a.diferenciais.perfilLocalizacao)
        if (a.diferenciais.condominio.isNotBlank()) add("Condomínio" to a.diferenciais.condominio)
    }
    Column(Modifier.fillMaxWidth().sup(11.dp, fundo = marca.c50, borda = marca.c100).padding(7.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { FaIcone(Fa.LIST_CHECK, 11f, marca.c800); Spacer(Modifier.width(6.dp)); T("Características · ${a.categoria.rotuloPlural}", 12f, marca.c800, 700) }
        linhas.chunked(2).forEach { par ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                par.forEach { (k, v) -> CartaoInfo(k, v, Color.White, marca.c100, Kp.gray400, Kp.gray900.copy(alpha = .9f), 12f, Modifier.weight(1f), tamRotulo = 9f, pesoValor = 600) }
                if (par.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (a.detalhes.notas.isNotBlank()) T(a.detalhes.notas, 10f, Kp.slate700, linha = 15f)
        if (a.diferenciais.pontosFortes.isNotBlank()) T(a.diferenciais.pontosFortes, 10f, Kp.slate700, linha = 15f)
    }
}

/** Confiança / titularidade. */
@Composable
private fun Titularidade() {
    Column(Modifier.fillMaxWidth().sup(16.dp, fundo = Color.White, borda = Kp.slate200).padding(14.dp)) {
        Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(32.dp).sup(12.dp, fundo = Kp.blue50), contentAlignment = Alignment.Center) { FaIcone(Fa.FILE_SHIELD, 12f, Kp.blue700) }
            Column { T("Titularidade & segurança", 10f, Kp.slate800, 700); T("Informações de verificação do anúncio", 8f, Kp.slate400) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CartaoInfo("Documento", "Documento associado ao anúncio", Kp.slate50, Kp.slate50, Kp.slate400, Kp.slate800, 10f, Modifier.weight(1f), tamRotulo = 8f, pesoValor = 600,
                explicacao = "o número e os ficheiros de titularidade nunca são públicos; só o Administrador os consulta para verificação.")
            CartaoInfo("Protecção", "Intermediação Kupila", Kp.emerald50, Kp.emerald50, Kp.emerald700, Kp.emerald700, 10f, Modifier.weight(1f), tamRotulo = 8f, pesoValor = 600,
                explicacao = "o sinal fica em custódia até à decisão da vistoria, com OTP e reembolso comprovado.")
        }
    }
}

/** Visor em ecrã inteiro com zoom (`modal-product-photo-viewer`). */
@Composable
private fun VisorFotos(fotos: List<Fotografia>, inicial: Int, aoFechar: () -> Unit) {
    Dialog(onDismissRequest = aoFechar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val pager = rememberPagerState(initialPage = inicial) { fotos.size }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(pager, Modifier.fillMaxSize()) { i ->
                var escala by remember { mutableFloatStateOf(1f) }
                var x by remember { mutableFloatStateOf(0f) }
                var y by remember { mutableFloatStateOf(0f) }
                val estado = rememberTransformableState { z, d, _ -> escala = (escala * z).coerceIn(1f, 5f); if (escala > 1f) { x += d.x; y += d.y } else { x = 0f; y = 0f } }
                AsyncImage(modeloImagem(fotos[i].url), "Fotografia ${i + 1} de ${fotos.size}", contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().transformable(estado, canPan = { escala > 1f })
                        .graphicsLayer(scaleX = escala, scaleY = escala, translationX = x, translationY = y)
                        .pointerInput(Unit) { detectTapGestures(onDoubleTap = { escala = if (escala > 1f) 1f else 2.5f; x = 0f; y = 0f }) })
            }
            Box(Modifier.align(Alignment.TopEnd).padding(12.dp)) { Vidro(Fa.XMARK, 40f, 14f, Modifier, aoFechar) }
            T("${pager.currentPage + 1}/${fotos.size}", 12f, Color.White, 700, Modifier.align(Alignment.BottomCenter).padding(24.dp))
        }
    }
}

