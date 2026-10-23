package ao.kupila.gestaonegocios.ui.anunciar

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ao.kupila.gestaonegocios.dominio.anuncio.DetalhesCategoria
import ao.kupila.gestaonegocios.dominio.anuncio.MeioRecebimento
import ao.kupila.gestaonegocios.dominio.anuncio.ValidacaoAnuncio
import ao.kupila.gestaonegocios.dominio.catalogo.Categoria
import ao.kupila.gestaonegocios.dominio.financeiro.CondicoesAluguer
import ao.kupila.gestaonegocios.dominio.financeiro.FormatoAoa
import ao.kupila.gestaonegocios.dominio.financeiro.Modalidade
import ao.kupila.gestaonegocios.dominio.financeiro.PrazoMinimo
import ao.kupila.gestaonegocios.dominio.financeiro.Promocao
import ao.kupila.gestaonegocios.dominio.financeiro.UnidadeDuracao
import ao.kupila.gestaonegocios.dominio.financeiro.UnidadePreco
import ao.kupila.gestaonegocios.ui.comum.Aviso
import ao.kupila.gestaonegocios.ui.comum.BotaoPrincipal
import ao.kupila.gestaonegocios.ui.comum.Campo
import ao.kupila.gestaonegocios.ui.comum.Carregando
import ao.kupila.gestaonegocios.ui.comum.LinhaInfo
import ao.kupila.gestaonegocios.ui.comum.Seccao
import ao.kupila.gestaonegocios.ui.comum.Seletor
import ao.kupila.gestaonegocios.ui.comum.TipoAviso
import ao.kupila.gestaonegocios.ui.comum.modeloImagem
import coil3.compose.AsyncImage

// Opções exactas dos selects da origem (HTML 6864-6943).
private val REPAROS = listOf("Não precisa de reparos", "Pequenos reparos", "Precisa de reparos", "Em reparação")
private val QUALIDADES = listOf("Mosaicada", "Pintada", "Climatizada", "Mobilada")
private val FINALIDADES = listOf("Residencial", "Comercial", "Agrícola", "Industrial", "Mista")
private val TOPOGRAFIAS = listOf("Plano", "Inclinado", "Irregular")
private val ACESSOS = listOf("Asfaltado", "Terra batida", "Outro")
private val COMBUSTIVEIS = listOf("Gasolina", "Gasóleo", "Híbrido", "Eléctrico")
private val TRANSMISSOES = listOf("Manual", "Automática")
private val CONDICOES = listOf("Novo", "Seminovo", "Usado - bom estado", "Usado - precisa reparação")
private val PERFIS_LOCALIZACAO = listOf("Zona urbana", "Zona suburbana", "Zona rural", "Condomínio", "Zona residencial", "Zona comercial", "Centro urbano")

