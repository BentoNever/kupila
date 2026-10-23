package ao.kupila.gestaonegocios.ui.raiz

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ao.kupila.gestaonegocios.dados.sessao.EstadoSessao
import ao.kupila.gestaonegocios.dominio.sessao.Inactividade
import ao.kupila.gestaonegocios.ui.Navegacao
import ao.kupila.gestaonegocios.ui.comum.Carregando
import ao.kupila.gestaonegocios.ui.entrada.ConflitoSessaoDialogo
import ao.kupila.gestaonegocios.ui.tema.TemaKupila

/**
 * Raiz: aplica a identidade visual guardada desde o 1.º frame (login e offline), encaminha pelo estado da sessão e
 * vigia a inactividade. A área autenticada só aparece com sessão [EstadoSessao.Activa].
 */
@Composable
fun Raiz(vm: RaizViewModel = hiltViewModel()) {
    val identidade by vm.identidade.collectAsStateWithLifecycle()
    val modoLocal by vm.modoLocal.collectAsStateWithLifecycle()
    val estado by vm.estado.collectAsStateWithLifecycle()
    val inactividade by vm.inactividade.collectAsStateWithLifecycle()
    val tomando by vm.tomando.collectAsStateWithLifecycle()
    TemaKupila(identidade, modoLocal) {
        Box(Modifier.fillMaxSize().pointerInput(Unit) {
            // Qualquer toque conta como actividade (sem consumir o evento).
            awaitPointerEventScope { while (true) { awaitPointerEvent(PointerEventPass.Initial); vm.actividade() } }
        }) {
            when (val e = estado) {
                EstadoSessao.ARestaurar -> Carregando("A restaurar a sessão com segurança…")
                else -> Navegacao(vm, e, identidade.logoLocal)
            }
            if (estado is EstadoSessao.Conflito) {
                val c = estado as EstadoSessao.Conflito
                ConflitoSessaoDialogo(c.dispositivo, c.vistoEm, tomando, vm::tomarSessao, vm::cancelarConflito)
            }
            (inactividade as? Inactividade.Fase.Aviso)?.let { a ->
                AlertDialog(
                    onDismissRequest = vm::actividade,
                    title = { Text("Ainda está aí?") },
                    text = { Text("Por segurança, a sessão será encerrada por inactividade em ${Inactividade.contagem(a.restanteMs)}. Os rascunhos ficam guardados neste dispositivo.") },
                    confirmButton = { TextButton(onClick = vm::actividade) { Text("Continuar sessão") } },
                    dismissButton = { TextButton(onClick = { vm.sair() }) { Text("Terminar sessão") } },
                )
            }
        }
    }
}
