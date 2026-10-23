package ao.kupila.gestaonegocios.ui.ajuda

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ao.kupila.gestaonegocios.BuildConfig
import ao.kupila.gestaonegocios.R
import ao.kupila.gestaonegocios.dados.local.PerguntaFaq
import ao.kupila.gestaonegocios.dados.local.SeccaoTexto
import ao.kupila.gestaonegocios.ui.comum.LinhaInfo
import ao.kupila.gestaonegocios.ui.comum.Logotipo
import ao.kupila.gestaonegocios.ui.comum.Seccao
import java.time.Year

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcraComTopo(titulo: String, aoVoltar: () -> Unit, conteudo: @Composable (PaddingValues) -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text(titulo) }, navigationIcon = { IconButton(onClick = aoVoltar) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } }) }) { conteudo(it) }
}

/** FAQ (`modal-kupila-faq`, HTML 8212-8228): expansão individual e "Ver mais/menos" (expandir/recolher tudo). */
@Composable
fun FaqEcra(perguntas: List<PerguntaFaq>, aoVoltar: () -> Unit, aoPoliticas: () -> Unit) {
    var abertas by rememberSaveable { mutableStateOf(setOf<Int>()) }
    EcraComTopo("Perguntas Frequentes", aoVoltar) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                val todas = abertas.size == perguntas.size && perguntas.isNotEmpty()
                TextButton(onClick = { abertas = if (todas) emptySet() else perguntas.indices.toSet() }) { Text(if (todas) "Ver menos" else "Ver mais") }
            }
            itemsIndexed(perguntas) { i, p ->
                Card(Modifier.fillMaxWidth().clickable(onClickLabel = if (i in abertas) "Recolher" else "Expandir") { abertas = if (i in abertas) abertas - i else abertas + i }) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(p.pergunta, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Icon(if (i in abertas) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
                        }
                        AnimatedVisibility(i in abertas) { Text(p.resposta, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
            item { TextButton(onClick = aoPoliticas) { Text("Abrir Manual e Políticas do Kupila-Gestão de negócios") } }
        }
    }
}

/** Reserva Protegida (`modal-reservation-protection`). */
@Composable
fun ProtecaoEcra(seccoes: List<SeccaoTexto>, prazoReservaMinutos: Long, aoVoltar: () -> Unit) {
    EcraComTopo("Reserva Protegida", aoVoltar) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            seccoes.forEach { s -> Seccao(s.titulo) { s.paragrafos.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) } } }
            Seccao("Prazo configurado") { Text(prazo(prazoReservaMinutos) + ", contado a partir da confirmação oficial do pagamento.", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

/** `formatReservationDuration` (HTML 12804). */
fun prazo(min: Long): String = when {
    min % 1440L == 0L -> "${min / 1440} dia(s)"
    min % 60L == 0L -> "${min / 60} hora(s)"
    else -> "$min minuto(s)"
}

/** Sobre: nome oficial, versão/build efectiva, propósito e crédito (`updateKupilaAboutMetadata`, 9083). */
@Composable
fun SobreEcra(aoVoltar: () -> Unit) {
    EcraComTopo("Sobre", aoVoltar) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Logotipo(72)
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.app_slogan), style = MaterialTheme.typography.bodyMedium)
            Seccao("Versão") {
                LinhaInfo("Versão", BuildConfig.VERSION_NAME)
                LinhaInfo("Build", BuildConfig.VERSION_CODE.toString())
                LinhaInfo("Identificador", BuildConfig.APPLICATION_ID)
            }
            Seccao("Propósito") {
                Text("Marketplace angolano de anúncio, descoberta, compra, aluguer, reserva e intermediação de bens, com reserva protegida por sinal " +
                    "validado, vistoria com OTP e reembolso conforme as regras. Não é parecer jurídico nem autorização regulatória.", style = MaterialTheme.typography.bodyMedium)
            }
            Text("${stringResource(R.string.credito_desenvolvimento)} · ${Year.now().value}", style = MaterialTheme.typography.bodySmall)
        }
    }
}
