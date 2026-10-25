package ao.kupila.gestaonegocios.ui.entrada

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ao.kupila.gestaonegocios.dominio.perfis.CanalAlerta
import ao.kupila.gestaonegocios.dominio.perfis.Papel
import ao.kupila.gestaonegocios.ui.comum.Fa
import ao.kupila.gestaonegocios.ui.kp.FaIcone
import ao.kupila.gestaonegocios.ui.kp.Kp
import ao.kupila.gestaonegocios.ui.kp.KpBotao
import ao.kupila.gestaonegocios.ui.kp.KpBotaoSec
import ao.kupila.gestaonegocios.ui.kp.KpCaixa
import ao.kupila.gestaonegocios.ui.kp.KpEntrada
import ao.kupila.gestaonegocios.ui.kp.KpRotulo
import ao.kupila.gestaonegocios.ui.kp.KpSelect
import ao.kupila.gestaonegocios.ui.kp.LocalMarca
import ao.kupila.gestaonegocios.ui.kp.T
import ao.kupila.gestaonegocios.ui.kp.sup
import ao.kupila.gestaonegocios.ui.kp.toque
import coil3.compose.AsyncImage
import java.io.File

/** CADASTRO DE NOVO UTILIZADOR (`#register-screen`, HTML 6544-6581): só Cliente ou Proprietário; Administrador não é criado por cadastro público. */
@Composable
fun CadastroEcra(vm: CadastroViewModel, logo: String?, aoVoltar: () -> Unit, aoLerPoliticas: () -> Unit) {
    val e by vm.estado.collectAsStateWithLifecycle()
    val f = e.form
    val marca = LocalMarca.current
    val escolher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(vm::foto) }
    Box(Modifier.fillMaxSize().background(Kp.slate100).safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(12.dp, 12.dp, 12.dp, 24.dp),
        contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 388.dp).fillMaxWidth().padding(horizontal = 2.dp)
            .sup(24.dp, fundo = Color.White, borda = Kp.gray200, sombra = Color.Black.copy(alpha = .25f), desfoque = 50.dp)) {
            Row(Modifier.fillMaxWidth().background(marca.c700).padding(20.dp, 20.dp, 20.dp, 16.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(36.dp).sup(36.dp, fundo = Color.White.copy(alpha = .10f), forma = CircleShape).toque(aoTocar = aoVoltar), contentAlignment = Alignment.Center) {
                    FaIcone(Fa.ARROW_LEFT, 14f, Color.White)
                }
                Column {
                    T(if (e.completar) "Concluir cadastro" else "Criar conta Kupila", 16f, Color.White, 700, linha = 24f)
                    T(if (e.completar) "Complete os dados para continuar" else "Cadastro seguro no Kupila", 10f, marca.c100, linha = 15f)
                }
            }
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (e.completar) KpCaixa(marca.c50, marca.c100) { T("A conta já foi criada mas o cadastro não terminou. Complete os dados para continuar.", 10f, marca.c900, linha = 15f) }
                Campo("Perfil", true) {
                    KpSelect(Papel.publicos.map { it to it.rotulo }, f.papel, vm::papel, altura = 41.dp, raio = 12.dp)
                }
                Campo("Nome completo", true, e.erros["nome"]) { KpEntrada(f.nome, vm::nome, altura = 42.dp, raio = 12.dp, padH = 10.dp) }
                Campo("E-mail", true, e.erros["email"]) {
                    KpEntrada(f.email, vm::email, altura = 42.dp, raio = 12.dp, padH = 10.dp, teclado = KeyboardType.Email, activo = !e.completar)
                    if (e.completar) Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        FaIcone(Fa.LOCK, 9f, marca.c700); Spacer(Modifier.width(4.dp)); T("E-mail confirmado pela autenticação; não pode ser alterado aqui.", 9f, marca.c700)
                    }
                }
                Campo("Telefone principal", true, e.erros["telefone"]) {
                    KpEntrada(f.telefone, { vm.telefone(it.filter(Char::isDigit).take(9)) }, altura = 42.dp, raio = 12.dp, padH = 12.dp, prefixo = "+244", teclado = KeyboardType.Phone)
                }
                if (f.papel == Papel.PROPRIETARIO) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Kp.gray900))
                    Campo("Foto do Proprietário", true, e.erros["foto"]) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFFF9FAFB))
                                .tracejado(Kp.gray300, 16f).toque { escolher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                contentAlignment = Alignment.Center) {
                                when {
                                    e.aProcessarFoto -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = marca.c600)
                                    f.fotoCaminho != null -> AsyncImage(File(f.fotoCaminho), "Foto seleccionada", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                    else -> FaIcone(Fa.USER, 16f, Kp.gray400)
                                }
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                // Câmara ou galeria, à escolha (sem passar por outra app).
                                val ctx = androidx.compose.ui.platform.LocalContext.current
                                val camara = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
                                    bmp?.let { b -> val ficheiro = File(ctx.cacheDir, "camara-${System.currentTimeMillis()}.jpg").apply { outputStream().use { o -> b.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, o) } }
                                        vm.foto(android.net.Uri.fromFile(ficheiro)) }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    KpBotaoSec("Câmara", { camara.launch(null) }, Modifier.weight(1f), icone = Fa.CAMERA, altura = 32.dp, tamanho = 9.5f)
                                    KpBotaoSec("Galeria", { escolher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, Modifier.weight(1f), icone = Fa.IMAGE, altura = 32.dp, tamanho = 9.5f)
                                }
                                T("Foto obrigatória, optimizada automaticamente.", 8.5f, Color(0xFF6B7280), linha = 12f)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) { Campo("Província", true) { KpSelect(vm.geografia.provincias.map { it to it }, f.provincia, vm::provincia, altura = 41.dp, raio = 12.dp) } }
                        Box(Modifier.weight(1f)) { Campo("Município", true, e.erros["municipio"]) {
                            KpSelect(vm.geografia.municipios(f.provincia).map { it to it }, f.municipio.ifEmpty { null }, vm::municipio, altura = 41.dp, raio = 12.dp) } }
                    }
                    Campo("Bairro", true, e.erros["bairro"]) { KpEntrada(f.bairro, vm::bairro, altura = 42.dp, raio = 12.dp, padH = 10.dp) }
                    Campo("Telefone alternativo", false, e.erros["telefoneAlt"]) {
                        KpEntrada(f.telefoneAlt, { vm.telefoneAlt(it.filter(Char::isDigit).take(9)) }, altura = 42.dp, raio = 12.dp, padH = 12.dp, prefixo = "+244", teclado = KeyboardType.Phone)
                    }
                    Campo("Canal preferido para alertas", false) {
                        KpSelect(CanalAlerta.entries.map { it to it.rotulo }, f.canal, vm::canal, altura = 41.dp, raio = 12.dp)
                        T("Usado para alertas de reserva e interesse relevante nos seus anúncios.", 8f, Kp.gray400, Modifier.padding(top = 4.dp))
                    }
                }
                // Uma só palavra-passe, com "mostrar" (como no login) — sem repetir.
                if (!e.completar) { var ver by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                    Campo("Palavra-passe (mín. 6 caracteres)", true, e.erros["senha"]) { KpEntrada(f.senha, vm::senha, senha = !ver, altura = 42.dp, raio = 12.dp, padH = 10.dp, ime = ImeAction.Done,
                        direita = { Box(Modifier.size(30.dp).toque { ver = !ver }, contentAlignment = Alignment.Center) { FaIcone(if (ver) Fa.EYE_SLASH else Fa.EYE, 10f, Kp.gray400, regular = true) } }) } }
                Column(Modifier.fillMaxWidth().sup(16.dp, fundo = Kp.slate50, borda = Kp.slate200).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FaIcone(Fa.FILE_SHIELD, 12f, marca.c600, Modifier.padding(top = 2.dp))
                        Column {
                            T("Políticas de Uso — resumo", 10f, Kp.slate800, 700, linha = 15f)
                            // Resumo de uma página: o essencial à vista; as políticas completas ficam a um toque.
                            listOf("A reserva só nasce depois de o pagamento do sinal ser validado; o OTP da vistoria fica no seu recibo.",
                                "Sem negócio após a vistoria, o sinal é reembolsável menos a comissão do Kupila.",
                                "Os anúncios exigem fotografias reais e documento de titularidade; duplicados são bloqueados.",
                                "Os seus dados pessoais só são vistos por quem precisa deles para concluir o negócio.").forEach { T("• $it", 9f, Kp.slate600, linha = 14f) }
                        }
                    }
                    KpBotaoSec("Ler as políticas completas", aoLerPoliticas, icone = Fa.BOOK_OPEN, altura = 33.dp, tamanho = 10f)
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Checkbox(f.politicasAceites, vm::aceitar, modifier = Modifier.size(16.dp), colors = CheckboxDefaults.colors(checkedColor = marca.c600))
                        T("Aceito as Políticas de Uso e Regras de Intermediação do Kupila.", 9f, Kp.slate700, linha = 13.5f, modifier = Modifier.toque { vm.aceitar(!f.politicasAceites) })
                    }
                    e.erros["politicas"]?.let { T(it, 8f, Kp.red600) }
                }
                if (e.aEnviar) KpCaixa(Kp.amber50, Kp.amber200) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(10.dp), strokeWidth = 1.5.dp, color = Kp.amber800); Spacer(Modifier.width(6.dp)); T("A preparar cadastro…", 10f, Kp.amber800)
                    }
                }
                e.erroGeral?.let { KpCaixa(Kp.red50, Color(0xFFFEE2E2)) { T(it, 10f, Kp.red700, linha = 15f) } }
                if (e.erros.isNotEmpty()) KpCaixa(Kp.red50, Color(0xFFFEE2E2)) { T("Reveja os campos assinalados. Os dados preenchidos foram preservados.", 10f, Kp.red700, linha = 15f) }
                KpBotao(if (e.completar) "Concluir cadastro" else "Criar conta", vm::enviar, icone = Fa.USER_CHECK, ocupado = e.aEnviar, altura = 40.dp, raio = 12.dp, tamanho = 12f)
                T("O Administrador não pode ser criado por cadastro público.", 9f, Color(0xFF6B7280), centro = true, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun Campo(rotulo: String, obrigatorio: Boolean, erro: String? = null, conteudo: @Composable () -> Unit) {
    Column {
        KpRotulo(rotulo, 12f, obrigatorio)
        Spacer(Modifier.height(2.dp))
        conteudo()
        erro?.let { T(it, 9f, Kp.red600, Modifier.padding(top = 3.dp)) }
    }
}

/** `border-2 border-dashed`. */
private fun Modifier.tracejado(cor: Color, raio: Float) = this.drawBehind {
    val r = raio.dp.toPx()
    drawRoundRect(cor, cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
        style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))))
}

