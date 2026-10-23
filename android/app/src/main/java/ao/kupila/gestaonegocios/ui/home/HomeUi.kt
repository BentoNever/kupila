package ao.kupila.gestaonegocios.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ao.kupila.gestaonegocios.dominio.anuncio.EstadoAnuncio
import ao.kupila.gestaonegocios.dominio.catalogo.Categoria
import ao.kupila.gestaonegocios.dominio.catalogo.Selo
import ao.kupila.gestaonegocios.dominio.catalogo.Vitrine
import ao.kupila.gestaonegocios.dominio.financeiro.Modalidade
import ao.kupila.gestaonegocios.ui.comum.Fa
import ao.kupila.gestaonegocios.ui.comum.modeloImagem
import ao.kupila.gestaonegocios.ui.explorar.CartaoAnuncio
import ao.kupila.gestaonegocios.ui.explorar.ExplorarViewModel
import ao.kupila.gestaonegocios.ui.explorar.precoComUnidade
import ao.kupila.gestaonegocios.ui.kp.FaIcone
import ao.kupila.gestaonegocios.ui.kp.Kp
import ao.kupila.gestaonegocios.ui.kp.LocalMarca
import ao.kupila.gestaonegocios.ui.kp.T
import ao.kupila.gestaonegocios.ui.kp.gradiente
import ao.kupila.gestaonegocios.ui.kp.sup
import ao.kupila.gestaonegocios.ui.kp.toque
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

// Cores computadas da origem (Kupila 1.0.239, preset de vitrine AURORA).
private val tealEscuro = Color(0xFF0F766E)
private val tealChip = Color(0xFF18928A)
private val textoChip = Color(0xFF226B6C)
private val bordaChip = Color(0xFFC0D6DB)
private val indigo = Color(0xFF4338CA)
private val violeta = Color(0xFFA78BFA)

/** TAB 1: EXPLORAR / CATÁLOGO (`#tab-explore`, HTML 6696-6779). */
@Composable
fun HomeEcra(vm: ExplorarViewModel, aoAbrir: (String) -> Unit, aoReservaProtegida: (String) -> Unit, aoConfianca: () -> Unit, reservas: (@Composable () -> Unit)? = null) {
    val f by vm.filtro.collectAsStateWithLifecycle()
    val cat by vm.catalogo.collectAsStateWithLifecycle()
    val est by vm.estadoCatalogo.collectAsStateWithLifecycle()
    PullToRefreshBox(est.aSincronizar, { vm.actualizar() }, Modifier.fillMaxSize().background(Kp.slate50)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp, 11.dp, 10.dp, 92.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PainelDescoberta(f.texto, vm::texto, vm::limparTexto, f.categoria, vm::categoria, f.modalidade, vm::modalidade)
            Vitrine(f.vitrine, vm::vitrine, cat, aoAbrir, aoReservaProtegida, aoConfianca)
            reservas?.invoke()
        }
    }
}

