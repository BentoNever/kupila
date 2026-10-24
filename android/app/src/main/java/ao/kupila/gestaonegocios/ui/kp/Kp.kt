package ao.kupila.gestaonegocios.ui.kp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import ao.kupila.gestaonegocios.R
import kotlin.math.cos
import kotlin.math.sin

/**
 * Sistema de design da origem (Kupila 1.0.239) traduzido para Compose: 1 px CSS = 1 dp/sp (viewport de 412 px do A54).
 * Valores tirados dos estilos COMPUTADOS no navegador (tools/referencia-html, docs/paridade-visual.md), não das classes isoladas.
 */
object Kp {
    // Tailwind slate/gray/emerald/amber/indigo usados pela origem.
    val slate50 = Color(0xFFF8FAFC); val slate100 = Color(0xFFF1F5F9); val slate200 = Color(0xFFE2E8F0); val slate300 = Color(0xFFCBD5E1)
    val slate400 = Color(0xFF94A3B8); val slate500 = Color(0xFF64748B); val slate600 = Color(0xFF475569); val slate700 = Color(0xFF334155)
    val slate800 = Color(0xFF1E293B); val slate900 = Color(0xFF0F172A); val slate950 = Color(0xFF020617)
    val gray50 = Color(0xFFF9FAFB); val gray100 = Color(0xFFF3F4F6); val gray500 = Color(0xFF6B7280); val gray600 = Color(0xFF4B5563); val gray800 = Color(0xFF1F2937)
    val gray200 = Color(0xFFE5E7EB); val gray300 = Color(0xFFD1D5DB); val gray400 = Color(0xFF9CA3AF); val gray700 = Color(0xFF374151); val gray900 = Color(0xFF111827)
    val emerald50 = Color(0xFFECFDF5); val emerald100 = Color(0xFFD1FAE5); val emerald300 = Color(0xFF6EE7B7); val emerald600 = Color(0xFF059669); val emerald700 = Color(0xFF047857); val emerald900 = Color(0xFF064E3B)
    val amber50 = Color(0xFFFFFBEB); val amber100 = Color(0xFFFEF3C7); val amber200 = Color(0xFFFDE68A); val amber300 = Color(0xFFFCD34D); val amber400 = Color(0xFFFBBF24); val amber700 = Color(0xFFB45309); val amber800 = Color(0xFF92400E); val amber900 = Color(0xFF78350F)
    val yellow300 = Color(0xFFFDE047); val yellow400 = Color(0xFFFACC15)
    val red50 = Color(0xFFFEF2F2); val red500 = Color(0xFFEF4444); val red600 = Color(0xFFDC2626); val red700 = Color(0xFFB91C1C)
    val blue50 = Color(0xFFEFF6FF); val blue600 = Color(0xFF2563EB); val blue700 = Color(0xFF1D4ED8)
    val violet50 = Color(0xFFF5F3FF); val violet100 = Color(0xFFEDE9FE); val violet600 = Color(0xFF7C3AED); val violet700 = Color(0xFF6D28D9); val violet800 = Color(0xFF5B21B6); val violet900 = Color(0xFF4C1D95)
    val indigo700 = Color(0xFF4338CA); val sky500 = Color(0xFF0EA5E9); val teal700 = Color(0xFF0F766E); val teal400 = Color(0xFF2DD4BF)
    val branco = Color.White

    val FaSolid = FontFamily(Font(R.font.fa_solid, FontWeight.Black))
    val FaRegular = FontFamily(Font(R.font.fa_regular, FontWeight.Normal))
    val FaBrands = FontFamily(Font(R.font.fa_brands, FontWeight.Normal))
}

/**
 * Paleta "brand" calculada como a origem (`kupila-1-0-161-first-paint-theme`): o tema do Administrador muda tudo de uma vez.
 * `fundo` (opcional) são as 3 cores escuras da marca para cabeçalho, barra e rodapé — como a identidade Spotec do PDV Digital
 * (laranja de acção sobre carvão). Sem elas, o fundo deriva da cor primária, como no HTML.
 */
