package ao.kupila.gestaonegocios.ui.anunciar

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ao.kupila.gestaonegocios.dominio.anuncio.DetalhesCategoria
import ao.kupila.gestaonegocios.dominio.anuncio.MeioRecebimento
import ao.kupila.gestaonegocios.dominio.anuncio.ValidacaoAnuncio
import ao.kupila.gestaonegocios.dominio.catalogo.Categoria
import ao.kupila.gestaonegocios.dominio.financeiro.CondicoesAluguer
import ao.kupila.gestaonegocios.dominio.financeiro.Dinheiro
import ao.kupila.gestaonegocios.dominio.financeiro.FormatoAoa
import ao.kupila.gestaonegocios.dominio.financeiro.Modalidade
import ao.kupila.gestaonegocios.dominio.financeiro.PrazoMinimo
import ao.kupila.gestaonegocios.dominio.financeiro.Promocao
import ao.kupila.gestaonegocios.dominio.financeiro.ResumoFinanceiro
import ao.kupila.gestaonegocios.dominio.financeiro.UnidadeDuracao
import ao.kupila.gestaonegocios.dominio.financeiro.UnidadePreco
import ao.kupila.gestaonegocios.dominio.perfis.CanalAlerta
import ao.kupila.gestaonegocios.ui.comum.Fa
import ao.kupila.gestaonegocios.ui.comum.modeloImagem
import ao.kupila.gestaonegocios.ui.kp.BlocoFinanceiro
import ao.kupila.gestaonegocios.ui.kp.BlocoProteccao
import ao.kupila.gestaonegocios.ui.kp.FaIcone
import ao.kupila.gestaonegocios.ui.kp.Kp
import ao.kupila.gestaonegocios.ui.kp.KpBotao
import ao.kupila.gestaonegocios.ui.kp.KpBotaoSec
import ao.kupila.gestaonegocios.ui.kp.KpEntrada
import ao.kupila.gestaonegocios.ui.kp.KpRotulo
import ao.kupila.gestaonegocios.ui.kp.KpSelect
import ao.kupila.gestaonegocios.ui.kp.LocalMarca
import ao.kupila.gestaonegocios.ui.kp.T
import ao.kupila.gestaonegocios.ui.kp.sup
import ao.kupila.gestaonegocios.ui.kp.toque
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
private fun aoa(d: Dinheiro) = FormatoAoa.formatar(d)

