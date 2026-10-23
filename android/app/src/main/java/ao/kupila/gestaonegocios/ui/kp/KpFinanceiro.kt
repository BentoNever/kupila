package ao.kupila.gestaonegocios.ui.kp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ao.kupila.gestaonegocios.ui.comum.Fa

/** Bloco do resumo financeiro da origem (Cliente / Proprietário / Plataforma / Custos): título com ícone, linhas e total separado. */
@Composable
fun BlocoFinanceiro(titulo: String, icone: String, borda: Color, fundo: Color, cor: Color, linhas: List<Pair<String, String>>, total: Pair<String, String>,
                    corTotal: Color = Kp.slate900, info: Boolean = true) {
    Column(Modifier.fillMaxWidth().sup(12.dp, fundo = fundo, borda = borda).padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { FaIcone(icone, 8f, cor); Spacer(Modifier.width(4.dp)); T(titulo, 8f, cor, 900, maiusculas = true) }
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            linhas.forEach { (k, v) -> LinhaFinanceira(k, v, info = info) }
            Box(Modifier.fillMaxWidth().height(1.dp).background(borda))
            LinhaFinanceira(total.first, total.second, corTotal, info)
        }
    }
}

@Composable
fun LinhaFinanceira(k: String, v: String, corValor: Color = Kp.slate900, info: Boolean = true) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        T(k, 9f, Kp.slate700); if (info) { Spacer(Modifier.width(6.dp)); FaIcone(Fa.CIRCLE_INFO, 8f, Kp.slate500) }
        Spacer(Modifier.weight(1f)); T(v, 9f, corValor, 700)
    }
}

/** "Protecção do Cliente" (borda escura e nota em teal). */
@Composable
fun BlocoProteccao(retido: String, reembolso: String) {
    Column(Modifier.fillMaxWidth().sup(12.dp, fundo = Color.White, borda = Kp.slate900, bordaLargura = 1.5.dp).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { FaIcone(Fa.ROTATE_LEFT, 8f, Kp.slate900); Spacer(Modifier.width(4.dp)); T("Protecção do Cliente", 8f, Kp.slate900, 900, maiusculas = true) }
        LinhaFinanceira("Valor retido do sinal", retido)
        LinhaFinanceira("Reembolso possível*", reembolso)
        T("*Se o negócio não for concluído e o reembolso for aprovado: sinal menos comissão, conforme as regras da plataforma.", 7f, Kp.teal700, linha = 11f)
    }
}
