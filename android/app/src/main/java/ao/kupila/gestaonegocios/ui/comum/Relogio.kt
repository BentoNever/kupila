package ao.kupila.gestaonegocios.ui.comum

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/**
 * UM relógio partilhado para todas as contagens decrescentes (reservas, recibo, painel): em vez de um temporizador por cartão,
 * há um só, alinhado ao segundo, que pára sozinho quando nenhum ecrã visível o usa (app em fundo = zero acordares de CPU).
 */
object Relogio {
    private val ambito = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val segundo: StateFlow<Long> = flow {
        while (true) { val t = System.currentTimeMillis(); emit(t); delay(1000 - t % 1000) }
    }.stateIn(ambito, SharingStarted.WhileSubscribed(0), System.currentTimeMillis())
}

/** Hora actual a cada segundo, só enquanto o ecrã está visível. */
@Composable
fun rememberAgora(): Long {
    val agora by Relogio.segundo.collectAsStateWithLifecycle()
    return agora
}