@Immutable
data class Marca(val primaria: Color, val destaque: Color, val fundo: List<Color>? = null) {
    /** Cabeçalho, "Explorar em" e rodapé. */
    val escuro: List<Color> get() = fundo ?: listOf(c900, c800, c700)
    /** Texto/ícone sobre a cor de acção. */
    val sobrePrimaria: Color get() = if (primaria.luminance() > 0.45f) Color(0xFF141615) else Color.White
    val spotec: Boolean get() = fundo != null
    private fun m(b: Color, t: Float) = Color(primaria.red + (b.red - primaria.red) * t, primaria.green + (b.green - primaria.green) * t, primaria.blue + (b.blue - primaria.blue) * t)
    val c50 = m(Color.White, .94f); val c100 = m(Color.White, .86f); val c200 = m(Color.White, .70f); val c300 = m(Color.White, .53f)
    val c400 = m(Color.White, .30f); val c500 = m(Color.White, .08f); val c600 = primaria; val c700 = m(Color.Black, .14f)
    // Com fundo próprio (identidade Spotec) os tons mais escuros são o carvão da marca, não o laranja escurecido.
    val c800 = fundo?.getOrNull(2) ?: m(Color.Black, .27f); val c900 = fundo?.getOrNull(1) ?: m(Color.Black, .40f); val c950 = fundo?.getOrNull(0) ?: m(Color.Black, .58f)
}

val LocalMarca = staticCompositionLocalOf { Marca(Color(0xFF16A34A), Color(0xFFFACC15)) }

/** Identidade visual Spotec (igual ao PDV Digital): laranja #F07E01 sobre carvão. */
object Spotec {
    val laranja = Color(0xFFF07E01)
    val carvao = listOf(Color(0xFF141615), Color(0xFF1E2022), Color(0xFF2D2F31))
    val fundoClaro = Color(0xFFF6F3EE)
    val marca = Marca(laranja, laranja, carvao)
}

/**
 * Tamanhos do PDV Digital (tipografia Material 3 com a fonte do telemóvel): os px do HTML (8–12) passam para a escala
 * legível do PDV (rótulos ~12 sp, texto ~14 sp, títulos ≥ 18 sp). Um só ponto: todos os ecrãs acompanham.
 */
fun pdvSp(px: Float): Float = if (px < 16f) px * 0.68f + 5f else px

/** Texto com as medidas exactas da origem (px → sp). */
@Composable
fun T(
    texto: String, tamanho: Float, cor: Color = Kp.gray900, peso: Int = 400, modifier: Modifier = Modifier, linha: Float? = null,
    espacamento: Float = 0f, maiusculas: Boolean = false, max: Int = Int.MAX_VALUE, centro: Boolean = false, fim: Boolean = false,
) {
    Text(if (maiusculas) texto.uppercase() else texto, modifier, color = cor, maxLines = max, overflow = TextOverflow.Ellipsis,
        textAlign = if (centro) TextAlign.Center else if (fim) TextAlign.End else null,
        style = LocalTextStyle.current.merge(TextStyle(fontSize = pdvSp(tamanho).sp, fontWeight = FontWeight(peso), lineHeight = (pdvSp(tamanho) * ((linha ?: (tamanho * 1.3f)) / tamanho)).sp,
            letterSpacing = if (espacamento == 0f) TextUnit.Unspecified else espacamento.sp)))
}

/** Variante com o modificador logo após a cor (forma mais usada nos ecrãs). */
@Composable
fun T(texto: String, tamanho: Float, cor: Color, modifier: Modifier, peso: Int = 400, linha: Float? = null, max: Int = Int.MAX_VALUE, centro: Boolean = false, maiusculas: Boolean = false, espacamento: Float = 0f) =
    T(texto, tamanho, cor, peso, modifier, linha, espacamento, maiusculas, max, centro)

