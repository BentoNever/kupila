package ao.kupila.gestaonegocios.dominio.perfis

import java.text.Normalizer

/**
 * Papéis da origem (`ROLE`, HTML 9168). O Administrador é guardado como "GESTOR" por compatibilidade
 * com os dados existentes; aliases aceites na leitura: ADMINISTRADOR, ADMIN (`canonicalRole`, 9170).
 * O papel efectivo vem SEMPRE do servidor (custom claim / perfil validado), nunca de um campo editável.
 */
enum class Papel(val codigo: String, val rotulo: String) {
    CLIENTE("CLIENTE", "Cliente"),
    PROPRIETARIO("PROPRIETARIO", "Proprietário"),
    ADMINISTRADOR("GESTOR", "Administrador");

    companion object {
        fun canonico(valor: String?): Papel? {
            val n = Normalizer.normalize(valor.orEmpty().trim(), Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "").uppercase()
            return when (n) {
                "GESTOR", "ADMINISTRADOR", "ADMIN" -> ADMINISTRADOR
                "PROPRIETARIO", "OWNER" -> PROPRIETARIO
                "CLIENTE", "CLIENT" -> CLIENTE
                else -> null
            }
        }
        /** Papéis que o cadastro público pode criar (secção 7: não existe criação pública de Administrador). */
        val publicos = listOf(CLIENTE, PROPRIETARIO)
    }
}

/** Estados de conta (`managed-user-status`, HTML 8046). */
enum class EstadoConta(val codigo: String, val rotulo: String) {
    ACTIVA("ACTIVE", "Activo"), INACTIVA("INACTIVE", "Inactivo"),
    SUSPENSA("SUSPENDED", "Suspenso"), ARQUIVADA("ARCHIVED", "Arquivado");
    companion object { fun de(c: String?) = entries.firstOrNull { it.codigo == c } }
}

/** Canal de alertas (`register-alert-channel`, `owner-alert-channel`). */
enum class CanalAlerta(val codigo: String, val rotulo: String) {
    AUTOMATICO("AUTO", "Automático (e-mail preferencial)"), EMAIL("EMAIL", "E-mail"),
    SMS("SMS", "SMS"), AMBOS("BOTH", "E-mail + SMS");
    companion object { fun de(c: String?) = entries.firstOrNull { it.codigo == c } ?: AUTOMATICO }
}

data class Residencia(val provincia: String = "", val municipio: String = "", val bairro: String = "")

/** Perfil do utilizador tal como o servidor o devolve ao próprio titular. */
data class Perfil(
    val uid: String,
    val papel: Papel,
    val estado: EstadoConta,
    val nome: String,
    val email: String,
    val telefonePrincipal: String = "",
    val telefoneAlternativo: String = "",
    val residencia: Residencia = Residencia(),
    val canalAlertas: CanalAlerta = CanalAlerta.AUTOMATICO,
    val fotoUrl: String? = null,
    /** uid do Administrador que criou a conta (null = cadastro público). Regra de edição (secção 26). */
    val criadoPor: String? = null,
    val politicaAceite: String? = null,
    /** Versão do perfil no servidor (validação antes de gravar — secção 12). */
    val versao: Long = 1,
)

/** Regras de permissão espelhadas das funções da origem (10149-10154). O servidor revalida tudo. */
object Permissoes {
    fun podeAnunciar(p: Perfil?) = p != null && p.estado == EstadoConta.ACTIVA && p.papel != Papel.CLIENTE
    fun podeGerirAnuncio(p: Perfil?, donoId: String) = p != null && p.estado == EstadoConta.ACTIVA &&
        (p.papel == Papel.ADMINISTRADOR || (p.papel == Papel.PROPRIETARIO && p.uid == donoId))
    fun podeValidarOtp(p: Perfil?, donoId: String) = podeGerirAnuncio(p, donoId)
    fun podeReservar(p: Perfil?) = p != null && p.estado == EstadoConta.ACTIVA
    fun eAdministrador(p: Perfil?) = p?.papel == Papel.ADMINISTRADOR && p.estado == EstadoConta.ACTIVA
    fun podeVerAutorizacoes(p: Perfil?) = p != null && p.papel != Papel.CLIENTE

    /**
     * Edição administrativa de dados pessoais: só se a conta foi criada por ESTE Administrador
     * (`isUserCreatedByCurrentAdmin`, 14737). Contas públicas: só mudança de estado.
     */
    fun adminPodeEditarDadosPessoais(admin: Perfil?, alvo: Perfil) = eAdministrador(admin) && alvo.criadoPor == admin!!.uid
    fun adminPodeMudarEstado(admin: Perfil?, alvo: Perfil) = eAdministrador(admin) && alvo.uid != admin!!.uid

    /** Perfil do Proprietário completo para anunciar (`isOwnerProfileComplete`, 12598). */
    fun perfilProprietarioCompleto(p: Perfil) = p.fotoUrl != null && p.nome.isNotBlank() && ValidacaoContacto.emailValido(p.email) &&
        p.residencia.provincia.isNotBlank() && p.residencia.municipio.isNotBlank() && p.residencia.bairro.isNotBlank() &&
        ValidacaoContacto.telefoneValido(p.telefonePrincipal)
}

object ValidacaoContacto {
    private val email = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$")
    fun emailValido(v: String) = email.matches(v.trim())

    /** Telefone angolano: 9 dígitos começados por 9; aceita +244/00244 sem duplicar o prefixo. */
    fun normalizarTelefone(v: String): String {
        var d = v.filter(Char::isDigit)
        if (d.startsWith("00244")) d = d.drop(5) else if (d.startsWith("244") && d.length == 12) d = d.drop(3)
        return d
    }
    fun telefoneValido(v: String) = normalizarTelefone(v).let { it.length == 9 && it.startsWith("9") }
    fun telefoneInternacional(v: String) = "+244 " + normalizarTelefone(v).chunked(3).joinToString(" ")
}
