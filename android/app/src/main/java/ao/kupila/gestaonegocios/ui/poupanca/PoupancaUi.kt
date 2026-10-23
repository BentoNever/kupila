package ao.kupila.gestaonegocios.ui.poupanca

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ao.kupila.gestaonegocios.dados.rede.Economia
import ao.kupila.gestaonegocios.dados.rede.RedeFirestore
import ao.kupila.gestaonegocios.ui.ajuda.EcraComTopo
import ao.kupila.gestaonegocios.ui.comum.Aviso
import ao.kupila.gestaonegocios.ui.comum.BotaoPrincipal
import ao.kupila.gestaonegocios.ui.comum.BotaoSecundario
import ao.kupila.gestaonegocios.ui.comum.Seccao
import ao.kupila.gestaonegocios.ui.comum.TipoAviso
import ao.kupila.gestaonegocios.ui.entrada.formatarData

/** Modo poupança (como no PDV Digital): contadores reais, pausa total e "Enviar agora". */
@Composable
fun PoupancaEcra(aoVoltar: () -> Unit) {
    val pausada by Economia.pausada.collectAsStateWithLifecycle()
    val janela by Economia.janela.collectAsStateWithLifecycle()
    val desde by Economia.desde.collectAsStateWithLifecycle()
    val lidos by Economia.lidos.collectAsStateWithLifecycle()
    val escritos by Economia.escritos.collectAsStateWithLifecycle()
    val evitados by Economia.evitados.collectAsStateWithLifecycle()
    val delta by Economia.poupadosDelta.collectAsStateWithLifecycle()
    EcraComTopo("Modo poupança", aoVoltar) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Aviso("A poupança inteligente está sempre ligada: nada fica a escutar em segundo plano, o app só actualiza quando está a usá-lo, " +
                "e só pede ao servidor o que mudou desde a última vez. Tudo o que já viu continua disponível sem rede.", TipoAviso.INFO, titulo = "Sempre activa")
            Seccao("Pausa total", subtitulo = "Zero dados: o app mostra o guardado e põe as suas acções na fila até religar.") {
                when {
                    janela -> Aviso("A enviar o que estava guardado; volta sozinho à pausa dentro de 2 minutos.", TipoAviso.INFO)
                    pausada -> Aviso("Em pausa" + (desde?.let { " desde ${formatarData(it)}" } ?: "") + ". Reservas e pagamentos precisam de ligação.", TipoAviso.AVISO)
                }
                if (pausada) {
                    BotaoPrincipal("Ligar", RedeFirestore::ligar, icone = Icons.Filled.PlayCircle)
                    BotaoSecundario("Enviar agora (2 min)", { RedeFirestore.enviarAgora() }, enabled = !janela, icone = Icons.Filled.CloudUpload)
                } else BotaoSecundario("Pausar a ligação", RedeFirestore::pausar, icone = Icons.Filled.PauseCircle)
            }
            Seccao("Neste telemóvel") {
                Contador("Documentos lidos", lidos)
                Contador("Documentos escritos", escritos)
                Contador("Pedidos evitados", evitados)
                Contador("Releituras poupadas pela sincronização por diferenças", delta)
                val total = lidos + delta + evitados
                if (total > 0) Text("Poupança estimada de leituras: ${(100.0 * (delta + evitados) / total).let { "%.1f".format(it) }}%",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun Contador(rotulo: String, v: Long) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(rotulo, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text("%,d".format(v).replace(',', ' '), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}
