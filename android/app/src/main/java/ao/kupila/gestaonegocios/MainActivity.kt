package ao.kupila.gestaonegocios

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ao.kupila.gestaonegocios.dominio.aparencia.IdentidadeVisual
import ao.kupila.gestaonegocios.ui.raiz.Raiz
import ao.kupila.gestaonegocios.ui.tema.TemaKupila
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            TemaKupila(IdentidadeVisual()) { Raiz() }
        }
    }
}
