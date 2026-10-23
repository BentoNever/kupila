package ao.kupila.gestaonegocios.ui.pendentes

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ao.kupila.gestaonegocios.dados.eventos.Registo
import ao.kupila.gestaonegocios.dominio.financeiro.Dinheiro
import ao.kupila.gestaonegocios.dominio.financeiro.FormatoAoa
import ao.kupila.gestaonegocios.dominio.transaccoes.EstadoIntencao
import ao.kupila.gestaonegocios.dominio.transaccoes.EstadoReembolso
import ao.kupila.gestaonegocios.ui.comum.Fa
import ao.kupila.gestaonegocios.ui.entrada.formatarData
import ao.kupila.gestaonegocios.ui.kp.FaIcone
import ao.kupila.gestaonegocios.ui.kp.Kp
import ao.kupila.gestaonegocios.ui.kp.KpCabecalhoModal
import ao.kupila.gestaonegocios.ui.kp.KpEntrada
import ao.kupila.gestaonegocios.ui.kp.LocalMarca
import ao.kupila.gestaonegocios.ui.kp.T
import ao.kupila.gestaonegocios.ui.kp.sup
import ao.kupila.gestaonegocios.ui.kp.toque
import ao.kupila.gestaonegocios.ui.negocios.NegociosViewModel

private enum class Filtro(val rotulo: String) { TODOS("Todos"), ERRO("Com erro"), PAGAMENTOS("Pagamentos"), REEMBOLSOS("Reembolsos"), CADASTROS("Cadastros") }

/** Processo da central (pagamento, reembolso ou liberação), normalizado para o cartão da origem. */
private data class Processo(val id: String, val tipo: Filtro, val categoria: String, val autor: String, val estado: String, val corEstado: Color, val fundoEstado: Color,
                            val motivo: String, val info: String, val em: Long?, val erro: Boolean, val registo: Registo, val accionavel: Boolean)

private fun aoa(v: Double) = FormatoAoa.formatar(Dinheiro.de(java.math.BigDecimal.valueOf(v)))

/**
 * "Processos Pendentes" (`#modal-admin-pending`, HTML 8078-8115; `openAdminPendingProcesses`). Confirmações financeiras continuam humanas,
 * mas sem burocracia: os comprovativos com referência coincidente aparecem primeiro e podem ser confirmados em lote.
 */
