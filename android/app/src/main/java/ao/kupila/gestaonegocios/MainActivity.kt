package ao.kupila.gestaonegocios

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ao.kupila.gestaonegocios.dominio.aparencia.IdentidadeVisual
import ao.kupila.gestaonegocios.ui.raiz.Raiz
import ao.kupila.gestaonegocios.ui.tema.TemaKupila
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
/** FragmentActivity: necessária ao diálogo da impressão digital (BiometricPrompt), como no PDV Digital. */
class MainActivity : androidx.fragment.app.FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            TemaKupila(IdentidadeVisual()) { Raiz() }
        }
    }

    /** Cada toque abre a janela de leitura da poupança inteligente (leituras automáticas só com interacção recente). */
    override fun onUserInteraction() { super.onUserInteraction(); ao.kupila.gestaonegocios.dados.rede.Economia.tocou() }
}
