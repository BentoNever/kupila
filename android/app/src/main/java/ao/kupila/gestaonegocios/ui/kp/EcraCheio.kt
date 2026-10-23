package ao.kupila.gestaonegocios.ui.kp

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** Botão "Entrar em tela cheia" da origem (`fa-expand`): esconde as barras do sistema; deslizar da borda mostra-as temporariamente. */
object EcraCheio {
    val activo = mutableStateOf(false)

    private tailrec fun Context.actividade(): Activity? = when (this) { is Activity -> this; is ContextWrapper -> baseContext.actividade(); else -> null }

    fun alternar(c: Context) {
        val a = c.actividade() ?: return
        val ctl = WindowCompat.getInsetsController(a.window, a.window.decorView)
        activo.value = !activo.value
        if (activo.value) {
            ctl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ctl.hide(WindowInsetsCompat.Type.systemBars())
        } else ctl.show(WindowInsetsCompat.Type.systemBars())
    }
}

@Composable
fun alternarEcraCheio(): () -> Unit { val c = LocalContext.current; return { EcraCheio.alternar(c) } }