/** TAB PUBLICAR (`#tab-publish`, HTML 6785-7177): 9 secções da origem + preenchimento a partir do título e rascunho automático. */
@Composable
fun AnunciarEcra(vm: AnunciarViewModel, aoVoltar: () -> Unit, aoLerPoliticas: () -> Unit, aoPagar: (String) -> Unit, aoPublicado: (String) -> Unit) {
    val e by vm.estado.collectAsStateWithLifecycle()
    val r by vm.resumo.collectAsStateWithLifecycle()
    val f = e.form
    val marca = LocalMarca.current
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
    Column(Modifier.fillMaxSize().background(Kp.slate50)) {
        // Título fixo da aba
        Column(Modifier.fillMaxWidth().background(Kp.slate50).padding(12.dp, 10.dp, 12.dp, 0.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                T(if (e.edicaoDe != null) "Editar Produto" else "Anunciar Produto", 13f, Kp.slate900, 700, Modifier.weight(1f))
                if (e.guardadoEm != null) Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).sup(6.dp, fundo = Kp.emerald600, forma = CircleShape)); Spacer(Modifier.width(4.dp)); T("Rascunho salvo · ${e.percentagem}%", 7f, Kp.emerald700, 700)
                }
            }
            T("Cadastre o produto na categoria correcta. O Kupila gere reserva, alertas, verificação e comissão.", 7.5f, Kp.slate500, Modifier.padding(top = 2.dp))
            Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(1.dp).background(Kp.slate900))
        }
        if (e.aCarregar) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = marca.c600) }; return@Column }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp, 14.dp, 12.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            e.avisoSubstituicao?.let { Aviso(it, Kp.amber50, Kp.amber200, Kp.amber800, Fa.TRIANGLE_EXCLAMATION) }
            if (e.erros.isNotEmpty()) Aviso(e.erros.joinToString("\n• ", "• "), Kp.red50, Color(0xFFFEE2E2), Kp.red700, Fa.TRIANGLE_EXCLAMATION)
            e.erroGeral?.let { Aviso(it, Kp.red50, Color(0xFFFEE2E2), Kp.red700, Fa.TRIANGLE_EXCLAMATION) }

            // 1. Identificação do produto
            CabecalhoSeccao(Fa.TAG, "1. Identificação do produto", "Categoria, modalidade, subcategoria e título.", primeira = true)
            Column {
                KpRotulo("Categoria do Produto", 9f, true, Color(0xFF1F2937))
                T("A categoria define os campos técnicos, filtros e regras de detecção de duplicados.", 8f, Kp.slate400, Modifier.padding(bottom = 8.dp))
                val cats = listOf(Triple(Categoria.IMOVEL, Fa.HOUSE, Kp.emerald600), Triple(Categoria.TERRENO, Fa.MAP, Kp.emerald600),
                    Triple(Categoria.VIATURA, Fa.CAR, Kp.blue600), Triple(Categoria.MOTORIZADA, Fa.MOTORCYCLE, Color(0xFFEA580C)))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    cats.chunked(2).forEach { par ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            par.forEachIndexed { i, (c, ic, cor) -> OpcaoCategoria(c.rotulo, ic, cor, f.categoria == c, Modifier.weight(if (i == 0) 1.4f else 1f)) { vm.categoria(c) } }
                        }
                    }
                    OpcaoCategoria("Outros Produtos", Fa.BOX_OPEN, Kp.violet600, f.categoria == Categoria.OUTRO, Modifier.fillMaxWidth(), "Eletrónicos, equipamentos, mobiliário, peças…") { vm.categoria(Categoria.OUTRO) }
                }
            }
            Campo("Subcategoria", true) { KpSelect(op(f.categoria.subcategorias), f.detalhes.subcategoria.ifBlank { null }, { s -> vm.detalhes(comSubcategoria(f.detalhes, s)) },
                altura = 31.dp, tamanho = 8.5f, vazio = "Seleccione a subcategoria") }
            Campo("Modalidade do Negócio", true) {
                KpSelect(listOf(Modalidade.VENDA to "Venda Definitiva", Modalidade.ARRENDAMENTO to "Arrendamento / Aluguer"), f.modalidade, vm::modalidade, altura = 31.dp, tamanho = 8.5f)
            }
            Campo("Título do Anúncio", true) {
                KpEntrada(f.titulo, vm::titulo, dica = "Ex: Vivenda T3 no Talatona", altura = 31.dp, tamanhoTexto = 8.5f)
                e.sugestao?.let { s ->
                    Row(Modifier.padding(top = 6.dp).fillMaxWidth().sup(10.dp, fundo = Kp.violet50, borda = Kp.violet100).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FaIcone(Fa.WAND_MAGIC_SPARKLES, 9f, Kp.violet700); Spacer(Modifier.width(6.dp))
                        T(s, 8f, Kp.violet800, Modifier.weight(1f), linha = 12f)
                        FaIcone(Fa.XMARK, 9f, Kp.violet700, Modifier.toque(aoTocar = vm::limparSugestao).padding(4.dp))
                    }
                }
            }

            // 2. Exigências do Proprietário
            CabecalhoSeccao(Fa.FILE_SIGNATURE, "2. Exigências do Proprietário", "Tempo mínimo e sinal obedecem às regras administrativas.")
            if (f.modalidade == Modalidade.VENDA) Aviso(null, marca.c50, marca.c100, marca.c800, Fa.LOCK) {
                androidx.compose.material3.Text(buildAnnotatedString { withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Venda:") }
                    append(" o sinal em percentagem é definido globalmente pelo Administrador e não pode ser alterado pelo Proprietário.") }, color = marca.c800, fontSize = 9.sp, lineHeight = 14.sp)
            } else f.aluguer?.let { CondicoesAluguerForm(it, vm::aluguer) }

            // 3. Características e diferenciais
            CabecalhoSeccao(Fa.LIST_CHECK, "3. Características e diferenciais", "Dados técnicos e qualidades reais que ajudam o Cliente a decidir.")
            Column(Modifier.fillMaxWidth().sup(12.dp, fundo = marca.c50, borda = marca.c100).padding(9.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { FaIcone(Fa.HOUSE_CIRCLE_CHECK, 12f, marca.c800); Spacer(Modifier.width(6.dp)); T(tituloCaracteristicas(f.categoria), 12f, marca.c800, 700) }
                T("Informe as condições reais do bem para o cliente analisar antes da vistoria.", 9f, marca.c700)
                DetalhesForm(f.detalhes) { vm.detalhes(it) }
            }
            Column(Modifier.fillMaxWidth().sup(12.dp, fundo = Kp.violet50, borda = Kp.violet100).padding(9.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val d = f.diferenciais
                Row(verticalAlignment = Alignment.CenterVertically) { FaIcone(Fa.WAND_MAGIC_SPARKLES, 12f, Kp.violet700); Spacer(Modifier.width(6.dp)); T("Diferenciais que chamam atenção do Cliente", 11f, Kp.violet900, 700) }
                T("Realce qualidades reais do produto sem substituir as características técnicas obrigatórias.", 8f, Kp.violet700)
                Campo("Perfil da localização") { KpSelect(listOf("" to "Não especificado") + op(PERFIS_LOCALIZACAO), d.perfilLocalizacao, { vm.diferenciais(d.copy(perfilLocalizacao = it)) }, altura = 31.dp, tamanho = 8.5f, peso = 700) }
                Campo("Condomínio / referência comercial") { KpEntrada(d.condominio, { vm.diferenciais(d.copy(condominio = it.take(100))) }, dica = "Ex: Condomínio X", altura = 31.dp, tamanhoTexto = 8.5f) }
                Campo("Outros pontos fortes / qualidades") { KpEntrada(d.pontosFortes, { vm.diferenciais(d.copy(pontosFortes = it.take(500))) }, dica = "Ex: rua asfaltada, segurança 24h, água regular, vista privilegiada, próximo de escolas, baixa quilometragem, pouco uso…", altura = 52.dp, tamanhoTexto = 8.5f, linhas = 3) }
                T("Informe apenas atributos verdadeiros e relevantes para a decisão do Cliente.", 7.5f, Kp.violet600)
            }

            // 4. Localização
            CabecalhoSeccao(Fa.LOCATION_DOT, "4. Localização", "Província, Município e dados privados para a vistoria.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { Campo("Província", true) { KpSelect(op(vm.geografia.provincias), f.provincia, vm::provincia, altura = 31.dp, tamanho = 8.5f) } }
                Box(Modifier.weight(1f)) { Campo("Município", true) { KpSelect(op(vm.geografia.municipios(f.provincia)), f.municipio.ifBlank { null }, vm::municipio, altura = 31.dp, tamanho = 8.5f) } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { Campo("Bairro", true) { KpEntrada(f.bairro, vm::bairro, dica = "Ex: Talatona", altura = 31.dp, tamanhoTexto = 8.5f) } }
                Box(Modifier.weight(1f)) { Campo("Ponto de Referência", true) { KpEntrada(f.pontoReferencia, vm::pontoReferencia, dica = "Ex: Próximo ao Shopping…", altura = 31.dp, tamanhoTexto = 8.5f) } }
            }
            Aviso("Bairro e Ponto de Referência ficam privados no catálogo. São visíveis ao proprietário titular e ao Administrador; para outros utilizadores, só são revelados após a reserva/pagamento confirmado.",
                Kp.amber50, Kp.amber200, Kp.amber800, Fa.EYE_SLASH)

            // 5. Preço, sinal e simulação
            CabecalhoSeccao(Fa.CALCULATOR, "5. Preço, sinal e simulação", "Condições financeiras calculadas automaticamente.")
            PrecoSinal(e, r, vm, aoLerPoliticas)

            // 6. Fotografias
            CabecalhoSeccao(Fa.IMAGES, "6. Fotografias", "Adicione imagens e escolha a fotografia de capa.")
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row { KpRotulo("Fotos do Pertence", 9f, true, Color(0xFF1F2937)); Spacer(Modifier.weight(1f)); T("${e.fotos.size}/${ValidacaoAnuncio.MAX_FOTOS}", 8f, Kp.slate400, 700) }
                T("Até 5 imagens • máximo de 1,20 MB por imagem. O app optimiza automaticamente as fotos; a primeira será a capa.", 9f, Kp.slate500, linha = 14f)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(e.fotos) { i, foto ->
                        Box(Modifier.size(80.dp).sup(10.dp, fundo = Kp.slate100, borda = if (i == 0) marca.c600 else Kp.slate200, bordaLargura = if (i == 0) 2.dp else 1.dp).toque { vm.capa(i) }) {
                            AsyncImage(modeloImagem(foto.dataUrl), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            if (i == 0) T("CAPA", 6f, Color.White, 900, Modifier.align(Alignment.BottomStart).padding(4.dp).sup(4.dp, fundo = marca.c700).padding(horizontal = 4.dp, vertical = 1.dp))
                            Box(Modifier.align(Alignment.TopEnd).padding(3.dp).size(18.dp).sup(18.dp, fundo = Kp.slate900.copy(.7f), forma = CircleShape).toque { vm.removerFoto(i) },
                                contentAlignment = Alignment.Center) { FaIcone(Fa.XMARK, 8f, Color.White) }
                        }
                    }
                    if (e.fotos.size < ValidacaoAnuncio.MAX_FOTOS) item {
                        Box(Modifier.size(80.dp).tracejado(Kp.gray300).toque { fotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, contentAlignment = Alignment.Center) {
                            if (e.aProcessarMedia > 0) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = marca.c600) else FaIcone(Fa.IMAGES, 16f, Kp.gray400, regular = true)
                        }
                    }
                }
            }

            // 7. Titularidade e recebimento
            CabecalhoSeccao(Fa.SHIELD_HALVED, "7. Titularidade e recebimento", "Documentos privados e destino do valor do Proprietário.")
            Box(Modifier.fillMaxWidth().height(1.dp).background(Kp.slate900))
            Campo("Nº / Referência do Documento de Titularidade", true) { KpEntrada(f.referenciaDocumento, vm::referenciaDocumento, dica = "Nº do Registo Predial / Título de Propriedade", altura = 31.dp, tamanhoTexto = 8.5f) }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row { KpRotulo("Comprovativos de Titularidade", 9f, true, Color(0xFF1F2937)); Spacer(Modifier.weight(1f)); T("${e.documentos.size}/${ValidacaoAnuncio.MAX_DOCUMENTOS}", 8f, Kp.slate400, 700) }
                T("Até 10 ficheiros JPG/PDF • máximo de 2 MB por ficheiro. Os comprovativos são processados automaticamente de forma segura.", 9f, Kp.slate500, linha = 14f)
                e.documentos.forEachIndexed { i, d ->
                    Row(Modifier.fillMaxWidth().sup(10.dp, fundo = Color.White, borda = Kp.slate200).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FaIcone(if (d.mime == "application/pdf") Fa.FILE_PDF else Fa.FILE_IMAGE, 11f, Kp.slate500, regular = true); Spacer(Modifier.width(8.dp))
                        T("${d.nome} · ${d.tamanho / 1024} KB", 8.5f, Kp.slate700, Modifier.weight(1f), max = 1)
                        FaIcone(Fa.XMARK, 9f, Kp.slate400, Modifier.toque { vm.removerDocumento(i) }.padding(4.dp))
                    }
                }
                if (e.documentos.size < ValidacaoAnuncio.MAX_DOCUMENTOS) Row(Modifier.fillMaxWidth().height(30.dp).tracejado(Kp.gray300)
                    .toque { docs.launch(arrayOf("image/jpeg", "image/png", "application/pdf")) }, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    FaIcone(Fa.FOLDER_OPEN, 9f, marca.c600); Spacer(Modifier.width(8.dp)); T("Adicionar documentos · escolher fonte", 8.5f, Kp.slate500)
                }
                Row { FaIcone(Fa.LOCK, 9f, marca.c600, Modifier.padding(top = 2.dp, end = 4.dp)); T("Os documentos ficam associados ao produto e são usados para validação de titularidade e prevenção de fraude.", 9f, Kp.slate500, linha = 14f) }
            }
            Row(Modifier.fillMaxWidth().sup(12.dp, fundo = Color.White, borda = Kp.slate200).padding(9.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(32.dp).sup(10.dp, fundo = Color.White, borda = Kp.slate300), contentAlignment = Alignment.Center) { FaIcone(Fa.FINGERPRINT, 12f, Kp.slate400) }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        T("Protecção contra anúncios duplicados", 9.5f, Kp.slate800, 700, Modifier.weight(1f))
                        T("ACTIVA", 6.5f, Kp.slate600, 800, Modifier.sup(999.dp, fundo = Kp.slate100).padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                    T("O Kupila compara documento, comprovativos, imagens e características antes de publicar.", 8.5f, Kp.slate500, Modifier.padding(top = 2.dp), linha = 12f)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Kp.slate900))
            val iban = f.recebimento is MeioRecebimento.Iban
            Campo("Forma de Pagamento / Recebimento", true) {
                KpSelect(listOf(true to "Transferência via IBAN", false to "Multicaixa Express"), if (f.recebimento == null) null else iban, { b ->
                    vm.recebimento(if (b) MeioRecebimento.Iban((f.recebimento as? MeioRecebimento.Iban)?.iban.orEmpty())
                        else MeioRecebimento.MulticaixaExpress((f.recebimento as? MeioRecebimento.MulticaixaExpress)?.telefone.orEmpty()))
                }, altura = 31.dp, tamanho = 8.5f)
            }
            when (val x = f.recebimento) {
                is MeioRecebimento.Iban -> Campo("IBAN Bancário", true) {
                    KpEntrada(x.iban, { vm.recebimento(MeioRecebimento.Iban(it.uppercase().filter { c -> c.isLetterOrDigit() }.take(25))) }, dica = "AO06.0000.0000.0000.0000.0", altura = 31.dp, tamanhoTexto = 8.5f,
                        erro = x.iban.length >= 4 && !ValidacaoAnuncio.ibanValido(x.iban))
                }
                is MeioRecebimento.MulticaixaExpress -> Campo("Número Multicaixa Express", true) {
                    KpEntrada(x.telefone, { vm.recebimento(MeioRecebimento.MulticaixaExpress(numero(it).take(9))) }, dica = "9XX XXX XXX", altura = 31.dp, tamanhoTexto = 8.5f, prefixo = "+244", teclado = KeyboardType.Phone)
                }
                null -> {}
            }
            Row { FaIcone(Fa.LOCK, 8.5f, marca.c600, Modifier.padding(top = 2.dp, end = 4.dp)); T("A forma seleccionada fica associada ao produto e ao respectivo proprietário.", 8.5f, Kp.slate500) }

            // 8. Alertas e promoção
            CabecalhoSeccao(Fa.BELL, "8. Alertas e promoção", "Preferências do anúncio e serviços promocionais opcionais.")
            Box(Modifier.fillMaxWidth().height(1.dp).background(Kp.slate900))
            val a = f.alertas
            Column(Modifier.fillMaxWidth().sup(12.dp, fundo = marca.c50, borda = marca.c100).padding(9.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FaIcone(Fa.BELL, 9f, marca.c800); Spacer(Modifier.width(6.dp))
                    Column(Modifier.weight(1f)) { T("Receber alertas deste produto", 9.5f, marca.c800, 700); T("Acompanhe reservas e sinais de interesse relevantes deste anúncio.", 8f, marca.c700) }
                    Caixa(a.activo) { vm.alertas(a.copy(activo = it)) }
                }
                if (a.activo) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Quando for reservado" to a.reserva, "Quando houver interesse" to a.interesse).forEachIndexed { i, (rot, v) ->
                        Row(Modifier.weight(1f).sup(10.dp, fundo = Color.White, borda = marca.c100).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Caixa(v) { vm.alertas(if (i == 0) a.copy(reserva = it) else a.copy(interesse = it)) }; Spacer(Modifier.width(6.dp)); T(rot, 8f, Kp.slate700, 600)
                        }
                    }
                }
                Campo("Canal para este produto") {
                    KpSelect(listOf<Pair<CanalAlerta?, String>>(null to "Usar preferência do meu perfil") + CanalAlerta.entries.map { it to it.rotulo }, a.canal, { vm.alertas(a.copy(canal = it)) },
                        altura = 31.dp, tamanho = 8.5f, peso = 700)
                }
                Row { FaIcone(Fa.CIRCLE_INFO, 7.5f, marca.c700, Modifier.padding(top = 2.dp, end = 4.dp))
                    T("O alerta no sino do Kupila permanece associado ao produto. E-mail/SMS dependem dos contactos cadastrados e da disponibilidade dos canais.", 7.5f, marca.c700, linha = 11f) }
            }

            // 9. Revisão e publicação
            CabecalhoSeccao(Fa.CIRCLE_CHECK, "9. Revisão e publicação", "Leia as regras, confirme os dados e publique ou cancele.")
            Column(Modifier.fillMaxWidth().sup(12.dp, fundo = Color.White, borda = Kp.slate200).padding(9.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FaIcone(Fa.SCALE_BALANCED, 14f, marca.c600)
                    Column { T("Aceite obrigatório antes de publicar", 9.5f, Kp.slate900, 700)
                        T("Confirme as regras de titularidade, sinal de reserva, intermediação, comissão, taxa de anúncio, privacidade e responsabilidades do anunciante.", 8.5f, Kp.slate500, linha = 12f) }
                }
                if (!e.politicaLida) KpBotaoSec("Ler regras aplicáveis ao anúncio", aoLerPoliticas, icone = Fa.BOOK_OPEN, altura = 28.dp, tamanho = 8.5f)
                Row(verticalAlignment = Alignment.Top) {
                    Caixa(f.politicaAceite, activo = e.politicaLida) { vm.aceitar(it) }; Spacer(Modifier.width(8.dp))
                    T("Li e aceito as Políticas de Uso e as Regras de Intermediação aplicáveis a este anúncio.", 8.5f, Kp.slate700, linha = 12f)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FaIcone(if (e.politicaLida) Fa.CIRCLE_CHECK else Fa.LOCK, 7.5f, if (e.politicaLida) Kp.emerald700 else Kp.amber700); Spacer(Modifier.width(4.dp))
                    T(if (e.politicaLida) "Leitura concluída (lembrada para esta versão das políticas)." else "Conclua a leitura para habilitar o aceite.", 7.5f, if (e.politicaLida) Kp.emerald700 else Kp.amber700)
                }
            }
            if (!e.administrador && e.edicaoDe == null) Promocoes(e.promocoes, r, vm::promocao)
            r?.let { ResumoAnuncio(f.modalidade, it) }
            e.progresso?.let { Aviso(it, marca.c50, marca.c100, marca.c800, Fa.SPINNER) }
            KpBotao(when { e.edicaoDe != null -> "Guardar alterações"; e.administrador -> "Publicar Pertence na Plataforma (isento)"; else -> "Publicar Pertence na Plataforma" },
                vm::publicar, icone = Fa.CLOUD_ARROW_UP, ocupado = e.aPublicar, activo = e.aProcessarMedia == 0, altura = 35.dp, raio = 12.dp, tamanho = 8.5f)
            KpBotao("Cancelar", sair, icone = Fa.XMARK, fundo = Color.White, cor = Kp.slate700, borda = Kp.slate200, altura = 33.dp, raio = 12.dp, tamanho = 8.5f, sombra = false)
        }
    }
}

