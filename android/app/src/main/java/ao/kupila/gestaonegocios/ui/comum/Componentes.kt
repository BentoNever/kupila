package ao.kupila.gestaonegocios.ui.comum

import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ao.kupila.gestaonegocios.ui.tema.LocalCoresKupila
import coil3.compose.AsyncImage

/** Campo de texto com erro por baixo; nunca apaga o conteúdo ao validar (secção 6). */
@Composable
fun Campo(
    valor: String, aoMudar: (String) -> Unit, rotulo: String, modifier: Modifier = Modifier,
    erro: String? = null, ajuda: String? = null, obrigatorio: Boolean = false, teclado: KeyboardType = KeyboardType.Text,
    linhas: Int = 1, maxCaracteres: Int? = null, enabled: Boolean = true, accaoIme: ImeAction = ImeAction.Next,
    aoConcluir: (() -> Unit)? = null, prefixo: String? = null,
) {
    OutlinedTextField(
        value = valor,
        onValueChange = { novo -> aoMudar(if (maxCaracteres != null) novo.take(maxCaracteres) else novo) },
        label = { Text(if (obrigatorio) "$rotulo *" else rotulo) },
        modifier = modifier.fillMaxWidth(),
        isError = erro != null, enabled = enabled,
        singleLine = linhas == 1, minLines = linhas, maxLines = if (linhas == 1) 1 else linhas + 4,
        keyboardOptions = KeyboardOptions(keyboardType = teclado, imeAction = accaoIme),
        keyboardActions = KeyboardActions(onDone = { aoConcluir?.invoke() }, onGo = { aoConcluir?.invoke() }),
        prefix = prefixo?.let { { Text(it) } },
        supportingText = when {
            erro != null -> { { Text(erro) } }
            ajuda != null || maxCaracteres != null -> { { Text(listOfNotNull(ajuda, maxCaracteres?.let { "${valor.length}/$it" }).joinToString(" · ")) } }
            else -> null
        },
    )
}

@Composable
fun CampoSenha(valor: String, aoMudar: (String) -> Unit, rotulo: String, erro: String? = null, accaoIme: ImeAction = ImeAction.Done, aoConcluir: (() -> Unit)? = null) {
    var visivel by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = valor, onValueChange = aoMudar, label = { Text(rotulo) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
        isError = erro != null, supportingText = erro?.let { { Text(it) } },
        visualTransformation = if (visivel) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = accaoIme),
        keyboardActions = KeyboardActions(onDone = { aoConcluir?.invoke() }),
        trailingIcon = {
            IconButton(onClick = { visivel = !visivel }) {
                Icon(if (visivel) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (visivel) "Ocultar palavra-passe" else "Mostrar palavra-passe")
            }
        },
    )
}

