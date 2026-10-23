package ao.kupila.gestaonegocios.dados.sessao

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ao.kupila.gestaonegocios.dados.remoto.ArranqueFirebase
import ao.kupila.gestaonegocios.dados.remoto.PedidoRegisto
import ao.kupila.gestaonegocios.dados.remoto.PerfilRemoto
import ao.kupila.gestaonegocios.dados.remoto.ResultadoSessao
import ao.kupila.gestaonegocios.dados.remoto.SeletorCentral
import ao.kupila.gestaonegocios.dados.remoto.ErroCentral
import ao.kupila.gestaonegocios.dados.remoto.MensagensErro
import ao.kupila.gestaonegocios.dados.seguranca.Cofre
import ao.kupila.gestaonegocios.dominio.perfis.CanalAlerta
import ao.kupila.gestaonegocios.dominio.perfis.EstadoConta
import ao.kupila.gestaonegocios.dominio.perfis.Papel
import ao.kupila.gestaonegocios.dominio.perfis.Perfil
import ao.kupila.gestaonegocios.dominio.perfis.Residencia
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dadosSessao by preferencesDataStore("kupila_sessao")

/** Estado global da sessão (secções 8 e 9). A área autenticada só abre em [Activa]. */
sealed interface EstadoSessao {
    data object ARestaurar : EstadoSessao
    data class SemSessao(val aviso: String? = null) : EstadoSessao
    data object AEntrar : EstadoSessao
    /** Outra instalação tem a sessão viva; o utilizador decide "Encerrar sessão anterior e continuar". */
    data class Conflito(val dispositivo: String, val vistoEm: Long?) : EstadoSessao
    /** Conta Auth sem perfil concluído (cadastro interrompido): completar sem repetir a criação da conta. */
    data class PerfilIncompleto(val uid: String, val email: String) : EstadoSessao
    /**
     * Sessão vigente. [offline] = restaurada sem rede a partir da última sessão validada: leitura de cache e rascunhos,
     * confirmações sensíveis bloqueadas até revalidar (secção 9).
     */
    data class Activa(val perfil: Perfil, val sessaoId: String, val offline: Boolean = false) : EstadoSessao
}

@Serializable
private data class SessaoGuardada(val uid: String, val sessaoId: String, val perfil: PerfilGuardado, val validadaEm: Long)

@Serializable
private data class PerfilGuardado(val uid: String, val papel: String, val estado: String, val nome: String, val email: String,
    val telefone: String = "", val telefoneAlt: String = "", val provincia: String = "", val municipio: String = "", val bairro: String = "",
    val canal: String = "AUTO", val foto: String? = null, val criadoPor: String? = null, val politica: String? = null, val versao: Long = 1)

/** Dados do cadastro público (`register-*`, HTML 6552-6571). */
data class DadosCadastro(
    val papel: Papel, val nome: String, val email: String, val telefonePrincipal: String, val telefoneAlternativo: String,
    val provincia: String, val municipio: String, val bairro: String, val canal: CanalAlerta,
    val politicaVersao: String, val politicaLidaAteFim: Boolean, val fotoJpeg: ByteArray?,
)

