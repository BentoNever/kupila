package ao.kupila.gestaonegocios.dominio.notificacoes

import ao.kupila.gestaonegocios.dominio.perfis.Papel
import java.time.Duration
import java.time.Instant

/** Filtros do sino (`setNotificationFilter`, HTML 7610-7615). */
enum class CategoriaNotificacao(val codigo: String, val rotulo: String) {
    RESERVA("RESERVATION", "Reservas"), MONETIZACAO("MONETIZATION", "Monetização"), INTERESSE("INTEREST", "Interesse"),
    ADMINISTRACAO("ADMIN", "Administração"), NOVIDADES("MARKETING", "Novidades");
    companion object { fun de(c: String?) = entries.firstOrNull { it.codigo == c } ?: ADMINISTRACAO }
}

enum class Severidade { INFO, AVISO, CRITICO }

/** Acção contextual ao tocar (`openNotificationAction`, 14522). */
enum class AccaoNotificacao(val codigo: String) {
    ABRIR_RESERVA("OPEN_RESERVATION"), ABRIR_ANUNCIO("OPEN_LISTING"), ABRIR_AUTORIZACAO("OPEN_AUTHORIZATION"),
    ABRIR_REEMBOLSO("OPEN_REFUND"), ABRIR_PENDENCIAS("OPEN_PENDING"), EXPLORAR("MARKETING_EXPLORE"),
    MINHAS_RESERVAS("MARKETING_RESERVATIONS"), MEUS_ANUNCIOS("MARKETING_LISTINGS"), NENHUMA("");
    companion object { fun de(c: String?) = entries.firstOrNull { it.codigo == c } ?: NENHUMA }
}

/** Notificação persistida pelo servidor; a chave idempotente impede duplicados (`notificationEventKey`). */
data class Notificacao(
    val id: String,
    val chaveEvento: String,
    val titulo: String,
    val mensagem: String,
    val categoria: CategoriaNotificacao,
    val severidade: Severidade,
    val accao: AccaoNotificacao,
    val entidadeId: String?,
    val criadaEm: Instant,
    val lida: Boolean,
    val cta: String? = null,
)

/**
 * Alerta de interesse ao Proprietário (secção 30): N clientes DISTINTOS numa janela, com repetição após cooldown.
 * Avaliado no servidor; esta regra pura é partilhada para testes e para explicar o comportamento.
 */
object RegraInteresse {
    fun deveAlertar(visitasClientes: List<Pair<String, Instant>>, agora: Instant, limiar: Int, janelaHoras: Int,
                    ultimoAlerta: Instant?, cooldownHoras: Int): Boolean {
        val inicio = agora.minus(Duration.ofHours(janelaHoras.toLong()))
        val distintos = visitasClientes.filter { it.second.isAfter(inicio) && !it.second.isAfter(agora) }.map { it.first }.toSet().size
        if (distintos < limiar) return false
        return ultimoAlerta == null || !ultimoAlerta.plus(Duration.ofHours(cooldownHoras.toLong())).isAfter(agora)
    }
}

/** Campanhas de marketing (`createMarketingCampaign`…, 14578-14609). */
enum class PublicoCampanha(val codigo: String, val rotulo: String) { TODOS("ALL", "Todos"), CLIENTES("CLIENTE", "Clientes"), PROPRIETARIOS("PROPRIETARIO", "Proprietários") }
enum class PrioridadeCampanha(val codigo: String) { NORMAL("NORMAL"), ALTA("HIGH") }
enum class AccaoCampanha(val codigo: String, val rotulo: String, val accao: AccaoNotificacao) {
    NENHUMA("NONE", "Sem ação", AccaoNotificacao.NENHUMA), EXPLORAR("EXPLORE", "Explorar produtos", AccaoNotificacao.EXPLORAR),
    RESERVAS("RESERVATIONS", "Minhas reservas", AccaoNotificacao.MINHAS_RESERVAS), ANUNCIOS("LISTINGS", "Meus anúncios", AccaoNotificacao.MEUS_ANUNCIOS)
}
enum class EstadoCampanha(val rotulo: String) { ACTIVA("Activa"), AGENDADA("Agendada"), TERMINADA("Terminada"), PAUSADA("Pausada") }

data class Campanha(
    val id: String, val titulo: String, val mensagem: String, val publico: PublicoCampanha, val prioridade: PrioridadeCampanha,
    val accao: AccaoCampanha, val cta: String, val inicio: Instant, val fim: Instant, val desactivada: Boolean, val criadaEm: Instant,
)

object RegrasCampanha {
    fun estado(c: Campanha, agora: Instant) = when {
        c.desactivada -> EstadoCampanha.PAUSADA
        agora.isBefore(c.inicio) -> EstadoCampanha.AGENDADA
        agora.isAfter(c.fim) -> EstadoCampanha.TERMINADA
        else -> EstadoCampanha.ACTIVA
    }

    /** Administradores não recebem campanhas (`deliverActiveMarketingCampaigns`). */
    fun destinada(c: Campanha, papel: Papel) = papel != Papel.ADMINISTRADOR &&
        (c.publico == PublicoCampanha.TODOS || c.publico.codigo == papel.codigo)

    fun validar(titulo: String, mensagem: String, inicio: Instant, fim: Instant): List<String> = buildList {
        if (titulo.trim().length < 3 || mensagem.trim().length < 5) add("Informe um título e uma mensagem de marketing válidos.")
        if (titulo.length > 70) add("O título tem no máximo 70 caracteres.")
        if (mensagem.length > 320) add("A mensagem tem no máximo 320 caracteres.")
        if (!fim.isAfter(inicio)) add("O fim da campanha deve ocorrer depois do início.")
    }

    /** Chave de entrega única por utilizador/campanha. */
    fun chaveEntrega(campanhaId: String, uid: String) = "MKT:$campanhaId:$uid"
}
