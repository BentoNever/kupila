package ao.kupila.gestaonegocios.ui.tema

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ao.kupila.gestaonegocios.dominio.aparencia.IdentidadeVisual
import ao.kupila.gestaonegocios.dominio.aparencia.ModoAparencia

/** Cores extra da vitrine/pesquisa (não existem no esquema Material). */
@Immutable
data class CoresKupila(
    val destaque: Color,
    val sobreDestaque: Color,
    val pesquisaPrimaria: Color,
    val pesquisaDestaque: Color,
    val vitrinePrimaria: Color,
    val vitrineDestaque: Color,
    val sucesso: Color,
    val aviso: Color,
    val pago: Color,
)

val LocalCoresKupila = staticCompositionLocalOf {
    CoresKupila(Color(0xFFFACC15), Color.Black, Color(0xFF0F766E), Color(0xFF2DD4BF), Color(0xFF4338CA), Color(0xFFA78BFA),
        Color(0xFF15803D), Color(0xFFB45309), Color(0xFFC026D3))
}

/** Texto legível sobre a cor (contraste), em vez de fixar branco. */
fun sobre(c: Color) = if (c.luminance() > 0.45f) Color(0xFF0B1210) else Color.White

/** Escurece/clareia mantendo o matiz (para contentores e modo escuro). */
fun misturar(a: Color, b: Color, t: Float) = Color(
    red = a.red + (b.red - a.red) * t, green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t, alpha = 1f,
)

private fun esquema(id: IdentidadeVisual, escuro: Boolean): ColorScheme {
    val p = Color(id.primaria)
    val d = Color(id.destaque)
    return if (escuro) darkColorScheme(
        primary = misturar(p, Color.White, 0.25f), onPrimary = sobre(misturar(p, Color.White, 0.25f)),
        primaryContainer = misturar(p, Color.Black, 0.55f), onPrimaryContainer = misturar(p, Color.White, 0.8f),
        secondary = d, onSecondary = sobre(d),
        background = Color(0xFF0E1412), onBackground = Color(0xFFE6ECE9),
        surface = Color(0xFF141B18), onSurface = Color(0xFFE6ECE9),
        surfaceVariant = Color(0xFF1F2925), onSurfaceVariant = Color(0xFFB9C6C0),
    ) else lightColorScheme(
        primary = misturar(p, Color.Black, 0.12f), onPrimary = sobre(misturar(p, Color.Black, 0.12f)),
        primaryContainer = misturar(p, Color.White, 0.85f), onPrimaryContainer = misturar(p, Color.Black, 0.6f),
        secondary = d, onSecondary = sobre(d),
        background = Color(0xFFF4F7F5), onBackground = Color(0xFF111816),
        surface = Color.White, onSurface = Color(0xFF111816),
        surfaceVariant = Color(0xFFE9EFEC), onSurfaceVariant = Color(0xFF4B5A54),
    )
}

/** Tipografia legível no celular: a origem usava 7–9 px; aqui nada abaixo de 12 sp (secção 6). */
private val tipografia = Typography().let { t ->
    t.copy(
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
        bodySmall = t.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
    )
}

@Composable
fun TemaKupila(identidade: IdentidadeVisual, modoLocal: ModoAparencia? = null, conteudo: @Composable () -> Unit) {
    val modo = modoLocal ?: identidade.modoPlataforma
    val escuro = when (modo) {
        ModoAparencia.CLARO -> false
        ModoAparencia.ESCURO -> true
        ModoAparencia.AUTOMATICO -> isSystemInDarkTheme()
    }
    val d = Color(identidade.destaque)
    val extra = CoresKupila(
        destaque = d, sobreDestaque = sobre(d),
        pesquisaPrimaria = Color(identidade.pesquisaPrimaria), pesquisaDestaque = Color(identidade.pesquisaDestaque),
        vitrinePrimaria = Color(identidade.vitrinePrimaria), vitrineDestaque = Color(identidade.vitrineDestaque),
        sucesso = if (escuro) Color(0xFF4ADE80) else Color(0xFF15803D),
        aviso = if (escuro) Color(0xFFFBBF24) else Color(0xFFB45309),
        pago = if (escuro) Color(0xFFF0ABFC) else Color(0xFFA21CAF),
    )
    CompositionLocalProvider(LocalCoresKupila provides extra, ao.kupila.gestaonegocios.ui.kp.LocalMarca provides ao.kupila.gestaonegocios.ui.kp.Marca(Color(identidade.primaria), d)) {
        MaterialTheme(colorScheme = esquema(identidade, escuro), typography = tipografia, content = conteudo)
    }
}