@Singleton
class SessaoRepositorio @Inject constructor(
    @param:ApplicationContext private val contexto: Context,
    private val central: SeletorCentral,
    private val dispositivo: Dispositivo,
    private val cofre: Cofre,
) {
    private val ambito = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val chave = stringPreferencesKey("sessao")
    private val mutex = Mutex()
    private val _estado = MutableStateFlow<EstadoSessao>(EstadoSessao.ARestaurar)
    val estado: StateFlow<EstadoSessao> = _estado.asStateFlow()

    private var batimento: Job? = null
    private var escuta: ListenerRegistration? = null
    private val auth get() = FirebaseAuth.getInstance()

    // ---------------------------------------------------------------- arranque
    /** Restaura a sessão: com rede revalida no Central; sem rede abre em modo offline limitado. */
    fun restaurar() = ambito.launch {
        mutex.withLock {
            if (!ArranqueFirebase.disponivel) { _estado.value = EstadoSessao.SemSessao(); return@launch }
            val user = auth.currentUser
            val guardada = lerGuardada()
            if (user == null || guardada == null || guardada.uid != user.uid) {
                if (user != null && guardada == null) concluirEntrada(user.uid, user.email.orEmpty(), sessaoActual = null)
                else { if (user == null) limparGuardada(); _estado.value = EstadoSessao.SemSessao() }
                return@launch
            }
            try {
                concluirEntrada(user.uid, user.email.orEmpty(), sessaoActual = guardada.sessaoId)
            } catch (e: Throwable) {
                if (eFalhaDeRede(e)) _estado.value = EstadoSessao.Activa(guardada.perfil.paraDominio(), guardada.sessaoId, offline = true)
                else { encerrarLocal(); _estado.value = EstadoSessao.SemSessao(MensagensErro.de(e)) }
            }
        }
    }

    // ---------------------------------------------------------------- entrada
    /** Login por e-mail e palavra-passe (secção 8). Uma tentativa de cada vez; nunca guarda a senha. */
    suspend fun entrar(email: String, senha: String): Result<Unit> = mutex.withLock {
        _estado.value = EstadoSessao.AEntrar
        // Entrar é sempre um pedido explícito: a pausa do modo poupança não pode impedir a autenticação.
        ao.kupila.gestaonegocios.dados.rede.Economia.permitirEntrada()
        ao.kupila.gestaonegocios.dados.rede.RedeFirestore.primeiroPlano()
        runCatching {
            val r = auth.signInWithEmailAndPassword(email.trim(), senha).await()
            concluirEntrada(r.user!!.uid, email.trim(), sessaoActual = lerGuardada()?.takeIf { it.uid == r.user!!.uid }?.sessaoId)
        }.onFailure { e ->
            if (_estado.value is EstadoSessao.AEntrar) _estado.value = EstadoSessao.SemSessao()
            if (e !is ErroCentral || !e.sessaoSubstituida) runCatching { if (_estado.value !is EstadoSessao.Activa) auth.signOut() }
        }
    }

    /** "Encerrar sessão anterior e continuar" (`confirmSingleSessionTakeover`). */
    suspend fun tomarSessao(): Result<Unit> = mutex.withLock {
        runCatching {
            val u = auth.currentUser ?: error("Sessão expirada. Entre novamente.")
            concluirEntrada(u.uid, u.email.orEmpty(), sessaoActual = null, forcar = true)
        }
    }

    /** "Voltar ao login" (`cancelSingleSessionTakeover`). */
    fun cancelarConflito() { auth.signOut(); _estado.value = EstadoSessao.SemSessao() }

    private suspend fun concluirEntrada(uid: String, email: String, sessaoActual: String?, forcar: Boolean = false) {
        val c = central.actual()
        // O papel e o estado vêm do perfil guardado no servidor (users/{uid}), nunca de um campo do APK (D-21/D-27).
        val remoto = c.meuPerfil() ?: run { _estado.value = EstadoSessao.PerfilIncompleto(uid, email); return }
        if (remoto.status != EstadoConta.ACTIVA.codigo) {
            auth.signOut()
            _estado.value = EstadoSessao.SemSessao(when (EstadoConta.de(remoto.status)) {
                EstadoConta.SUSPENSA -> "Esta conta está suspensa. Contacte o Administrador."
                EstadoConta.ARQUIVADA -> "Esta conta foi arquivada. Contacte o Administrador."
                else -> "Esta conta está inactiva. Contacte o Administrador."
            })
            return
        }
        when (val s = c.reivindicarSessao(dispositivo.instalacaoId(), dispositivo.rotulo, forcar, sessaoActual)) {
            is ResultadoSessao.Conflito -> { _estado.value = EstadoSessao.Conflito(s.dispositivo, s.vistoEm); return }
            is ResultadoSessao.Concedida -> {
                val perfil = remoto.guardado()
                guardar(SessaoGuardada(uid, s.sessaoId, perfil, System.currentTimeMillis()))
                dispositivo.lembrar(ContaConhecida(uid, perfil.nome, perfil.email, perfil.papel, System.currentTimeMillis()))
                _estado.value = EstadoSessao.Activa(perfil.paraDominio(), s.sessaoId)
                vigiar(uid, s.sessaoId)
            }
        }
    }

    private fun PerfilRemoto.guardado() = PerfilGuardado(uid, role, status, name, email, phonePrimary, phoneAlt, province, municipality,
        neighborhood, alertChannel, photoPath, createdBy, policyVersion, version)

    private suspend fun carregarPerfil(): PerfilGuardado =
        central.actual().meuPerfil()?.guardado() ?: throw ErroCentral("not-found", "Perfil inexistente.")

    /** Recarrega o perfil após alteração (ex.: cadastro de Proprietário). */
    suspend fun actualizarPerfil() {
        val a = _estado.value as? EstadoSessao.Activa ?: return
        val p = carregarPerfil()
        guardar(SessaoGuardada(p.uid, a.sessaoId, p, System.currentTimeMillis()))
        _estado.value = a.copy(perfil = p.paraDominio(), offline = false)
    }

    // ---------------------------------------------------------------- cadastro
    /** Cadastro público: cria a conta Auth, envia a foto (Proprietário) e conclui no Central. */
    suspend fun registar(d: DadosCadastro, senha: String): Result<Unit> = mutex.withLock {
        ao.kupila.gestaonegocios.dados.rede.Economia.permitirEntrada()
        runCatching {
            val u = auth.currentUser?.takeIf { it.email.equals(d.email.trim(), true) }
                ?: auth.createUserWithEmailAndPassword(d.email.trim(), senha).await().user!!
            concluirCadastroInterno(u.uid, d)
        }
    }

    /** Conclui o cadastro de uma conta Auth já criada ([EstadoSessao.PerfilIncompleto]). */
    suspend fun concluirCadastro(d: DadosCadastro): Result<Unit> = mutex.withLock {
        runCatching { concluirCadastroInterno(auth.currentUser?.uid ?: error("Sessão expirada. Entre novamente."), d) }
    }

    private suspend fun concluirCadastroInterno(uid: String, d: DadosCadastro) {
        central.actual().registar(PedidoRegisto(d.papel.codigo, d.nome, d.email.trim(), d.telefonePrincipal, d.telefoneAlternativo,
            d.provincia, d.municipio, d.bairro, d.canal.codigo, d.politicaVersao, d.politicaLidaAteFim, d.fotoJpeg))
        concluirEntrada(uid, d.email.trim(), sessaoActual = null)
    }

    // ---------------------------------------------------------------- recuperação
    /** Link oficial de redefinição. Resposta neutra: não confirma se o e-mail existe (secção 8). */
    suspend fun recuperar(email: String): Result<Unit> = runCatching {
        try { auth.sendPasswordResetEmail(email.trim()).await() }
        catch (e: com.google.firebase.auth.FirebaseAuthInvalidUserException) { /* resposta neutra */ }
    }

    // ---------------------------------------------------------------- vigilância da sessão
    private fun vigiar(uid: String, sessaoId: String) {
        escuta?.remove()
        escuta = FirebaseFirestore.getInstance().document("activeSessions/$uid").addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { s, _ ->
            // Só instantâneos confirmados pelo servidor: a cache local pode trazer a sessão anterior e encerrar esta por engano.
            if (s == null || s.metadata.isFromCache || s.metadata.hasPendingWrites()) return@addSnapshotListener
            val actual = s.getString("sessionId") ?: return@addSnapshotListener
            if (actual != sessaoId && _estado.value is EstadoSessao.Activa) ambito.launch { substituida() }
        }
        batimento?.cancel()
        batimento = ambito.launch {
            while (isActive) {
                // 150 s: abaixo dos 180 s com que a web considera a sessão viva, com 70% menos escritas do que os 45 s da origem.
                delay(150_000)
                if (!PrimeiroPlano.activo.value || ao.kupila.gestaonegocios.dados.rede.Economia.emPausa) continue
                val r = runCatching { central.actual().sinalSessao(sessaoId) }
                if (r.getOrNull() == false) { substituida(); break }
                (central.actual() as? ao.kupila.gestaonegocios.dados.remoto.CentralSpark)?.presenca(dispositivo.instalacaoId(), dispositivo.rotulo, sessaoId, true, "app")
            }
        }
    }

    private suspend fun substituida() {
        encerrarLocal()
        _estado.value = EstadoSessao.SemSessao("A sua conta foi aberta noutro dispositivo. Esta sessão foi encerrada.")
    }

    /** Sessão rejeitada pelo Central numa mutação (sessão substituída): termina localmente. */
    fun tratarErroSessao(e: Throwable) { if (e is ErroCentral && e.sessaoSubstituida) ambito.launch { substituida() } }

    // ---------------------------------------------------------------- saída
    /** Logout: liberta a sessão no Central (se houver rede), remove escutas e contexto privado. */
    suspend fun sair() {
        val a = _estado.value as? EstadoSessao.Activa
        if (a != null) runCatching { central.actual().terminarSessao(a.sessaoId) }
        encerrarLocal()
        _estado.value = EstadoSessao.SemSessao()
    }

    /** Fim automático por inactividade (`automaticSessionLock`, HTML 10776-10790). */
    suspend fun sairPorInactividade(minutos: Int) {
        sair()
        _estado.value = EstadoSessao.SemSessao("Sessão protegida: encerrada automaticamente após $minutos minuto(s) de inatividade. Faça login novamente.")
    }

    private suspend fun encerrarLocal() {
        batimento?.cancel(); batimento = null
        escuta?.remove(); escuta = null
        limparGuardada()
        runCatching { auth.signOut() }
        OuvintesSessao.encerrar()
    }

    // ---------------------------------------------------------------- persistência cifrada
    private suspend fun lerGuardada(): SessaoGuardada? = contexto.dadosSessao.data.first()[chave]
        ?.let { cofre.decifrar(it) }?.let { runCatching { json.decodeFromString(SessaoGuardada.serializer(), it) }.getOrNull() }

    private suspend fun guardar(s: SessaoGuardada) {
        contexto.dadosSessao.edit { it[chave] = cofre.cifrar(json.encodeToString(SessaoGuardada.serializer(), s)) }
    }

    private suspend fun limparGuardada() { contexto.dadosSessao.edit { it.remove(chave) } }

    private fun eFalhaDeRede(e: Throwable): Boolean = e is FirebaseNetworkException || e is java.io.IOException ||
        (e is ErroCentral && e.semRede) || e.cause?.let { eFalhaDeRede(it) } == true

    private fun PerfilGuardado.paraDominio() = Perfil(
        uid = uid, papel = Papel.canonico(papel) ?: Papel.CLIENTE, estado = EstadoConta.de(estado) ?: EstadoConta.INACTIVA,
        nome = nome, email = email, telefonePrincipal = telefone, telefoneAlternativo = telefoneAlt,
        residencia = Residencia(provincia, municipio, bairro), canalAlertas = CanalAlerta.de(canal), fotoUrl = foto,
        criadoPor = criadoPor, politicaAceite = politica, versao = versao,
    )
}

/** Registo de recursos ligados à sessão (listeners, filas) que têm de terminar ao sair/trocar de conta (secção 9). */
object OuvintesSessao {
    private val acoes = mutableListOf<() -> Unit>()
    @Synchronized fun registar(a: () -> Unit) { acoes += a }
    @Synchronized fun encerrar() { acoes.toList().forEach { runCatching(it) }; acoes.clear() }
}

/** Primeiro plano do processo (heartbeat e presença só em primeiro plano — secção 29). */
object PrimeiroPlano {
    val activo = MutableStateFlow(false)
}