/** Ícone FontAwesome com o mesmo glifo da origem (`fa-solid` por omissão; `regular = true` para `fa-regular`). */
@Composable
fun FaIcone(glifo: String, tamanho: Float, cor: Color, modifier: Modifier = Modifier, regular: Boolean = false, marcas: Boolean = false) {
    Text(glifo, modifier, color = cor, fontSize = pdvSp(tamanho).sp, lineHeight = pdvSp(tamanho).sp, letterSpacing = 0.em,
        fontFamily = if (marcas) Kp.FaBrands else if (regular) Kp.FaRegular else Kp.FaSolid, fontWeight = if (regular || marcas) FontWeight.Normal else FontWeight.Black)
}

/** `linear-gradient(<ângulo>deg, ...)` do CSS. */
fun gradiente(angulo: Float, vararg paragens: Pair<Float, Color>): Brush = object : androidx.compose.ui.graphics.ShaderBrush() {
    override fun createShader(size: androidx.compose.ui.geometry.Size): androidx.compose.ui.graphics.Shader {
        val r = Math.toRadians(angulo.toDouble())
        val dx = sin(r).toFloat(); val dy = -cos(r).toFloat()
        val meia = (kotlin.math.abs(size.width * dx) + kotlin.math.abs(size.height * dy)) / 2f
        val c = Offset(size.width / 2, size.height / 2)
        return androidx.compose.ui.graphics.LinearGradientShader(Offset(c.x - dx * meia, c.y - dy * meia), Offset(c.x + dx * meia, c.y + dy * meia),
            paragens.map { it.second }, paragens.map { it.first })
    }
}

fun gradiente(angulo: Float, cores: List<Color>): Brush = gradiente(angulo, *cores.mapIndexed { i, c -> (i.toFloat() / (cores.size - 1).coerceAtLeast(1)) to c }.toTypedArray())
fun gradiente(angulo: Float, a: Color, b: Color): Brush = gradiente(angulo, listOf(a, b))
fun gradiente(angulo: Float, a: Color, b: Color, c: Color): Brush = gradiente(angulo, listOf(a, b, c))

/** Superfície CSS: fundo (cor ou gradiente), borda 1 px, raio e sombra "box-shadow" (cor, y, desfoque). */
fun Modifier.sup(
    raio: Dp, fundo: Color? = null, pincel: Brush? = null, borda: Color? = null, bordaLargura: Dp = 1.dp,
    sombra: Color? = null, sombraY: Dp = 0.dp, desfoque: Dp = 0.dp, forma: Shape = RoundedCornerShape(raio),
): Modifier {
    var m = this
    if (sombra != null && desfoque > 0.dp) m = m.shadow(desfoque / 2, forma, clip = false, ambientColor = sombra, spotColor = sombra)
    m = m.clip(forma)
    if (pincel != null) m = m.background(pincel, forma) else if (fundo != null) m = m.background(fundo, forma)
    if (borda != null) m = m.border(bordaLargura, borda, forma)
    return m
}

/** Linha inferior (border-bottom) sem bordas laterais. */
fun Modifier.linhaSuperior(cor: Color = Kp.gray200, largura: Dp = 1.dp) = drawBehind {
    drawLine(cor, Offset(0f, largura.toPx() / 2), Offset(size.width, largura.toPx() / 2), largura.toPx())
}

fun Modifier.linhaInferior(cor: Color, largura: Dp = 1.dp) = drawBehind {
    val y = size.height - largura.toPx() / 2
    drawLine(cor, Offset(0f, y), Offset(size.width, y), largura.toPx())
}

/** Ponto/círculo de estado (ex.: `kupila-feature-status`). */
@Composable
fun Ponto(cor: Color, tamanho: Dp, borda: Color? = null, modifier: Modifier = Modifier) {
    Box(modifier.size(tamanho).sup(tamanho, fundo = cor, borda = borda, forma = androidx.compose.foundation.shape.CircleShape))
}