private fun comSubcategoria(d: DetalhesCategoria, s: String) = when (d) {
    is DetalhesCategoria.Imovel -> d.copy(subcategoria = s); is DetalhesCategoria.Terreno -> d.copy(subcategoria = s); is DetalhesCategoria.Viatura -> d.copy(subcategoria = s)
    is DetalhesCategoria.Motorizada -> d.copy(subcategoria = s); is DetalhesCategoria.Outro -> d.copy(subcategoria = s)
}

private fun tituloCaracteristicas(c: Categoria) = when (c) {
    Categoria.IMOVEL -> "Características do Imóvel"; Categoria.TERRENO -> "Características do Terreno"; Categoria.VIATURA -> "Características da Viatura"
    Categoria.MOTORIZADA -> "Características da Motorizada"; Categoria.OUTRO -> "Características do Produto"
}

/** `border-2 border-dashed` arredondado. */
private fun Modifier.tracejado(cor: Color, raio: Dp = 10.dp) = this.drawBehind {
    val r = raio.toPx()
    drawRoundRect(cor, cornerRadius = CornerRadius(r, r), style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))))
}

@Composable
private fun CabecalhoSeccao(icone: String, titulo: String, sub: String, primeira: Boolean = false) {
    val marca = LocalMarca.current
    Column {
        if (!primeira) Box(Modifier.padding(bottom = 14.dp).fillMaxWidth().height(1.dp).background(Kp.slate200))
        Row(Modifier.padding(start = 10.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(26.dp).sup(8.dp, fundo = marca.c50), contentAlignment = Alignment.Center) { FaIcone(icone, 9f, marca.c700) }
            Column { T(titulo, 10f, Kp.slate900, 900); T(sub, 7.5f, Kp.slate400, 600, Modifier.padding(top = 2.dp)) }
        }
    }
}

