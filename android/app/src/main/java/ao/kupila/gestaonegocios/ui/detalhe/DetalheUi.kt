package ao.kupila.gestaonegocios.ui.detalhe

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ao.kupila.gestaonegocios.dominio.anuncio.AnuncioPublico
import ao.kupila.gestaonegocios.dominio.anuncio.DetalhesCategoria
import ao.kupila.gestaonegocios.dominio.anuncio.EstadoAnuncio
import ao.kupila.gestaonegocios.dominio.anuncio.EstadoVerificacao
import ao.kupila.gestaonegocios.dominio.anuncio.Fotografia
import ao.kupila.gestaonegocios.dominio.catalogo.Categoria
import ao.kupila.gestaonegocios.dominio.financeiro.BaseComissao
import ao.kupila.gestaonegocios.dominio.financeiro.FormatoAoa
import ao.kupila.gestaonegocios.dominio.financeiro.Modalidade
import ao.kupila.gestaonegocios.dominio.financeiro.ResumoFinanceiro
import ao.kupila.gestaonegocios.ui.comum.Aviso
import ao.kupila.gestaonegocios.ui.comum.BotaoPrincipal
import ao.kupila.gestaonegocios.ui.comum.Carregando
import ao.kupila.gestaonegocios.ui.comum.LinhaInfo
import ao.kupila.gestaonegocios.ui.comum.Seccao
import ao.kupila.gestaonegocios.ui.comum.Selo
import ao.kupila.gestaonegocios.ui.comum.TipoAviso
import ao.kupila.gestaonegocios.ui.comum.modeloImagem
import ao.kupila.gestaonegocios.ui.explorar.precoComUnidade
import ao.kupila.gestaonegocios.ui.tema.LocalCoresKupila
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

