package ao.kupila.gestaonegocios.ui.entrada

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ao.kupila.gestaonegocios.dados.local.Conteudos
import ao.kupila.gestaonegocios.dados.local.Geografia
import ao.kupila.gestaonegocios.dados.media.Imagens
import ao.kupila.gestaonegocios.dados.remoto.MensagensErro
import ao.kupila.gestaonegocios.dados.sessao.DadosCadastro
import ao.kupila.gestaonegocios.dados.sessao.EstadoSessao
import ao.kupila.gestaonegocios.dados.sessao.SessaoRepositorio
import ao.kupila.gestaonegocios.dominio.perfis.CanalAlerta
import ao.kupila.gestaonegocios.dominio.perfis.Papel
import ao.kupila.gestaonegocios.dominio.perfis.ValidacaoContacto
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class FormCadastro(
    val papel: Papel = Papel.CLIENTE,
    val nome: String = "",
    val email: String = "",
    val telefone: String = "",
    val telefoneAlt: String = "",
    val provincia: String = "Luanda",
    val municipio: String = "",
    val bairro: String = "",
    val canal: CanalAlerta = CanalAlerta.AUTOMATICO,
    val senha: String = "",
    val confirmar: String = "",
    val politicasLidas: Boolean = false,
    val politicasAceites: Boolean = false,
    val fotoCaminho: String? = null,
)

data class EstadoCadastro(val form: FormCadastro = FormCadastro(), val erros: Map<String, String> = emptyMap(), val aEnviar: Boolean = false,
    val erroGeral: String? = null, val aProcessarFoto: Boolean = false, val completar: Boolean = false)

/**
 * Cadastro público (secção 8; `handleRegister`, HTML 11199). Os dados (excepto palavras-passe) sobrevivem a rotação e
 * recriação do processo; a foto fica num ficheiro privado temporário. Modo "completar" para contas Auth sem perfil.
 */
