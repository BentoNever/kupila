package ao.kupila.gestaonegocios.ui.kp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ao.kupila.gestaonegocios.R

/** O fundo da marca (cabeçalhos, login, restauro) — as três cores escuras da identidade activa (carvão Spotec). */
val GradienteMarca: Brush @Composable @ReadOnlyComposable get() = Brush.linearGradient(LocalMarca.current.escuro)

/** O traço do "S" Spotec: a curva laranja do logótipo, como assinatura discreta nos cabeçalhos (igual ao PDV Digital). */
@Composable
fun TracoSpotec(modifier: Modifier = Modifier, alfa: Float = 0.9f, espessura: Float = 3f) {
    val cor = LocalMarca.current.primaria
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val p = Path().apply {
            moveTo(w * 0.02f, h * 0.98f)
            cubicTo(w * 0.40f, h * 1.02f, w * 0.18f, h * 0.42f, w * 0.52f, h * 0.36f)
            cubicTo(w * 0.80f, h * 0.31f, w * 0.92f, h * 0.12f, w * 0.99f, h * 0.02f)
        }
        drawPath(p, cor.copy(alpha = alfa), style = Stroke(width = espessura.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** O logótipo Spotec — versão clara sobre fundos escuros, original sobre fundos claros. */
@Composable
fun LogoSpotec(modifier: Modifier = Modifier, sobreEscuro: Boolean = true) {
    Image(painterResource(if (sobreEscuro) R.drawable.logo_spotec_claro else R.drawable.logo_spotec), "Spotec-Soluções", modifier)
}

/** Cabeçalho da marca (login e restauro): carvão → grafite, traço laranja do "S", símbolo e título — como no PDV Digital. */
@Composable
fun CabecalhoMarca(titulo: String, subtitulo: String, modifier: Modifier = Modifier, altura: Int = 270, folgaInferior: Int = 56, logoFundo: Boolean = false,
                   simbolo: @Composable () -> Unit) {
    val marca = LocalMarca.current
    Box(modifier.fillMaxWidth().heightIn(min = altura.dp).clip(RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp)).background(GradienteMarca)) {
        if (logoFundo) LogoSpotec(Modifier.align(Alignment.Center).padding(bottom = 30.dp).fillMaxWidth(0.92f)
            .graphicsLayer { alpha = 0.09f; rotationZ = -8f }, sobreEscuro = true)
        else TracoSpotec(Modifier.matchParentSize().padding(top = 30.dp, bottom = 10.dp), alfa = 0.30f, espessura = 3.5f)
        Box(Modifier.size(200.dp).align(Alignment.TopEnd).clip(CircleShape).background(marca.primaria.copy(alpha = 0.06f)))
        Column(Modifier.fillMaxWidth().heightIn(min = altura.dp).statusBarsPadding().padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = folgaInferior.dp),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            simbolo()
            Spacer(Modifier.height(14.dp))
            Text(titulo, color = Color.White, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Text(subtitulo, color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp, textAlign = TextAlign.Center)
        }
    }
}
