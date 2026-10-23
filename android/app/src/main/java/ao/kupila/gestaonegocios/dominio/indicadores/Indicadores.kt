package ao.kupila.gestaonegocios.dominio.indicadores

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Crescimento face ao período anterior equivalente (secção 29). Corrige `monitoringGrowth` (HTML 11510),
 * que devolvia 100% quando a base era zero: sem base não existe percentagem válida.
 */
sealed interface Variacao {
    val absoluta: Long
    data class Percentual(override val absoluta: Long, val percentagem: BigDecimal) : Variacao
    /** Base zero: mostrar variação absoluta e "sem base de comparação". */
    data class SemBase(override val absoluta: Long) : Variacao
    data object SemDados : Variacao { override val absoluta = 0L }
}

object Crescimento {
    fun de(actual: Long?, anterior: Long?): Variacao {
        if (actual == null || anterior == null) return Variacao.SemDados
        val abs = actual - anterior
        if (anterior == 0L) return Variacao.SemBase(abs)
        val p = BigDecimal(abs).multiply(BigDecimal(100)).divide(BigDecimal(kotlin.math.abs(anterior)), 1, RoundingMode.HALF_UP)
        return Variacao.Percentual(abs, p)
    }

    fun texto(v: Variacao): String = when (v) {
        is Variacao.Percentual -> (if (v.percentagem.signum() > 0) "+" else "") + v.percentagem.toPlainString().replace('.', ',') + "%"
        is Variacao.SemBase -> (if (v.absoluta > 0) "+" else "") + v.absoluta + " · sem base anterior"
        Variacao.SemDados -> "Sem dados suficientes"
    }
}

/** Janelas do gráfico de monitoramento (7/14/30 dias). */
enum class JanelaMonitoramento(val dias: Int) { SETE(7), CATORZE(14), TRINTA(30) }

/**
 * Presença ≠ assiduidade (secção 29): heartbeat de primeiro plano com TTL. "Em uso" = primeiro plano e visto dentro da janela.
 */
object Presenca {
    /** `PRESENCE_HEARTBEAT_MS` / `PRESENCE_ONLINE_WINDOW_MS` (HTML 11454-11455). */
    const val HEARTBEAT_MS = 90_000L
    const val JANELA_ONLINE_MS = 210_000L
    fun online(ultimoSinalMs: Long, agoraMs: Long, estado: String) = agoraMs - ultimoSinalMs <= JANELA_ONLINE_MS && estado != "OFFLINE"
}
