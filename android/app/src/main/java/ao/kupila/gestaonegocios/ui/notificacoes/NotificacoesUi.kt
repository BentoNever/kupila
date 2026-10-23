package ao.kupila.gestaonegocios.ui.notificacoes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ao.kupila.gestaonegocios.dados.ContaActiva
import ao.kupila.gestaonegocios.dados.local.NotificacaoLocal
import ao.kupila.gestaonegocios.ui.ajuda.EcraComTopo
import ao.kupila.gestaonegocios.ui.comum.EstadoVazio
import ao.kupila.gestaonegocios.ui.entrada.formatarData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Notificações derivadas dos eventos da conta (D-29): locais, sem leituras extra. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NotificacoesViewModel @Inject constructor(private val conta: ContaActiva) : ViewModel() {
    val lista = conta.recursos.flatMapLatest { it?.base?.notificacoes()?.observar(null) ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    fun lerTodas() = viewModelScope.launch { conta.recursos.value?.base?.notificacoes()?.marcarTodasLidas() }
    fun ler(n: NotificacaoLocal) = viewModelScope.launch { conta.recursos.value?.base?.notificacoes()?.marcarLidas(listOf(n.id)) }
}

@Composable
fun NotificacoesEcra(vm: NotificacoesViewModel, aoVoltar: () -> Unit, aoAccao: (NotificacaoLocal) -> Unit) {
    val l by vm.lista.collectAsStateWithLifecycle()
    EcraComTopo("Notificações", aoVoltar) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val lista = l.orEmpty()
            if (lista.any { !it.lida }) item { TextButton(onClick = vm::lerTodas) { Text("Marcar todas como lidas") } }
            if (l != null && lista.isEmpty()) item { EstadoVazio("Sem notificações", "Reservas, pagamentos e autorizações aparecem aqui.") }
            items(lista, key = { it.id }) { n ->
                Card(onClick = { vm.ler(n); aoAccao(n) }, modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = if (n.lida) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(n.titulo, fontWeight = if (n.lida) FontWeight.Normal else FontWeight.Bold)
                        Text(n.mensagem, style = MaterialTheme.typography.bodySmall)
                        Text(formatarData(n.criadaEm), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
