package ao.kupila.gestaonegocios.dados.rede

import ao.kupila.gestaonegocios.dados.remoto.ArranqueFirebase
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Ligação do Firestore ao ciclo de vida (bateria + quotas): em segundo plano a rede do SDK é desligada (as escutas deixam de
 * receber e de gastar; as escritas pendentes ficam na fila persistente do SDK e seguem sozinhas ao religar). Na pausa total do
 * modo poupança a rede fica desligada até "Ligar"/"Enviar agora".
 */
object RedeFirestore {
    private val ambito = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var desligar: Job? = null
    private var janela: Job? = null
    @Volatile private var primeiroPlano = true

    private fun fs() = if (ArranqueFirebase.disponivel) FirebaseFirestore.getInstance() else null

    private fun aplicar() {
        val f = fs() ?: return
        if (primeiroPlano && (!Economia.emPausa || Economia.emEntrada)) f.enableNetwork() else f.disableNetwork()
    }

    fun primeiroPlano() { primeiroPlano = true; desligar?.cancel(); aplicar() }

    /** Dá 20 s para terminar envios em curso antes de desligar. */
    fun segundoPlano() {
        primeiroPlano = false
        desligar?.cancel()
        desligar = ambito.launch { delay(20_000); if (!primeiroPlano) aplicar() }
    }

    fun pausar() { Economia.pausar(); aplicar() }
    fun ligar() { Economia.ligar(); janela?.cancel(); aplicar() }

    /** "Enviar agora": religa durante [ms] para enviar a fila e volta sozinho à pausa. */
    fun enviarAgora(ms: Long = 120_000L) {
        Economia.abrirJanela(ms); aplicar()
        janela?.cancel()
        janela = ambito.launch { delay(ms + 500); aplicar() }
    }
}
