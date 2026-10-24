package ao.kupila.gestaonegocios.ui.kp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ao.kupila.gestaonegocios.ui.comum.Fa
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Tipo do aviso: muda só o ícone/cor (como `showKupilaAlert` da origem). */
enum class TipoAvisoKp(val icone: String, val cor: Color) {
    INFO(Fa.CIRCLE_INFO, Kp.yellow300), SUCESSO(Fa.CIRCLE_CHECK, Color(0xFF4ADE80)), ATENCAO(Fa.TRIANGLE_EXCLAMATION, Kp.amber300), ERRO(Fa.CIRCLE_EXCLAMATION, Color(0xFFFCA5A5))
}

data class AvisoKp(val mensagem: String, val titulo: String = "Aviso Kupila", val tipo: TipoAvisoKp = TipoAvisoKp.INFO, val botao: String = "Entendido")

/**
 * Sistema visual global (`#kupila-alert-overlay` e `#kupila-loading-overlay`, HTML 6409-6429): substitui alertas nativos e
 * indica carregamentos. Qualquer ecrã ou ViewModel chama [Avisos.mostrar] / [Avisos.carregar]; a raiz desenha-os por cima de tudo.
 */
object Avisos {
    private val _aviso = MutableStateFlow<AvisoKp?>(null)
    val aviso = _aviso.asStateFlow()
    private val _carregando = MutableStateFlow<String?>(null)
    val carregando = _carregando.asStateFlow()

    fun mostrar(mensagem: String, titulo: String = "Aviso Kupila", tipo: TipoAvisoKp = TipoAvisoKp.INFO) { _aviso.value = AvisoKp(mensagem, titulo, tipo) }
    fun fechar() { _aviso.value = null }
    fun carregar(texto: String = "A carregar…") { _carregando.value = texto }
    fun concluir() { _carregando.value = null }

    /** Executa com o cartão de carregamento visível (sempre fechado no fim, mesmo com erro). */
    suspend fun <T> comCarregamento(texto: String, bloco: suspend () -> T): T { carregar(texto); try { return bloco() } finally { concluir() } }
}

/** Desenha o aviso e o carregamento globais (colocar uma vez, na raiz). */
@Composable
fun AvisosGlobais() {
    val aviso by Avisos.aviso.collectAsState()
    val texto by Avisos.carregando.collectAsState()
    texto?.let { KpCarregando(it) }
    aviso?.let { a -> KpAlerta(a.mensagem, Avisos::fechar, a.titulo, a.tipo, a.botao) }
}

/** Cartão "A carregar…" (`#kupila-loading-card`): branco, 280 dp, spinner da cor de acção e "Kupila.ao". */
@Composable
fun KpCarregando(texto: String) {
    val marca = LocalMarca.current
    Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Kp.slate950.copy(alpha = .35f)).padding(20.dp), contentAlignment = Alignment.Center) {
            Column(Modifier.widthIn(max = 280.dp).fillMaxWidth().sup(16.dp, fundo = Color.White, borda = Kp.gray100, sombra = Color.Black.copy(.25f), desfoque = 40.dp).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(Modifier.size(34.dp), color = marca.primaria, strokeWidth = 3.dp)
                T(texto, 12f, Kp.gray800, 700, Modifier.padding(top = 12.dp), centro = true)
                T("Kupila.ao", 9f, Kp.gray400, Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** "Aviso Kupila" (`#kupila-alert-overlay`): folha inferior, cabeçalho escuro da marca com ícone, mensagem e "Entendido". */
@Composable
fun KpAlerta(mensagem: String, aoFechar: () -> Unit, titulo: String = "Aviso Kupila", tipo: TipoAvisoKp = TipoAvisoKp.INFO, botao: String = "Entendido") =
    KpDialogo(titulo, aoFechar, tipo.icone, tipo.cor, confirmar = botao to aoFechar) { T(mensagem, 12f, Kp.gray700, linha = 19f) }

/**
 * Diálogo do sistema visual Kupila (mesma estrutura do "Aviso Kupila") com conteúdo livre e até dois botões —
 * substitui os diálogos genéricos do Material.
 */
@Composable
fun KpDialogo(titulo: String, aoFechar: () -> Unit, icone: String = Fa.CIRCLE_INFO, corIcone: Color = Kp.yellow300, subtitulo: String = "Mensagem da aplicação",
              confirmar: Pair<String, () -> Unit>? = null, confirmarActivo: Boolean = true, cancelar: Pair<String, () -> Unit>? = null, perigo: Boolean = false,
              conteudo: @Composable ColumnScope.() -> Unit) {
    val marca = LocalMarca.current
    Dialog(onDismissRequest = aoFechar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .55f)), contentAlignment = Alignment.BottomCenter) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(Color.White).navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().background(if (marca.spotec) gradiente(90f, marca.escuro[0], marca.escuro[2]) else androidx.compose.ui.graphics.SolidColor(marca.c900))
                    .padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).sup(12.dp, fundo = Color.White.copy(alpha = .10f)), contentAlignment = Alignment.Center) { FaIcone(icone, 12f, corIcone) }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) { T(titulo, 14f, Color.White, 700, max = 2); T(subtitulo, 9f, if (marca.spotec) Color.White.copy(.7f) else marca.c100) }
                }
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    conteudo()
                    confirmar?.let { (t, f) ->
                        KpBotao(t, f, fundo = if (perigo) Kp.red600 else marca.primaria, cor = if (perigo) Color.White else marca.sobrePrimaria, altura = 44.dp, raio = 12.dp, tamanho = 12f,
                            activo = confirmarActivo, modifier = Modifier.padding(top = 6.dp))
                    }
                    cancelar?.let { (t, f) -> KpBotaoSec(t, f, altura = 40.dp) }
                }
            }
        }
    }
}
