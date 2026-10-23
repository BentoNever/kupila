package ao.kupila.gestaonegocios.ui.kp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ao.kupila.gestaonegocios.ui.comum.Fa

/** Toque sem ondulação Material (a origem não tem ripple). */
fun Modifier.toque(enabled: Boolean = true, aoTocar: () -> Unit) = this.clickable(enabled = enabled, interactionSource = null, indication = null, onClick = aoTocar)

/** `<label class="block text-xs font-semibold text-gray-700 mb-1">` com o tamanho real do ecrã. */
@Composable
fun KpRotulo(texto: String, tamanho: Float = 8.5f, obrigatorio: Boolean = false, cor: Color = Kp.gray700) =
    T(if (obrigatorio) "$texto *" else texto, tamanho, cor, 600, Modifier.padding(bottom = 2.dp))

/** `<input class="w-full bg-white border border-gray-300 rounded-xl p-3 text-xs">` (o valor fica a 16 px como no WebView). */
@Composable
fun KpEntrada(
    valor: String, aoMudar: (String) -> Unit, modifier: Modifier = Modifier, dica: String = "", altura: Dp = 33.dp, raio: Dp = 9.dp,
    tamanhoTexto: Float = 16f, teclado: KeyboardType = KeyboardType.Text, ime: ImeAction = ImeAction.Next, aoConcluir: (() -> Unit)? = null,
    senha: Boolean = false, erro: Boolean = false, prefixo: String? = null, direita: (@Composable () -> Unit)? = null, linhas: Int = 1, activo: Boolean = true,
    padH: Dp = 9.dp,
) {
    val marca = LocalMarca.current
    var foco by remember { mutableStateOf(false) }
    BasicTextField(
        valor, aoMudar, modifier.fillMaxWidth().then(if (linhas == 1) Modifier.height(altura) else Modifier.heightIn(min = altura))
            .onFoco { foco = it },
        enabled = activo, singleLine = linhas == 1, minLines = linhas,
        textStyle = TextStyle(fontSize = tamanhoTexto.sp, color = Kp.gray900),
        cursorBrush = SolidColor(marca.c600),
        visualTransformation = if (senha) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (senha) KeyboardType.Password else teclado, imeAction = ime),
        keyboardActions = KeyboardActions(onDone = { aoConcluir?.invoke() }, onGo = { aoConcluir?.invoke() }, onSend = { aoConcluir?.invoke() }),
        decorationBox = { campo ->
            Row(Modifier.fillMaxSize().sup(raio, fundo = if (activo) Color.White else Kp.slate50, borda = if (erro) Kp.red500 else if (foco) marca.c500 else Kp.gray300)
                .padding(horizontal = padH, vertical = if (linhas == 1) 0.dp else 5.dp), verticalAlignment = if (linhas == 1) Alignment.CenterVertically else Alignment.Top) {
                prefixo?.let { T(it, 9f, Kp.gray400, 700, Modifier.padding(end = 6.dp)) }
                Box(Modifier.weight(1f), contentAlignment = if (linhas == 1) Alignment.CenterStart else Alignment.TopStart) {
                    if (valor.isEmpty() && dica.isNotEmpty()) T(dica, tamanhoTexto, Kp.gray400, max = linhas)
                    campo()
                }
                direita?.invoke()
            }
        },
    )
}

private fun Modifier.onFoco(f: (Boolean) -> Unit) = this.onFocusChanged { f(it.isFocused) }

