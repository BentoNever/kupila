package ao.kupila.gestaonegocios.ui.entrada

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ao.kupila.gestaonegocios.R
import ao.kupila.gestaonegocios.dados.sessao.ContaConhecida
import ao.kupila.gestaonegocios.dominio.perfis.Papel
import ao.kupila.gestaonegocios.ui.comum.Aviso
import ao.kupila.gestaonegocios.ui.comum.BotaoPrincipal
import ao.kupila.gestaonegocios.ui.comum.BotaoSecundario
import ao.kupila.gestaonegocios.ui.comum.Campo
import ao.kupila.gestaonegocios.ui.comum.CampoSenha
import ao.kupila.gestaonegocios.ui.comum.Logotipo
import ao.kupila.gestaonegocios.ui.comum.TipoAviso
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Cabeçalho da marca (login/cadastro): cores e logótipo da identidade vigente, já no 1.º frame. */
@Composable
fun CabecalhoMarca(logo: String?, subtitulo: String) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary).statusBarsPadding().padding(vertical = 24.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Logotipo(60, logo)
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimary, textAlign = TextAlign.Center)
        Text(subtitulo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f), textAlign = TextAlign.Center)
    }
}

@Composable
fun LoginEcra(logo: String?, aviso: String?, aoCriarConta: () -> Unit, aoRecuperar: (String) -> Unit, vm: LoginViewModel = hiltViewModel()) {
    val e by vm.estado.collectAsStateWithLifecycle()
    val contas by vm.contas.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).imePadding()) {
        CabecalhoMarca(logo, stringResource(R.string.app_slogan))
        Column(Modifier.widthIn(max = 520.dp).align(Alignment.CenterHorizontally).padding(20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            aviso?.let { Aviso(it, TipoAviso.AVISO) }
            if (contas.isNotEmpty()) {
                Text("Contas neste dispositivo", style = MaterialTheme.typography.titleSmall)
                contas.forEach { c -> CartaoConta(c, aoEscolher = { vm.escolherConta(c) }, aoEsquecer = { vm.esquecerConta(c) }) }
            }
            Campo(e.email, vm::email, "E-mail", teclado = KeyboardType.Email, obrigatorio = true)
            CampoSenha(e.senha, vm::senha, "Palavra-passe", accaoIme = ImeAction.Go, aoConcluir = vm::entrar)
            e.erro?.let { Aviso(it, TipoAviso.ERRO) }
            Text(e.estadoAutomatico, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            BotaoPrincipal("Entrar", vm::entrar, ocupado = e.aEntrar, icone = Icons.AutoMirrored.Filled.Login)
            TextButton(onClick = { aoRecuperar(e.email) }, modifier = Modifier.fillMaxWidth()) { Text("Esqueceu as credenciais?") }
            BotaoSecundario("Criar nova conta", aoCriarConta, icone = Icons.Filled.PersonAdd)
        }
    }
}

@Composable
private fun CartaoConta(c: ContaConhecida, aoEscolher: () -> Unit, aoEsquecer: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClickLabel = "Usar esta conta", onClick = aoEscolher), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                Text(c.nome.split(" ").filter(String::isNotBlank).take(2).joinToString("") { it.take(1).uppercase() }.ifEmpty { "K" },
                    color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f)) {
                Text(c.nome.ifBlank { c.email }, style = MaterialTheme.typography.titleSmall)
                Text("${Papel.canonico(c.papel)?.rotulo ?: "Utilizador"} · ${c.email}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Último acesso: " + formatarData(c.ultimoAcesso), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = aoEsquecer) { Icon(Icons.Filled.Close, "Esquecer esta conta neste dispositivo") }
        }
    }
}

private val LUANDA: TimeZone = TimeZone.getTimeZone("Africa/Luanda")
fun formatarData(ms: Long, comHora: Boolean = true): String = SimpleDateFormat(if (comHora) "dd/MM/yyyy HH:mm" else "dd/MM/yyyy", Locale.forLanguageTag("pt-AO"))
    .apply { timeZone = LUANDA }.format(Date(ms))

/** Conflito de sessão (`modal-single-session-conflict`, HTML 6479-6492). */
@Composable
fun ConflitoSessaoDialogo(dispositivo: String, vistoEm: Long?, ocupado: Boolean, aoContinuar: () -> Unit, aoVoltar: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        icon = { Icon(Icons.Filled.Devices, null) },
        title = { Text("Conta em uso noutro dispositivo") },
        text = {
            Text("Esta conta tem uma sessão activa em “$dispositivo”" + (vistoEm?.let { " (actividade às ${formatarData(it)})" } ?: "") +
                ". Para continuar aqui, a sessão anterior será encerrada e deixará de poder fazer alterações.")
        },
        confirmButton = { TextButton(onClick = aoContinuar, enabled = !ocupado) { Text("Encerrar sessão anterior e continuar") } },
        dismissButton = { TextButton(onClick = aoVoltar, enabled = !ocupado) { Text("Voltar ao login") } },
    )
}

@Composable
fun RecuperacaoEcra(aoVoltar: () -> Unit, vm: RecuperacaoViewModel = hiltViewModel()) {
    val e by vm.estado.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).imePadding()) {
        CabecalhoMarca(null, "Recuperar credenciais")
        Column(Modifier.widthIn(max = 520.dp).align(Alignment.CenterHorizontally).padding(20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Indique o e-mail da conta. Se existir uma conta com este e-mail, receberá um link oficial para definir nova palavra-passe.",
                style = MaterialTheme.typography.bodyMedium)
            Campo(e.email, vm::email, "E-mail da conta", teclado = KeyboardType.Email, accaoIme = ImeAction.Send, aoConcluir = { vm.enviar() })
            e.erro?.let { Aviso(it, TipoAviso.ERRO) }
            if (e.enviado) Aviso("Pedido registado. Se o e-mail estiver associado a uma conta, o link de recuperação será enviado. Verifique também o spam.", TipoAviso.INFO)
            val restante = ((e.esperarAte - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
            BotaoPrincipal(if (e.enviado) (if (restante > 0) "Reenviar em ${restante}s" else "Reenviar link de recuperação") else "Enviar link de recuperação",
                { vm.enviar() }, ocupado = e.aEnviar, enabled = restante <= 0L)
            BotaoSecundario("Voltar ao login", aoVoltar)
            Spacer(Modifier.height(8.dp))
            Text("A palavra-passe nunca é mostrada nem enviada por mensagem.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
