package ao.kupila.gestaonegocios.ui.anunciar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ao.kupila.gestaonegocios.dados.rascunhos.RascunhosRepositorio
import ao.kupila.gestaonegocios.dominio.rascunhos.PoliticaRascunhos
import ao.kupila.gestaonegocios.ui.ajuda.EcraComTopo
import ao.kupila.gestaonegocios.ui.comum.BotaoPrincipal
import ao.kupila.gestaonegocios.ui.comum.BotaoSecundario
import ao.kupila.gestaonegocios.ui.comum.EstadoVazio
import ao.kupila.gestaonegocios.ui.entrada.formatarData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RascunhosViewModel @Inject constructor(private val repo: RascunhosRepositorio) : ViewModel() {
    val lista = repo.lista().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    init { viewModelScope.launch { repo.importarNuvem() } }
    fun remover(ids: Collection<String>) = viewModelScope.launch { repo.remover(ids.toList()) }
}

/** Cadastros inacabados (secção 13): continuar com um toque, apagar vários de uma vez. */
@Composable
fun RascunhosEcra(vm: RascunhosViewModel, aoVoltar: () -> Unit, aoAbrir: (String) -> Unit, aoNovo: () -> Unit) {
    val l by vm.lista.collectAsStateWithLifecycle()
    var marcados by rememberSaveable { mutableStateOf(setOf<String>()) }
    EcraComTopo("Cadastros inacabados", aoVoltar) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text("Guardados automaticamente (até ${PoliticaRascunhos.LIMITE}). Continue noutro telemóvel com a mesma conta.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val lista = l
            if (lista != null && lista.isEmpty()) item { EstadoVazio("Nenhum cadastro inacabado", "O que começar a anunciar fica guardado aqui sozinho.", accao = "Anunciar" to aoNovo) }
            items(lista.orEmpty(), key = { it.id }) { r ->
                Card(modifier = Modifier.fillMaxWidth(), onClick = { aoAbrir(r.id) }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(r.id in marcados, { on -> marcados = if (on) marcados + r.id else marcados - r.id }, enabled = !r.protegido)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(r.titulo, style = MaterialTheme.typography.titleMedium)
                            Text((if (r.protegido) "A aguardar pagamento · " else "") + "Actualizado ${formatarData(r.actualizadoEm.toEpochMilli())}",
                                style = MaterialTheme.typography.bodySmall)
                            LinearProgressIndicator({ r.percentagemConclusao / 100f }, Modifier.fillMaxWidth())
                        }
                    }
                }
            }
            if (!lista.isNullOrEmpty()) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (marcados.isNotEmpty()) BotaoSecundario("Apagar ${marcados.size} seleccionado(s)", { vm.remover(marcados); marcados = emptySet() })
                    BotaoPrincipal("Novo anúncio", aoNovo, icone = Icons.Filled.Add)
                }
            }
        }
    }
}