/** Botão principal `bg-brand-600 text-white font-bold py-3 rounded-xl shadow-lg` (sombra da cor da marca). */
@Composable
fun KpBotao(
    texto: String, aoTocar: () -> Unit, modifier: Modifier = Modifier, icone: String? = null, ocupado: Boolean = false, activo: Boolean = true,
    fundo: Color? = null, pincel: androidx.compose.ui.graphics.Brush? = null, cor: Color = Color.White, borda: Color? = null, altura: Dp = 31.dp, raio: Dp = 9.dp,
    tamanho: Float = 9.5f, sombra: Boolean = true, iconeRegular: Boolean = false,
) {
    val marca = LocalMarca.current
    val f = fundo ?: marca.c600
    Row(modifier.fillMaxWidth().height(altura)
        .sup(raio, fundo = if (pincel == null) (if (activo) f else f.copy(alpha = .55f)) else null, pincel = pincel, borda = borda,
            sombra = if (sombra && borda == null && activo) f.copy(alpha = .28f) else null, desfoque = 20.dp)
        .toque(activo && !ocupado, aoTocar).padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        if (ocupado) { CircularProgressIndicator(Modifier.size((tamanho + 2).dp), color = cor, strokeWidth = 1.5.dp); Spacer(Modifier.width(6.dp)) }
        else if (icone != null) { FaIcone(icone, tamanho, cor, regular = iconeRegular); Spacer(Modifier.width(6.dp)) }
        T(texto, tamanho, cor, 700)
    }
}

/** Botão secundário `bg-white border border-brand-200 text-brand-700`. */
@Composable
fun KpBotaoSec(texto: String, aoTocar: () -> Unit, modifier: Modifier = Modifier, icone: String? = null, activo: Boolean = true, altura: Dp = 31.dp, tamanho: Float = 9.5f, cor: Color? = null, borda: Color? = null) {
    val marca = LocalMarca.current
    KpBotao(texto, aoTocar, modifier, icone, activo = activo, fundo = Color.White, cor = cor ?: marca.c700, borda = borda ?: marca.c200, altura = altura, tamanho = tamanho, sombra = false)
}

/** Caixa informativa `bg-*-50 border border-*-100 rounded-2xl p-3`. */
@Composable
fun KpCaixa(fundo: Color, borda: Color, modifier: Modifier = Modifier, raio: Dp = 12.dp, pad: Dp = 9.dp, conteudo: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().sup(raio, fundo = fundo, borda = borda).padding(pad), verticalArrangement = Arrangement.spacedBy(4.dp), content = conteudo)
}

/** Selector nativo `<select>` com o mesmo aspecto do campo. */
@Composable
fun <V> KpSelect(
    opcoes: List<Pair<V, String>>, valor: V?, aoEscolher: (V) -> Unit, modifier: Modifier = Modifier, altura: Dp = 33.dp, tamanho: Float = 16f, raio: Dp = 9.dp,
    vazio: String = "Seleccione", activo: Boolean = true, corTexto: Color = Kp.gray900, borda: Color = Kp.gray300, peso: Int = 400, centrar: Boolean = false,
) {
    var aberto by remember { mutableStateOf(false) }
    val rotulo = opcoes.firstOrNull { it.first == valor }?.second ?: opcoes.firstOrNull { it.first == null }?.second ?: vazio
    Box(modifier) {
        Row(Modifier.fillMaxWidth().height(altura).sup(raio, fundo = Color.White, borda = borda).toque(activo) { aberto = true }.padding(start = 9.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            T(rotulo, tamanho, if (valor == null && opcoes.none { it.first == null }) Kp.gray400 else corTexto, peso, max = 1, centro = centrar, modifier = Modifier.weight(1f))
            FaIcone(Fa.CHEVRON_DOWN, tamanho * .6f, corTexto)
        }
        DropdownMenu(aberto, { aberto = false }) {
            opcoes.forEach { (v, r) -> DropdownMenuItem(text = { T(r, 13f, Kp.gray900) }, onClick = { aoEscolher(v); aberto = false }) }
        }
    }
}
/**
 * Folha modal da origem: `fixed inset-0 bg-black/55 flex items-end` + `rounded-t-3xl` + cabeçalho `bg-slate-950` com
 * kicker em maiúsculas, título com ícone e botão fechar circular `bg-white/10`.
 */
@Composable
fun KpFolha(
    kicker: String, titulo: String, aoFechar: () -> Unit, modifier: Modifier = Modifier, iconeTitulo: String? = null, corIcone: Color = Kp.yellow400,
    subtitulo: String? = null, corKicker: Color = Kp.slate400, fundoCabecalho: Color = Kp.slate950, fundo: Color = Color.White, alturaMax: Float = .92f,
    rodape: (@Composable ColumnScope.() -> Unit)? = null, conteudo: @Composable ColumnScope.() -> Unit,
) {
    Dialog(aoFechar, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .55f)).toque(aoTocar = aoFechar), contentAlignment = Alignment.BottomCenter) {
            Column(modifier.fillMaxWidth().heightIn(max = (LocalConfiguration.current.screenHeightDp * alturaMax).dp).sup(0.dp, fundo = fundo, forma = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .toque { }.navigationBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().background(fundoCabecalho).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        if (kicker.isNotEmpty()) T(kicker, 9f, corKicker, 700, espacamento = 1.44f, maiusculas = true)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            iconeTitulo?.let { FaIcone(it, 14f, corIcone); Spacer(Modifier.width(6.dp)) }
                            T(titulo, 14f, Color.White, 700, max = 1)
                        }
                        subtitulo?.let { T(it, 8f, Kp.slate400, max = 1, modifier = Modifier.padding(top = 2.dp)) }
                    }
                    KpFechar(aoFechar)
                }
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = conteudo)
                rodape?.let { Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), content = it) }
            }
        }
    }
}

