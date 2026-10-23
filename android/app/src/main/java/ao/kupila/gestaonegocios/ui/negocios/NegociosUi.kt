package ao.kupila.gestaonegocios.ui.negocios

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ao.kupila.gestaonegocios.dados.eventos.Registo
import ao.kupila.gestaonegocios.dominio.financeiro.Dinheiro
import ao.kupila.gestaonegocios.dominio.financeiro.FormatoAoa
import ao.kupila.gestaonegocios.dominio.perfis.Papel
import ao.kupila.gestaonegocios.dominio.transaccoes.EstadoIntencao
import ao.kupila.gestaonegocios.dominio.transaccoes.EstadoLiberacao
import ao.kupila.gestaonegocios.dominio.transaccoes.EstadoReembolso
import ao.kupila.gestaonegocios.dominio.transaccoes.EstadoReserva
import ao.kupila.gestaonegocios.dominio.transaccoes.MotivoReembolso
import ao.kupila.gestaonegocios.ui.comum.Aviso
import ao.kupila.gestaonegocios.ui.comum.BotaoPrincipal
import ao.kupila.gestaonegocios.ui.comum.BotaoSecundario
import ao.kupila.gestaonegocios.ui.comum.Campo
import ao.kupila.gestaonegocios.ui.comum.EstadoVazio
import ao.kupila.gestaonegocios.ui.comum.LinhaInfo
import ao.kupila.gestaonegocios.ui.comum.Seletor
import ao.kupila.gestaonegocios.ui.comum.TipoAviso
import ao.kupila.gestaonegocios.ui.entrada.formatarData
import kotlinx.coroutines.delay

private enum class Vista(val rotulo: String) { CLIENTE("Minhas reservas"), PROPRIETARIO("Meus produtos"), ADMIN("Pendências") }

private fun aoa(v: Double) = FormatoAoa.formatar(Dinheiro.de(java.math.BigDecimal.valueOf(v)))
private fun rotuloReserva(c: String) = EstadoReserva.entries.firstOrNull { it.codigo == c }?.rotulo ?: c
private fun rotuloIntencao(c: String) = EstadoIntencao.entries.firstOrNull { it.codigo == c }?.rotulo ?: c
private fun rotuloReembolso(c: String) = EstadoReembolso.entries.firstOrNull { it.codigo == c }?.rotulo ?: c
private fun rotuloLiberacao(c: String) = EstadoLiberacao.entries.firstOrNull { it.codigo == c }?.rotulo ?: c

