package ao.kupila.gestaonegocios.ui.entrada

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ao.kupila.gestaonegocios.dados.sessao.ContaConhecida
import ao.kupila.gestaonegocios.ui.comum.biometriaDisponivel
import ao.kupila.gestaonegocios.ui.comum.pedirBiometria
import ao.kupila.gestaonegocios.ui.kp.GradienteMarca
import ao.kupila.gestaonegocios.ui.kp.LocalMarca
import ao.kupila.gestaonegocios.ui.kp.LogoSpotec
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Saudação pela hora de Angola (como no PDV Digital). */
fun saudacao(): String = when (java.time.LocalTime.now(java.time.ZoneId.of("Africa/Luanda")).hour) { in 5..11 -> "Bom dia"; in 12..18 -> "Boa tarde"; else -> "Boa noite" }

/** Avatar da pessoa: as iniciais num círculo da cor de acção (sem foto guardada no aparelho). */
@Composable
fun AvatarPessoa(nome: String, tamanho: Int, modifier: Modifier = Modifier) {
    val marca = LocalMarca.current
    Box(modifier.size(tamanho.dp).clip(CircleShape).background(marca.primaria.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
        Text(iniciais(nome), color = marca.primaria, fontWeight = FontWeight.Bold, fontSize = (tamanho * 0.36f).sp)
    }
}

/** Campo de texto do PDV Digital (contorno, canto 14 dp, ícone à esquerda). */
@Composable
internal fun CampoPdv(valor: String, aoMudar: (String) -> Unit, dica: String, icone: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier,
                      teclado: KeyboardType = KeyboardType.Text, ime: ImeAction = ImeAction.Next, aoConcluir: () -> Unit = {}, senha: Boolean = false) {
    var ver by remember { mutableStateOf(false) }
    val marca = LocalMarca.current
    OutlinedTextField(valor, aoMudar, modifier.fillMaxWidth(), placeholder = { Text(dica) }, singleLine = true, shape = RoundedCornerShape(14.dp),
        leadingIcon = { Icon(icone, null) },
        trailingIcon = if (senha) ({ IconButton(onClick = { ver = !ver }) { Icon(if (ver) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (ver) "Esconder senha" else "Mostrar senha") } }) else null,
        visualTransformation = if (senha && !ver) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = teclado, imeAction = ime, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onAny = { aoConcluir() }),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = marca.primaria, cursorColor = marca.primaria, focusedLeadingIconColor = marca.primaria))
}

/** Botão principal do PDV Digital (cor de acção, 56 dp, texto em maiúsculas espaçadas). */
@Composable
internal fun BotaoPdv(texto: String, aoTocar: () -> Unit, ocupado: Boolean = false, activo: Boolean = true, icone: androidx.compose.ui.graphics.vector.ImageVector? = Icons.AutoMirrored.Filled.ArrowForward, textoOcupado: String? = null) {
    val marca = LocalMarca.current
    Button(onClick = aoTocar, enabled = activo && !ocupado, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = marca.primaria, contentColor = marca.sobrePrimaria)) {
        if (ocupado) {
            CircularProgressIndicator(Modifier.size(22.dp), color = marca.sobrePrimaria, strokeWidth = 2.5.dp)
            textoOcupado?.let { Spacer(Modifier.width(10.dp)); Text(it, fontWeight = FontWeight.Bold) }
        } else {
            Text(texto, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
            icone?.let { Spacer(Modifier.width(8.dp)); Icon(it, null) }
        }
    }
}

/** Faixa de erro do PDV Digital. */
@Composable
internal fun ErroPdv(texto: String?) {
    AnimatedVisibility(texto != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.errorContainer) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(10.dp))
                Text(texto.orEmpty(), color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 13.sp, lineHeight = 17.sp)
            }
        }
    }
}

/**
 * Cabeçalho do login (igual ao PDV Digital): o logótipo Spotec em fundo, o símbolo da app e o título. Encolhe com o
 * teclado aberto (o símbolo sai) para o cartão caber sempre sem rolar.
 */
