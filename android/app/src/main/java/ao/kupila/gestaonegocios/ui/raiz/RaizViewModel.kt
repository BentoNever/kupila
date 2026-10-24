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
    val spotec = ajustes.spotec

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

    // ---------------- restauro (como o PDV Digital): a sessão fica protegida, não termina; ao desbloquear continua onde estava.
    private val _bloqueio = MutableStateFlow<ao.kupila.gestaonegocios.ui.entrada.MotivoBloqueio?>(null)
    val bloqueio: StateFlow<ao.kupila.gestaonegocios.ui.entrada.MotivoBloqueio?> = _bloqueio.asStateFlow()
    private var saiuEm = 0L

    fun foiParaFundo() { saiuEm = SystemClock.elapsedRealtime() }
    /** Volta ao ecrã: protege a sessão só se esteve fora mais tempo do que o limite de inactividade da plataforma. */
    fun voltou() {
        if (saiuEm == 0L || conta.recursos.value == null || _bloqueio.value != null) return
        val fora = SystemClock.elapsedRealtime() - saiuEm
        saiuEm = 0L
        if (fora >= ajustes.ajustes.value.inactividadeMinutos * 60_000L) _bloqueio.value = ao.kupila.gestaonegocios.ui.entrada.MotivoBloqueio.SAIDA
        actividade()
    }
    suspend fun desbloquear(senha: String): String? = sessao.desbloquear(senha).also { if (it == null) { _bloqueio.value = null; actividade() } }
    fun desbloquearPorBiometria() { _bloqueio.value = null; actividade() }
    fun trocarConta() = viewModelScope.launch { _bloqueio.value = null; sessao.sair() }

    init {
        viewModelScope.launch {
            // Verificação a cada 5 s (precisão suficiente para minutos de inactividade, 5× menos acordares).
            while (isActive) {
                delay(5000)
                if (conta.recursos.value == null) { ultimaActividade = SystemClock.elapsedRealtime(); _inactividade.value = Inactividade.Fase.Activa; continue }
                val minutos = ajustes.ajustes.value.inactividadeMinutos
                val f = Inactividade.fase(SystemClock.elapsedRealtime() - ultimaActividade, minutos)
                _inactividade.value = f
                if (f is Inactividade.Fase.Esgotada && _bloqueio.value == null) { _bloqueio.value = ao.kupila.gestaonegocios.ui.entrada.MotivoBloqueio.INACTIVIDADE; actividade() }
            }
        }
    }
}
