package ao.kupila.gestaonegocios.ui.anunciar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Color
import ao.kupila.gestaonegocios.ui.comum.Fa
import ao.kupila.gestaonegocios.ui.kp.FaIcone
import ao.kupila.gestaonegocios.ui.kp.Kp
import ao.kupila.gestaonegocios.ui.kp.KpBotao
import ao.kupila.gestaonegocios.ui.kp.KpFechar
import ao.kupila.gestaonegocios.ui.kp.T
import ao.kupila.gestaonegocios.ui.kp.sup
import ao.kupila.gestaonegocios.ui.kp.toque
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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

/**
 * CADASTROS INACABADOS (`#modal-product-drafts`, HTML 8235-8249; render 22028): continuar com um toque, eliminar um ou vários,
 * ou iniciar um novo anúncio. Os rascunhos são guardados sozinhos (até 5) e sincronizados entre telemóveis da mesma conta.
 */
@Composable
fun RascunhosEcra(vm: RascunhosViewModel, aoVoltar: () -> Unit, aoAbrir: (String) -> Unit, aoNovo: () -> Unit) {
    val marca = ao.kupila.gestaonegocios.ui.kp.LocalMarca.current
    val l by vm.lista.collectAsStateWithLifecycle()
    var marcados by rememberSaveable { mutableStateOf(setOf<String>()) }
    val lista = l.orEmpty()
    Column(Modifier.background(Kp.slate50)) {
        Row(Modifier.fillMaxWidth().background(ao.kupila.gestaonegocios.ui.kp.gradiente(90f, 0f to Kp.slate950, 1f to marca.c900)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                T("Continuidade protegida", 9f, marca.c200, 700, espacamento = 1.4f, maiusculas = true)
                Row(verticalAlignment = Alignment.CenterVertically) { FaIcone(Fa.BOX_ARCHIVE, 11f, Kp.yellow400); Spacer(Modifier.width(6.dp)); T("Cadastros inacabados", 14f, Color.White, 700) }
                T("${lista.size} de ${PoliticaRascunhos.LIMITE} rascunho(s) nesta conta · seleção múltipla disponível.", 8f, Kp.slate300, Modifier.padding(top = 2.dp))
            }
            Box(Modifier.size(36.dp).sup(36.dp, fundo = Color.White.copy(.1f), forma = CircleShape).toque(aoTocar = aoNovo), contentAlignment = Alignment.Center) { FaIcone(Fa.PLUS, 11f, Color.White) }
            Spacer(Modifier.width(6.dp)); KpFechar(aoVoltar)
        }
        T("Encontrámos trabalhos ainda não concluídos. Pode continuar qualquer um deles ou iniciar um novo anúncio. Quando já existirem ${PoliticaRascunhos.LIMITE}, o novo trabalho substitui apenas o rascunho mais antigo.",
            9f, Kp.slate600, Modifier.fillMaxWidth().background(Color.White).padding(16.dp, 12.dp), linha = 14f)
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (l != null && lista.isEmpty()) T("Não existem cadastros inacabados.", 10f, Kp.slate400, Modifier.fillMaxWidth().padding(vertical = 40.dp), centro = true)
            if (marcados.isNotEmpty()) KpBotao("Eliminar ${marcados.size} seleccionado(s)", { vm.remover(marcados); marcados = emptySet() }, icone = Fa.TRASH,
                fundo = Kp.red50, cor = Kp.red600, borda = Color(0xFFFEE2E2), altura = 36.dp, raio = 12.dp, tamanho = 10f)
            lista.forEach { r ->
                val sel = r.id in marcados
                Column(Modifier.fillMaxWidth().sup(16.dp, fundo = Color.White, borda = if (sel) marca.c300 else Kp.slate200, bordaLargura = if (sel) 2.dp else 1.dp).padding(12.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Box(Modifier.size(32.dp).sup(12.dp, fundo = Kp.slate50, borda = Kp.slate200).toque(!r.protegido) { marcados = if (sel) marcados - r.id else marcados + r.id }, contentAlignment = Alignment.Center) {
                            if (sel) FaIcone(Fa.CHECK, 10f, marca.c600)
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Row { Column(Modifier.weight(1f)) {
                                    T(r.titulo, 10f, Kp.slate900, 900, max = 1)
                                    T("${if (r.protegido) "Pagamento realmente pendente" else "Cadastro inacabado"} · guardado em ${formatarData(r.actualizadoEm.toEpochMilli())}", 8f, Kp.slate400, Modifier.padding(top = 2.dp), max = 2)
                                }
                                T("${r.percentagemConclusao}%", 8f, marca.c700, 900, Modifier.sup(999.dp, fundo = marca.c50).padding(horizontal = 8.dp, vertical = 4.dp)) }
                            Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(6.dp).sup(6.dp, fundo = Kp.slate100)) {
                                Box(Modifier.fillMaxWidth(r.percentagemConclusao / 100f).height(6.dp).sup(6.dp, fundo = marca.c600))
                            }
                        }
                    }
                    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KpBotao("Continuar", { aoAbrir(r.id) }, Modifier.weight(1f), icone = Fa.PEN_TO_SQUARE, altura = 34.dp, raio = 12.dp, tamanho = 9f)
                        if (!r.protegido) Box(Modifier.size(40.dp, 34.dp).sup(12.dp, fundo = Kp.red50, borda = Color(0xFFFEE2E2)).toque { vm.remover(listOf(r.id)) }, contentAlignment = Alignment.Center) {
                            FaIcone(Fa.TRASH, 9f, Kp.red600)
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().background(Color.White).padding(12.dp)) {
            KpBotao("Não continuar · iniciar novo anúncio", aoNovo, icone = Fa.PLUS, altura = 38.dp, raio = 12.dp, tamanho = 9f)
        }
    }
}