@Composable
fun KpFechar(aoFechar: () -> Unit, tamanho: Dp = 36.dp, fundo: Color = Color.White.copy(alpha = .10f), cor: Color = Color.White) {
    Box(Modifier.size(tamanho).sup(tamanho, fundo = fundo, borda = Color.White.copy(alpha = .14f), forma = CircleShape).toque(aoTocar = aoFechar), contentAlignment = Alignment.Center) {
        FaIcone(Fa.XMARK, 12f, cor)
    }
}

/** Pílula/etiqueta (`text-[8px] font-bold px-2 py-1 rounded-full`). */
@Composable
fun KpPilula(texto: String, fundo: Color, cor: Color, modifier: Modifier = Modifier, tamanho: Float = 8f, peso: Int = 700, borda: Color? = null, icone: String? = null, pad: PaddingValues = PaddingValues(horizontal = 8.dp, vertical = 3.dp)) {
    Row(modifier.sup(999.dp, fundo = fundo, borda = borda).padding(pad), verticalAlignment = Alignment.CenterVertically) {
        icone?.let { FaIcone(it, tamanho, cor); Spacer(Modifier.width(3.dp)) }
        T(texto, tamanho, cor, peso, max = 1)
    }
}

@Composable
fun KpLinha(cor: Color = Kp.gray200, modifier: Modifier = Modifier) = Box(modifier.fillMaxWidth().height(1.dp).background(cor))


/**
 * Cabeçalho dos modais da origem: `bg-slate-950 text-white px-4 py-3.5` com kicker em maiúsculas (tracking .16em), título com ícone,
 * conteúdo opcional à direita e o botão fechar circular.
 */
@Composable
fun KpCabecalhoModal(
    kicker: String, titulo: String, aoFechar: () -> Unit, icone: String? = null, corIcone: Color = Kp.yellow300, iconeRegular: Boolean = false,
    subtitulo: String? = null, fundo: Color = Kp.slate950, corKicker: Color = Kp.slate400, direita: (@Composable () -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth().background(fundo).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            if (kicker.isNotEmpty()) T(kicker, 9f, corKicker, 700, espacamento = 1.44f, maiusculas = true)
            Row(verticalAlignment = Alignment.CenterVertically) {
                icone?.let { FaIcone(it, 11f, corIcone, regular = iconeRegular); Spacer(Modifier.width(6.dp)) }
                T(titulo, 11f, Color.White, 700, max = 1)
            }
            subtitulo?.let { T(it, 8f, Kp.slate400, Modifier.padding(top = 2.dp), max = 1) }
        }
        direita?.let { it(); Spacer(Modifier.width(8.dp)) }
        KpFechar(aoFechar)
    }
}
