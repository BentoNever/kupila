package ao.kupila.gestaonegocios.ui.checkout

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ao.kupila.gestaonegocios.dados.remoto.Origem
import ao.kupila.gestaonegocios.dominio.financeiro.Dinheiro
import ao.kupila.gestaonegocios.dominio.financeiro.FormatoAoa
import ao.kupila.gestaonegocios.ui.ajuda.EcraComTopo
import ao.kupila.gestaonegocios.ui.comum.Aviso
import ao.kupila.gestaonegocios.ui.comum.BotaoPrincipal
import ao.kupila.gestaonegocios.ui.comum.BotaoSecundario
import ao.kupila.gestaonegocios.ui.comum.Campo
import ao.kupila.gestaonegocios.ui.comum.Carregando
import ao.kupila.gestaonegocios.ui.comum.EstadoVazio
import ao.kupila.gestaonegocios.ui.comum.LinhaInfo
import ao.kupila.gestaonegocios.ui.comum.Seccao
import ao.kupila.gestaonegocios.ui.comum.TipoAviso
import ao.kupila.gestaonegocios.ui.entrada.formatarData

@Composable
fun CheckoutEcra(vm: CheckoutViewModel, aoVoltar: () -> Unit, aoConcluido: (tipo: String, reservaId: String?, listingId: String) -> Unit) {
    val e by vm.estado.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val escolher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(vm::comprovativo) }
    val valor = (e.intent["amountText"] as? String)?.toBigDecimalOrNull()?.let { FormatoAoa.formatar(Dinheiro.de(it)) } ?: ""
    EcraComTopo(if (e.tipo == "RESERVATION") "Pagar reserva" else "Pagar publicação", aoVoltar) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (e.fase) {
                FaseCheckout.CARREGAR -> Carregando("A abrir o pagamento…")
                FaseCheckout.INEXISTENTE -> EstadoVazio("Pagamento não encontrado", "Volte ao produto e tente de novo.", accao = "Voltar" to aoVoltar)
                FaseCheckout.EXPIRADO -> EstadoVazio("A referência expirou", "Por segurança cada referência vale 30 minutos. Volte e gere uma nova; nada foi cobrado em duplicado.", accao = "Voltar" to aoVoltar)
                FaseCheckout.CONCLUIDO -> {
                    Icon(Icons.Filled.CheckCircle, null, Modifier.size(72.dp).align(Alignment.CenterHorizontally), tint = MaterialTheme.colorScheme.primary)
                    Text(if (e.tipo == "RESERVATION") "Reserva confirmada" else "Pagamento confirmado: anúncio em publicação",
                        style = MaterialTheme.typography.headlineSmall, modifier = Modifier.align(Alignment.CenterHorizontally))
                    BotaoPrincipal(if (e.tipo == "RESERVATION") "Ver a reserva e o código OTP" else "Ver o anúncio",
                        { aoConcluido(e.tipo, e.intent["reservationId"] as? String, e.intent["listingId"] as? String ?: "") })
                }
                FaseCheckout.VERIFICACAO -> {
                    Icon(Icons.Filled.HourglassTop, null, Modifier.size(64.dp).align(Alignment.CenterHorizontally), tint = MaterialTheme.colorScheme.primary)
                    Text("Comprovativo recebido", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.align(Alignment.CenterHorizontally))
                    Aviso(if (e.intent["status"] == "RECEIPT_MATCHED") "A referência do comprovativo coincide. Falta só a confirmação do recebimento pela equipa Kupila; esta página actualiza-se sozinha."
                        else "A equipa Kupila está a validar o comprovativo. Esta página actualiza-se sozinha; pode sair, avisamos quando estiver confirmado.")
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    BotaoSecundario("Continuar a explorar", aoVoltar)
                }
                FaseCheckout.PAGAR, FaseCheckout.REJEITADO -> {
                    if (e.fase == FaseCheckout.REJEITADO) Aviso((e.intent["rejectionReason"] as? String).orEmpty().ifBlank { "O comprovativo não foi aceite." } +
                        " Pode enviar outro comprovativo; não precisa de pagar de novo se já pagou.", TipoAviso.ERRO, titulo = "Comprovativo não aceite")
                    Seccao("1. Pague", subtitulo = (e.intent["listingTitle"] as? String).orEmpty()) {
                        Text(valor, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(e.metodo == "REF", { vm.metodo("REF") }, { Text("Referência") })
                            FilterChip(e.metodo == "EXPRESS", { vm.metodo("EXPRESS") }, { Text("Multicaixa Express") })
                        }
                        val entidade = (e.intent["entity"] as? String).orEmpty()
                        if (entidade.isNotBlank()) LinhaInfo("Entidade", entidade)
                        else Aviso("A entidade de pagamento está em configuração. Use os dados indicados pela equipa Kupila; a referência abaixo identifica o seu pagamento.", TipoAviso.AVISO)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Referência", style = MaterialTheme.typography.labelMedium)
                                SelectionContainer { Text(e.referencia.chunked(3).joinToString(" "), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold) }
                            }
                            IconButton(onClick = { copiar(ctx, e.referencia) }) { Icon(Icons.Filled.ContentCopy, "Copiar referência") }
                        }
                        Origem.ms(e.intent["expiresAt"])?.let { Text("Válida até ${formatarData(it)}", style = MaterialTheme.typography.bodySmall) }
                        if (e.metodo == "EXPRESS") Campo(e.telefoneExpress, vm::telefone, "Número Multicaixa Express usado", teclado = KeyboardType.Phone)
                    }
                    Seccao("2. Mostre o comprovativo", subtitulo = "Uma foto ou captura de ecrã. Lemos a referência automaticamente.") {
                        when {
                            e.aLer -> { Text("A ler o comprovativo…"); LinearProgressIndicator(Modifier.fillMaxWidth()) }
                            e.aEnviar -> { Text("A enviar…"); LinearProgressIndicator(Modifier.fillMaxWidth()) }
                            e.comprovativo != null && !e.confere -> {
                                Aviso(if (e.referenciaLida.isBlank()) "Não encontrámos a referência na imagem." else "Lemos a referência ${e.referenciaLida}, diferente da esperada (${e.referencia}).",
                                    TipoAviso.AVISO, titulo = "Validação manual")
                                BotaoPrincipal("Enviar mesmo assim para validação", { vm.enviar(manual = true) })
                                BotaoSecundario("Escolher outra imagem", { vm.descartar(); escolher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
                            }
                            else -> BotaoPrincipal("Escolher comprovativo", { escolher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, icone = Icons.Filled.Receipt)
                        }
                    }
                    e.erro?.let { Aviso(it, TipoAviso.ERRO) }
                }
            }
        }
    }
}

private fun copiar(ctx: Context, texto: String) {
    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Referência Kupila", texto))
}