@Composable
fun PendentesEcra(vm: NegociosViewModel, aoVoltar: () -> Unit) {
    val marca = LocalMarca.current
    val intencoes by vm.intencoesTodas.collectAsStateWithLifecycle()
    val reembolsos by vm.reembolsosAdmin.collectAsStateWithLifecycle()
    val reservas by vm.reservasTodas.collectAsStateWithLifecycle()
    val ocupado by vm.ocupado.collectAsStateWithLifecycle()
    val msg by vm.mensagem.collectAsStateWithLifecycle()
    var filtro by rememberSaveable { mutableStateOf(Filtro.TODOS) }
    var busca by rememberSaveable { mutableStateOf("") }
    var marcados by remember { mutableStateOf(setOf<String>()) }
    val todos = remember(intencoes, reembolsos, reservas) { processos(intencoes, reembolsos, reservas) }
    val visiveis = todos.filter { (filtro == Filtro.TODOS || (filtro == Filtro.ERRO && it.erro) || it.tipo == filtro) &&
        (busca.isBlank() || "${it.autor} ${it.id} ${it.info} ${it.motivo}".contains(busca, true)) }
    Column {
        KpCabecalhoModal("Administração", "Processos Pendentes", aoVoltar, icone = Fa.LIST_CHECK, corIcone = Color.White, corKicker = Kp.yellow400,
            subtitulo = "${todos.size} processo(s) pendente(s) · toque num registo para gerir")
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().background(Color.White).padding(16.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
                T("Concluídos, cancelados e expirados são excluídos automaticamente.", 7.5f, Kp.slate500, Modifier.weight(1f))
                Row(Modifier.height(24.dp).sup(10.dp, fundo = marca.c50, borda = marca.c100).toque(aoTocar = { vm.actualizar() }).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    FaIcone(Fa.ARROWS_ROTATE, 8.5f, marca.c700); Spacer(Modifier.width(6.dp)); T("Actualizar", 8.5f, marca.c700, 700)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Kp.slate200))
            Column(Modifier.fillMaxWidth().background(Color.White).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Filtro.entries.forEach { f ->
                        val on = filtro == f
                        T(f.rotulo, 8.5f, if (on) Color.White else Kp.slate700, 700, Modifier.height(31.dp).sup(10.dp, fundo = if (on) marca.c700 else Color.White, borda = if (on) null else Kp.slate200)
                            .toque { filtro = f }.padding(horizontal = 10.dp, vertical = 9.dp))
                    }
                    FaIcone(Fa.TRASH_ARROW_UP, 13f, Kp.slate500, Modifier.padding(horizontal = 8.dp))
                }
                KpEntrada(busca, { busca = it }, dica = "Pesquisar processos…", altura = 38.dp, raio = 12.dp, tamanhoTexto = 9f,
                    direita = null, prefixo = null)
                Box(Modifier.fillMaxWidth().height(36.dp).sup(12.dp, fundo = Color.White, borda = Kp.slate200).toque {
                    marcados = if (visiveis.all { it.id in marcados }) marcados - visiveis.map { it.id }.toSet() else marcados + visiveis.filter { it.accionavel }.map { it.id }
                }, contentAlignment = Alignment.Center) { T(if (visiveis.isNotEmpty() && visiveis.all { it.id in marcados }) "Limpar selecção" else "Selecionar visíveis", 8.5f, Kp.slate700, 700) }
                T("${visiveis.size} de ${todos.size} processo(s) exibido(s) · ${marcados.size} selecionado(s) · proteção financeira aplica-se apenas aos registos reais", 7.5f, Kp.slate600, 700, linha = 12f)
                msg?.let { T(it, 8f, marca.c800, 700, Modifier.fillMaxWidth().sup(10.dp, fundo = marca.c50, borda = marca.c100).toque { vm.limparMensagem() }.padding(8.dp), linha = 12f) }
                val sel = todos.filter { it.id in marcados && it.accionavel && it.tipo == Filtro.PAGAMENTOS }
                if (sel.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Botao("Confirmar ${sel.size} recebimento(s)", marca.c600, Color.White, Modifier.weight(1f), ocupado != null) { sel.forEach { vm.confirmarPagamento(it.registo) }; marcados = emptySet() }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Kp.slate200))
            Column(Modifier.fillMaxWidth().background(Kp.slate50).padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (visiveis.isEmpty()) Column(Modifier.fillMaxWidth().padding(vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    FaIcone(Fa.CIRCLE_CHECK, 18f, marca.c600); T("Sem processos pendentes nesta vista.", 9f, Kp.slate400, Modifier.padding(top = 8.dp))
                }
                visiveis.forEach { p -> CartaoProcesso(p, p.id in marcados, { on -> marcados = if (on) marcados + p.id else marcados - p.id }, vm, ocupado) }
            }
            Row(Modifier.fillMaxWidth().background(Color.White).padding(16.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
                FaIcone(Fa.CIRCLE_INFO, 7.5f, Kp.slate400); Spacer(Modifier.width(6.dp))
                T("Autor, processo, estado, motivo, data e informação operacional são consolidados nesta central.", 7.5f, Kp.slate400)
            }
        }
    }
}

