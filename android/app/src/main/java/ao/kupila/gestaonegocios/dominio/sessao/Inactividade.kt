package ao.kupila.gestaonegocios.dominio.sessao

/** Regras de inactividade da origem (`getSessionInactivityMs`/`getSessionWarningMs`, HTML 10732-10739). */
object Inactividade {
    fun totalMs(minutos: Int) = maxOf(1, minutos) * 60_000L
    /** Aviso nos últimos 20% do tempo, entre 15 s e 60 s. */
    fun avisoMs(minutos: Int) = (totalMs(minutos) * 0.20).toLong().coerceIn(15_000L, 60_000L)

    sealed interface Fase { data object Activa : Fase; data class Aviso(val restanteMs: Long) : Fase; data object Esgotada : Fase }

    fun fase(decorridoMs: Long, minutos: Int): Fase {
        val restante = totalMs(minutos) - decorridoMs
        return when {
            restante <= 0 -> Fase.Esgotada
            restante <= avisoMs(minutos) -> Fase.Aviso(restante)
            else -> Fase.Activa
        }
    }

    fun contagem(ms: Long): String { val s = ((ms + 999) / 1000).coerceAtLeast(0); return "%02d:%02d".format(s / 60, s % 60) }
}