/** "Descoberta: pesquisa + categoria + finalidade" (`#home-discovery-panel`). */
@Composable
private fun PainelDescoberta(texto: String, aoTexto: (String) -> Unit, aoLimpar: () -> Unit, categoria: Categoria?, aoCategoria: (Categoria?) -> Unit,
                             modalidade: Modalidade?, aoModalidade: (Modalidade?) -> Unit) {
    Column(Modifier.fillMaxWidth()
        .sup(15.dp, pincel = gradiente(135f, 0f to Color(0xFFE5F0EF), .54f to Color.White, 1f to Color(0xFFE4F9F7)), borda = Color(0xFFB1CED2),
            sombra = tealEscuro.copy(alpha = .10f), desfoque = 28.dp).padding(7.dp)) {
        // Pesquisa
        Box(Modifier.fillMaxWidth().height(42.dp).sup(15.dp, pincel = gradiente(120f, tealEscuro.copy(alpha = .18f), Kp.teal400.copy(alpha = .22f))).padding(2.dp)) {
            BasicTextField(texto, aoTexto, Modifier.fillMaxWidth(.57f).height(38.dp).sup(14.dp, fundo = Color.White.copy(alpha = .88f), sombra = tealEscuro.copy(.06f), desfoque = 14.dp),
                singleLine = true, textStyle = TextStyle(fontSize = 10.sp, fontWeight = FontWeight(600), color = Kp.slate900), cursorBrush = SolidColor(tealEscuro),
                decorationBox = { campo ->
                    Row(Modifier.fillMaxSize().padding(start = 10.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FaIcone(Fa.MAGNIFYING_GLASS, 12f, tealEscuro); Spacer(Modifier.width(14.dp))
                        Box(Modifier.weight(1f)) { if (texto.isEmpty()) T("Pesquisar produto, município ou marca", 10f, Kp.slate400, 600, max = 1); campo() }
                    }
                })
            Box(Modifier.align(Alignment.CenterEnd).padding(end = 5.dp).size(27.dp).sup(9.dp).toque(aoTocar = aoLimpar), contentAlignment = Alignment.Center) {
                FaIcone(Fa.XMARK, 10f, Kp.slate400)
            }
        }
        Spacer(Modifier.height(10.dp))
        // Categorias (grelha de 3 colunas como a origem renderiza no telemóvel)
        val cats: List<Triple<Categoria?, String, String>> = listOf(Triple(null, Fa.BORDER_ALL, "Todos"), Triple(Categoria.IMOVEL, Fa.HOUSE, "Imóveis"),
            Triple(Categoria.TERRENO, Fa.MAP, "Terrenos"), Triple(Categoria.VIATURA, Fa.CAR, "Viaturas"), Triple(Categoria.MOTORIZADA, Fa.MOTORCYCLE, "Motorizadas"),
            Triple(Categoria.OUTRO, Fa.BOX_OPEN, "Outros"))
        Grelha3(cats, 5.dp) { (c, ic, r) ->
            val on = categoria == c
            Row(Modifier.fillMaxWidth().height(30.dp)
                .sup(10.dp, fundo = if (on) null else Color.White.copy(alpha = .74f), pincel = if (on) gradiente(135f, tealEscuro, tealChip) else null,
                    borda = if (on) null else bordaChip, sombra = if (on) tealEscuro.copy(.22f) else null, desfoque = 16.dp)
                .toque { aoCategoria(c) }.padding(horizontal = 6.dp, vertical = 5.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                FaIcone(ic, 8f, if (on) Color.White else textoChip); Spacer(Modifier.width(4.dp)); T(r, 8f, if (on) Color.White else textoChip, 750, max = 1)
            }
        }
        Spacer(Modifier.height(10.dp))
        // Tudo / Comprar / Alugar
        Row(Modifier.fillMaxWidth().height(38.dp).sup(12.dp, fundo = Color(0xFFE1ECEF), borda = tealEscuro.copy(alpha = .11f)).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf<Pair<Modalidade?, String>>(null to "Tudo", Modalidade.VENDA to "Comprar", Modalidade.ARRENDAMENTO to "Alugar").forEach { (m, r) ->
                val on = modalidade == m
                Box(Modifier.weight(1f).height(28.dp).sup(9.dp, fundo = if (on) Color.White else null, sombra = if (on) tealEscuro.copy(.10f) else null, desfoque = 12.dp)
                    .toque { aoModalidade(m) }, contentAlignment = Alignment.Center) { T(r, 9f, if (on) tealEscuro else Kp.slate500, 750) }
            }
        }
    }
}

@Composable
private fun <X> Grelha3(itens: List<X>, espaco: androidx.compose.ui.unit.Dp, celula: @Composable (X) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(espaco)) {
        itens.chunked(3).forEach { linha ->
            Row(horizontalArrangement = Arrangement.spacedBy(espaco)) {
                linha.forEach { Box(Modifier.weight(1f)) { celula(it) } }
                repeat(3 - linha.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** "Vitrine principal moderna" (`#home-showcase`): modos + carrossel infinito com dois cartões visíveis. */
@Composable
private fun Vitrine(vitrine: Vitrine, aoVitrine: (Vitrine) -> Unit, cartoes: List<CartaoAnuncio>?, aoAbrir: (String) -> Unit, aoReservaProtegida: (String) -> Unit, aoConfianca: () -> Unit) {
    val itens = cartoes.orEmpty().take(12)
    val estado = rememberLazyListState()
    val ambito = rememberCoroutineScope()
    val total = itens.size
    val inicio = remember(total) { if (total < 2) 0 else (Int.MAX_VALUE / 2).let { it - it % total } }
    LaunchedEffect(total, vitrine) { estado.scrollToItem(inicio) }
    val actual by remember(total) { derivedStateOf { if (total == 0) 0 else estado.firstVisibleItemIndex % total + 1 } }
    Column(Modifier.fillMaxWidth()
        .sup(15.dp, pincel = vitrineFundo, borda = Color(0xFFA6A7E2), sombra = indigo.copy(alpha = .16f), desfoque = 30.dp).padding(7.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                T("Descobrir agora", 7f, Color(0xFF3D34B7), 850, espacamento = .77f, maiusculas = true, linha = 10.5f)
                Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    T(vitrine.titulo, 13f, Color(0xFF3530A0), 900, espacamento = -.195f, max = 1, linha = 15f)
                    Spacer(Modifier.width(6.dp))
                    T("SMART", 6f, Color(0xFF3C33B3), 900, Modifier.sup(999.dp, fundo = Color(0xFFEAE3FE), borda = Color(0xFFC9C0F4)).padding(horizontal = 6.dp, vertical = 2.dp))
                }
                T(vitrine.subtitulo, 8f, Color(0xFF5559A8), Modifier.padding(top = 2.dp), linha = 10f)
            }
            T(if (total == 0) "0/0" else "$actual/$total", 7f, Color(0xFF3F3AB0), 850,
                Modifier.sup(999.dp, fundo = Color(0xFFEDE8FE), borda = Color(0xFFB9BAE6)).padding(horizontal = 6.dp, vertical = 3.dp))
        }
        Spacer(Modifier.height(10.dp))
        val modos = listOf(Vitrine.DESTAQUES to null, Vitrine.RECENTES to Fa.CLOCK, Vitrine.EM_ALTA to Fa.FIRE, Vitrine.PROCURADOS to Fa.ARROW_TREND_UP, Vitrine.PATROCINADOS to Fa.BULLHORN)
        Grelha3(modos, 5.dp) { (v, ic) ->
            val on = vitrine == v
            Row(Modifier.fillMaxWidth().height(30.dp)
                .sup(10.dp, fundo = if (on) null else Color(0xFFF8F6FF).copy(alpha = .92f), pincel = if (on) gradiente(135f, indigo, violeta) else null,
                    borda = if (on) null else Color(0xFFBCBEE7), sombra = if (on) indigo.copy(.28f) else null, desfoque = 17.dp)
                .toque { aoVitrine(v) }.padding(start = 8.dp, end = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                // "fa-sparkles" não existe no FontAwesome Free: a origem mostra o botão sem ícone; mantém-se igual.
                ic?.let { FaIcone(it, 7.5f, if (on) Color.White else Color(0xFF4441AB), regular = it == Fa.CLOCK); Spacer(Modifier.width(5.dp)) }
                T(v.rotuloCurto, 7.5f, if (on) Color.White else Color(0xFF4441AB), 800, max = 1)
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().sup(15.dp, pincel = gradiente(120f, indigo.copy(alpha = .12f), violeta.copy(alpha = .16f))).padding(3.dp)) {
            if (total == 0) {
                Column(Modifier.fillMaxWidth().sup(16.dp, fundo = Kp.slate50, borda = Kp.slate200).padding(horizontal = 16.dp, vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    FaIcone(Fa.IMAGES, 18f, Kp.slate400, regular = true); Spacer(Modifier.height(4.dp))
                    T(if (cartoes == null) "A carregar os produtos…" else "Nenhum produto disponível para estes filtros.", 9f, Kp.slate400, 600, centro = true)
                }
            } else {
                LazyRow(state = estado, flingBehavior = rememberSnapFlingBehavior(estado), modifier = Modifier.fillMaxWidth(),
                    userScrollEnabled = total > 1, contentPadding = PaddingValues(0.dp)) {
                    items(if (total < 2) total else Int.MAX_VALUE, key = null) { i ->
                        val c = itens[i % total]
                        CartaoVitrine(c, i % total + 1, Modifier.fillParentMaxWidth(if (total < 2) 1f else .5f), { aoAbrir(c.anuncio.id) }, { aoReservaProtegida(c.anuncio.id) }, aoConfianca)
                    }
                }
                if (total > 1) {
                    SetaCarrossel(Fa.CHEVRON_LEFT, Modifier.align(Alignment.CenterStart)) { ambito.launch { estado.animateScrollToItem(estado.firstVisibleItemIndex - 1) } }
                    SetaCarrossel(Fa.CHEVRON_RIGHT, Modifier.align(Alignment.CenterEnd)) { ambito.launch { estado.animateScrollToItem(estado.firstVisibleItemIndex + 1) } }
                }
            }
        }
    }
}

private val vitrineFundo: Brush = object : androidx.compose.ui.graphics.ShaderBrush() {
    override fun createShader(size: androidx.compose.ui.geometry.Size): androidx.compose.ui.graphics.Shader {
        // radial-gradient(circle at 92% 4%, violeta .32 → transparente 35%) sobre linear-gradient(138deg, #DDDBF5, #F4F0FE 52%, #EAE9F9)
        return androidx.compose.ui.graphics.LinearGradientShader(androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(size.width, size.height),
            listOf(Color(0xFFDDDBF5), Color(0xFFF4F0FE), Color(0xFFEAE9F9)), listOf(0f, .52f, 1f))
    }
}

@Composable
private fun SetaCarrossel(glifo: String, modifier: Modifier, aoTocar: () -> Unit) {
    val marca = LocalMarca.current
    Box(modifier.width(29.dp).height(36.dp).sup(12.dp, fundo = Color.White.copy(alpha = .94f), borda = Kp.slate200.copy(alpha = .96f), sombra = Kp.slate900.copy(.13f), desfoque = 16.dp)
        .toque(aoTocar = aoTocar), contentAlignment = Alignment.Center) { FaIcone(glifo, 9f, marca.c800) }
}

/** `article.kupila-feature-card` (renderMainCarouselPage, HTML 13356-13383). */
@Composable
fun CartaoVitrine(c: CartaoAnuncio, numero: Int, modifier: Modifier, aoAbrir: () -> Unit, aoReservaProtegida: () -> Unit, aoConfianca: () -> Unit) {
    val a = c.anuncio
    val marca = LocalMarca.current
    val reservado = a.estado == EstadoAnuncio.RESERVADO
    Box(modifier.sup(14.dp, fundo = Color.White, borda = Color(0xFFE9EEF3), sombra = Kp.slate900.copy(.055f), desfoque = 16.dp).toque(aoTocar = aoAbrir)) {
    Column {
        Box(Modifier.fillMaxWidth().aspectRatio(183f / 253f).background(Color(0xFFEEF2F7))) {
            val capa = a.fotos.firstOrNull()
            if (capa != null) AsyncImage(modeloImagem(capa.miniatura ?: capa.url), a.titulo, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            val (fundoSelo, icone, corIcone) = destaque(c, marca.c700)
            Box(Modifier.padding(5.dp).size(18.dp).sup(7.dp, fundo = fundoSelo, sombra = Kp.slate900.copy(.18f), desfoque = 8.dp), contentAlignment = Alignment.Center) {
                icone?.let { FaIcone(it, 7f, corIcone) }
            }
            T(numero.toString().padStart(2, '0'), 6.5f, Color.White, 900, Modifier.padding(start = 28.dp, top = 5.dp).height(18.dp)
                .sup(7.dp, fundo = Kp.slate900.copy(alpha = .78f), borda = Color.White.copy(alpha = .58f), sombra = Kp.slate900.copy(.18f), desfoque = 9.dp).padding(horizontal = 5.dp, vertical = 5.5.dp),
                espacamento = .26f, linha = 6.5f)
            Box(Modifier.align(Alignment.TopEnd).padding(5.dp).size(7.dp)
                .sup(7.dp, fundo = if (reservado) Kp.amber400 else Color(0xFF29AA58), borda = Color.White.copy(alpha = .9f), forma = CircleShape, sombra = Kp.slate900.copy(.22f), desfoque = 6.dp))
            T(a.detalhes.subcategoria.ifBlank { a.categoria.rotuloPlural }, 6.5f, Color.White, 850, Modifier.align(Alignment.BottomStart).padding(5.dp), max = 1, linha = 6.5f)
            if (capa?.sintetica == true) T("Ilustração gerada", 5.5f, Color.White, 800, Modifier.align(Alignment.BottomEnd).padding(5.dp).sup(5.dp, fundo = Kp.slate900.copy(.7f)).padding(3.dp, 1.dp))
        }
        Box(Modifier.fillMaxWidth().padding(6.dp, 6.dp, 32.dp, 6.dp)) {
            Column {
                T(a.titulo, 9f, Kp.slate900, 800, max = 2, linha = 10.8f)
                Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    FaIcone(Fa.LOCATION_DOT, 7f, Kp.slate400); Spacer(Modifier.width(2.dp))
                    T(a.municipio.ifBlank { a.provincia.ifBlank { "Angola" } }, 7f, Kp.slate400, max = 1, linha = 8.4f)
                }
                T(precoComUnidade(a), 8.5f, marca.c800, 900, Modifier.padding(top = 6.dp), max = 1, linha = 9.35f)
            }
        }
    }
    // Acessos rápidos (`kupila-card-quick-tools`): Reserva Protegida e Credibilidade, no canto do corpo do cartão.
    Column(Modifier.align(Alignment.BottomEnd).padding(5.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        AcessoRapido(Fa.SHIELD_HALVED, aoReservaProtegida)
        AcessoRapido(Fa.SHIELD_HEART, aoConfianca)
    }
    }
}

@Composable
private fun AcessoRapido(glifo: String, aoTocar: () -> Unit) {
    Box(Modifier.size(20.dp).sup(7.dp, fundo = Color.White.copy(alpha = .97f), borda = Kp.slate200, sombra = Kp.slate900.copy(.10f), desfoque = 7.dp).toque(aoTocar = aoTocar),
        contentAlignment = Alignment.Center) { FaIcone(glifo, 7.5f, LocalMarca.current.c800) }
}

/** `listingHighlightMeta` (HTML 13119): Novo · Em Alta · Mais Procurado · Destaque · categoria. */
private fun destaque(c: CartaoAnuncio, marca700: Color): Triple<Color, String?, Color> = when (c.selo) {
    Selo.NOVO -> Triple(Kp.sky500, null, Color.White)                      // fa-sparkles (Pro): sem glifo no FontAwesome Free
    Selo.EM_ALTA -> Triple(Color(0xFFF97316), Fa.FIRE, Color.White)
    Selo.MAIS_PROCURADO -> Triple(Kp.violet600, Fa.ARROW_TREND_UP, Color.White)
    Selo.DESTAQUE, Selo.TOP_PATROCINADO, Selo.PATROCINADO -> Triple(Kp.amber400, Fa.STAR, Kp.slate900)
    Selo.CATEGORIA -> Triple(marca700.copy(alpha = .9f), Fa.TAG, Color.White)
}

