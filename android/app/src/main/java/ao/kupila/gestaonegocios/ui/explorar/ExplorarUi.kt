package ao.kupila.gestaonegocios.ui.explorar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ao.kupila.gestaonegocios.dominio.anuncio.EstadoAnuncio
import ao.kupila.gestaonegocios.dominio.catalogo.Categoria
import ao.kupila.gestaonegocios.dominio.catalogo.Selo
import ao.kupila.gestaonegocios.dominio.catalogo.Vitrine
import ao.kupila.gestaonegocios.dominio.financeiro.FormatoAoa
import ao.kupila.gestaonegocios.dominio.financeiro.Modalidade
import ao.kupila.gestaonegocios.ui.comum.Aviso
import ao.kupila.gestaonegocios.ui.comum.EstadoVazio
import ao.kupila.gestaonegocios.ui.comum.Seletor
import ao.kupila.gestaonegocios.ui.comum.TipoAviso
import ao.kupila.gestaonegocios.ui.comum.modeloImagem
import ao.kupila.gestaonegocios.ui.entrada.formatarData
import ao.kupila.gestaonegocios.ui.tema.LocalCoresKupila
import coil3.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplorarEcra(vm: ExplorarViewModel, aoAbrir: (String) -> Unit) {
    val f by vm.filtro.collectAsStateWithLifecycle()
    val cat by vm.catalogo.collectAsStateWithLifecycle()
    val est by vm.estadoCatalogo.collectAsStateWithLifecycle()
    val actualizado by vm.actualizadoEm.collectAsStateWithLifecycle()
    val offline by vm.offline.collectAsStateWithLifecycle()
    val cores = LocalCoresKupila.current
    val lista = rememberLazyListState()
    val noFim by remember { derivedStateOf { val i = lista.layoutInfo; i.totalItemsCount > 0 && (i.visibleItemsInfo.lastOrNull()?.index ?: 0) >= i.totalItemsCount - 3 } }
    LaunchedEffect(noFim) { if (noFim && est.haMais && !offline) vm.carregarMais() }

    PullToRefreshBox(isRefreshing = est.aSincronizar, onRefresh = vm::actualizar, modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = lista, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(cores.pesquisaPrimaria, cores.pesquisaPrimaria.copy(alpha = 0.85f))))
                    .padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(f.texto, vm::texto, Modifier.fillMaxWidth(), singleLine = true,
                        placeholder = { Text("Pesquisar produto, município ou marca") }, leadingIcon = { Icon(Icons.Filled.Search, null) },
                        trailingIcon = { if (f.texto.isNotEmpty()) IconButton(onClick = vm::limparTexto) { Icon(Icons.Filled.Clear, "Limpar pesquisa") } },
                        shape = RoundedCornerShape(16.dp),
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White,
                            focusedTextColor = Color(0xFF111816), unfocusedTextColor = Color(0xFF111816)))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { FilterChip(f.categoria == null, { vm.categoria(null) }, { Text("Todos") }) }
                        items(Categoria.entries) { c -> FilterChip(f.categoria == c, { vm.categoria(c) }, { Text(if (c == Categoria.OUTRO) "Outros" else c.rotuloPlural) }) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(f.modalidade == null, { vm.modalidade(null) }, { Text("Tudo") })
                        FilterChip(f.modalidade == Modalidade.VENDA, { vm.modalidade(Modalidade.VENDA) }, { Text("Comprar") })
                        FilterChip(f.modalidade == Modalidade.ARRENDAMENTO, { vm.modalidade(Modalidade.ARRENDAMENTO) }, { Text("Alugar") })
                    }
                    Seletor("Província", listOf<Pair<String?, String>>(null to "Todas (21 Províncias)") + vm.geografia.provincias.map { it to it }, f.provincia, vm::provincia)
                }
            }
            if (offline || est.erro != null) item {
                Aviso(if (offline) "Sem ligação: a mostrar dados guardados" + (actualizado?.let { " (actualizados em ${formatarData(it)})" } ?: "") + ". Reservas e pagamentos exigem ligação."
                    else est.erro!!, TipoAviso.AVISO, Modifier.padding(horizontal = 16.dp))
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(f.vitrine.titulo, style = MaterialTheme.typography.titleLarge)
                    Text(f.vitrine.subtitulo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(Vitrine.entries) { v -> FilterChip(f.vitrine == v, { vm.vitrine(v) }, { Text(v.rotuloCurto) }) }
                    }
                }
            }
            val itens = cat
            when {
                itens == null -> item { EstadoVazio("A carregar o catálogo…", "Os anúncios aparecem assim que forem recebidos.") }
                itens.isEmpty() -> item {
                    EstadoVazio(if (f.vitrine == Vitrine.PATROCINADOS) "Sem anúncios patrocinados" else "Nenhum produto encontrado",
                        if (f.activo) "Ajuste os filtros ou a pesquisa." else "Ainda não existem anúncios publicados.",
                        accao = if (f.activo) "Limpar filtros" to { vm.texto(""); vm.categoria(null); vm.modalidade(null); vm.provincia(null) } else null)
                }
                else -> items(itens, key = { it.anuncio.id }) { c -> CartaoProduto(c, Modifier.padding(horizontal = 16.dp)) { aoAbrir(c.anuncio.id) } }
            }
        }
    }
}

@Composable
fun CartaoProduto(c: CartaoAnuncio, modifier: Modifier = Modifier, aoAbrir: () -> Unit) {
    val a = c.anuncio
    val cores = LocalCoresKupila.current
    Card(modifier.fillMaxWidth().clickable(onClickLabel = "Abrir detalhes", onClick = aoAbrir), shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f).background(MaterialTheme.colorScheme.surfaceVariant)) {
                val capa = a.fotos.firstOrNull()
                if (capa != null) AsyncImage(modeloImagem(capa.miniatura ?: capa.url), a.titulo, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Icon(Icons.Filled.ImageNotSupported, null, Modifier.align(Alignment.Center))
                Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val (texto, cor) = when (c.selo) {
                        Selo.TOP_PATROCINADO, Selo.PATROCINADO -> c.selo.rotulo to cores.pago
                        Selo.CATEGORIA -> a.categoria.rotuloPlural to MaterialTheme.colorScheme.primary
                        else -> c.selo.rotulo to MaterialTheme.colorScheme.primary
                    }
                    ao.kupila.gestaonegocios.ui.comum.Selo(texto, cor)
                    if (a.estado == EstadoAnuncio.RESERVADO) ao.kupila.gestaonegocios.ui.comum.Selo("Reservado", cores.aviso)
                    if (capa?.sintetica == true) ao.kupila.gestaonegocios.ui.comum.Selo("Ilustração gerada", Color(0xCC111816))
                }
            }
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(a.titulo, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Filled.Place, null, Modifier.padding(end = 2.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${a.municipio}, ${a.provincia}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(precoComUnidade(a), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("${if (a.modalidade == Modalidade.VENDA) "Venda" else "Aluguer"} · ${a.detalhes.subcategoria.ifBlank { a.categoria.rotulo }} · ${a.id}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** `formatListingPrice` (HTML 12471): no aluguer mostra "/ unidade". */
fun precoComUnidade(a: ao.kupila.gestaonegocios.dominio.anuncio.AnuncioPublico): String {
    val base = FormatoAoa.formatar(a.preco)
    val c = a.aluguer ?: return base
    val unidade = if (c.unidade == ao.kupila.gestaonegocios.dominio.financeiro.UnidadePreco.PERSONALIZADA) c.nomeUnidadePersonalizada.ifBlank { "unidade" } else c.unidade.rotulo
    return "$base / $unidade"
}
