package ao.kupila.gestaonegocios.ui.raiz

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ao.kupila.gestaonegocios.dados.ContaActiva
import ao.kupila.gestaonegocios.dados.ajustes.AjustesRepositorio
import ao.kupila.gestaonegocios.dados.local.Conteudos
import ao.kupila.gestaonegocios.dados.local.Geografia
import ao.kupila.gestaonegocios.dados.remoto.MensagensErro
import ao.kupila.gestaonegocios.dados.sessao.SessaoRepositorio
import ao.kupila.gestaonegocios.dominio.sessao.Inactividade
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RaizViewModel @Inject constructor(
    val sessao: SessaoRepositorio,
    val conta: ContaActiva,
    val ajustes: AjustesRepositorio,
    val conteudos: Conteudos,
    val geografia: Geografia,
) : ViewModel() {
    val estado = sessao.estado
    val identidade = ajustes.identidade
    val modoLocal = ajustes.modoLocal

    private val _tomando = MutableStateFlow(false)
    val tomando: StateFlow<Boolean> = _tomando.asStateFlow()
    private val _erroConflito = MutableStateFlow<String?>(null)
    val erroConflito: StateFlow<String?> = _erroConflito.asStateFlow()

    fun tomarSessao() = viewModelScope.launch {
        _tomando.value = true
        _erroConflito.value = sessao.tomarSessao().exceptionOrNull()?.let(MensagensErro::de)
        _tomando.value = false
    }
    fun cancelarConflito() = sessao.cancelarConflito()
    fun sair() = viewModelScope.launch { sessao.sair() }

    // ---------------- inactividade (secção 9; decisão D-25): tempo real decorrido, não temporizador de UI.
    private var ultimaActividade = SystemClock.elapsedRealtime()
    private val _inactividade = MutableStateFlow<Inactividade.Fase>(Inactividade.Fase.Activa)
    val inactividade: StateFlow<Inactividade.Fase> = _inactividade.asStateFlow()

    fun actividade() {
        ultimaActividade = SystemClock.elapsedRealtime()
        if (_inactividade.value !is Inactividade.Fase.Activa) _inactividade.value = Inactividade.Fase.Activa
    }

    init {
        viewModelScope.launch {
            while (isActive) {
                delay(1000)
                if (conta.recursos.value == null) { ultimaActividade = SystemClock.elapsedRealtime(); _inactividade.value = Inactividade.Fase.Activa; continue }
                val minutos = ajustes.ajustes.value.inactividadeMinutos
                val f = Inactividade.fase(SystemClock.elapsedRealtime() - ultimaActividade, minutos)
                _inactividade.value = f
                if (f is Inactividade.Fase.Esgotada) { sessao.sairPorInactividade(minutos); actividade() }
            }
        }
    }
}