@Composable
private fun Campo(rotulo: String, obrigatorio: Boolean = false, conteudo: @Composable ColumnScope.() -> Unit) {
    Column { KpRotulo(rotulo, 9f, obrigatorio, Color(0xFF1F2937)); Spacer(Modifier.height(4.dp)); conteudo() }
}

@Composable
private fun Aviso(texto: String?, fundo: Color, borda: Color, cor: Color, icone: String, conteudo: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().sup(12.dp, fundo = fundo, borda = borda).padding(9.dp)) {
        FaIcone(icone, 9f, cor, Modifier.padding(top = 2.dp, end = 6.dp))
        if (conteudo != null) conteudo() else T(texto.orEmpty(), 9f, cor, linha = 14f)
    }
}

@Composable
private fun Caixa(v: Boolean, activo: Boolean = true, aoMudar: (Boolean) -> Unit) {
    Checkbox(v, aoMudar, Modifier.size(18.dp), enabled = activo, colors = CheckboxDefaults.colors(checkedColor = LocalMarca.current.c600, uncheckedColor = Kp.slate300))
}

@Composable
private fun OpcaoCategoria(rotulo: String, icone: String, cor: Color, on: Boolean, modifier: Modifier, dica: String? = null, aoTocar: () -> Unit) {
    val marca = LocalMarca.current
    Row(modifier.height(33.dp).sup(9.dp, fundo = if (on) marca.c50 else Color.White, borda = if (on) marca.c500 else Kp.slate900, bordaLargura = if (on) 1.5.dp else 1.dp)
        .toque(aoTocar = aoTocar).padding(horizontal = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).sup(12.dp, fundo = Color.White, borda = if (on) marca.c600 else Kp.slate400, forma = CircleShape), contentAlignment = Alignment.Center) {
            if (on) Box(Modifier.size(6.dp).sup(6.dp, fundo = marca.c600, forma = CircleShape))
        }
        Spacer(Modifier.width(10.dp)); FaIcone(icone, 9f, cor); Spacer(Modifier.width(8.dp))
        T(rotulo, 11f, Kp.slate900, max = 1)
        dica?.let { Spacer(Modifier.weight(1f)); T(it, 7f, Kp.slate400, max = 1) }
    }
}

