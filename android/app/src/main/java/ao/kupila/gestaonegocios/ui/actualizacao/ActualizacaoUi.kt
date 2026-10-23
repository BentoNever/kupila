package ao.kupila.gestaonegocios.ui.actualizacao

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ao.kupila.gestaonegocios.BuildConfig
import ao.kupila.gestaonegocios.dados.actualizacao.ActualizadorApp
import ao.kupila.gestaonegocios.dominio.actualizacao.DistribuicaoApp
import ao.kupila.gestaonegocios.dominio.actualizacao.PoliticaVersao
import ao.kupila.gestaonegocios.ui.ajuda.EcraComTopo
import ao.kupila.gestaonegocios.ui.comum.Seccao
import ao.kupila.gestaonegocios.ui.tema.LocalCoresKupila

private fun mb(b: Long) = "%.1f".format(b / 1_048_576.0).replace('.', ',')
private fun tempo(s: Long) = when { s < 0 -> "a calcular…"; s < 60 -> "~$s s"; s < 3600 -> "~${(s + 30) / 60} min"; else -> "~${s / 3600} h ${(s % 3600) / 60} min" }

/**
 * Faixa de actualização (como a do PDV Digital): avisa a nova versão, um toque descarrega em segundo plano, mostra MB, velocidade e
 * tempo estimado, depois a instalação em %. Sem nada a fazer, não ocupa espaço.
 */
