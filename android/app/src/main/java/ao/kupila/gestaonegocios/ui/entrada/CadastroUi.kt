package ao.kupila.gestaonegocios.ui.entrada

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ao.kupila.gestaonegocios.dominio.perfis.CanalAlerta
import ao.kupila.gestaonegocios.dominio.perfis.Papel
import ao.kupila.gestaonegocios.ui.comum.Aviso
import ao.kupila.gestaonegocios.ui.comum.BotaoPrincipal
import ao.kupila.gestaonegocios.ui.comum.BotaoSecundario
import ao.kupila.gestaonegocios.ui.comum.Campo
import ao.kupila.gestaonegocios.ui.comum.CampoSenha
import ao.kupila.gestaonegocios.ui.comum.Seletor
import ao.kupila.gestaonegocios.ui.comum.TipoAviso
import coil3.compose.AsyncImage
import java.io.File

/** Ecrã "Criar conta" (HTML 6547-6571). O cadastro público só cria Cliente ou Proprietário. */
@Composable
fun CadastroEcra(vm: CadastroViewModel, logo: String?, aoVoltar: () -> Unit, aoLerPoliticas: () -> Unit) {
    val e by vm.estado.collectAsStateWithLifecycle()
    val f = e.form
    val escolher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(vm::foto) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).imePadding()) {
        CabecalhoMarca(logo, if (e.completar) "Concluir cadastro" else "Criar conta")
        Column(Modifier.widthIn(max = 560.dp).align(Alignment.CenterHorizontally).padding(20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (e.completar) Aviso("A conta já foi criada mas o cadastro não terminou. Complete os dados para continuar.", TipoAviso.INFO)
            Text("Perfil *", style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Papel.publicos.forEachIndexed { i, p ->
                    SegmentedButton(selected = f.papel == p, onClick = { vm.papel(p) }, shape = SegmentedButtonDefaults.itemShape(i, Papel.publicos.size)) { Text(p.rotulo) }
                }
            }
            Text(if (f.papel == Papel.CLIENTE) "O Cliente pesquisa e reserva produtos." else "O Proprietário também anuncia e gere os seus próprios produtos.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Campo(f.nome, vm::nome, "Nome completo", obrigatorio = true, erro = e.erros["nome"], maxCaracteres = 120)
            Campo(f.email, vm::email, "E-mail", obrigatorio = true, teclado = KeyboardType.Email, erro = e.erros["email"], enabled = !e.completar)
            Campo(f.telefone, vm::telefone, "Telefone principal", obrigatorio = true, teclado = KeyboardType.Phone, prefixo = "+244 ", erro = e.erros["telefone"])
            if (f.papel == Papel.PROPRIETARIO) {
                Text("Foto do Proprietário *", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(88.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(onClickLabel = "Escolher foto") { escolher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        contentAlignment = Alignment.Center) {
                        when {
                            e.aProcessarFoto -> CircularProgressIndicator(Modifier.size(28.dp))
                            f.fotoCaminho != null -> AsyncImage(File(f.fotoCaminho), "Foto seleccionada", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            else -> Icon(Icons.Filled.AddAPhoto, null)
                        }
                    }
                    Text("JPG, PNG ou WebP até 2 MB. A foto é optimizada no dispositivo.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                }
                e.erros["foto"]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Seletor("Província", vm.geografia.provincias.map { it to it }, f.provincia, vm::provincia, obrigatorio = true)
                Seletor("Município", vm.geografia.municipios(f.provincia).map { it to it }, f.municipio.ifEmpty { null }, vm::municipio, obrigatorio = true, erro = e.erros["municipio"])
                Campo(f.bairro, vm::bairro, "Bairro", obrigatorio = true, erro = e.erros["bairro"], maxCaracteres = 100)
                Campo(f.telefoneAlt, vm::telefoneAlt, "Telefone alternativo", teclado = KeyboardType.Phone, prefixo = "+244 ", erro = e.erros["telefoneAlt"])
                Seletor("Canal preferido para alertas", CanalAlerta.entries.map { it to it.rotulo }, f.canal, vm::canal)
            }
            if (!e.completar) {
                CampoSenha(f.senha, vm::senha, "Palavra-passe *", erro = e.erros["senha"], accaoIme = ImeAction.Next)
                CampoSenha(f.confirmar, vm::confirmar, "Confirmar palavra-passe *", erro = e.erros["confirmar"])
            }
            TextButton(onClick = aoLerPoliticas) { Text("Ler Políticas e Regras do Kupila-Gestão de negócios") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = f.politicasAceites, onCheckedChange = vm::aceitar, enabled = f.politicasLidas)
                Text(if (f.politicasLidas) "Li e aceito as Políticas de Uso e Regras de Intermediação." else "Leia as políticas até ao fim para poder aceitar.",
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
            e.erros["politicas"]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            e.erroGeral?.let { Aviso(it, TipoAviso.ERRO) }
            if (e.erros.isNotEmpty()) Aviso("Reveja os campos assinalados. Os dados preenchidos foram preservados.", TipoAviso.AVISO)
            BotaoPrincipal(if (e.completar) "Concluir cadastro" else "Criar conta", vm::enviar, ocupado = e.aEnviar)
            BotaoSecundario("Voltar ao login", aoVoltar)
        }
    }
}