@Composable
private fun CondicoesAluguerForm(c: CondicoesAluguer, aoMudar: (CondicoesAluguer) -> Unit) {
    val marca = LocalMarca.current
    Column(Modifier.fillMaxWidth().sup(12.dp, fundo = marca.c50, borda = marca.c100).padding(9.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { FaIcone(Fa.LOCK, 9f, marca.c800); Spacer(Modifier.width(6.dp))
            T("Aluguer: o sinal em meses é definido globalmente pelo Administrador; o Proprietário define a unidade do preço e o tempo mínimo.", 9f, marca.c800, Modifier.weight(1f), linha = 14f) }
        Campo("Unidade do preço", true) { KpSelect(UnidadePreco.entries.map { it to "Por ${it.rotulo}" }, c.unidade, { aoMudar(c.copy(unidade = it)) }, altura = 31.dp, tamanho = 8.5f) }
        if (c.unidade == UnidadePreco.PERSONALIZADA) {
            Campo("Nome da unidade", true) { KpEntrada(c.nomeUnidadePersonalizada, { aoMudar(c.copy(nomeUnidadePersonalizada = it.take(40))) }, altura = 31.dp, tamanhoTexto = 8.5f) }
            Campo("Quantas unidades equivalem a 1 mês", true) { KpEntrada(c.unidadesPersonalizadasPorMes?.toPlainString().orEmpty(),
                { aoMudar(c.copy(unidadesPersonalizadasPorMes = it.replace(',', '.').toBigDecimalOrNull())) }, altura = 31.dp, tamanhoTexto = 8.5f, teclado = KeyboardType.Decimal) }
        }
        Campo("Tempo mínimo de contrato", true) {
            KpSelect(listOf("3_MONTHS" to "3 meses", "6_MONTHS" to "6 meses", "12_MONTHS" to "12 meses", "CUSTOM" to "Personalizado"), c.prazo.codigoOrigem, { cod ->
                aoMudar(c.copy(prazo = when (cod) { "6_MONTHS" -> PrazoMinimo.SeisMeses; "12_MONTHS" -> PrazoMinimo.Anual; "CUSTOM" -> PrazoMinimo.Personalizado(1, UnidadeDuracao.MES); else -> PrazoMinimo.TresMeses }))
            }, altura = 31.dp, tamanho = 8.5f)
        }
        (c.prazo as? PrazoMinimo.Personalizado)?.let { p ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { Campo("Quantidade") { KpEntrada(p.quantidade.toString(), { aoMudar(c.copy(prazo = p.copy(quantidade = numero(it).toIntOrNull() ?: 1))) }, altura = 31.dp, tamanhoTexto = 8.5f, teclado = KeyboardType.Number) } }
                Box(Modifier.weight(1f)) { Campo("Unidade") { KpSelect(UnidadeDuracao.entries.map { it to it.rotulo }, p.unidade, { aoMudar(c.copy(prazo = p.copy(unidade = it))) }, altura = 31.dp, tamanho = 8.5f) } }
            }
        }
    }
}