/** Contagem decrescente que se actualiza sozinha (1/min — barato em bateria). */
@Composable
private fun Restante(expiraMs: Long?) {
    if (expiraMs == null) return
    val agora by produceState(System.currentTimeMillis()) { while (true) { delay(60_000); value = System.currentTimeMillis() } }
    val m = (expiraMs - agora) / 60_000
    Text(if (m <= 0) "Prazo terminado" else "Faltam ${m / 60} h ${m % 60} min", style = MaterialTheme.typography.labelMedium,
        color = if (m in 1..120) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NegociosEcra(vm: NegociosViewModel, aoPagar: (String) -> Unit, aoProduto: (String) -> Unit) {
    val papel = vm.papel
    val vistas = buildList { add(Vista.CLIENTE); if (papel != Papel.CLIENTE) add(Vista.PROPRIETARIO); if (papel == Papel.ADMINISTRADOR) add(Vista.ADMIN) }
    var vista by rememberSaveable { mutableStateOf(if (papel == Papel.ADMINISTRADOR) Vista.ADMIN else if (papel == Papel.PROPRIETARIO) Vista.PROPRIETARIO else Vista.CLIENTE) }
    val sinc by vm.aSincronizar.collectAsStateWithLifecycle()
    val msg by vm.mensagem.collectAsStateWithLifecycle()
    val ocupado by vm.ocupado.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(msg) { msg?.let { snack.showSnackbar(it); vm.limparMensagem() } }

    Scaffold(snackbarHost = { SnackbarHost(snack) }) { pad ->
        PullToRefreshBox(sinc, { vm.actualizar() }, Modifier.fillMaxSize().padding(pad)) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Text("Negócios", style = MaterialTheme.typography.headlineSmall) }
                if (vistas.size > 1) item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { vistas.forEach { v -> FilterChip(vista == v, { vista = v }, { Text(v.rotulo) }) } }
                }
                when (vista) {
                    Vista.CLIENTE -> cliente(vm, ocupado, aoPagar, aoProduto)
                    Vista.PROPRIETARIO -> proprietario(vm, ocupado, aoProduto)
                    Vista.ADMIN -> administrador(vm, ocupado)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------- Cliente
private fun androidx.compose.foundation.lazy.LazyListScope.cliente(vm: NegociosViewModel, ocupado: String?, aoPagar: (String) -> Unit, aoProduto: (String) -> Unit) {
    item {
        val pag by vm.pagamentosAbertos.collectAsStateWithLifecycle()
        if (pag.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Pagamentos em curso", style = MaterialTheme.typography.titleMedium)
            pag.forEach { i ->
                Card(modifier = Modifier.fillMaxWidth(), onClick = { aoPagar(i.id) }) {
                    Column(Modifier.padding(14.dp)) {
                        Text(i.s("listingTitle"), fontWeight = FontWeight.SemiBold)
                        Text("${if (i.s("type") == "RESERVATION") "Sinal" else "Publicação"} · ${aoa(i.n("amount"))} · ${rotuloIntencao(i.s("status"))}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    item {
        val rs by vm.reservasCliente.collectAsStateWithLifecycle()
        val rfs by vm.reembolsosCliente.collectAsStateWithLifecycle()
        if (rs.isEmpty()) EstadoVazio("Sem reservas", "Quando reservar um produto, o código OTP e o prazo aparecem aqui.")
        else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { rs.forEach { rc -> CartaoReservaCliente(vm, rc, rfs.firstOrNull { it.s("reservationId") == rc.r.id }, ocupado, aoProduto) } }
    }
}

@Composable
private fun CartaoReservaCliente(vm: NegociosViewModel, rc: ReservaCliente, reembolso: Registo?, ocupado: String?, aoProduto: (String) -> Unit) {
    val r = rc.r
    val ctx = LocalContext.current
    var pedir by remember { mutableStateOf(false) }
    var testemunhar by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(r.s("listingTitle"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(end = 8.dp))
            Text(rotuloReserva(r.s("status")), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            if (r.s("status") == "ACTIVE") Restante(r.ms("expiresAt"))
            LinhaInfo("Sinal pago", aoa(r.n("amount")))
            if (r.s("status") == "ACTIVE" && rc.otp != null) Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Código OTP (entregue ao Proprietário só se fechar negócio)", style = MaterialTheme.typography.labelSmall)
                    Text(rc.otp.chunked(3).joinToString(" "), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = { (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("OTP", rc.otp)) }) { Icon(Icons.Filled.ContentCopy, "Copiar OTP") }
            }
            if (r.s("status") == "ACTIVE") Text("Encontro: ${r.s("neighborhood")}, ${r.s("referencePoint")} · ${r.s("ownerName")} ${r.s("ownerPhone")}", style = MaterialTheme.typography.bodySmall)
            reembolso?.let { Aviso("Reembolso: ${rotuloReembolso(it.s("status"))}", TipoAviso.INFO) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { aoProduto(r.s("listingId")) }) { Text("Ver produto") }
                if (reembolso == null && r.s("status") in setOf("ACTIVE", "EXPIRED") && rc.otp != null) TextButton(onClick = { pedir = true }) { Text("Não fechei negócio") }
                if (r.s("status") == "COMPLETED") TextButton(onClick = { testemunhar = true }) { Text("Partilhar experiência") }
            }
        }
    }
    if (testemunhar) {
        var estrelas by remember { mutableStateOf(5) }
        var texto by remember { mutableStateOf("") }
        var modo by remember { mutableStateOf(ao.kupila.gestaonegocios.dominio.testemunhos.ModoNome.ABREVIADO) }
        AlertDialog(onDismissRequest = { testemunhar = false }, title = { Text("Como correu?") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row { (1..5).forEach { n -> TextButton(onClick = { estrelas = n }) { Text(if (n <= estrelas) "★" else "☆", style = MaterialTheme.typography.headlineSmall) } } }
                Campo(texto, { texto = it }, "O seu testemunho", linhas = 3, maxCaracteres = 600)
                Seletor("Mostrar como", ao.kupila.gestaonegocios.dominio.testemunhos.ModoNome.entries.map { it to it.rotulo }, modo, { modo = it })
            }
        }, confirmButton = { TextButton(enabled = texto.trim().length >= 20, onClick = { vm.testemunho(r, estrelas, texto, modo); testemunhar = false }) { Text("Enviar") } },
            dismissButton = { TextButton(onClick = { testemunhar = false }) { Text("Agora não") } })
    }
    if (pedir) DialogoReembolso(ocupado == "rfd:${r.id}", aoFechar = { pedir = false }) { motivo, desc, uri -> vm.pedirReembolso(r.id, rc.otp!!, motivo, desc, uri); pedir = false }
}

@Composable
private fun DialogoReembolso(ocupado: Boolean, aoFechar: () -> Unit, aoEnviar: (MotivoReembolso, String, Uri) -> Unit) {
    var motivo by remember { mutableStateOf(MotivoReembolso.entries.first()) }
    var desc by remember { mutableStateOf("") }
    var uri by remember { mutableStateOf<Uri?>(null) }
    val escolher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri = it }
    AlertDialog(onDismissRequest = aoFechar, title = { Text("Pedir reembolso") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Seletor("Motivo", MotivoReembolso.entries.map { it to it.rotulo }, motivo, { motivo = it })
            Campo(desc, { desc = it }, "O que aconteceu", linhas = 3, maxCaracteres = 600)
            BotaoSecundario(if (uri == null) "Anexar evidência (foto ou PDF)" else "Evidência anexada ✓", { escolher.launch(arrayOf("image/*", "application/pdf")) })
            Text(motivo.declaracao, style = MaterialTheme.typography.bodySmall)
            Text("A comissão Kupila não é reembolsável. O OTP desta reserva é usado automaticamente para comprovar a vistoria.", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(enabled = uri != null && desc.trim().length >= 20 && !ocupado, onClick = { aoEnviar(motivo, desc, uri!!) }) { Text("Declaro e envio") } },
        dismissButton = { TextButton(onClick = aoFechar) { Text("Cancelar") } })
}

// ---------------------------------------------------------------------------------------------- Proprietário
private fun androidx.compose.foundation.lazy.LazyListScope.proprietario(vm: NegociosViewModel, ocupado: String?, aoProduto: (String) -> Unit) {
    item {
        val rs by vm.reservasDono.collectAsStateWithLifecycle()
        val rfs by vm.reembolsosDono.collectAsStateWithLifecycle()
        val ctx = LocalContext.current
        val pend = rfs.filter { it.s("status") == "PENDING_OWNER_CONFIRMATION" }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            pend.forEach { p ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Pedido de reembolso · ${p.s("listingTitle")}", style = MaterialTheme.typography.titleMedium)
                        Text("${p.s("clientName")}: ${p.s("description")}", style = MaterialTheme.typography.bodySmall)
                        BotaoSecundario("Abrir evidência", { vm.abrirEvidencia(p.id) { ctx.startActivity(it) } })
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BotaoPrincipal("Confirmo: não fechámos", { vm.reverReembolso(p.id, true) }, Modifier.weight(1f), ocupado = ocupado == "rev:${p.id}")
                            BotaoSecundario("Contestar", { vm.reverReembolso(p.id, false) }, Modifier.weight(1f))
                        }
                    }
                }
            }
            val activas = rs.filter { it.s("status") == "ACTIVE" }
            val outras = rs.filter { it.s("status") != "ACTIVE" }.sortedByDescending { it.ms("updatedAt") ?: 0 }
            if (rs.isEmpty() && pend.isEmpty()) EstadoVazio("Sem reservas nos seus produtos", "Quando um Cliente reservar, valida aqui o OTP no fecho do negócio.")
            activas.forEach { r -> CartaoReservaDono(vm, r, ocupado, aoProduto) }
            if (outras.isNotEmpty()) Text("Histórico", style = MaterialTheme.typography.titleMedium)
            outras.take(30).forEach { r ->
                Card(modifier = Modifier.fillMaxWidth(), onClick = { aoProduto(r.s("listingId")) }) {
                    Column(Modifier.padding(14.dp)) {
                        Text(r.s("listingTitle"), fontWeight = FontWeight.SemiBold)
                        Text(rotuloReserva(r.s("status")) + (r.s("payoutStatus").takeIf { it.isNotBlank() }?.let { " · " + rotuloLiberacao(it) } ?: ""), style = MaterialTheme.typography.bodySmall)
                        LinhaInfo("Líquido", aoa(r.n("ownerNetAmount")))
                    }
                }
            }
        }
    }
}

@Composable
private fun CartaoReservaDono(vm: NegociosViewModel, r: Registo, ocupado: String?, aoProduto: (String) -> Unit) {
    var otp by rememberSaveable(r.id) { mutableStateOf("") }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(r.s("listingTitle"), style = MaterialTheme.typography.titleMedium)
            Text("Reservado por ${r.s("userName")}", style = MaterialTheme.typography.bodySmall)
            Restante(r.ms("expiresAt"))
            LinhaInfo("Vai receber (líquido)", aoa(r.n("ownerNetAmount")))
            Campo(otp, { otp = it.filter(Char::isDigit).take(6) }, "OTP do Cliente (ao fechar negócio)", teclado = KeyboardType.NumberPassword,
                aoConcluir = { if (otp.length == 6) vm.validarOtp(r.id, otp) })
            BotaoPrincipal("Confirmar negócio", { vm.validarOtp(r.id, otp) }, ocupado = ocupado == "otp:${r.id}", enabled = otp.length == 6)
            TextButton(onClick = { aoProduto(r.s("listingId")) }) { Text("Ver produto") }
        }
    }
}

