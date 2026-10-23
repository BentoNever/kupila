package ao.kupila.gestaonegocios.ui.notificacoes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ao.kupila.gestaonegocios.dados.ContaActiva
import ao.kupila.gestaonegocios.dados.local.NotificacaoLocal
import ao.kupila.gestaonegocios.ui.comum.Fa
import ao.kupila.gestaonegocios.ui.entrada.formatarData
import ao.kupila.gestaonegocios.ui.kp.FaIcone
import ao.kupila.gestaonegocios.ui.kp.Kp
import ao.kupila.gestaonegocios.ui.kp.KpCabecalhoModal
import ao.kupila.gestaonegocios.ui.kp.LocalMarca
import ao.kupila.gestaonegocios.ui.kp.T
import ao.kupila.gestaonegocios.ui.kp.sup
import ao.kupila.gestaonegocios.ui.kp.toque
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

/** Categorias do centro de alertas da origem (`modal-notifications`, HTML 7603-7622). */
private val CATEGORIAS = listOf<Pair<String?, String>>(null to "Todas", "RESERVATION" to "Reservas", "MONETIZATION" to "Monetização",
    "INTEREST" to "Interesse", "ADMIN" to "Administração", "NEWS" to "Novidades")

@Composable
fun NotificacoesEcra(vm: NotificacoesViewModel, aoVoltar: () -> Unit, aoAccao: (NotificacaoLocal) -> Unit) {
    val l by vm.lista.collectAsStateWithLifecycle()
    val marca = LocalMarca.current
    var cat by rememberSaveable { mutableStateOf<String?>(null) }
    val todas = l.orEmpty()
    val naoLidas = todas.count { !it.lida }
    val filtradas = todas.filter { cat == null || it.categoria == cat }
    Column {
        KpCabecalhoModal("Centro de alertas", "Notificações", aoVoltar, icone = Fa.BELL, iconeRegular = true, direita = {
            T("$naoLidas não lidas", 8f, Color(0xFFFCA5A5), 800, Modifier.sup(999.dp, fundo = Kp.red500.copy(.18f), borda = Kp.red500.copy(.25f)).padding(horizontal = 8.dp, vertical = 4.dp))
        })
        Column(Modifier.fillMaxWidth().background(Color.White).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CATEGORIAS.chunked(3).forEach { trio ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    trio.forEach { (codigo, rotulo) ->
                        val on = cat == codigo
                        Box(Modifier.weight(1f).height(25.dp).sup(10.dp, fundo = if (on) Kp.slate900 else Kp.slate100).toque { cat = codigo }, contentAlignment = Alignment.Center) {
                            T(rotulo, 8f, if (on) Color.White else Kp.slate500, if (on) 800 else 700)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(.66f).height(25.dp).sup(10.dp, fundo = marca.c50, borda = marca.c200).toque(aoTocar = { vm.lerTodas() }),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                FaIcone(Fa.CHECK_DOUBLE, 8f, marca.c700); Spacer(Modifier.width(6.dp)); T("Marcar lidas", 8f, marca.c700, 700)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Kp.slate900))
        Column(Modifier.fillMaxWidth().background(Kp.slate50).heightIn(min = 130.dp, max = 520.dp).verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (l != null && filtradas.isEmpty()) {
                Row(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    FaIcone(Fa.BELL_SLASH, 16f, Kp.slate400, regular = true); Spacer(Modifier.width(4.dp)); T("Sem notificações nesta categoria.", 9f, Kp.slate400)
                }
            }
            filtradas.forEach { n -> Notificacao(n) { vm.ler(n); aoAccao(n) } }
        }
    }
}

@Composable
private fun Notificacao(n: NotificacaoLocal, aoTocar: () -> Unit) {
    val (fundo, cor, icone) = when (n.severidade) {
        "AVISO" -> Triple(Kp.amber50, Kp.amber700, Fa.TRIANGLE_EXCLAMATION)
        "IMPORTANTE" -> Triple(Kp.red50, Kp.red600, Fa.TRIANGLE_EXCLAMATION)
        else -> Triple(Kp.emerald50, Kp.emerald700, Fa.BELL)
    }
    Row(Modifier.fillMaxWidth().sup(14.dp, fundo = if (n.lida) Color.White else Color(0xFFF8FFFB), borda = if (n.lida) Kp.slate200 else Kp.emerald100).toque(aoTocar = aoTocar).padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(30.dp).sup(10.dp, fundo = fundo), contentAlignment = Alignment.Center) { FaIcone(icone, 11f, cor, regular = icone == Fa.BELL) }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                T(n.titulo, 9f, Kp.slate900, if (n.lida) 700 else 900, Modifier.weight(1f), max = 1)
                if (!n.lida) Box(Modifier.size(6.dp).sup(6.dp, fundo = Kp.red500))
            }
            T(n.mensagem, 8f, Kp.slate600, Modifier.padding(top = 2.dp), linha = 12f)
            T(formatarData(n.criadaEm), 7f, Kp.slate400, Modifier.padding(top = 4.dp))
        }
    }
}