@Composable
private fun DetalhesForm(d: DetalhesCategoria, aoMudar: (DetalhesCategoria) -> Unit) {
    @Composable fun ent(rot: String, v: String, ob: Boolean = false, dica: String = "", tec: KeyboardType = KeyboardType.Text, linhas: Int = 1, m: Modifier = Modifier, f: (String) -> Unit) =
        Box(m) { Campo(rot, ob) { KpEntrada(v, f, dica = dica, altura = if (linhas > 1) 52.dp else 31.dp, tamanhoTexto = 8.5f, teclado = tec, linhas = linhas) } }
    @Composable fun sel(rot: String, ops: List<Pair<String, String>>, v: String, ob: Boolean = false, m: Modifier = Modifier, f: (String) -> Unit) =
        Box(m) { Campo(rot, ob) { KpSelect(ops, v.ifBlank { if (ops.any { it.first.isEmpty() }) "" else null }, f, altura = 31.dp, tamanho = 8.5f) } }
    when (d) {
        is DetalhesCategoria.Imovel -> {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ent("Quantidade de Compartimentos", d.compartimentos?.toString().orEmpty(), true, "Ex: 6", KeyboardType.Number, m = Modifier.weight(1f)) { aoMudar(d.copy(compartimentos = numero(it).toIntOrNull())) }
                sel("Estado / Reparos", op(REPAROS), d.estadoReparos, true, Modifier.weight(1f)) { aoMudar(d.copy(estadoReparos = it)) }
            }
            Campo("Qualidade / Acabamentos") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    QUALIDADES.chunked(2).forEach { par ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            par.forEach { q ->
                                Row(Modifier.weight(1f).height(28.dp).sup(10.dp, fundo = Color.White, borda = Kp.slate200).toque { aoMudar(d.copy(qualidades = if (q in d.qualidades) d.qualidades - q else d.qualidades + q)) }
                                    .padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Caixa(q in d.qualidades) { aoMudar(d.copy(qualidades = if (it) d.qualidades + q else d.qualidades - q)) }; Spacer(Modifier.width(8.dp)); T(q, 8.5f, Kp.slate700)
                                }
                            }
                        }
                    }
                }
            }
            ent("Outras Características / Informações", d.notas, dica = "Ex: 3 quartos, 2 casas de banho, quintal, garagem, água da rede, gerador, condições especiais, reparos necessários...", linhas = 3) { aoMudar(d.copy(notas = it.take(600))) }
            T("Descreva detalhes relevantes que ajudem o cliente a avaliar o imóvel.", 8f, Kp.slate400)
        }
        is DetalhesCategoria.Terreno -> {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ent("Área (m²)", d.areaM2?.toPlainString().orEmpty(), true, "Ex: 600", KeyboardType.Decimal, m = Modifier.weight(1f)) { aoMudar(d.copy(areaM2 = it.replace(',', '.').toBigDecimalOrNull())) }
                sel("Finalidade", op(FINALIDADES), d.finalidade, true, Modifier.weight(1f)) { aoMudar(d.copy(finalidade = it)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                sel("Topografia", listOf("" to "Não informado") + op(TOPOGRAFIAS), d.topografia, m = Modifier.weight(1f)) { aoMudar(d.copy(topografia = it)) }
                sel("Acesso", listOf("" to "Não informado") + op(ACESSOS), d.acesso, m = Modifier.weight(1f)) { aoMudar(d.copy(acesso = it)) }
            }
            ent("Infraestruturas / Observações", d.notas, dica = "Água, energia, vedação, estrada, marcos, coordenadas…", linhas = 3) { aoMudar(d.copy(notas = it.take(600))) }
        }
        is DetalhesCategoria.Viatura -> {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ent("Marca", d.marca, true, "Toyota", m = Modifier.weight(1f)) { aoMudar(d.copy(marca = it.take(80))) }
                ent("Modelo", d.modelo, true, "Hilux", m = Modifier.weight(1f)) { aoMudar(d.copy(modelo = it.take(80))) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ent("Ano", d.ano?.toString().orEmpty(), true, "2022", KeyboardType.Number, m = Modifier.weight(1f)) { aoMudar(d.copy(ano = numero(it).take(4).toIntOrNull())) }
                ent("Quilometragem", d.quilometragem?.toString().orEmpty(), dica = "Ex: 45000", tec = KeyboardType.Number, m = Modifier.weight(1f)) { aoMudar(d.copy(quilometragem = numero(it).toLongOrNull())) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                sel("Combustível", op(COMBUSTIVEIS), d.combustivel, m = Modifier.weight(1f)) { aoMudar(d.copy(combustivel = it)) }
                sel("Transmissão", op(TRANSMISSOES), d.transmissao, m = Modifier.weight(1f)) { aoMudar(d.copy(transmissao = it)) }
            }
            ent("Estado / Observações", d.notas, dica = "Conservação, revisões, avarias, extras…", linhas = 2) { aoMudar(d.copy(notas = it.take(500))) }
        }
        is DetalhesCategoria.Motorizada -> {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ent("Marca", d.marca, true, "Honda", m = Modifier.weight(1f)) { aoMudar(d.copy(marca = it.take(80))) }
                ent("Modelo", d.modelo, true, "CB 125", m = Modifier.weight(1f)) { aoMudar(d.copy(modelo = it.take(80))) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ent("Ano", d.ano?.toString().orEmpty(), true, "2023", KeyboardType.Number, m = Modifier.weight(1f)) { aoMudar(d.copy(ano = numero(it).take(4).toIntOrNull())) }
                ent("Cilindrada", d.cilindrada?.toString().orEmpty(), dica = "125", tec = KeyboardType.Number, m = Modifier.weight(1f)) { aoMudar(d.copy(cilindrada = numero(it).toIntOrNull())) }
                ent("Km", d.quilometragem?.toString().orEmpty(), dica = "Ex: 8000", tec = KeyboardType.Number, m = Modifier.weight(1f)) { aoMudar(d.copy(quilometragem = numero(it).toLongOrNull())) }
            }
            ent("Estado / Observações", d.notas, dica = "Conservação, revisões, acessórios, avarias…", linhas = 2) { aoMudar(d.copy(notas = it.take(500))) }
        }
        is DetalhesCategoria.Outro -> {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                sel("Estado", op(CONDICOES), d.condicao, true, Modifier.weight(1f)) { aoMudar(d.copy(condicao = it)) }
                ent("Quantidade", d.quantidade.toString(), dica = "1", tec = KeyboardType.Number, m = Modifier.weight(1f)) { aoMudar(d.copy(quantidade = numero(it).toIntOrNull()?.coerceAtLeast(1) ?: 1)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ent("Marca", d.marca, dica = "Opcional", m = Modifier.weight(1f)) { aoMudar(d.copy(marca = it.take(80))) }
                ent("Modelo / Referência", d.modelo, dica = "Opcional", m = Modifier.weight(1f)) { aoMudar(d.copy(modelo = it.take(80))) }
            }
            ent("Descrição técnica", d.notas, true, "Características, dimensões, capacidade, especificações, defeitos conhecidos…", linhas = 3) { aoMudar(d.copy(notas = it.take(700))) }
        }
    }
}

/** 5. Preço, sinal e simulação. */
@Composable
private fun PrecoSinal(e: EstadoAnunciar, r: ResumoFinanceiro?, vm: AnunciarViewModel, aoRegras: () -> Unit) {
    val marca = LocalMarca.current
    val f = e.form
    var precoTexto by rememberSaveable(e.rascunhoId) { mutableStateOf(f.preco?.valor?.stripTrailingZeros()?.toPlainString().orEmpty()) }
    Column(Modifier.fillMaxWidth().sup(12.dp, fundo = Kp.slate100).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Campo(if (f.modalidade == Modalidade.VENDA) "Preço do Negócio (Kwanza - AOA)" else "Preço por unidade (Kwanza - AOA)", true) {
            KpEntrada(precoTexto, { precoTexto = it; vm.preco(it) }, dica = "Ex: 15000000", altura = 31.dp, tamanhoTexto = 8.5f, teclado = KeyboardType.Decimal)
            T(f.preco?.let { FormatoAoa.formatar(it) } ?: "Informe o preço total do produto.", 7f, Kp.slate500, Modifier.padding(top = 4.dp))
        }
        val p = r?.entrada?.parametros
        Column(Modifier.fillMaxWidth().sup(10.dp, fundo = marca.c50, borda = marca.c100).padding(9.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    T(if (f.modalidade == Modalidade.VENDA) "Sinal de Reserva global — Venda (%)" else "Sinal de Reserva global — Aluguer (meses)", 8f, marca.c900, 700)
                    T("Definido pelo Administrador nos Ajustes da Plataforma. O Proprietário apenas consulta este valor.", 8f, marca.c700, Modifier.padding(top = 2.dp), linha = 12f)
                }
                Spacer(Modifier.width(8.dp))
                Box(Modifier.width(95.dp).height(31.dp).sup(9.dp, fundo = Kp.slate50, borda = marca.c200).padding(horizontal = 9.dp), contentAlignment = Alignment.CenterEnd) {
                    T(p?.let { if (f.modalidade == Modalidade.VENDA) FormatoAoa.formatar(it.sinalVenda) else "${it.mesesSinalAluguer} mês" } ?: "%", 9f, marca.c700, 800)
                }
            }
            Box(Modifier.fillMaxWidth().sup(8.dp, fundo = Color.White).padding(8.dp)) {
                androidx.compose.material3.Text(buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = marca.c900)) { append("Como funciona:") }
                    append(" o Cliente paga antecipadamente o sinal para reservar o produto. Na venda, a percentagem global é definida pelo Administrador; no aluguer/renda, a quantidade de meses de sinal também é definida globalmente pelo Administrador. A comissão continua a seguir a regra financeira da plataforma e o líquido permanece em custódia até à decisão da vistoria.")
                }, color = marca.c900, fontSize = 8.sp, lineHeight = 14.sp)
            }
            Row(Modifier.toque(aoTocar = aoRegras), verticalAlignment = Alignment.CenterVertically) {
                FaIcone(Fa.CIRCLE_INFO, 8f, marca.c700); Spacer(Modifier.width(6.dp)); T("Ver regras completas de reserva e intermediação", 8f, marca.c700, 700)
            }
        }
        Column(Modifier.fillMaxWidth().sup(10.dp, fundo = Color.White, borda = Kp.slate200).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val zero = Dinheiro.ZERO
            Linha("Sinal global (${p?.let { if (f.modalidade == Modalidade.VENDA) FormatoAoa.formatar(it.sinalVenda) else "${it.mesesSinalAluguer} mês" } ?: "—"}):", aoa(r?.sinal ?: zero), Kp.slate900)
            Linha("Comissão da Plataforma (${p?.let { FormatoAoa.formatar(it.comissao) } ?: "—"} ${if (f.modalidade == Modalidade.VENDA) "do sinal" else "do contrato mínimo"}):", aoa(r?.comissao ?: zero), Kp.red600)
            Linha("Taxa de Anúncio do Proprietário (${p?.let { FormatoAoa.formatar(it.taxaAnuncio) } ?: "—"} do preço):", aoa(r?.taxaAnuncio ?: zero), Kp.amber700)
            Box(Modifier.fillMaxWidth().height(1.dp).background(Kp.slate900))
            Row { T("Valor Líquido a Receber pós-vistoria:", 10.5f, Kp.slate900, 700, Modifier.weight(1f)); T(aoa(r?.liquidoCustodia ?: zero), 10.5f, Kp.emerald600, 700) }
        }
    }
}