// ---------------------------------------------------------------------------------------------- Administrador
private fun androidx.compose.foundation.lazy.LazyListScope.administrador(vm: NegociosViewModel, ocupado: String?) {
    item {
        val pend by vm.pendencias.collectAsStateWithLifecycle()
        val coincidentes = pend.count { it.coincide }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Pagamentos a confirmar (${pend.size})", style = MaterialTheme.typography.titleMedium)
            if (coincidentes > 1) BotaoPrincipal("Confirmar os $coincidentes com referência coincidente", vm::confirmarCoincidentes, ocupado = ocupado == "lote", icone = Icons.Filled.DoneAll)
            Text("Confirme só depois de verificar o recebimento no extracto. Cada confirmação é auditada.", style = MaterialTheme.typography.bodySmall)
            if (pend.isEmpty()) EstadoVazio("Nada por confirmar", "Os comprovativos enviados aparecem aqui.")
        }
    }
    item {
        val pend by vm.pendencias.collectAsStateWithLifecycle()
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { pend.forEach { CartaoPendencia(vm, it, ocupado) } }
    }
    item {
        val rfs by vm.reembolsosAdmin.collectAsStateWithLifecycle()
        val decidir = rfs.filter { it.s("status") in setOf("OWNER_CONFIRMED", "CONTESTED_PENDING_ADMIN") }
        val pagar = rfs.filter { it.s("status") == "APPROVED_REFUND_PENDING" }
        val ctx = LocalContext.current
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (decidir.isNotEmpty()) Text("Reembolsos para decidir", style = MaterialTheme.typography.titleMedium)
            decidir.forEach { p ->
                var fundamento by rememberSaveable(p.id) { mutableStateOf("") }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(p.s("listingTitle"), style = MaterialTheme.typography.titleMedium)
                        Text(rotuloReembolso(p.s("status")) + " · " + aoa(p.n("refundableAmount")), style = MaterialTheme.typography.labelLarge)
                        Text(p.s("description"), style = MaterialTheme.typography.bodySmall)
                        BotaoSecundario("Abrir evidência e verificar integridade", { vm.abrirEvidencia(p.id) { ctx.startActivity(it) } })
                        Campo(fundamento, { fundamento = it }, "Fundamento da decisão", linhas = 2)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BotaoPrincipal("Aprovar", { vm.decidirReembolso(p.id, true, fundamento) }, Modifier.weight(1f), ocupado = ocupado == "dec:${p.id}",
                                enabled = p.s("status") == "OWNER_CONFIRMED")
                            BotaoSecundario("Recusar", { vm.decidirReembolso(p.id, false, fundamento) }, Modifier.weight(1f), enabled = fundamento.isNotBlank())
                        }
                    }
                }
            }
            if (pagar.isNotEmpty()) Text("Reembolsos aprovados por transferir", style = MaterialTheme.typography.titleMedium)
            pagar.forEach { p -> CartaoTransferencia(p.s("listingTitle") + " · " + p.s("clientName"), p.n("refundableAmount"), ocupado == "rpg:${p.id}") { vm.confirmarReembolsoPago(p.id, it) } }
        }
    }
    item {
        val rs by vm.reservasTodas.collectAsStateWithLifecycle()
        val lib = rs.filter { it.s("status") == "COMPLETED" && it.s("payoutStatus") in setOf("RELEASE_REQUESTED", "PROCESSING") && it.s("refundRequestId").isBlank() }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (lib.isNotEmpty()) Text("Liberações ao Proprietário", style = MaterialTheme.typography.titleMedium)
            lib.forEach { r -> CartaoTransferencia(r.s("listingTitle") + " · " + r.s("ownerName"), r.n("ownerNetAmount"), ocupado == "lib:${r.id}") { vm.confirmarLiberacao(r.id, it) } }
        }
    }
}