/** Selector (lista fechada) acessível; `opcoes` = (valor, rótulo). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> Seletor(
    rotulo: String, opcoes: List<Pair<T, String>>, seleccionado: T?, aoEscolher: (T) -> Unit,
    modifier: Modifier = Modifier, erro: String? = null, obrigatorio: Boolean = false, enabled: Boolean = true, vazio: String = "Seleccione",
) {
    var aberto by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = aberto, onExpandedChange = { if (enabled) aberto = it }, modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = opcoes.firstOrNull { it.first == seleccionado }?.second ?: "", onValueChange = {}, readOnly = true, enabled = enabled,
            label = { Text(if (obrigatorio) "$rotulo *" else rotulo) }, placeholder = { Text(vazio) }, isError = erro != null,
            supportingText = erro?.let { { Text(it) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = aberto) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled),
        )
        ExposedDropdownMenu(expanded = aberto, onDismissRequest = { aberto = false }) {
            opcoes.forEach { (v, r) -> DropdownMenuItem(text = { Text(r) }, onClick = { aoEscolher(v); aberto = false }) }
        }
    }
}

@Composable
fun BotaoPrincipal(texto: String, aoClicar: () -> Unit, modifier: Modifier = Modifier, ocupado: Boolean = false, enabled: Boolean = true, icone: ImageVector? = null) {
    Button(onClick = aoClicar, enabled = enabled && !ocupado, modifier = modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(14.dp)) {
        if (ocupado) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary); Spacer(Modifier.width(10.dp)) }
        else if (icone != null) { Icon(icone, null); Spacer(Modifier.width(8.dp)) }
        Text(texto, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun BotaoSecundario(texto: String, aoClicar: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icone: ImageVector? = null) {
    OutlinedButton(onClick = aoClicar, enabled = enabled, modifier = modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) {
        if (icone != null) { Icon(icone, null); Spacer(Modifier.width(8.dp)) }
        Text(texto)
    }
}

enum class TipoAviso { INFO, AVISO, ERRO }

/** Caixa de estado contextual (erro recuperável, aviso, informação) com acção seguinte opcional. */
@Composable
fun Aviso(texto: String, tipo: TipoAviso = TipoAviso.INFO, modifier: Modifier = Modifier, titulo: String? = null, accao: Pair<String, () -> Unit>? = null) {
    val cores = LocalCoresKupila.current
    val (cor, icone) = when (tipo) {
        TipoAviso.INFO -> MaterialTheme.colorScheme.primary to Icons.Filled.Info
        TipoAviso.AVISO -> cores.aviso to Icons.Filled.WarningAmber
        TipoAviso.ERRO -> MaterialTheme.colorScheme.error to Icons.Filled.ErrorOutline
    }
    Surface(color = cor.copy(alpha = 0.10f), shape = RoundedCornerShape(14.dp), modifier = modifier.fillMaxWidth().border(1.dp, cor.copy(alpha = 0.35f), RoundedCornerShape(14.dp))) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icone, null, tint = cor)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                titulo?.let { Text(it, style = MaterialTheme.typography.titleSmall, color = cor) }
                Text(texto, style = MaterialTheme.typography.bodyMedium)
                accao?.let { (r, f) -> androidx.compose.material3.TextButton(onClick = f) { Text(r) } }
            }
        }
    }
}

@Composable
fun Seccao(titulo: String, modifier: Modifier = Modifier, subtitulo: String? = null, conteudo: @Composable () -> Unit) {
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            subtitulo?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            conteudo()
        }
    }
}

@Composable
fun LinhaInfo(rotulo: String, valor: String, destaque: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Text(rotulo, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(valor, style = if (destaque) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            fontWeight = if (destaque) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

@Composable
fun EstadoVazio(titulo: String, texto: String, modifier: Modifier = Modifier, accao: Pair<String, () -> Unit>? = null) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(titulo, style = MaterialTheme.typography.titleMedium)
        Text(texto, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        accao?.let { (r, f) -> OutlinedButton(onClick = f) { Text(r) } }
    }
}

@Composable
fun Carregando(texto: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator(); Spacer(Modifier.size(12.dp)); Text(texto, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Marca Kupila: "K" num quadrado branco arredondado (origem 6502) ou o logótipo configurado pelo Administrador. */
@Composable
fun Logotipo(tamanho: Int = 56, logoUrl: String? = null) {
    Box(Modifier.size(tamanho.dp).clip(RoundedCornerShape((tamanho / 4).dp)).background(Color.White).semantics { contentDescription = "Kupila-Gestão de negócios" },
        contentAlignment = Alignment.Center) {
        if (logoUrl != null) AsyncImage(model = logoUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Text("K", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black), color = Color(0xFF15803D))
    }
}

/** Selo pequeno (categoria, modalidade, disponibilidade, patrocinado). */
@Composable
fun Selo(texto: String, cor: Color, sobreCor: Color = Color.White, modifier: Modifier = Modifier) {
    Text(texto, color = sobreCor, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = modifier.clip(RoundedCornerShape(50)).background(cor).padding(horizontal = 10.dp, vertical = 4.dp))
}

/** Modelo para o Coil: URL https ou data:image legado (descodificado para bytes). */
fun modeloImagem(url: String): Any = if (url.startsWith("data:")) runCatching {
    Base64.decode(url.substringAfter(','), Base64.DEFAULT)
}.getOrDefault(url) else url