/**
 * Detalhe do anúncio (`modal-details`, HTML 7313-7457). Cabeçalho e acção principal fixos, conteúdo rolável entre ambos,
 * sem cobertura por barras do sistema ou teclado (secção 6; correcções 1.0.237-1.0.239).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetalheEcra(vm: DetalheViewModel, aoVoltar: () -> Unit, aoReservar: ((String) -> Unit)?, aoEditar: ((String) -> Unit)?, aoPedirAutorizacao: ((String) -> Unit)?) {
    val a by vm.anuncio.collectAsStateWithLifecycle()
    val resumo by vm.resumo.collectAsStateWithLifecycle()
    val privado by vm.privado.collectAsStateWithLifecycle()
    val accoes by vm.accoes.collectAsStateWithLifecycle()
    val erro by vm.erroActualizacao.collectAsStateWithLifecycle()
    var visor by rememberSaveable { mutableStateOf<Int?>(null) }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Produto") }, navigationIcon = { IconButton(onClick = aoVoltar) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } })
    }, bottomBar = {
        val ac = accoes
        if (a != null && ac != null) Surface(tonalElevation = 3.dp) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when {
                    ac.podeEditar && aoEditar != null -> BotaoPrincipal("Editar anúncio", { aoEditar(vm.id) }, icone = Icons.Filled.Edit)
                    ac.podeReservar && aoReservar != null -> {
                        val ocupado by vm.aReservar.collectAsStateWithLifecycle()
                        BotaoPrincipal(resumo?.let { "Reservar · sinal ${ao.kupila.gestaonegocios.dominio.financeiro.FormatoAoa.formatar(it.sinal)}" } ?: "Reservar",
                            { vm.reservar(aoReservar) }, ocupado = ocupado, icone = Icons.Filled.Payments)
                    }
                    ac.motivoSemReserva != null -> Text(ac.motivoSemReserva, style = MaterialTheme.typography.bodySmall)
                }
                if (ac.podePedirAutorizacao && aoPedirAutorizacao != null) TextButton(onClick = { aoPedirAutorizacao(vm.id) }) { Text("Pedir autorização para desanunciar ou excluir") }
            }
        }
    }) { pad ->
        val an = a
        if (an == null) { Carregando("A carregar os detalhes do produto…", Modifier.padding(pad)); return@Scaffold }
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Galeria(an.fotos, aoAmpliar = { visor = it })
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                erro?.let { Aviso("Não foi possível confirmar a versão mais recente: $it", TipoAviso.AVISO) }
                Etiquetas(an)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("ID ${an.id}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(an.titulo, style = MaterialTheme.typography.headlineSmall)
                    Text("${an.municipio}, ${an.provincia}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                resumo?.let { ResumoSeccao(an, it, mostrarProprietario = accoes?.podeEditar == true) }
                Caracteristicas(an, resumo)
                if (an.diferenciais.perfilLocalizacao.isNotBlank() || an.diferenciais.condominio.isNotBlank() || an.diferenciais.pontosFortes.isNotBlank())
                    Seccao("Diferenciais") {
                        if (an.diferenciais.perfilLocalizacao.isNotBlank()) LinhaInfo("Perfil da localização", an.diferenciais.perfilLocalizacao)
                        if (an.diferenciais.condominio.isNotBlank()) LinhaInfo("Condomínio / referência comercial", an.diferenciais.condominio)
                        if (an.diferenciais.pontosFortes.isNotBlank()) Text(an.diferenciais.pontosFortes, style = MaterialTheme.typography.bodyMedium)
                    }
                Seccao("Titularidade & segurança") {
                    Text(when (an.verificacaoTitularidade) {
                        EstadoVerificacao.VERIFICADO_SEM_DUPLICADO -> "Documentos de titularidade recebidos e verificação de duplicados concluída."
                        EstadoVerificacao.REVISAO_MANUAL -> "Verificação de titularidade em revisão administrativa."
                        EstadoVerificacao.PENDENTE -> "Verificação de titularidade pendente."
                    }, style = MaterialTheme.typography.bodyMedium)
                    Text("Os documentos de titularidade e os dados de recebimento nunca são públicos nem revelados ao Cliente pela reserva.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DadosProtegidos(privado)
                Seccao("Validação em 2 etapas", subtitulo = "Reserva protegida") {
                    Text("1. Gere a referência e pague o sinal. 2. Submeta o comprovativo: a reserva só é confirmada depois de o pagamento ser validado. " +
                        "O prazo da reserva começa na confirmação oficial. Na vistoria, o Proprietário confirma o negócio com o OTP do comprovativo.",
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
    visor?.let { i -> a?.let { VisorFotos(it.fotos, i) { visor = null } } }
}

@Composable
private fun Etiquetas(a: AnuncioPublico) {
    val cores = LocalCoresKupila.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Selo(a.categoria.rotulo, MaterialTheme.colorScheme.primary)
        Selo(if (a.modalidade == Modalidade.VENDA) "Venda definitiva" else "Arrendamento / Aluguer", MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.onSecondary)
        Selo(when (a.estado) { EstadoAnuncio.ACTIVO -> "Disponível"; EstadoAnuncio.RESERVADO -> "Reservado"; else -> a.estado.rotulo },
            if (a.estado == EstadoAnuncio.ACTIVO) cores.sucesso else cores.aviso)
        a.promocao?.let { Selo("Patrocinado", cores.pago) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Galeria(fotos: List<Fotografia>, aoAmpliar: (Int) -> Unit) {
    if (fotos.isEmpty()) {
        Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.ImageNotSupported, "Sem fotografias")
        }
        return
    }
    val pager = rememberPagerState { fotos.size }
    val ambito = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f)) {
            HorizontalPager(pager, Modifier.fillMaxSize()) { i ->
                AsyncImage(modeloImagem(fotos[i].url), "Fotografia ${i + 1} de ${fotos.size}", contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().pointerInput(i) { detectTapGestures(onTap = { aoAmpliar(i) }) })
            }
            Text("${pager.currentPage + 1}/${fotos.size}", color = Color.White, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp).clip(RoundedCornerShape(50)).background(Color(0x99000000)).padding(horizontal = 10.dp, vertical = 4.dp))
            if (fotos[pager.currentPage].sintetica) Text("Ilustração gerada · não é fotografia real", color = Color.White, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp).clip(RoundedCornerShape(50)).background(Color(0xCC111816)).padding(horizontal = 10.dp, vertical = 4.dp))
        }
        if (fotos.size > 1) LazyRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(fotos) { i, f ->
                AsyncImage(modeloImagem(f.miniatura ?: f.url), "Ver fotografia ${i + 1}", contentScale = ContentScale.Crop,
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(10.dp))
                        .border(2.dp, if (i == pager.currentPage) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(10.dp))
                        .clickable { ambito.launch { pager.animateScrollToPage(i) } })
            }
        }
    }
}

/** Visor em ecrã inteiro com zoom (`modal-product-photo-viewer`). */
@Composable
private fun VisorFotos(fotos: List<Fotografia>, inicial: Int, aoFechar: () -> Unit) {
    Dialog(onDismissRequest = aoFechar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val pager = rememberPagerState(initialPage = inicial) { fotos.size }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = true) { i ->
                var escala by remember { mutableFloatStateOf(1f) }
                var x by remember { mutableFloatStateOf(0f) }
                var y by remember { mutableFloatStateOf(0f) }
                val estado = rememberTransformableState { z, d, _ -> escala = (escala * z).coerceIn(1f, 5f); if (escala > 1f) { x += d.x; y += d.y } else { x = 0f; y = 0f } }
                AsyncImage(modeloImagem(fotos[i].url), "Fotografia ${i + 1} de ${fotos.size}", contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().transformable(estado, canPan = { escala > 1f })
                        .graphicsLayer(scaleX = escala, scaleY = escala, translationX = x, translationY = y)
                        .pointerInput(Unit) { detectTapGestures(onDoubleTap = { escala = if (escala > 1f) 1f else 2.5f; x = 0f; y = 0f }) })
            }
            IconButton(onClick = aoFechar, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) { Icon(Icons.Filled.Close, "Fechar", tint = Color.White) }
            Text("${pager.currentPage + 1}/${fotos.size}", color = Color.White, modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp))
        }
    }
}