@Composable
private fun CabecalhoLogin(modifier: Modifier, logo: String?) {
    val marca = LocalMarca.current
    BoxWithConstraints(modifier.clip(RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp)).background(GradienteMarca)) {
        val alto = maxHeight
        LogoSpotec(Modifier.align(Alignment.Center).padding(bottom = 30.dp).fillMaxWidth(0.92f)
            .graphicsLayer { alpha = 0.09f; rotationZ = -8f }, sobreEscuro = true)
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 46.dp),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            if (alto >= 250.dp) {
                Surface(shape = RoundedCornerShape(24.dp), color = Color.White.copy(alpha = 0.06f), border = BorderStroke(1.dp, marca.primaria.copy(alpha = 0.45f)),
                    modifier = Modifier.size(76.dp)) {
                    Box(contentAlignment = Alignment.Center) { LogoMarca(60f, 18f, 32f, logo) }
                }
                Spacer(Modifier.height(12.dp))
            }
            Text("Kupila", color = Color.White, style = if (alto >= 180.dp) MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold) else MaterialTheme.typography.titleLarge)
            if (alto >= 150.dp) Text("Gestão de negócios · reservas protegidas", color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp, textAlign = TextAlign.Center)
        }
    }
}

/**
 * LOGIN (igual ao PDV Digital): só e-mail, senha e ENTRAR. A conta reconhecida é cumprimentada pela hora de Angola; as contas
 * deste telemóvel aparecem em pastilhas (um toque → entra com a credencial segura ou pede a impressão digital); a entrada é
 * automática quando as credenciais ficam completas; deslizar para baixo actualiza e volta a tentar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginEcra(logo: String?, aviso: String?, aoCriarConta: () -> Unit, aoRecuperar: ((String) -> Unit)? = null, vm: LoginViewModel = hiltViewModel()) {
    val e by vm.estado.collectAsStateWithLifecycle()
    val contas by vm.contas.collectAsStateWithLifecycle()
    val marca = LocalMarca.current
    val contexto = LocalContext.current
    val escopo = rememberCoroutineScope()
    var recuperar by rememberSaveable { mutableStateOf(false) }
    var avisoVisivel by rememberSaveable(aviso) { mutableStateOf(aviso != null) }
    var aActualizar by remember { mutableStateOf(false) }
    val focoSenha = remember { FocusRequester() }
    val temBio = remember { biometriaDisponivel(contexto) }
    val contaEscrita = contas.firstOrNull { it.email.trim().equals(e.email.trim(), ignoreCase = true) }
    // Impressão digital → entra com a credencial cifrada deste telemóvel (sem escrever a senha).
    val entrarComDedo: (ContaConhecida) -> Unit = { c -> pedirBiometria(contexto, "Entrar no Kupila", c.nome.ifBlank { c.email }) { vm.escolherConta(c) } }
    val tocarConta: (ContaConhecida) -> Unit = { c ->
        if (c.rapido && temBio) { vm.email(c.email); entrarComDedo(c) }
        else if (c.rapido) vm.escolherConta(c)
        else { vm.email(c.email); escopo.launch { delay(150); runCatching { focoSenha.requestFocus() } } }
    }
    // Abre o pedido do dedo sozinho, uma vez, para a última conta usada (sem burocracia).
    var bioPedida by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(contas, temBio) {
        val ultima = contas.firstOrNull { it.rapido } ?: return@LaunchedEffect
        if (temBio && !bioPedida && aviso == null) { bioPedida = true; vm.email(ultima.email); delay(350); entrarComDedo(ultima) }
    }
    PullToRefreshBox(isRefreshing = aActualizar, onRefresh = {
        aActualizar = true
        escopo.launch { if (e.erro != null && e.senha.isNotEmpty()) vm.entrar(); delay(600); aActualizar = false }
    }, modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            val alturaEcra = maxHeight
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Column(Modifier.fillMaxWidth().heightIn(min = alturaEcra).imePadding().navigationBarsPadding()) {
                    CabecalhoLogin(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(min = 150.dp, max = 280.dp), logo)
                    ElevatedCard(Modifier.padding(horizontal = 20.dp).fillMaxWidth().offset(y = (-36).dp), shape = RoundedCornerShape(24.dp),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val primeiro = contaEscrita?.nome?.split(' ')?.firstOrNull { it.isNotBlank() }
                                AnimatedVisibility(primeiro != null, enter = fadeIn() + scaleIn(initialScale = 0.8f), exit = fadeOut()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        AvatarPessoa(contaEscrita?.nome.orEmpty(), 46, Modifier.border(2.dp, marca.primaria, CircleShape)); Spacer(Modifier.width(12.dp))
                                    }
                                }
                                Text(if (primeiro != null) "${saudacao()}, $primeiro!" else "${saudacao()}! Bem-vindo(a)", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.weight(1f), maxLines = 2)
                            }
                            AnimatedVisibility(avisoVisivel && aviso != null) {
                                Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFFCA8A04).copy(alpha = 0.14f), border = BorderStroke(1.dp, Color(0xFFCA8A04).copy(alpha = 0.45f))) {
                                    Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.Top) {
                                        Icon(Icons.Filled.Info, null, tint = Color(0xFFCA8A04)); Spacer(Modifier.width(10.dp))
                                        Text(aviso.orEmpty(), fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
                                        IconButton(onClick = { avisoVisivel = false }) { Icon(Icons.Filled.Close, "Fechar aviso") }
                                    }
                                }
                            }
                            if (contaEscrita?.rapido == true && e.erro == null) Text(if (temBio) "Reconhecemos este telemóvel — toque na impressão digital ou escreva a senha." else "Reconhecemos este telemóvel — basta a sua senha.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 17.sp)
                            // TROCA RÁPIDA: quem já entrou neste telemóvel; um toque entra (dedo ou credencial segura).
                            if (contas.size > 1 || (contas.size == 1 && contaEscrita == null)) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                contas.forEach { c ->
                                    FilterChip(contaEscrita?.uid == c.uid, { tocarConta(c) }, label = { Text(c.nome.split(' ').firstOrNull { it.isNotBlank() } ?: c.email, maxLines = 1) },
                                        leadingIcon = { AvatarPessoa(c.nome.ifBlank { c.email }, 22) },
                                        trailingIcon = { Icon(Icons.Filled.Close, "Esquecer ${c.nome}", Modifier.size(16.dp).clip(CircleShape).clickable { vm.esquecerConta(c) }) })
                                }
                                AssistChip({ vm.email(""); vm.senha("") }, label = { Text("Outra conta") }, leadingIcon = { Icon(Icons.Filled.Person, null, Modifier.size(18.dp)) })
                            }
                            Text("E-mail", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            CampoPdv(e.email, vm::email, "exemplo@kupila.ao", Icons.Filled.Mail, teclado = KeyboardType.Email, aoConcluir = { runCatching { focoSenha.requestFocus() } })
                            Text("Senha", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            CampoPdv(e.senha, vm::senha, "A sua senha", Icons.Filled.Lock, Modifier.focusRequester(focoSenha), teclado = KeyboardType.Password, ime = ImeAction.Go, aoConcluir = vm::entrar, senha = true)
                            ErroPdv(e.erro)
                            BotaoPdv("ENTRAR", vm::entrar, ocupado = e.aEntrar, activo = e.email.isNotBlank() && e.senha.isNotEmpty(), textoOcupado = "A verificar…")
                            if (temBio && contaEscrita?.rapido == true) FilledTonalButton(onClick = { entrarComDedo(contaEscrita) }, enabled = !e.aEntrar, shape = RoundedCornerShape(18.dp),
                                modifier = Modifier.fillMaxWidth().height(48.dp)) {
                                Icon(Icons.Filled.Fingerprint, null); Spacer(Modifier.width(8.dp)); Text("Entrar com a impressão digital", fontWeight = FontWeight.SemiBold)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                TextButton(onClick = { recuperar = true }) { Text("Esqueceu-se da senha?", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = marca.primaria) }
                                TextButton(onClick = aoCriarConta) { Icon(Icons.Filled.PersonAdd, null, Modifier.size(18.dp), tint = marca.primaria); Spacer(Modifier.width(4.dp))
                                    Text("Criar conta", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = marca.primaria) }
                            }
                        }
                    }
                    Spacer(Modifier.weight(0.15f))
                    if (WindowInsets.ime.getBottom(LocalDensity.current) == 0) Column(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Uma solução", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LogoSpotec(Modifier.height(28.dp).padding(top = 4.dp), sobreEscuro = false)
                    }
                }
            }
        }
    }
    if (recuperar) RecuperacaoFolha(e.email, contas, aoFechar = { recuperar = false })
}