@HiltViewModel
class CadastroViewModel @Inject constructor(
    private val sessao: SessaoRepositorio,
    private val imagens: Imagens,
    val geografia: Geografia,
    val conteudos: Conteudos,
    @param:ApplicationContext private val contexto: Context,
    private val guardado: SavedStateHandle,
) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoCadastro(form = restaurar()))
    val estado: StateFlow<EstadoCadastro> = _estado.asStateFlow()

    init {
        (sessao.estado.value as? EstadoSessao.PerfilIncompleto)?.let { p ->
            _estado.update { it.copy(completar = true, form = it.form.copy(email = p.email)) }
        }
    }

    /** Perda zero: SavedStateHandle (rodar/memória) e, por baixo, o rascunho persistente (app fechada, telemóvel reiniciado). */
    private fun r(k: String): String? = guardado[k] ?: ao.kupila.gestaonegocios.dados.rascunhos.RascunhoAuto.ler(contexto, "cadastro.$k")
    private fun restaurar() = FormCadastro(
        papel = Papel.canonico(r("papel")) ?: Papel.CLIENTE, nome = r("nome") ?: "", email = r("email") ?: "",
        telefone = r("telefone") ?: "", telefoneAlt = r("telefoneAlt") ?: "", provincia = r("provincia") ?: "Luanda",
        municipio = r("municipio") ?: "", bairro = r("bairro") ?: "", canal = CanalAlerta.de(r("canal")),
        politicasLidas = (guardado["lidas"] ?: r("lidas")?.toBoolean()) ?: false, politicasAceites = (guardado["aceites"] ?: r("aceites")?.toBoolean()) ?: false,
        fotoCaminho = r("foto")?.takeIf { File(it).exists() },
    )

    private fun mudar(f: (FormCadastro) -> FormCadastro) {
        _estado.update { it.copy(form = f(it.form), erroGeral = null) }
        val x = _estado.value.form
        guardado["papel"] = x.papel.codigo; guardado["nome"] = x.nome; guardado["email"] = x.email; guardado["telefone"] = x.telefone
        guardado["telefoneAlt"] = x.telefoneAlt; guardado["provincia"] = x.provincia; guardado["municipio"] = x.municipio; guardado["bairro"] = x.bairro
        guardado["canal"] = x.canal.codigo; guardado["lidas"] = x.politicasLidas; guardado["aceites"] = x.politicasAceites; guardado["foto"] = x.fotoCaminho
        mapOf("papel" to x.papel.codigo, "nome" to x.nome, "email" to x.email, "telefone" to x.telefone, "telefoneAlt" to x.telefoneAlt, "provincia" to x.provincia,
            "municipio" to x.municipio, "bairro" to x.bairro, "canal" to x.canal.codigo, "lidas" to x.politicasLidas.toString(), "aceites" to x.politicasAceites.toString(),
            "foto" to x.fotoCaminho.orEmpty()).forEach { (k, v) -> ao.kupila.gestaonegocios.dados.rascunhos.RascunhoAuto.escrever(contexto, "cadastro.$k", v) }
    }

    fun papel(p: Papel) = mudar { it.copy(papel = p) }
    fun nome(v: String) = mudar { it.copy(nome = v) }
    fun email(v: String) = mudar { it.copy(email = v) }
    fun telefone(v: String) = mudar { it.copy(telefone = v.filter(Char::isDigit).take(9)) }
    fun telefoneAlt(v: String) = mudar { it.copy(telefoneAlt = v.filter(Char::isDigit).take(9)) }
    fun provincia(v: String) = mudar { it.copy(provincia = v, municipio = "") }
    fun municipio(v: String) = mudar { it.copy(municipio = v) }
    fun bairro(v: String) = mudar { it.copy(bairro = v) }
    fun canal(v: CanalAlerta) = mudar { it.copy(canal = v) }
    fun senha(v: String) = _estado.update { it.copy(form = it.form.copy(senha = v)) }
    fun confirmar(v: String) = _estado.update { it.copy(form = it.form.copy(confirmar = v)) }
    fun leituraConcluida() = mudar { it.copy(politicasLidas = true) }
    /** Aceite imediato (resumo à vista); `politicasLidas` continua a registar, com verdade, se a leitura completa foi feita. */
    fun aceitar(v: Boolean) = mudar { it.copy(politicasAceites = v) }

    fun foto(uri: Uri) = viewModelScope.launch {
        _estado.update { it.copy(aProcessarFoto = true) }
        runCatching {
            val bytes = imagens.fotoPerfil(uri)
            File(contexto.filesDir, "rascunhos/cadastro").apply { mkdirs() }.let { d -> File(d, "foto-perfil.jpg").apply { writeBytes(bytes) } }
        }.onSuccess { f -> mudar { it.copy(fotoCaminho = f.absolutePath) } }
            .onFailure { e -> _estado.update { it.copy(erroGeral = e.message ?: "Não foi possível processar a foto.") } }
        _estado.update { it.copy(aProcessarFoto = false) }
    }

    private fun validar(f: FormCadastro, completar: Boolean): Map<String, String> = buildMap {
        if (f.nome.trim().length < 3) put("nome", "Informe o nome completo.")
        if (!ValidacaoContacto.emailValido(f.email)) put("email", "Informe um e-mail válido.")
        if (!ValidacaoContacto.telefoneValido(f.telefone)) put("telefone", "Telefone principal com 9 dígitos (ex.: 923000000).")
        if (f.papel == Papel.PROPRIETARIO) {
            if (f.fotoCaminho == null) put("foto", "A fotografia do Proprietário é obrigatória.")
            if (!geografia.valido(f.provincia, f.municipio)) put("municipio", "Seleccione o município.")
            if (f.bairro.isBlank()) put("bairro", "Informe o bairro.")
            if (f.telefoneAlt.isNotEmpty() && !ValidacaoContacto.telefoneValido(f.telefoneAlt)) put("telefoneAlt", "O telefone alternativo deve ter 9 dígitos.")
        }
        if (!completar) {
            if (f.senha.length < 6) put("senha", "Escolha uma palavra-passe com pelo menos 6 caracteres.")
        }
        if (!f.politicasAceites) put("politicas", "Marque a caixa de aceite das políticas.")
    }

    fun enviar() = viewModelScope.launch {
        val e = _estado.value
        if (e.aEnviar) return@launch
        val erros = validar(e.form, e.completar)
        _estado.update { it.copy(erros = erros) }
        if (erros.isNotEmpty()) return@launch
        _estado.update { it.copy(aEnviar = true, erroGeral = null) }
        val f = e.form
        val dono = f.papel == Papel.PROPRIETARIO
        val d = DadosCadastro(f.papel, f.nome.trim(), f.email.trim(), f.telefone, if (dono) f.telefoneAlt else "",
            if (dono) f.provincia else "", if (dono) f.municipio else "", if (dono) f.bairro.trim() else "", if (dono) f.canal else CanalAlerta.AUTOMATICO,
            conteudos.versaoPoliticas, f.politicasLidas, if (dono) f.fotoCaminho?.let { File(it).takeIf(File::exists)?.readBytes() } else null)
        val r = if (e.completar) sessao.concluirCadastro(d) else sessao.registar(d, f.senha)
        r.onSuccess { f.fotoCaminho?.let { File(it).delete() }; guardado.keys().forEach { guardado.remove<Any>(it) }; ao.kupila.gestaonegocios.dados.rascunhos.RascunhoAuto.limpar(contexto, "cadastro.") }
        _estado.update { it.copy(aEnviar = false, erroGeral = r.exceptionOrNull()?.let(MensagensErro::de)) }
    }
}