@Composable
private fun CartaoTransferencia(titulo: String, valor: Double, ocupado: Boolean, aoConfirmar: (String) -> Unit) {
    var ref by rememberSaveable(titulo) { mutableStateOf("") }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(titulo, fontWeight = FontWeight.SemiBold)
            LinhaInfo("Valor", aoa(valor), destaque = true)
            Campo(ref, { ref = it }, "Referência da transferência feita")
            BotaoPrincipal("Registar transferência", { aoConfirmar(ref) }, ocupado = ocupado, enabled = ref.isNotBlank())
        }
    }
}

@Composable
private fun CartaoPendencia(vm: NegociosViewModel, p: Pendencia, ocupado: String?) {
    val i = p.intent
    var aberto by remember { mutableStateOf(false) }
    var rejeitar by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth(), onClick = { aberto = !aberto }) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(i.s("listingTitle"), style = MaterialTheme.typography.titleMedium)
            Text("${if (i.s("type") == "RESERVATION") "Sinal de reserva" else "Publicação"} · ${i.s("userName")}", style = MaterialTheme.typography.bodySmall)
            LinhaInfo("Valor", aoa(i.n("amount")), destaque = true)
            LinhaInfo("Referência", i.s("reference"))
            i.ms("receiptSubmittedAt")?.let { LinhaInfo("Comprovativo", formatarData(it)) }
            Aviso(if (p.coincide) "A referência lida no comprovativo coincide." else "Referência não reconhecida automaticamente: verifique com atenção.",
                if (p.coincide) TipoAviso.INFO else TipoAviso.AVISO)
            if (aberto) {
                val img by produceState<Pair<ByteArray?, String>?>(null, i.id) { value = vm.comprovativo(i) }
                when (val x = img) {
                    null -> Text("A abrir o comprovativo…", style = MaterialTheme.typography.bodySmall)
                    else -> x.first?.let { b -> BitmapFactory.decodeByteArray(b, 0, b.size)?.let { bmp ->
                        Image(bmp.asImageBitmap(), "Comprovativo", Modifier.fillMaxWidth().heightIn(max = 420.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Fit) } }
                        ?: Text("Sem imagem do comprovativo.", style = MaterialTheme.typography.bodySmall)
                }
            } else Text("Toque para ver o comprovativo", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BotaoPrincipal("Confirmar recebimento", { vm.confirmarPagamento(i) }, Modifier.weight(1f), ocupado = ocupado == "pay:${i.id}")
                BotaoSecundario("Rejeitar", { rejeitar = true }, Modifier.weight(1f))
            }
        }
    }
    if (rejeitar) {
        var motivo by remember { mutableStateOf("") }
        var porReferencia by remember { mutableStateOf(!p.coincide) }
        AlertDialog(onDismissRequest = { rejeitar = false }, title = { Text("Rejeitar comprovativo") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(porReferencia, { porReferencia = true }, { Text("Referência errada") })
                    FilterChip(!porReferencia, { porReferencia = false }, { Text("Não recebido") })
                }
                Campo(motivo, { motivo = it }, "Explicação para o pagador", linhas = 2)
            }
        }, confirmButton = { TextButton(onClick = { vm.rejeitar(i, motivo, porReferencia); rejeitar = false }) { Text("Rejeitar") } },
            dismissButton = { TextButton(onClick = { rejeitar = false }) { Text("Cancelar") } })
    }
}
