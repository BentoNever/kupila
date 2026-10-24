package ao.kupila.gestaonegocios.ui.raiz

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ao.kupila.gestaonegocios.dados.sessao.EstadoSessao
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
    val tomando by vm.tomando.collectAsStateWithLifecycle()
    val bloqueio by vm.bloqueio.collectAsStateWithLifecycle()
    val spotec by vm.spotec.collectAsStateWithLifecycle()
    val ciclo = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(ciclo) {
        val o = androidx.lifecycle.LifecycleEventObserver { _, ev ->
            if (ev == androidx.lifecycle.Lifecycle.Event.ON_STOP) vm.foiParaFundo() else if (ev == androidx.lifecycle.Lifecycle.Event.ON_START) vm.voltou()
        }
        ciclo.lifecycle.addObserver(o); onDispose { ciclo.lifecycle.removeObserver(o) }
    }
    TemaKupila(identidade, modoLocal, spotec) {
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
            // Restauro por cima do ecrã onde a pessoa estava (a navegação e os formulários continuam por baixo).
            val activa = estado as? EstadoSessao.Activa
            if (activa != null && bloqueio != null) ao.kupila.gestaonegocios.ui.entrada.EcraRestauro(activa.perfil.nome, bloqueio!!, vm::desbloquear,
                porBiometria = vm::desbloquearPorBiometria, trocarConta = { vm.trocarConta() })
        }
    }
}
