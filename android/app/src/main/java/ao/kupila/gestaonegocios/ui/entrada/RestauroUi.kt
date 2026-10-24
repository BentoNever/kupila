package ao.kupila.gestaonegocios.ui.entrada

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ao.kupila.gestaonegocios.ui.comum.biometriaDisponivel
import ao.kupila.gestaonegocios.ui.comum.pedirBiometria
import ao.kupila.gestaonegocios.ui.kp.CabecalhoMarca
import ao.kupila.gestaonegocios.ui.kp.LocalMarca
import kotlinx.coroutines.launch

/** Porque a sessão ficou protegida. */
enum class MotivoBloqueio { SAIDA, INACTIVIDADE }

/**
 * RESTAURAR SESSÃO (igual ao PDV Digital): fica POR CIMA do ecrã onde a pessoa estava — ao desbloquear continua exactamente aí
 * (formulário a meio, checkout, painel…). Pede logo a impressão digital; a senha é verificada no aparelho (funciona sem internet).
 * "Outra conta" termina a sessão; os rascunhos ficam no telemóvel.
 */
@Composable
fun EcraRestauro(nome: String, motivo: MotivoBloqueio, desbloquear: suspend (String) -> String?, porBiometria: () -> Unit, trocarConta: () -> Unit) {
    val contexto = LocalContext.current
    val marca = LocalMarca.current
    val temBio = remember { biometriaDisponivel(contexto) }
    val pedir = { pedirBiometria(contexto, "Restaurar sessão", nome, porBiometria) }
    LaunchedEffect(Unit) { if (temBio) pedir() }
    var senha by remember { mutableStateOf("") }
    var erro by remember { mutableStateOf<String?>(null) }
    var aVerificar by remember { mutableStateOf(false) }
    var confirmarTroca by remember { mutableStateOf(false) }
    val escopo = rememberCoroutineScope()
    val primeiro = nome.trim().substringBefore(' ').ifBlank { "de novo" }
    val submeter = {
        if (senha.isNotEmpty() && !aVerificar) {
            aVerificar = true; erro = null
            escopo.launch { erro = desbloquear(senha); aVerificar = false; if (erro != null) senha = "" }
        }
    }
    BackHandler { }   // o "voltar" não fura o restauro
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding()) {
            when (motivo) {
                MotivoBloqueio.SAIDA -> CabecalhoMarca("Restaurar sessão", "A app saiu do ecrã — a sua sessão ficou protegida") { SimboloApp() }
                MotivoBloqueio.INACTIVIDADE -> CabecalhoMarca("Sessão bloqueada", "Por segurança, depois de algum tempo sem uso") { SimboloApp() }
            }
            ElevatedCard(Modifier.padding(horizontal = 20.dp).fillMaxWidth().offset(y = (-48).dp), shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    AvatarPessoa(nome, 68)
                    Text("Olá de novo, $primeiro", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                    val (etiqueta, cor) = when (motivo) {
                        MotivoBloqueio.SAIDA -> "Uso normal: a app foi minimizada ou o ecrã apagou-se." to Color(0xFF2563EB)
                        MotivoBloqueio.INACTIVIDADE -> "Bloqueio por inactividade durante o uso — sem toques há algum tempo." to Color(0xFFCA8A04)
                    }
                    Surface(shape = RoundedCornerShape(18.dp), color = cor.copy(alpha = 0.12f)) {
                        Text(etiqueta, fontSize = 12.sp, color = cor, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                    }
                    Text(if (temBio) "Use a impressão digital ou a senha para continuar exactamente onde estava." else "Introduza a sua senha para continuar exactamente onde estava.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, textAlign = TextAlign.Center)
                    if (temBio) FilledTonalButton(onClick = pedir, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(18.dp)) {
                        Icon(Icons.Filled.Fingerprint, null); Spacer(Modifier.width(8.dp)); Text("Usar impressão digital", fontWeight = FontWeight.Bold)
                    }
                    CampoPdv(senha, { senha = it; erro = null }, "Senha", Icons.Filled.Lock, teclado = KeyboardType.Password, ime = ImeAction.Go, aoConcluir = submeter, senha = true)
                    ErroPdv(erro)
                    BotaoPdv("DESBLOQUEAR", submeter, ocupado = aVerificar, activo = senha.isNotEmpty(), icone = Icons.Filled.LockOpen)
                    TextButton(onClick = { confirmarTroca = true }) { Text("Não é $primeiro? Entrar com outra conta", color = marca.primaria) }
                }
            }
        }
    }
    if (confirmarTroca) AlertDialog(onDismissRequest = { confirmarTroca = false },
        icon = { Icon(Icons.AutoMirrored.Filled.Logout, null, tint = Color(0xFFDC2626)) },
        title = { Text("Entrar com outra conta?") },
        text = { Text("A sessão de $primeiro termina neste telemóvel. Os rascunhos continuam guardados no telemóvel.") },
        confirmButton = { TextButton(onClick = { confirmarTroca = false; trocarConta() }) { Text("TERMINAR E TROCAR", color = Color(0xFFDC2626), fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = { confirmarTroca = false }) { Text("Cancelar") } })
}

/** O símbolo da app no cabeçalho da marca (moldura com a cor de acção, como o ícone do PDV). */
@Composable
internal fun SimboloApp(logo: String? = null) {
    val marca = LocalMarca.current
    Surface(shape = RoundedCornerShape(26.dp), color = Color.White.copy(alpha = 0.06f), border = BorderStroke(1.dp, marca.primaria.copy(alpha = 0.45f)), modifier = Modifier.size(88.dp)) {
        Box(contentAlignment = Alignment.Center) { LogoMarca(68f, 20f, 36f, logo) }
    }
}