@Composable
private fun Linha(k: String, v: String, cor: Color) { Row { T(k, 10f, Kp.slate700, Modifier.weight(1f)); T(v, 10f, cor, 700) } }

/** "Serviços de destaque do anúncio". */
@Composable
private fun Promocoes(sel: Set<Promocao>, r: ResumoFinanceiro?, aoMudar: (Promocao, Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Kp.slate900))
        Row(verticalAlignment = Alignment.CenterVertically) { FaIcone(Fa.BULLHORN, 11f, Kp.violet600); Spacer(Modifier.width(8.dp)); T("Serviços de destaque do anúncio", 11f, Kp.slate900, 700) }
        T("Opcionais. Cada serviço é cobrado como percentagem do preço do produto e pago juntamente com a taxa de anúncio. O valor exacto em AOA aparece antes do pagamento. Rótulos orgânicos como “Mais Procurado” continuam dependentes de procura real.",
            8.5f, Kp.slate500, linha = 12f)
        val info = mapOf(Promocao.DESTAQUE to Triple("Maior prioridade na vitrine de Destaques.", Kp.violet50, Kp.violet600),
            Promocao.TOP to Triple("Entrada na lista paga, sempre identificada como “Patrocinado”.", Color(0xFFFFFBEB), Kp.amber700),
            Promocao.PRIORIDADE to Triple("Impulsionamento temporário entre anúncios elegíveis.", Color(0xFFF0F9FF), Color(0xFF0369A1)))
        Promocao.entries.forEach { p ->
            val (sub, fundo, cor) = info.getValue(p)
            Row(Modifier.fillMaxWidth().sup(12.dp, fundo = fundo, borda = Kp.slate900).toque { aoMudar(p, p !in sel) }.padding(9.dp), verticalAlignment = Alignment.Top) {
                Caixa(p in sel) { aoMudar(p, it) }; Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) { T(if (p == Promocao.PRIORIDADE) "Prioridade na Vitrine" else p.rotulo, 10f, Kp.slate900, 700); T(sub, 7.5f, Kp.slate500, Modifier.padding(top = 2.dp)) }
                val pct = r?.entrada?.parametros?.promocoes?.get(p)?.let { FormatoAoa.formatar(it) } ?: "—"
                T("$pct · ${r?.promocoes?.get(p)?.let { aoa(it) } ?: "0 AOA"}", 8.5f, cor, 700)
            }
        }
        Row(Modifier.fillMaxWidth().sup(12.dp, fundo = Kp.slate900).padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            T("Taxa anúncio + promoções", 8.5f, Color.White, Modifier.weight(1f)); T(aoa(r?.totalPublicacao ?: Dinheiro.ZERO), 11f, Kp.yellow400, 700)
        }
    }
}