private fun op(l: List<String>) = l.map { it to it }
private fun numero(t: String) = t.filter(Char::isDigit)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AnunciarEcra(vm: AnunciarViewModel, aoVoltar: () -> Unit, aoLerPoliticas: () -> Unit, aoPagar: (String) -> Unit, aoPublicado: (String) -> Unit) {
    val e by vm.estado.collectAsStateWithLifecycle()
    val r by vm.resumo.collectAsStateWithLifecycle()
    val f = e.form
    val sair = { vm.aoSair(); aoVoltar() }
    BackHandler(onBack = sair)
    LaunchedEffect(e.destino) {
        when (val d = e.destino) {
            is Destino.Pagamento -> { vm.destinoTratado(); aoPagar(d.intentId) }
            is Destino.Publicado -> { vm.destinoTratado(); aoPublicado(d.id) }
            else -> {}
        }
    }
    val fotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(ValidacaoAnuncio.MAX_FOTOS)) { vm.adicionarFotos(it) }
    val docs = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.adicionarDocumentos(it) }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Column {
                    Text(if (e.edicaoDe != null) "Editar anúncio" else "Anunciar")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (e.guardadoEm != null) Icon(Icons.Filled.CloudDone, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(if (e.guardadoEm != null) " Guardado automaticamente · ${e.percentagem}%" else "${e.percentagem}% preenchido",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }, navigationIcon = { IconButton(onClick = sair) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } })
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp, 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator({ e.percentagem / 100f }, Modifier.fillMaxWidth())
                    e.progresso?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    val rotulo = when {
                        e.edicaoDe != null -> "Guardar alterações"
                        e.administrador -> "Publicar (isento)"
                        r != null -> "Pagar ${FormatoAoa.formatar(r!!.totalPublicacao)} e publicar"
                        else -> "Continuar para pagamento"
                    }
                    BotaoPrincipal(rotulo, vm::publicar, ocupado = e.aPublicar, enabled = e.aProcessarMedia == 0)
                }
            }
        },
    ) { pad ->
        if (e.aCarregar) { Carregando("A preparar o formulário…", Modifier.padding(pad)); return@Scaffold }
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            e.avisoSubstituicao?.let { Aviso(it, TipoAviso.AVISO) }
            if (e.erros.isNotEmpty()) Aviso(e.erros.joinToString("\n• ", "• "), TipoAviso.ERRO, titulo = "Falta pouco")
            e.erroGeral?.let { Aviso(it, TipoAviso.ERRO) }

            // 1. Título primeiro: dele deduzimos categoria, modalidade, marca, modelo, ano e área.
            Seccao("O que está a anunciar?", subtitulo = "Escreva o título; nós preenchemos o resto.") {
                Campo(f.titulo, vm::titulo, "Título", obrigatorio = true, maxCaracteres = 120, ajuda = "Ex: Vende-se Toyota Hilux 2019 em Talatona")
                e.sugestao?.let { s ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(s, Modifier.weight(1f).padding(horizontal = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        IconButton(onClick = vm::limparSugestao) { Icon(Icons.Filled.Close, "Fechar sugestão") }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Categoria.entries.forEach { c -> FilterChip(f.categoria == c, { vm.categoria(c) }, { Text(c.rotulo) }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(f.modalidade == Modalidade.VENDA, { vm.modalidade(Modalidade.VENDA) }, { Text("Venda") })
                    FilterChip(f.modalidade == Modalidade.ARRENDAMENTO, { vm.modalidade(Modalidade.ARRENDAMENTO) }, { Text("Aluguer") })
                }
            }

            Seccao("Fotografias", subtitulo = "Até ${ValidacaoAnuncio.MAX_FOTOS}. Optimizadas automaticamente; a primeira é a capa.") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(e.fotos) { i, foto ->
                        Box(Modifier.size(104.dp).clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = "Tornar capa") { vm.capa(i) }) {
                            AsyncImage(modeloImagem(foto.dataUrl), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            if (i == 0) Icon(Icons.Filled.Star, "Capa", Modifier.align(Alignment.BottomStart).padding(4.dp), tint = MaterialTheme.colorScheme.tertiary)
                            IconButton(onClick = { vm.removerFoto(i) }, Modifier.align(Alignment.TopEnd).size(28.dp)) { Icon(Icons.Filled.Close, "Remover fotografia") }
                        }
                    }
                    if (e.fotos.size < ValidacaoAnuncio.MAX_FOTOS) item {
                        Box(Modifier.size(104.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                            .clickable { fotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Filled.AddAPhoto, null); Text("Adicionar", style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                }
                if (e.aProcessarMedia > 0) LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            Seccao("Preço") {
                var precoTexto by rememberSaveable(e.rascunhoId) { mutableStateOf(f.preco?.valor?.stripTrailingZeros()?.toPlainString().orEmpty()) }
                Campo(precoTexto, { precoTexto = it; vm.preco(it) }, if (f.modalidade == Modalidade.ARRENDAMENTO) "Preço por unidade" else "Preço de venda",
                    obrigatorio = true, teclado = KeyboardType.Decimal, prefixo = "AOA ", ajuda = f.preco?.let { FormatoAoa.formatar(it) })
                if (f.modalidade == Modalidade.ARRENDAMENTO) f.aluguer?.let { CondicoesAluguerForm(it, vm::aluguer) }
            }

            Seccao("Características de ${f.categoria.rotulo}") { DetalhesForm(f.categoria, f.detalhes, vm::detalhes) }

            Seccao("Localização", subtitulo = "Pré-preenchida com a sua residência.") {
                Seletor("Província", op(vm.geografia.provincias), f.provincia, vm::provincia, obrigatorio = true)
                Seletor("Município", op(vm.geografia.municipios(f.provincia)), f.municipio.ifBlank { null }, vm::municipio, obrigatorio = true, enabled = f.provincia.isNotBlank())
                Campo(f.bairro, vm::bairro, "Bairro", obrigatorio = true)
                Campo(f.pontoReferencia, vm::pontoReferencia, "Ponto de referência", obrigatorio = true)
            }

            Seccao("Diferenciais", subtitulo = "Opcional: realça o que chama a atenção do Cliente.") {
                val d = f.diferenciais
                Seletor("Perfil da localização", listOf("" to "Não especificado") + op(PERFIS_LOCALIZACAO), d.perfilLocalizacao, { vm.diferenciais(d.copy(perfilLocalizacao = it)) })
                Campo(d.condominio, { vm.diferenciais(d.copy(condominio = it)) }, "Condomínio / referência comercial", maxCaracteres = 100)
                Campo(d.pontosFortes, { vm.diferenciais(d.copy(pontosFortes = it)) }, "Outros pontos fortes", linhas = 3, maxCaracteres = 500)
            }

            Seccao("Titularidade (privado)", subtitulo = "Só o Administrador vê. JPG ou PDF, até 2 MB cada (máx. ${ValidacaoAnuncio.MAX_DOCUMENTOS}).") {
                Campo(f.referenciaDocumento, vm::referenciaDocumento, "Número/referência do documento", obrigatorio = true)
                e.documentos.forEachIndexed { i, d ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Description, null)
                        Text("${d.nome} · ${d.tamanho / 1024} KB", Modifier.weight(1f).padding(horizontal = 8.dp), style = MaterialTheme.typography.bodySmall)
                        IconButton(onClick = { vm.removerDocumento(i) }) { Icon(Icons.Filled.Close, "Remover documento") }
                    }
                }
                if (e.documentos.size < ValidacaoAnuncio.MAX_DOCUMENTOS)
                    TextButton(onClick = { docs.launch(arrayOf("image/jpeg", "image/png", "application/pdf")) }) { Icon(Icons.Filled.AttachFile, null); Spacer(Modifier.width(6.dp)); Text("Anexar comprovativo") }
            }

            Seccao("Recebimento", subtitulo = "Para onde vai o valor quando o negócio for concluído. Lembramos o último usado.") {
                val iban = f.recebimento is MeioRecebimento.Iban
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(!iban && f.recebimento != null, { vm.recebimento(MeioRecebimento.MulticaixaExpress((f.recebimento as? MeioRecebimento.MulticaixaExpress)?.telefone.orEmpty())) }, { Text("Multicaixa Express") })
                    FilterChip(iban, { vm.recebimento(MeioRecebimento.Iban((f.recebimento as? MeioRecebimento.Iban)?.iban.orEmpty())) }, { Text("IBAN") })
                }
                when (val x = f.recebimento) {
                    is MeioRecebimento.Iban -> Campo(x.iban, { vm.recebimento(MeioRecebimento.Iban(it.uppercase().filter { c -> c.isLetterOrDigit() }.take(25))) }, "IBAN", obrigatorio = true,
                        erro = "Formato: AO + 23 dígitos".takeIf { x.iban.length >= 4 && !ValidacaoAnuncio.ibanValido(x.iban) })
                    is MeioRecebimento.MulticaixaExpress -> Campo(x.telefone, { vm.recebimento(MeioRecebimento.MulticaixaExpress(numero(it).take(9))) }, "Número Multicaixa Express",
                        obrigatorio = true, teclado = KeyboardType.Phone)
                    null -> {}
                }
            }

            Seccao("Alertas deste produto") {
                val a = f.alertas
                LinhaInterruptor("Receber alertas", a.activo) { vm.alertas(a.copy(activo = it)) }
                if (a.activo) {
                    LinhaInterruptor("Quando for reservado", a.reserva) { vm.alertas(a.copy(reserva = it)) }
                    LinhaInterruptor("Quando houver interesse", a.interesse) { vm.alertas(a.copy(interesse = it)) }
                }
            }

            if (!e.administrador && e.edicaoDe == null) Seccao("Mais visibilidade", subtitulo = "Opcional; cobrado com a publicação.") {
                Promocao.entries.forEach { p ->
                    val valor = r?.promocoes?.get(p)
                    LinhaInterruptor(p.rotulo + (valor?.let { " · ${FormatoAoa.formatar(it)}" } ?: ""), p in e.promocoes) { vm.promocao(p, it) }
                }
            }

            r?.let { res ->
                Seccao("Resumo") {
                    if (!res.valido) Aviso(res.problemas.joinToString("\n") { it.mensagem }, TipoAviso.ERRO)
                    LinhaInfo("Sinal da reserva (pago pelo Cliente)", FormatoAoa.formatar(res.sinal))
                    LinhaInfo("Comissão Kupila", FormatoAoa.formatar(res.comissao))
                    LinhaInfo("Recebe na conclusão", FormatoAoa.formatar(res.liquidoCustodia))
                    LinhaInfo("Taxa de anúncio", FormatoAoa.formatar(res.taxaAnuncio))
                    LinhaInfo("Total a pagar agora", FormatoAoa.formatar(res.totalPublicacao), destaque = true)
                }
            }

            Seccao("Políticas") {
                if (!e.politicaLida) TextButton(onClick = aoLerPoliticas) { Text("Ler as Políticas de Uso e Regras de Intermediação") }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(enabled = e.politicaLida) { vm.aceitar(!f.politicaAceite) }) {
                    Checkbox(f.politicaAceite, vm::aceitar, enabled = e.politicaLida)
                    Text(if (e.politicaLida) "Li e aceito as Políticas de Uso e as Regras de Intermediação." else "Leia as políticas para poder aceitar (só uma vez por versão).",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LinhaInterruptor(rotulo: String, valor: Boolean, aoMudar: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { aoMudar(!valor) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(rotulo, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(valor, aoMudar)
    }
}

@Composable
private fun CondicoesAluguerForm(c: CondicoesAluguer, aoMudar: (CondicoesAluguer) -> Unit) {
    Seletor("Unidade do preço", UnidadePreco.entries.map { it to it.rotulo.replaceFirstChar(Char::uppercase) }, c.unidade, { aoMudar(c.copy(unidade = it)) })
    if (c.unidade == UnidadePreco.PERSONALIZADA) {
        Campo(c.nomeUnidadePersonalizada, { aoMudar(c.copy(nomeUnidadePersonalizada = it.take(40))) }, "Nome da unidade", obrigatorio = true)
        Campo(c.unidadesPersonalizadasPorMes?.toPlainString().orEmpty(), { aoMudar(c.copy(unidadesPersonalizadasPorMes = it.replace(',', '.').toBigDecimalOrNull())) },
            "Quantas unidades equivalem a 1 mês", obrigatorio = true, teclado = KeyboardType.Decimal)
    }
    val opcoes = listOf<Pair<String, String>>("3_MONTHS" to "3 meses", "6_MONTHS" to "6 meses", "12_MONTHS" to "12 meses", "CUSTOM" to "Personalizado")
    Seletor("Tempo mínimo de contrato", opcoes, c.prazo.codigoOrigem, { cod ->
        aoMudar(c.copy(prazo = when (cod) { "6_MONTHS" -> PrazoMinimo.SeisMeses; "12_MONTHS" -> PrazoMinimo.Anual; "CUSTOM" -> PrazoMinimo.Personalizado(1, UnidadeDuracao.MES); else -> PrazoMinimo.TresMeses }))
    })
    (c.prazo as? PrazoMinimo.Personalizado)?.let { p ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Campo(p.quantidade.toString(), { aoMudar(c.copy(prazo = p.copy(quantidade = numero(it).toIntOrNull() ?: 1))) }, "Quantidade", Modifier.weight(1f), teclado = KeyboardType.Number)
            Seletor("Unidade", UnidadeDuracao.entries.map { it to it.rotulo }, p.unidade, { aoMudar(c.copy(prazo = p.copy(unidade = it))) }, Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetalhesForm(cat: Categoria, d: DetalhesCategoria, aoMudar: (DetalhesCategoria) -> Unit) {
    Seletor("Subcategoria", op(cat.subcategorias), d.subcategoria.ifBlank { null }, { s ->
        aoMudar(when (d) { is DetalhesCategoria.Imovel -> d.copy(subcategoria = s); is DetalhesCategoria.Terreno -> d.copy(subcategoria = s); is DetalhesCategoria.Viatura -> d.copy(subcategoria = s)
            is DetalhesCategoria.Motorizada -> d.copy(subcategoria = s); is DetalhesCategoria.Outro -> d.copy(subcategoria = s) })
    }, obrigatorio = true)
    when (d) {
        is DetalhesCategoria.Imovel -> {
            Campo(d.compartimentos?.toString().orEmpty(), { aoMudar(d.copy(compartimentos = numero(it).toIntOrNull())) }, "Quantidade de compartimentos", obrigatorio = true, teclado = KeyboardType.Number)
            Seletor("Estado / Reparos", op(REPAROS), d.estadoReparos.ifBlank { null }, { aoMudar(d.copy(estadoReparos = it)) }, obrigatorio = true)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QUALIDADES.forEach { q -> FilterChip(q in d.qualidades, { aoMudar(d.copy(qualidades = if (q in d.qualidades) d.qualidades - q else d.qualidades + q)) }, { Text(q) }) }
            }
            Campo(d.notas, { aoMudar(d.copy(notas = it)) }, "Outras características", linhas = 3, maxCaracteres = 600)
        }
        is DetalhesCategoria.Terreno -> {
            Campo(d.areaM2?.toPlainString().orEmpty(), { aoMudar(d.copy(areaM2 = it.replace(',', '.').toBigDecimalOrNull())) }, "Área (m²)", obrigatorio = true, teclado = KeyboardType.Decimal)
            Seletor("Finalidade", op(FINALIDADES), d.finalidade.ifBlank { null }, { aoMudar(d.copy(finalidade = it)) }, obrigatorio = true)
            Seletor("Topografia", listOf("" to "Não informado") + op(TOPOGRAFIAS), d.topografia, { aoMudar(d.copy(topografia = it)) })
            Seletor("Acesso", listOf("" to "Não informado") + op(ACESSOS), d.acesso, { aoMudar(d.copy(acesso = it)) })
            Campo(d.notas, { aoMudar(d.copy(notas = it)) }, "Infraestruturas / observações", linhas = 3, maxCaracteres = 600)
        }
        is DetalhesCategoria.Viatura -> {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Campo(d.marca, { aoMudar(d.copy(marca = it.take(80))) }, "Marca", Modifier.weight(1f), obrigatorio = true)
                Campo(d.modelo, { aoMudar(d.copy(modelo = it.take(80))) }, "Modelo", Modifier.weight(1f), obrigatorio = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Campo(d.ano?.toString().orEmpty(), { aoMudar(d.copy(ano = numero(it).take(4).toIntOrNull())) }, "Ano", Modifier.weight(1f), obrigatorio = true, teclado = KeyboardType.Number)
                Campo(d.quilometragem?.toString().orEmpty(), { aoMudar(d.copy(quilometragem = numero(it).toLongOrNull())) }, "Quilometragem", Modifier.weight(1f), teclado = KeyboardType.Number)
            }
            Seletor("Combustível", op(COMBUSTIVEIS), d.combustivel.ifBlank { null }, { aoMudar(d.copy(combustivel = it)) })
            Seletor("Transmissão", op(TRANSMISSOES), d.transmissao.ifBlank { null }, { aoMudar(d.copy(transmissao = it)) })
            Campo(d.notas, { aoMudar(d.copy(notas = it)) }, "Estado / observações", linhas = 2, maxCaracteres = 500)
        }
        is DetalhesCategoria.Motorizada -> {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Campo(d.marca, { aoMudar(d.copy(marca = it.take(80))) }, "Marca", Modifier.weight(1f), obrigatorio = true)
                Campo(d.modelo, { aoMudar(d.copy(modelo = it.take(80))) }, "Modelo", Modifier.weight(1f), obrigatorio = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Campo(d.ano?.toString().orEmpty(), { aoMudar(d.copy(ano = numero(it).take(4).toIntOrNull())) }, "Ano", Modifier.weight(1f), obrigatorio = true, teclado = KeyboardType.Number)
                Campo(d.cilindrada?.toString().orEmpty(), { aoMudar(d.copy(cilindrada = numero(it).toIntOrNull())) }, "Cilindrada (cc)", Modifier.weight(1f), teclado = KeyboardType.Number)
            }
            Campo(d.quilometragem?.toString().orEmpty(), { aoMudar(d.copy(quilometragem = numero(it).toLongOrNull())) }, "Quilometragem", teclado = KeyboardType.Number)
            Campo(d.notas, { aoMudar(d.copy(notas = it)) }, "Estado / observações", linhas = 2, maxCaracteres = 500)
        }
        is DetalhesCategoria.Outro -> {
            Seletor("Estado", op(CONDICOES), d.condicao.ifBlank { null }, { aoMudar(d.copy(condicao = it)) }, obrigatorio = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Campo(d.marca, { aoMudar(d.copy(marca = it.take(80))) }, "Marca", Modifier.weight(1f))
                Campo(d.modelo, { aoMudar(d.copy(modelo = it.take(80))) }, "Modelo", Modifier.weight(1f))
            }
            Campo(d.quantidade.toString(), { aoMudar(d.copy(quantidade = numero(it).toIntOrNull()?.coerceAtLeast(1) ?: 1)) }, "Quantidade", teclado = KeyboardType.Number)
            Campo(d.notas, { aoMudar(d.copy(notas = it)) }, "Descrição técnica", obrigatorio = true, linhas = 3, maxCaracteres = 700)
        }
    }
}
