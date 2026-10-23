package ao.kupila.gestaonegocios.ui.ajuda

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ao.kupila.gestaonegocios.dados.local.SeccaoTexto
import ao.kupila.gestaonegocios.ui.comum.BotaoPrincipal
import kotlinx.coroutines.launch

/**
 * Manual, Políticas e Intermediação (`modal-kupila-policies`, HTML 8324-8391). Em contexto de aceite (cadastro ou anúncio),
 * "Li e compreendi estas regras" só fica disponível depois de ler até ao fim (gate da origem 8928-8970); o aceite em si é
 * a caixa explícita no formulário — a leitura não é tratada como consentimento.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoliticasEcra(seccoes: List<SeccaoTexto>, versao: String, comAceite: Boolean, aoVoltar: () -> Unit, aoConfirmarLeitura: () -> Unit, aoSimular: (() -> Unit)? = null) {
    val lista = rememberLazyListState()
    val ambito = rememberCoroutineScope()
    val leuAteFim by remember { derivedStateOf { val info = lista.layoutInfo; info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 1 } }
    val inicioPoliticas = seccoes.indexOfFirst { it.titulo.startsWith("Políticas de Uso") }.coerceAtLeast(0)
    Scaffold(topBar = {
        TopAppBar(title = { Text("Manual, Políticas e Intermediação") },
            navigationIcon = { IconButton(onClick = aoVoltar) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } })
    }, bottomBar = {
        if (comAceite) Surface(tonalElevation = 3.dp) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (leuAteFim) "Leitura concluída. Confirme e marque a caixa de aceite no formulário." else "Leia até ao final para confirmar a leitura.",
                    style = MaterialTheme.typography.bodySmall)
                BotaoPrincipal("Li e compreendi estas regras", aoConfirmarLeitura, enabled = leuAteFim)
            }
        }
    }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), state = lista, contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = false, onClick = { ambito.launch { lista.animateScrollToItem(1) } }, label = { Text("Manual") })
                    FilterChip(selected = false, onClick = { ambito.launch { lista.animateScrollToItem(inicioPoliticas + 1) } }, label = { Text("Políticas") })
                    aoSimular?.let { FilterChip(selected = false, onClick = it, label = { Text("Simular") }) }
                }
            }
            items(seccoes) { s ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (s.titulo.isNotBlank()) Text(s.titulo, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                    s.paragrafos.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                    Text("Versão das políticas: $versao", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