/** "RESUMO FINANCEIRO · Quem paga, quem recebe e quanto" do formulário. */
@Composable
private fun ResumoAnuncio(modalidade: Modalidade, r: ResumoFinanceiro) {
    val marca = LocalMarca.current
    val p = r.entrada.parametros
    Column(Modifier.fillMaxWidth().sup(14.dp, fundo = Color.White, borda = marca.c200, sombra = Color.Black.copy(.08f), desfoque = 6.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                T("Resumo Financeiro", 8f, Kp.emerald700, 900, espacamento = 1.12f, maiusculas = true)
                T("Quem paga, quem recebe e quanto", 11f, Kp.slate900, 900, Modifier.padding(top = 4.dp))
                T("Cadastro/Anúncio · ${if (modalidade == Modalidade.VENDA) "Venda" else "Aluguer"} · cálculos instantâneos com os Ajustes globais.", 7.5f, Kp.slate500, Modifier.padding(top = 4.dp))
            }
            Box(Modifier.size(28.dp).sup(28.dp, fundo = Color.White, borda = marca.c200, forma = CircleShape), contentAlignment = Alignment.Center) { FaIcone(Fa.CIRCLE_INFO, 9f, marca.c700) }
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(36.dp).sup(10.dp, fundo = marca.c50, borda = marca.c100), contentAlignment = Alignment.Center) { FaIcone(Fa.CHART_PIE, 14f, marca.c700) }
        }
        listOf("Sinal venda" to FormatoAoa.formatar(p.sinalVenda), "Sinal renda" to "${p.mesesSinalAluguer} mês(es)", "Comissão" to FormatoAoa.formatar(p.comissao), "Taxa anúncio" to FormatoAoa.formatar(p.taxaAnuncio))
            .chunked(2).forEach { par ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    par.forEach { (k, v) ->
                        Column(Modifier.weight(1f).sup(8.dp, fundo = Kp.slate50, borda = Kp.slate100).padding(8.dp, 6.dp)) { T(k, 6f, Kp.slate400, 900, maiusculas = true); T(v, 8f, Kp.slate800, 700) }
                    }
                }
            }
        BlocoFinanceiro("Cliente", Fa.USER, Color(0xFFDBEAFE), Kp.blue50.copy(.6f), Kp.blue700, listOf("Base do negócio" to aoa(r.baseNegocio), "Sinal / reserva" to aoa(r.sinal)), "Saldo após sinal" to aoa(r.saldoRestante))
        BlocoFinanceiro("Proprietário", Fa.HOUSE_USER, Kp.emerald100, Kp.emerald50.copy(.6f), Kp.emerald700, listOf("Líquido do sinal" to aoa(r.liquidoCustodia), "Recebe depois" to aoa(r.saldoRestante)),
            "Líquido após custos" to aoa(r.liquidoPrevistoProprietario), Kp.emerald700)
        BlocoFinanceiro("Plataforma", Fa.BUILDING_COLUMNS, Kp.violet100, Color.White, Kp.violet700, listOf("Comissão" to aoa(r.comissao), "Publicação + promoções" to aoa(r.totalPublicacao)),
            "Receita estimada" to aoa(r.comissao + r.totalPublicacao), Kp.violet700)
        BlocoFinanceiro("Custos do Proprietário", Fa.WALLET, Kp.amber200, Color(0xFFFFFDF5), Kp.amber800, listOf("Taxa de anúncio" to aoa(r.taxaAnuncio), "Promoções opcionais" to aoa(r.totalPromocoes)),
            "Total de publicação" to aoa(r.totalPublicacao))
        BlocoProteccao(aoa(r.liquidoCustodia), aoa(r.reembolsoElegivel))
        Box(Modifier.fillMaxWidth().sup(9.dp, fundo = Kp.slate50, borda = Kp.slate100).padding(8.dp)) {
            androidx.compose.material3.Text(buildAnnotatedString {
                val b = SpanStyle(fontWeight = FontWeight.Bold, color = Kp.slate700)
                withStyle(b) { append("Cliente:") }; append(if (modalidade == Modalidade.VENDA) " paga ${FormatoAoa.formatar(p.sinalVenda)} do preço como sinal e o restante apenas no fecho. " else " paga ${p.mesesSinalAluguer} mês(es) como sinal e o restante conforme o contrato. ")
                withStyle(b) { append("Proprietário:") }; append(" recebe o sinal líquido da comissão; taxa de anúncio e promoções são custos próprios da publicação. ")
                withStyle(b) { append("Plataforma:") }; append(" recebe comissão + custos de publicação, sem aumentar o preço do Cliente.")
            }, color = Kp.slate500, fontSize = 8.sp, lineHeight = 13.sp)
        }
    }
}