private fun processos(intencoes: List<Registo>, reembolsos: List<Registo>, reservas: List<Registo>): List<Processo> = buildList {
    intencoes.filter { it.s("status") in setOf("AWAITING_PAYMENT", "AWAITING_PROVIDER_CONFIGURATION", "VERIFYING", "RECEIPT_MATCHED", "REJECTED_REFERENCE", "REJECTED_PAYMENT") }.forEach { i ->
        val st = i.s("status"); val rot = EstadoIntencao.entries.firstOrNull { it.codigo == st }?.rotulo ?: st
        val erro = st.startsWith("REJECTED")
        val (c, f) = when { erro -> Kp.red600 to Kp.red50; st == "RECEIPT_MATCHED" -> Kp.emerald700 to Kp.emerald50; st == "VERIFYING" -> Kp.blue700 to Kp.blue50; else -> Kp.amber800 to Kp.amber50 }
        add(Processo(i.id, Filtro.PAGAMENTOS, if (i.s("type") == "LISTING") "Pagamento/Publicação" else "Pagamento/Reserva", i.s("userName").ifBlank { "Utilizador" }, rot, c, f,
            i.s("rejectionReason").ifBlank { rot }, "Produto ${i.s("listingId")} · ${aoa(i.n("amount"))} · Ref. ${i.s("reference")}",
            i.ms("receiptSubmittedAt") ?: i.ms("createdAt"), erro, i, st in setOf("VERIFYING", "RECEIPT_MATCHED")))
    }
    reembolsos.filter { it.s("status") in setOf("PENDING_OWNER_CONFIRMATION", "OWNER_CONFIRMED", "CONTESTED_PENDING_ADMIN", "APPROVED_REFUND_PENDING") }.forEach { r ->
        val st = r.s("status"); val rot = EstadoReembolso.entries.firstOrNull { it.codigo == st }?.rotulo ?: st
        add(Processo(r.id, Filtro.REEMBOLSOS, "Reembolso", r.s("clientName").ifBlank { "Cliente" }, rot, Kp.violet700, Kp.violet50, r.s("description").ifBlank { rot },
            "Reserva ${r.s("reservationId")} · ${aoa(r.n("refundableAmount"))}", r.ms("updatedAt") ?: r.ms("createdAt"), false, r, st != "PENDING_OWNER_CONFIRMATION"))
    }
    reservas.filter { it.s("status") == "COMPLETED" && it.s("payoutStatus") in setOf("RELEASE_REQUESTED", "PROCESSING", "FAILED") && it.s("refundRequestId").isBlank() }.forEach { r ->
        add(Processo(r.id, Filtro.CADASTROS, "Liberação ao Proprietário", r.s("ownerName").ifBlank { "Proprietário" }, if (r.s("payoutStatus") == "FAILED") "Falhou" else "Liberação solicitada",
            Kp.amber800, Kp.amber50, r.s("payoutFailureReason").ifBlank { "Negócio confirmado por OTP" }, "${r.s("listingTitle")} · ${aoa(r.n("ownerNetAmount"))}",
            r.ms("updatedAt"), r.s("payoutStatus") == "FAILED", r, true))
    }
}.sortedWith(compareByDescending<Processo> { it.accionavel }.thenByDescending { it.registo.s("status") == "RECEIPT_MATCHED" }.thenByDescending { it.em ?: 0 })

