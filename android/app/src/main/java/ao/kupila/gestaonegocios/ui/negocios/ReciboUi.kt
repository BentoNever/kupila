package ao.kupila.gestaonegocios.ui.negocios

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ao.kupila.gestaonegocios.dominio.financeiro.Dinheiro
import ao.kupila.gestaonegocios.dominio.financeiro.FormatoAoa
import ao.kupila.gestaonegocios.ui.comum.Fa
import ao.kupila.gestaonegocios.ui.entrada.formatarData
import ao.kupila.gestaonegocios.ui.kp.FaIcone
import ao.kupila.gestaonegocios.ui.kp.Kp
import ao.kupila.gestaonegocios.ui.kp.KpBotao
import ao.kupila.gestaonegocios.ui.kp.LocalMarca
import ao.kupila.gestaonegocios.ui.kp.T
import ao.kupila.gestaonegocios.ui.kp.sup
import ao.kupila.gestaonegocios.ui.kp.toque
import kotlinx.coroutines.delay

/**
 * RECIBO DA RESERVA (`#modal-receipt`, HTML 7509-7570): reserva confirmada, OTP da vistoria em destaque (toque para copiar),
 * contagem decrescente em tempo real, contactos do Proprietário, comprovativo JPG/PDF e protecção após a vistoria.
 */