@Composable
fun FaixaActualizacao(abrirEcra: () -> Unit) {
    val c = LocalContext.current
    val estado by ActualizadorApp.estado.collectAsStateWithLifecycle()
    val nova by ActualizadorApp.disponivel.collectAsStateWithLifecycle()
    val cores = LocalCoresKupila.current
    val visivel = estado.ocupado || (estado.erro && nova != null) || (nova != null && !estado.terminou)
    val actualizar = { if (c.packageManager.canRequestPackageInstalls()) ActualizadorApp.descarregarEInstalar(c) else abrirEcra() }
    AnimatedVisibility(visivel, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary).padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.14f))
            .clickable { if (estado.ocupado) abrirEcra() else actualizar() }.padding(horizontal = 12.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.SystemUpdate, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                val titulo = when {
                    estado.ocupado && estado.fase == "instalar" -> "A instalar a actualização · ${estado.percentagem}%"
                    estado.ocupado -> "A descarregar ${nova?.nome ?: "a actualização"}"
                    estado.erro -> "Actualização interrompida"
                    else -> "Nova versão ${nova?.nome} disponível"
                }
                Text(titulo, color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (!estado.ocupado) Box(Modifier.clip(CircleShape).background(Color.White).padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Text(if (estado.erro) (if (estado.podeRetomarDownload) "Continuar" else "Tentar") else "Actualizar", color = Color(0xFF111816), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                }
            }
            if (estado.ocupado) {
                val alvo = if (estado.fase == "download" && estado.total > 0) estado.bytes.toFloat() / estado.total else estado.percentagem / 100f
                val p by animateFloatAsState(alvo.coerceIn(0f, 1f), label = "faixaActualizacao")
                val cor = if (estado.fase == "instalar") cores.sucesso else cores.destaque
                Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f))) {
                    Box(Modifier.fillMaxWidth(p).height(6.dp).clip(CircleShape).background(Brush.horizontalGradient(listOf(cor.copy(alpha = 0.75f), cor, Color.White))))
                }
                Text(if (estado.fase == "download" && estado.total > 0) "${mb(estado.bytes)} de ${mb(estado.total)} MB · ${if (estado.bytesPorSeg > 0) "${mb(estado.bytesPorSeg)} MB/s · " else ""}${tempo(estado.etaSeg)}"
                    else estado.etapa, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else if (estado.erro) Text(estado.etapa, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall, maxLines = 2)
            else nova?.novidades?.firstOrNull()?.let { Text("Novidade: $it", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

/** Aviso único por versão, sem interromper o trabalho. */
@Composable
fun DialogoNovaActualizacao(versao: ActualizadorApp.VersaoDisponivel, adiar: () -> Unit, actualizar: () -> Unit) {
    AlertDialog(onDismissRequest = adiar, icon = { Icon(Icons.Filled.SystemUpdate, null) }, title = { Text("Nova actualização disponível", fontWeight = FontWeight.Bold) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Kupila-Gestão de negócios ${versao.nome}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("${mb(versao.tamanho)} MB · dados e sessão preservados", style = MaterialTheme.typography.bodySmall)
            if (versao.novidades.isNotEmpty()) { Text("O que melhora", fontWeight = FontWeight.SemiBold); versao.novidades.take(6).forEach { Text("• $it") } }
            else Text("Inclui melhorias de estabilidade, segurança e desempenho.")
            Text("A verificação confirma a assinatura e a versão antes de instalar.", style = MaterialTheme.typography.bodySmall)
        } },
        dismissButton = { OutlinedButton(onClick = adiar) { Text("Mais tarde") } }, confirmButton = { Button(onClick = actualizar) { Text("Actualizar") } })
}

/** Ecrã de actualização (procurar, descarregar, instalar, APK manual), portado do PDV Digital. */
@Composable
fun EcraActualizacao(voltar: () -> Unit) {
    val c = LocalContext.current
    LaunchedEffect(Unit) { ActualizadorApp.inicializar(c); ActualizadorApp.verificar(c, forcar = true) }
    val estado by ActualizadorApp.estado.collectAsStateWithLifecycle()
    val confirmacao by ActualizadorApp.confirmacao.collectAsStateWithLifecycle()
    val disponivel by ActualizadorApp.disponivel.collectAsStateWithLifecycle()
    val verificacao by ActualizadorApp.verificacao.collectAsStateWithLifecycle()
    val resultado by ActualizadorApp.resultadoVerificacao.collectAsStateWithLifecycle()
    val aVerificar by ActualizadorApp.aVerificar.collectAsStateWithLifecycle()
    val cores = LocalCoresKupila.current
    var origem by rememberSaveable { mutableStateOf<String?>(null) }
    var online by rememberSaveable { mutableStateOf(false) }
    var aviso by rememberSaveable { mutableStateOf<String?>(null) }
    val progresso by animateFloatAsState(estado.percentagem / 100f, label = "actualizacao")
    val autorizar = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (c.packageManager.canRequestPackageInstalls()) {
            if (online) { online = false; ActualizadorApp.descarregarEInstalar(c) }
            origem?.let { ActualizadorApp.instalar(c, Uri.parse(it)); origem = null }
        } else aviso = "A autorização não foi concedida. A versão actual continua disponível."
    }
    val pedirAutorizacao: () -> Unit = {
        runCatching { autorizar.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${c.packageName}"))) }
            .onFailure { aviso = "Abra as definições do Android e autorize actualizações pelo Kupila-Gestão de negócios." }
    }
    val actualizarAgora: () -> Unit = { aviso = null; if (c.packageManager.canRequestPackageInstalls()) ActualizadorApp.descarregarEInstalar(c) else { online = true; pedirAutorizacao() } }
    val escolher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { origem = uri.toString(); aviso = null
            if (c.packageManager.canRequestPackageInstalls()) { ActualizadorApp.instalar(c, uri); origem = null } else pedirAutorizacao() }
    }
    LaunchedEffect(confirmacao) { confirmacao?.let { p -> runCatching { c.startActivity(p) }.onSuccess { ActualizadorApp.confirmacao.value = null }.onFailure { aviso = "Toque em Confirmar no Android para continuar." } } }
    EcraComTopo("Actualização do app", voltar) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.SystemUpdate, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Kupila-Gestão de negócios", style = MaterialTheme.typography.titleLarge)
                    Text("Instalada: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.bodySmall)
                }
            }
            val v = disponivel
            when {
                !estado.ocupado && !aVerificar && resultado?.situacao == PoliticaVersao.Situacao.ACTUALIZADA -> Seccao("O app já está atualizado") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.CheckCircle, null, tint = cores.sucesso); Text("A versão ${resultado?.publicada?.nome} é a última publicada.")
                    }
                }
                !estado.ocupado && aVerificar -> Seccao("A confirmar a versão mais recente…") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                !estado.ocupado && verificacao?.startsWith("Não foi possível") == true -> Seccao("Verificação indisponível") { Text(verificacao.orEmpty()) }
                estado.ocupado || estado.erro -> Seccao(if (estado.terminou) "Actualizado" else if (estado.erro) "Actualização interrompida" else "A actualizar… ${estado.percentagem}%") {
                    LinearProgressIndicator(progress = { progresso }, modifier = Modifier.fillMaxWidth().height(8.dp), color = if (estado.erro) MaterialTheme.colorScheme.error else cores.sucesso)
                    Text(estado.etapa, style = MaterialTheme.typography.bodySmall)
                    if (estado.ocupado && estado.fase == "download" && estado.total > 0)
                        Text("${mb(estado.bytes)} de ${mb(estado.total)} MB · ${if (estado.bytesPorSeg > 0) "${mb(estado.bytesPorSeg)} MB/s · " else ""}falta ${tempo(estado.etaSeg)}", fontWeight = FontWeight.SemiBold)
                    if (estado.erro && estado.podeRetomarDownload) Text("O que já foi descarregado fica guardado: continuar não gasta os dados outra vez.", style = MaterialTheme.typography.bodySmall, color = cores.sucesso)
                    if (estado.ocupado) Text("Descarregar → verificar assinatura → instalar. Pode continuar a usar o telemóvel.", style = MaterialTheme.typography.bodySmall)
                }
                v != null -> Seccao("Nova versão ${v.nome}", subtitulo = if (v.tamanho > 0) "${mb(v.tamanho)} MB · os seus dados ficam intactos" else null) {
                    v.novidades.take(8).forEach { Text("• $it") }
                }
                else -> Seccao(verificacao ?: "A procurar actualizações…") {}
            }
            if (v != null && !estado.ocupado) Button(onClick = actualizarAgora, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(if (estado.erro && estado.podeRetomarDownload) "Continuar o download" else if (estado.erro) "Tentar novamente" else "Actualizar agora")
            }
            confirmacao?.let { p -> Button(onClick = { runCatching { c.startActivity(p) }.onSuccess { ActualizadorApp.confirmacao.value = null } }, modifier = Modifier.fillMaxWidth()) { Text("Confirmar no Android") } }
            if (estado.retomar) OutlinedButton(onClick = { ActualizadorApp.retomar(c) }, modifier = Modifier.fillMaxWidth()) { Text("Continuar instalação") }
            aviso?.let { Text(it, color = cores.aviso) }
            if (!estado.ocupado) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { ActualizadorApp.verificar(c, forcar = true) }, enabled = !aVerificar, modifier = Modifier.weight(1f)) { Text(if (aVerificar) "A verificar…" else "Procurar") }
                OutlinedButton(onClick = { escolher.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream")) }, modifier = Modifier.weight(1f)) { Text("Ficheiro APK") }
            }
            Text("Só são aceites versões oficiais publicadas em ${DistribuicaoApp.RELEASES}: o pacote, a versão e a assinatura são verificados antes da instalação.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