@Composable
private fun CartaoProcesso(p: Processo, marcado: Boolean, aoMarcar: (Boolean) -> Unit, vm: NegociosViewModel, ocupado: String?) {
    val marca = LocalMarca.current
    var aberto by remember { mutableStateOf(false) }
    var texto by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().sup(16.dp, fundo = Color.White, borda = Kp.slate200, sombra = Kp.slate900.copy(.06f), desfoque = 8.dp).toque { aberto = !aberto }.padding(14.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Checkbox(marcado, aoMarcar, Modifier.size(18.dp).padding(top = 4.dp), enabled = p.accionavel, colors = CheckboxDefaults.colors(checkedColor = marca.c600, uncheckedColor = Kp.slate400))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) { T(p.categoria, 8f, Kp.slate400, 600, maiusculas = true); T(p.autor, 10.5f, Kp.slate900, 900, Modifier.padding(top = 2.dp), max = 1) }
                    T(p.estado, 7f, p.corEstado, 800, Modifier.sup(999.dp, fundo = p.fundoEstado, borda = p.corEstado.copy(.2f)).padding(horizontal = 8.dp, vertical = 4.dp), max = 1)
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.size(24.dp).sup(10.dp, fundo = Kp.slate50), contentAlignment = Alignment.Center) { FaIcone(if (aberto) Fa.CHEVRON_UP else Fa.CHEVRON_DOWN, 8f, Kp.slate500) }
                }
                Spacer(Modifier.height(8.dp))
                Rotulo("Processo", p.id); Rotulo("Motivo", p.motivo); Rotulo("Informações", p.info)
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    FaIcone(Fa.CLOCK, 8.5f, Kp.slate400, regular = true); Spacer(Modifier.width(4.dp)); T(p.em?.let { formatarData(it) } ?: "—", 8.5f, Kp.slate400, Modifier.weight(1f))
                    T(if (aberto) "Fechar" else "Gerir", 7.5f, marca.c700, 800); FaIcone(if (aberto) Fa.CHEVRON_UP else Fa.CHEVRON_DOWN, 6.5f, marca.c700, Modifier.padding(start = 3.dp))
                }
                Row(Modifier.padding(top = 8.dp).fillMaxWidth().sup(999.dp, fundo = Color(0xFFFFF7ED), borda = Color(0xFFFED7AA)).padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    FaIcone(Fa.SHIELD_HALVED, 7f, Color(0xFFC2410C)); Spacer(Modifier.width(4.dp)); T("Registo real protegido · pode ser removido da Central", 7f, Color(0xFFC2410C), 800)
                }
                AnimatedVisibility(aberto) {
                    Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (p.tipo == Filtro.PAGAMENTOS && p.accionavel) {
                            val img by produceState<Pair<ByteArray?, String>?>(null, p.id) { value = vm.comprovativo(p.registo) }
                            img?.first?.let { b -> BitmapFactory.decodeByteArray(b, 0, b.size)?.let { bmp ->
                                Image(bmp.asImageBitmap(), "Comprovativo", Modifier.fillMaxWidth().heightIn(max = 360.dp).sup(12.dp, fundo = Kp.slate100), contentScale = ContentScale.Fit) } }
                            img?.second?.takeIf { it.isNotBlank() }?.let { T("Referência lida no comprovativo: $it", 8.5f, Kp.slate600) }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Botao("Confirmar recebimento", marca.c600, Color.White, Modifier.weight(1f), ocupado == "pay:${p.id}") { vm.confirmarPagamento(p.registo) }
                                Botao("Rejeitar", Kp.red50, Kp.red600, Modifier.weight(1f), ocupado == "rej:${p.id}") { vm.rejeitar(p.registo, texto, p.registo.s("status") != "RECEIPT_MATCHED") }
                            }
                            KpEntrada(texto, { texto = it }, dica = "Motivo da rejeição (opcional)", altura = 31.dp, tamanhoTexto = 8.5f)
                        }
                        if (p.tipo == Filtro.REEMBOLSOS) when (p.registo.s("status")) {
                            "APPROVED_REFUND_PENDING" -> {
                                KpEntrada(texto, { texto = it }, dica = "Referência da transferência do reembolso", altura = 31.dp, tamanhoTexto = 8.5f)
                                Botao("Registar reembolso pago", marca.c600, Color.White, Modifier.fillMaxWidth(), ocupado == "rpg:${p.id}") { vm.confirmarReembolsoPago(p.id, texto) }
                            }
                            "OWNER_CONFIRMED", "CONTESTED_PENDING_ADMIN" -> {
                                KpEntrada(texto, { texto = it }, dica = "Fundamento da decisão", altura = 31.dp, tamanhoTexto = 8.5f)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Botao("Aprovar", marca.c600, Color.White, Modifier.weight(1f), ocupado == "dec:${p.id}") { vm.decidirReembolso(p.id, true, texto) }
                                    Botao("Recusar", Kp.red50, Kp.red600, Modifier.weight(1f), ocupado == "dec:${p.id}") { vm.decidirReembolso(p.id, false, texto) }
                                }
                            }
                            else -> T("A aguardar a confirmação do Proprietário.", 8.5f, Kp.slate500)
                        }
                        if (p.tipo == Filtro.CADASTROS) {
                            KpEntrada(texto, { texto = it }, dica = "Referência da transferência ao Proprietário", altura = 31.dp, tamanhoTexto = 8.5f)
                            Botao("Registar liberação", marca.c600, Color.White, Modifier.fillMaxWidth(), ocupado == "lib:${p.id}") { vm.confirmarLiberacao(p.id, texto) }
                        }
                        if (!p.accionavel && p.tipo == Filtro.PAGAMENTOS) T("Aguarda o pagamento e o comprovativo do utilizador; não exige acção.", 8.5f, Kp.slate500)
                    }
                }
            }
        }
    }
}

@Composable
private fun Rotulo(k: String, v: String) {
    androidx.compose.material3.Text(buildAnnotatedString { withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Kp.slate600)) { append("$k: ") }; append(v) },
        Modifier.padding(top = 4.dp), color = Kp.slate700, fontSize = 9.sp, lineHeight = 13.sp, maxLines = 2)
}

@Composable
private fun Botao(texto: String, fundo: Color, cor: Color, modifier: Modifier, ocupado: Boolean, aoTocar: () -> Unit) {
    Box(modifier.height(32.dp).sup(10.dp, fundo = fundo).toque(!ocupado, aoTocar), contentAlignment = Alignment.Center) { T(if (ocupado) "A processar…" else texto, 9f, cor, 700) }
}
