package ao.kupila.gestaonegocios.ui.meus

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ao.kupila.gestaonegocios.dados.eventos.Registo
import ao.kupila.gestaonegocios.dominio.perfis.Papel
import ao.kupila.gestaonegocios.ui.comum.Fa
import ao.kupila.gestaonegocios.ui.entrada.formatarData
import ao.kupila.gestaonegocios.ui.kp.FaIcone
import ao.kupila.gestaonegocios.ui.kp.Kp
import ao.kupila.gestaonegocios.ui.kp.KpBotao
import ao.kupila.gestaonegocios.ui.kp.KpBotaoSec
import ao.kupila.gestaonegocios.ui.kp.KpFechar
import ao.kupila.gestaonegocios.ui.kp.T
import ao.kupila.gestaonegocios.ui.kp.sup

/** `getAuthorizationStatusLabel` da origem. */
private fun estado(s: String) = when (s) {
    "PENDING_ADMIN" -> "Aguarda Administrador"; "CODE_SENT" -> "Autorizado"; "USED" -> "Concluído"; "EXPIRED" -> "Expirado"; "REJECTED" -> "Recusado"; else -> s
}

/** `maskDestination` da origem. */
private fun mascarar(v: String) = when {
    v.contains('@') -> v.substringBefore('@').take(2).ifEmpty { "*" } + "***@" + v.substringAfter('@')
    v.length >= 9 -> "+244 *** *** " + v.takeLast(3)
    else -> v
}

/**
 * AUTORIZAÇÕES DE PRODUTO (`#modal-listing-authorizations`, HTML 8146-8157; renderAuthorizationCenter 14633).
 * Sem burocracia: o Administrador autoriza num toque e o código chega na app do Proprietário, que conclui num toque (D-08) —
 * ninguém copia códigos entre ecrãs.
 */
@Composable
fun AutorizacoesEcra(vm: MeusAnunciosViewModel, aoFechar: () -> Unit) {
    val pedidos by vm.pedidos.collectAsStateWithLifecycle()
    val ocupado by vm.ocupado.collectAsStateWithLifecycle()
    val msg by vm.mensagem.collectAsStateWithLifecycle()
    val admin = vm.papel == Papel.ADMINISTRADOR
    val l = pedidos.sortedByDescending { it.ms("requestedAt") ?: it.ms("createdAt") ?: 0 }.take(100)
    Column(Modifier.background(Color.White)) {
        Row(Modifier.fillMaxWidth().background(Kp.slate900).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) { FaIcone(Fa.KEY, 12f, Kp.yellow400); Spacer(Modifier.width(6.dp)); T("Autorizações de Produto", 14f, Color.White, 700) }
                T(if (admin) "Pedidos pendentes de proprietários" else "Os seus pedidos de autorização", 9f, Kp.slate400, Modifier.padding(top = 2.dp))
            }
            KpFechar(aoFechar)
        }
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().sup(12.dp, fundo = Kp.amber50, borda = Kp.amber100).padding(12.dp)) {
                FaIcone(Fa.CIRCLE_INFO, 9f, Kp.amber900, Modifier.padding(top = 2.dp, end = 4.dp))
                T("Desanunciar ou excluir por iniciativa do proprietário exige autorização do Administrador. A operação não reembolsa taxas/comissões já pagas.", 10f, Kp.amber900, linha = 15f)
            }
            msg?.let { T(it, 10f, Kp.emerald700, 700, Modifier.fillMaxWidth().sup(12.dp, fundo = Kp.emerald50).padding(10.dp)) }
            if (l.isEmpty()) T("Nenhum pedido de autorização.", 12f, Kp.gray400, Modifier.fillMaxWidth().padding(vertical = 32.dp), centro = true)
            l.forEach { r -> CartaoPedido(r, admin, ocupado, vm) }
        }
    }
}

@Composable
private fun CartaoPedido(r: Registo, admin: Boolean, ocupado: String?, vm: MeusAnunciosViewModel) {
    val st = r.s("status")
    Column(Modifier.fillMaxWidth().sup(16.dp, fundo = Color.White, borda = Kp.gray200).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                T(r.s("listingTitle").ifBlank { r.s("listingId") }, 12f, Kp.gray800, 700, max = 1)
                T("${r.id} • ${if (r.s("action") == "DELETE") "Excluir" else "Desanunciar"}", 9f, Kp.gray400, max = 1)
            }
            T(estado(st), 8f, Kp.amber800, 700, Modifier.sup(999.dp, fundo = Kp.amber50).padding(horizontal = 8.dp, vertical = 4.dp))
        }
        Column(Modifier.fillMaxWidth().sup(12.dp, fundo = Kp.gray50).padding(8.dp)) {
            buildList {
                add("Proprietário:" to r.s("ownerName").ifBlank { "—" })
                add("Pedido:" to ((r.ms("requestedAt") ?: r.ms("createdAt"))?.let { formatarData(it) } ?: "—"))
                if (r.s("deliveryChannel").isNotBlank()) add("Entrega:" to "${r.s("deliveryChannel")} • ${mascarar(r.s("deliveryDestination"))}")
                if (st == "CODE_SENT") r.ms("codeExpiresAt")?.let { add("Código expira:" to formatarData(it)) }
                add("Reembolso:" to "Não aplicável")
            }.forEach { (k, v) ->
                Text(buildAnnotatedString { withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(k) }; append(" $v") }, color = Kp.gray600, fontSize = 11.sp, lineHeight = 15.sp)
            }
        }
        when {
            admin && st == "PENDING_ADMIN" -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KpBotao("Autorizar e enviar", { vm.gerar(r) }, Modifier.weight(1f), icone = Fa.KEY, ocupado = ocupado == "ger:${r.id}", altura = 36.dp, raio = 12.dp, tamanho = 10f)
                KpBotaoSec("Recusar", { vm.recusar(r) }, Modifier.weight(.6f), altura = 36.dp, tamanho = 10f, cor = Kp.red600, borda = Kp.red50)
            }
            !admin && st == "CODE_SENT" -> KpBotao("Concluir agora", { vm.concluir(r) }, icone = Fa.CIRCLE_CHECK, ocupado = ocupado == "usar:${r.id}", altura = 36.dp, raio = 12.dp, tamanho = 10f)
        }
    }
}