@Composable
private fun ResumoSeccao(a: AnuncioPublico, r: ResumoFinanceiro, mostrarProprietario: Boolean) {
    var info by rememberSaveable { mutableStateOf(false) }
    val f: (ao.kupila.gestaonegocios.dominio.financeiro.Dinheiro) -> String = { FormatoAoa.formatar(it) }
    Seccao("Resumo do negócio") {
        LinhaInfo(if (a.modalidade == Modalidade.VENDA) "Preço" else "Preço do aluguer", precoComUnidade(a), destaque = true)
        if (a.modalidade == Modalidade.ARRENDAMENTO) LinhaInfo("Valor mínimo do contrato", f(r.baseNegocio))
        LinhaInfo(if (a.modalidade == Modalidade.VENDA) "Sinal de reserva (${FormatoAoa.formatar(r.entrada.parametros.sinalVenda)})"
            else "Sinal (${r.entrada.parametros.mesesSinalAluguer} mês(es))", f(r.sinal), destaque = true)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { LinhaInfo("Comissão da plataforma (${r.baseComissao.rotulo})", f(r.comissao)) }
            IconButton(onClick = { info = !info }) { Icon(Icons.Filled.Info, "Explicar a comissão") }
        }
        AnimatedVisibility(info) {
            Text(if (r.baseComissao == BaseComissao.SINAL)
                "Na venda, a comissão é ${FormatoAoa.formatar(r.entrada.parametros.comissao)} do sinal. O restante do sinal fica em custódia até à decisão da vistoria."
            else "No aluguer, a comissão é ${FormatoAoa.formatar(r.entrada.parametros.comissao)} do valor mínimo do contrato (${f(r.baseNegocio)}), descontada do sinal.",
                style = MaterialTheme.typography.bodySmall)
        }
        if (!r.valido) Aviso("Este anúncio tem uma condição financeira inválida com os ajustes actuais (${r.problemas.first().mensagem}). A reserva fica bloqueada até revisão administrativa.", TipoAviso.AVISO)
        if (mostrarProprietario) Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Para o Proprietário", style = MaterialTheme.typography.titleSmall)
                LinhaInfo("Líquido do sinal (em custódia até à vistoria)", f(r.liquidoCustodia))
                LinhaInfo("Saldo a acertar entre as partes", f(r.saldoRestante))
                LinhaInfo("Taxa de publicação (paga à parte)", f(r.totalPublicacao))
                LinhaInfo("Total líquido previsto", f(r.liquidoPrevistoProprietario), destaque = true)
            }
        }
        Text("Fluxo completo deste negócio", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text("O Cliente paga ${f(r.sinal)} de sinal. Depois da confirmação do pagamento, ${f(r.comissao)} correspondem à comissão e " +
            "${f(r.liquidoCustodia)} ficam em custódia até à decisão da vistoria. O saldo de ${f(r.saldoRestante)} pertence ao acordo entre as partes e " +
            "não é pago dentro da plataforma. Se o negócio não for fechado, o reembolso elegível é de ${f(r.reembolsoElegivel)}, sujeito à análise.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Caracteristicas(a: AnuncioPublico, r: ResumoFinanceiro?) {
    val titulo = when (a.categoria) {
        Categoria.IMOVEL -> "Características do Imóvel"; Categoria.TERRENO -> "Características do Terreno"; Categoria.VIATURA -> "Características da Viatura"
        Categoria.MOTORIZADA -> "Características da Motorizada"; Categoria.OUTRO -> "Características do Produto"
    }
    val ni = "Não informado"
    fun km(v: Long?) = v?.let { FormatoAoa.formatarNumero(java.math.BigDecimal(it)) + " km" } ?: ni
    Seccao(titulo) {
        LinhaInfo("Categoria", a.categoria.rotuloPlural)
        LinhaInfo("Subcategoria", a.detalhes.subcategoria.ifBlank { ni })
        a.aluguer?.let { c ->
            LinhaInfo("Tempo mínimo", c.prazo.rotulo())
            r?.termosAluguer?.let { LinhaInfo("Unidades cobradas", it.unidadesCobranca.toString()) }
        }
        when (val d = a.detalhes) {
            is DetalhesCategoria.Imovel -> { LinhaInfo("Compartimentos", d.compartimentos?.toString() ?: ni); LinhaInfo("Estado / Reparos", d.estadoReparos.ifBlank { ni })
                LinhaInfo("Acabamentos", d.qualidades.joinToString(", ").ifBlank { ni }) }
            is DetalhesCategoria.Terreno -> { LinhaInfo("Área", d.areaM2?.let { FormatoAoa.formatarNumero(it) + " m²" } ?: ni); LinhaInfo("Finalidade", d.finalidade.ifBlank { ni })
                LinhaInfo("Topografia", d.topografia.ifBlank { ni }); LinhaInfo("Acesso", d.acesso.ifBlank { ni }) }
            is DetalhesCategoria.Viatura -> { LinhaInfo("Marca", d.marca.ifBlank { ni }); LinhaInfo("Modelo", d.modelo.ifBlank { ni }); LinhaInfo("Ano", d.ano?.toString() ?: ni)
                LinhaInfo("Quilometragem", km(d.quilometragem)); LinhaInfo("Combustível", d.combustivel.ifBlank { ni }); LinhaInfo("Transmissão", d.transmissao.ifBlank { ni }) }
            is DetalhesCategoria.Motorizada -> { LinhaInfo("Marca", d.marca.ifBlank { ni }); LinhaInfo("Modelo", d.modelo.ifBlank { ni }); LinhaInfo("Ano", d.ano?.toString() ?: ni)
                LinhaInfo("Cilindrada", d.cilindrada?.let { "$it cc" } ?: ni); LinhaInfo("Quilometragem", km(d.quilometragem)) }
            is DetalhesCategoria.Outro -> { LinhaInfo("Estado", d.condicao.ifBlank { ni }); LinhaInfo("Marca", d.marca.ifBlank { ni }); LinhaInfo("Modelo / Referência", d.modelo.ifBlank { ni })
                LinhaInfo("Quantidade", d.quantidade.toString()) }
        }
        if (a.detalhes.notas.isNotBlank()) { Text("Observações", style = MaterialTheme.typography.titleSmall); Text(a.detalhes.notas, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun DadosProtegidos(p: AcessoPrivado) {
    val (aberto, titulo, texto) = when (p) {
        AcessoPrivado.ACarregar -> Triple(false, "A carregar dados protegidos…", "")
        AcessoPrivado.Protegido -> Triple(false, "Dados protegidos até à validação do pagamento.",
            "Bairro, Ponto de Referência, Proprietário e Contacto só serão revelados depois que o comprovativo e o pagamento integral do sinal forem confirmados.")
        is AcessoPrivado.Titular -> Triple(true, "Produto da sua tutela.", "Localização e dados de contacto disponíveis ao titular.")
        is AcessoPrivado.Administrador -> Triple(true, "Acesso administrativo completo.", "Os dados protegidos deste anúncio estão disponíveis.")
        is AcessoPrivado.ReservaConfirmada -> Triple(true, "Reserva paga e confirmada.", "Bairro, Ponto de Referência, Proprietário e Contacto desbloqueados para acompanhar o negócio.")
        is AcessoPrivado.Falhou -> Triple(false, "Não foi possível carregar os dados protegidos.", p.mensagem)
    }
    Seccao("Localização e contacto") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (aberto) Icons.Filled.LockOpen else Icons.Filled.Lock, null, tint = if (aberto) LocalCoresKupila.current.sucesso else LocalCoresKupila.current.aviso)
            Text(titulo, style = MaterialTheme.typography.titleSmall)
        }
        if (texto.isNotBlank()) Text(texto, style = MaterialTheme.typography.bodySmall)
        val d = when (p) { is AcessoPrivado.Titular -> p.dados; is AcessoPrivado.Administrador -> p.dados; is AcessoPrivado.ReservaConfirmada -> p.dados; else -> null }
        if (d != null) {
            LinhaInfo("Bairro", d.bairro.ifBlank { "Não informado" }); LinhaInfo("Ponto de referência", d.pontoReferencia.ifBlank { "Não informado" })
            LinhaInfo("Proprietário", d.nomeProprietario.ifBlank { "Não informado" }); LinhaInfo("Contacto", d.contactoProprietario.ifBlank { "Não informado" })
        }
    }
}