@Composable
fun ReciboEcra(vm: NegociosViewModel, reservaId: String, aoFechar: () -> Unit, aoReembolso: () -> Unit) {
    val marca = LocalMarca.current
    val contexto = LocalContext.current
    val copiar = LocalClipboardManager.current
    val reservas by vm.reservasCliente.collectAsStateWithLifecycle()
    val ocupado by vm.ocupado.collectAsStateWithLifecycle()
    val rc = reservas.firstOrNull { it.r.id == reservaId }
    val agora = ao.kupila.gestaonegocios.ui.comum.rememberAgora()
    Column(Modifier.background(Color.White).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (rc == null) { T("A carregar o recibo…", 12f, Kp.gray400, Modifier.padding(32.dp)); return@Column }
        val r = rc.r
        Box(Modifier.size(56.dp).sup(56.dp, fundo = Kp.emerald100, forma = CircleShape), contentAlignment = Alignment.Center) { FaIcone(Fa.SHIELD_HALVED, 20f, Kp.emerald600) }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            T("Reserva Confirmada com Sucesso!", 16f, Kp.gray900, 700, centro = true)
            T("O sinal foi processado e colocado em Pagamento Verificado.", 12f, Kp.gray500, Modifier.padding(top = 4.dp), centro = true)
            T(r.id, 9f, Kp.gray400, Modifier.padding(top = 4.dp))
        }
        // OTP: toque para copiar (a pessoa não tem de o escrever nem procurar).
        Column(Modifier.fillMaxWidth().sup(16.dp, fundo = Kp.amber50, borda = Kp.amber300, bordaLargura = 2.dp).toque { rc.otp?.let { copiar.setText(AnnotatedString(it)); ao.kupila.gestaonegocios.ui.kp.Avisos.mostrar("Código copiado. Apresente-o ao Proprietário apenas depois da vistoria.", "OTP copiado", ao.kupila.gestaonegocios.ui.kp.TipoAvisoKp.SUCESSO) } }
            .padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            T("O Seu Código de Vistoria (OTP)", 10f, Kp.amber700, 600, espacamento = 1f, maiusculas = true)
            T(rc.otp ?: "A aguardar…", 30f, Kp.amber900, 800, Modifier.padding(vertical = 4.dp), espacamento = 6f)
            T("Apresente este código ao proprietário apenas quando concluir a verificação ou vistoria do produto. Toque para copiar.", 10f, Kp.amber800, centro = true, linha = 15f)
        }
        val expira = r.ms("expiresAt")
        val restante = (expira ?: 0L) - agora
        val activa = r.s("status") == "ACTIVE" && restante > 0
        Column(Modifier.fillMaxWidth().sup(16.dp, fundo = Kp.slate900).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                T("Tempo restante", 9f, Kp.slate400, 600, Modifier.weight(1f), maiusculas = true)
                T(if (activa) "Reserva activa" else when (r.s("status")) { "COMPLETED" -> "Concluída"; "EXPIRED" -> "Expirada"; "REFUNDED" -> "Reembolsada"; else -> "Encerrada" }, 9f, Kp.yellow300, 700,
                    Modifier.sup(999.dp, fundo = Color(0xFFEAB308).copy(alpha = .2f)).padding(horizontal = 8.dp, vertical = 2.dp))
            }
            T(if (activa) "%02d:%02d:%02d".format(restante / 3_600_000, restante / 60_000 % 60, restante / 1000 % 60) else "--:--:--", 20f, Kp.yellow300, 700)
            T("Expira em: ${expira?.let { formatarData(it) } ?: "—"}", 10f, Kp.slate300)
        }
        Column(Modifier.fillMaxWidth().sup(12.dp, fundo = Kp.gray50).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("Sinal pago:" to FormatoAoa.formatar(Dinheiro.de(java.math.BigDecimal.valueOf(r.n("amount")))),
                "Contacto do Proprietário:" to r.s("ownerPhone").let { if (it.isBlank()) "—" else "+244 $it" },
                "Província / Município:" to r.s("location").ifBlank { r.s("province").ifBlank { "—" } },
                "Bairro:" to r.s("neighborhood").ifBlank { "—" }, "Ponto de Referência:" to r.s("referencePoint").ifBlank { "—" }).forEach { (k, v) ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { T(k, 12f, Kp.gray600, Modifier.weight(1f)); T(v, 12f, Kp.gray800, 600, fim = true) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KpBotao("Comprovativo JPG", { vm.exportarRecibo(rc, false) { contexto.startActivity(it) } }, Modifier.weight(1f), icone = Fa.IMAGE, iconeRegular = true,
                fundo = marca.c50, cor = marca.c700, borda = marca.c100, altura = 40.dp, raio = 12.dp, tamanho = 10f, ocupado = ocupado == "rec:${r.id}")
            KpBotao("Comprovativo PDF", { vm.exportarRecibo(rc, true) { contexto.startActivity(it) } }, Modifier.weight(1f), icone = Fa.FILE_PDF, iconeRegular = true,
                fundo = Kp.red50, cor = Kp.red700, borda = Color(0xFFFEE2E2), altura = 40.dp, raio = 12.dp, tamanho = 10f)
        }
        Column(Modifier.fillMaxWidth().sup(16.dp, fundo = Kp.violet50, borda = Color(0xFFDDD6FE)).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { FaIcone(Fa.SHIELD_HEART, 9f, Color(0xFF4C1D95)); Spacer(Modifier.width(6.dp)); T("Protecção após a vistoria", 9f, Color(0xFF4C1D95), 900) }
            T("Se o Cliente não gostar do produto e o negócio não for fechado, pode solicitar no app o reembolso do sinal, descontada a comissão da plataforma. A solicitação exige o OTP da vistoria, motivo/evidência e confirmação do Proprietário. O valor só é marcado como reembolsado depois da validação financeira.",
                8f, Color(0xFF5B21B6), Modifier.padding(top = 4.dp), linha = 13f)
        }
        if (rc.otp != null && r.s("status") in setOf("ACTIVE", "EXPIRED")) KpBotao("Não fechei negócio — pedir reembolso", aoReembolso, icone = Fa.ROTATE_LEFT,
            fundo = Color.White, cor = Color(0xFF6D28D9), borda = Color(0xFFDDD6FE), altura = 40.dp, raio = 12.dp, tamanho = 10f)
        KpBotao("Entendido e Voltar ao Catálogo", aoFechar, fundo = Kp.slate900, cor = Color.White, altura = 44.dp, raio = 12.dp, tamanho = 12f)
    }
}
