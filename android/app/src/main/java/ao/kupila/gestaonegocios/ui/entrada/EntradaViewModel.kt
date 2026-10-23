package ao.kupila.gestaonegocios.ui.entrada

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ao.kupila.gestaonegocios.dados.remoto.MensagensErro
import ao.kupila.gestaonegocios.dados.sessao.ContaConhecida
import ao.kupila.gestaonegocios.dados.sessao.Dispositivo
import ao.kupila.gestaonegocios.dados.sessao.SessaoRepositorio
import ao.kupila.gestaonegocios.dominio.perfis.ValidacaoContacto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EstadoLogin(
    val email: String = "",
    val senha: String = "",
    val aEntrar: Boolean = false,
    val erro: String? = null,
    /** Mensagem do estado da entrada automática (`setAutoLoginStatus`). */
    val estadoAutomatico: String = "Login automático activo: depois de inserir credenciais válidas, o acesso inicia sozinho.",
)

/**
 * Login por e-mail/palavra-passe com entrada automática controlada (secção 8; `scheduleAutomaticCredentialLogin`, 10495):
 * debounce de 700 ms, só uma tentativa em curso, não repete a mesma assinatura sem edição; o botão Entrar fica sempre disponível.
 * A palavra-passe vive só na memória do ViewModel (nunca no SavedStateHandle, preferências ou registos).
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val sessao: SessaoRepositorio,
    dispositivo: Dispositivo,
    private val guardado: SavedStateHandle,
) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoLogin(email = guardado["email"] ?: ""))
    val estado: StateFlow<EstadoLogin> = _estado.asStateFlow()
    val contas: StateFlow<List<ContaConhecida>> = dispositivo.contasConhecidas.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val dispositivoRef = dispositivo
    private var automatico: Job? = null
    private var ultimaAssinatura: String? = null

    fun email(v: String) { guardado["email"] = v; _estado.update { it.copy(email = v, erro = null) }; aoEditar() }
    fun senha(v: String) { _estado.update { it.copy(senha = v, erro = null) }; aoEditar() }

    /** Conta com credencial segura entra de imediato; sem ela, preenche o e-mail e pede só a palavra-passe ("Confirmar"). */
    fun escolherConta(c: ContaConhecida) {
        automatico?.cancel()
        guardado["email"] = c.email
        viewModelScope.launch {
            val s = dispositivoRef.acessoRapido(c.email)
            _estado.update { it.copy(email = c.email, senha = s ?: "", erro = null) }
            if (s != null && !executar()) {
                dispositivoRef.removerAcessoRapido(c.email)
                _estado.update { it.copy(senha = "", erro = (it.erro ?: "") .ifBlank { "Confirme a palavra-passe desta conta." }) }
            }
        }
    }
    fun esquecerConta(c: ContaConhecida) = viewModelScope.launch { dispositivoRef.esquecer(c.uid) }

    private fun aoEditar() {
        ultimaAssinatura = null
        automatico?.cancel()
        val e = _estado.value
        if (!ValidacaoContacto.emailValido(e.email) || e.senha.length < 6 || e.aEntrar) {
            _estado.update { it.copy(estadoAutomatico = EstadoLogin().estadoAutomatico) }; return
        }
        val assinatura = e.email.trim().lowercase() + "\n" + e.senha
        _estado.update { it.copy(estadoAutomatico = "Credenciais completas. A validar automaticamente…") }
        automatico = viewModelScope.launch {
            delay(700)
            val agora = _estado.value
            if (agora.email.trim().lowercase() + "\n" + agora.senha != assinatura || assinatura == ultimaAssinatura) return@launch
            ultimaAssinatura = assinatura
            val ok = executar()
            if (!ok) _estado.update { it.copy(estadoAutomatico = "Continue a corrigir as credenciais; a entrada será tentada novamente automaticamente.") }
        }
    }

    fun entrar() {
        automatico?.cancel()
        viewModelScope.launch { executar() }
    }

    private suspend fun executar(): Boolean {
        val e = _estado.value
        if (e.aEntrar) return false
        if (!ValidacaoContacto.emailValido(e.email)) { _estado.update { it.copy(erro = "Informe um e-mail válido.") }; return false }
        if (e.senha.isEmpty()) { _estado.update { it.copy(erro = "Informe a palavra-passe.") }; return false }
        _estado.update { it.copy(aEntrar = true, erro = null) }
        val r = sessao.entrar(e.email, e.senha)
        if (r.isSuccess) runCatching { dispositivoRef.guardarAcessoRapido(e.email, e.senha) }
        _estado.update { it.copy(aEntrar = false, erro = r.exceptionOrNull()?.let(MensagensErro::de), senha = if (r.isSuccess) "" else it.senha) }
        return r.isSuccess
    }
}

data class EstadoRecuperacao(val email: String = "", val aEnviar: Boolean = false, val enviado: Boolean = false, val erro: String? = null, val esperarAte: Long = 0)

/** Recuperação por link oficial; resposta neutra e cooldown de 60 s (`startCredentialRecoveryCooldown`). */
@HiltViewModel
class RecuperacaoViewModel @Inject constructor(private val sessao: SessaoRepositorio, guardado: SavedStateHandle) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoRecuperacao(email = guardado["email"] ?: ""))
    val estado: StateFlow<EstadoRecuperacao> = _estado.asStateFlow()
    fun email(v: String) = _estado.update { it.copy(email = v, erro = null) }
    fun enviar() = viewModelScope.launch {
        val e = _estado.value
        if (System.currentTimeMillis() < e.esperarAte) return@launch
        if (!ValidacaoContacto.emailValido(e.email)) { _estado.update { it.copy(erro = "Informe um e-mail válido.") }; return@launch }
        _estado.update { it.copy(aEnviar = true, erro = null) }
        val r = sessao.recuperar(e.email)
        _estado.update { it.copy(aEnviar = false, enviado = r.isSuccess, erro = r.exceptionOrNull()?.let(MensagensErro::de),
            esperarAte = if (r.isSuccess) System.currentTimeMillis() + 60_000 else 0) }
    }
}
