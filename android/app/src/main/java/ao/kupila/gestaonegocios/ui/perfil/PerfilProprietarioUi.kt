package ao.kupila.gestaonegocios.ui.perfil

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ao.kupila.gestaonegocios.dados.ContaActiva
import ao.kupila.gestaonegocios.dados.local.Geografia
import ao.kupila.gestaonegocios.dados.media.Imagens
import ao.kupila.gestaonegocios.dados.remoto.CentralSpark
import ao.kupila.gestaonegocios.dados.remoto.MensagensErro
import ao.kupila.gestaonegocios.dados.sessao.SessaoRepositorio
import ao.kupila.gestaonegocios.dominio.perfis.CanalAlerta
import ao.kupila.gestaonegocios.dominio.perfis.ValidacaoContacto
import ao.kupila.gestaonegocios.ui.comum.Fa
import ao.kupila.gestaonegocios.ui.comum.modeloImagem
import ao.kupila.gestaonegocios.ui.entrada.iniciais
import ao.kupila.gestaonegocios.ui.kp.FaIcone
import ao.kupila.gestaonegocios.ui.kp.Kp
import ao.kupila.gestaonegocios.ui.kp.KpBotao
import ao.kupila.gestaonegocios.ui.kp.KpEntrada
import ao.kupila.gestaonegocios.ui.kp.KpFechar
import ao.kupila.gestaonegocios.ui.kp.KpRotulo
import ao.kupila.gestaonegocios.ui.kp.KpSelect
import ao.kupila.gestaonegocios.ui.kp.LocalMarca
import ao.kupila.gestaonegocios.ui.kp.T
import ao.kupila.gestaonegocios.ui.kp.sup
import ao.kupila.gestaonegocios.ui.kp.toque
import coil3.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FormPerfil(val nome: String = "", val email: String = "", val provincia: String = "Luanda", val municipio: String = "", val bairro: String = "",
                      val telefone: String = "", val telefoneAlt: String = "", val canal: CanalAlerta = CanalAlerta.AUTOMATICO, val foto: ByteArray? = null,
                      val fotoUrl: String? = null, val aGuardar: Boolean = false, val aProcessar: Boolean = false, val erro: String? = null, val ok: Boolean = false)

/** "Cadastro do Proprietário" (`#modal-owner-registration`, HTML 7644-7712): pré-preenchido com o perfil; grava só o que mudou. */
@HiltViewModel
class PerfilProprietarioViewModel @Inject constructor(
    private val conta: ContaActiva, private val central: CentralSpark, private val sessao: SessaoRepositorio, private val imagens: Imagens, val geografia: Geografia,
) : ViewModel() {
    val estado = MutableStateFlow(conta.recursos.value?.perfil?.let { p ->
        FormPerfil(p.nome, p.email, p.residencia.provincia.ifBlank { "Luanda" }, p.residencia.municipio, p.residencia.bairro, p.telefonePrincipal, p.telefoneAlternativo, p.canalAlertas, fotoUrl = p.fotoUrl)
    } ?: FormPerfil())

    fun mudar(f: (FormPerfil) -> FormPerfil) = estado.update { f(it).copy(erro = null, ok = false) }
    fun foto(uri: Uri) = viewModelScope.launch {
        estado.update { it.copy(aProcessar = true) }
        runCatching { imagens.fotoPerfil(uri) }.onSuccess { b -> estado.update { it.copy(foto = b, aProcessar = false) } }
            .onFailure { e -> estado.update { it.copy(aProcessar = false, erro = e.message) } }
    }
    fun guardar() = viewModelScope.launch {
        val f = estado.value
        val erro = when {
            f.nome.isBlank() -> "Indique o nome."
            f.municipio.isBlank() || f.bairro.isBlank() -> "Indique Província, Município e Bairro de residência."
            !ValidacaoContacto.telefoneValido(f.telefone) -> "Informe um telefone principal válido com 9 dígitos."
            f.telefoneAlt.isNotBlank() && !ValidacaoContacto.telefoneValido(f.telefoneAlt) -> "O telefone alternativo deve ter 9 dígitos."
            f.foto == null && f.fotoUrl.isNullOrBlank() -> "A foto do Proprietário é obrigatória."
            else -> null
        }
        if (erro != null) { estado.update { it.copy(erro = erro) }; return@launch }
        estado.update { it.copy(aGuardar = true) }
        runCatching {
            central.actualizarPerfil(mapOf("name" to f.nome.trim(), "phonePrimary" to f.telefone, "phoneAlt" to f.telefoneAlt, "province" to f.provincia,
                "municipality" to f.municipio, "neighborhood" to f.bairro.trim(), "alertChannel" to f.canal.codigo), f.foto)
            sessao.actualizarPerfil()
        }.onSuccess { estado.update { it.copy(aGuardar = false, ok = true) } }.onFailure { e -> estado.update { it.copy(aGuardar = false, erro = MensagensErro.de(e)) } }
    }
}

@Composable
fun PerfilProprietarioEcra(vm: PerfilProprietarioViewModel, aoFechar: () -> Unit) {
    val f by vm.estado.collectAsStateWithLifecycle()
    val marca = LocalMarca.current
    val escolher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(vm::foto) }
    Column {
        Row(Modifier.fillMaxWidth().background(marca.c700).padding(16.dp, 14.dp), verticalAlignment = Alignment.CenterVertically) {
            FaIcone(Fa.USER_SHIELD, 13f, Kp.yellow400); Spacer(Modifier.width(8.dp)); T("Cadastro do Proprietário", 11f, Color.White, 700, Modifier.weight(1f))
            KpFechar(aoFechar, fundo = Kp.slate900)
        }
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().sup(12.dp, fundo = marca.c50, borda = marca.c100).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                FaIcone(Fa.CIRCLE_INFO, 9f, marca.c800); Spacer(Modifier.width(8.dp)); T("O cadastro é obrigatório antes de publicar qualquer produto.", 9f, marca.c800)
            }
            Row(Modifier.fillMaxWidth().sup(16.dp, fundo = Kp.slate50, borda = Kp.slate200).padding(9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(64.dp).sup(16.dp, fundo = marca.c100), contentAlignment = Alignment.Center) {
                    when {
                        f.foto != null -> AsyncImage(f.foto, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        !f.fotoUrl.isNullOrBlank() -> AsyncImage(modeloImagem(f.fotoUrl!!), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else -> T(iniciais(f.nome), 18f, marca.c800, 700)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    KpRotulo("Foto do Proprietário", 8.5f, true)
                    Row(Modifier.height(32.dp).sup(12.dp, fundo = Color.White, borda = Kp.slate300).toque { escolher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                        .padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        FaIcone(Fa.CAMERA, 9f, marca.c600); Spacer(Modifier.width(8.dp)); T(if (f.aProcessar) "A optimizar…" else "Seleccionar Foto", 9f, Kp.slate800, 700)
                    }
                    T("A foto é obrigatória e será optimizada automaticamente antes de ser guardada.", 7.5f, Kp.slate400, linha = 12f)
                }
            }
            Campo("Nome", true) { KpEntrada(f.nome, { v -> vm.mudar { it.copy(nome = v.take(120)) } }, altura = 41.dp, raio = 12.dp, padH = 10.dp) }
            Campo("E-mail Funcional", true) {
                KpEntrada(f.email, {}, altura = 41.dp, raio = 12.dp, padH = 10.dp, activo = false)
                T("Usado para autenticação, notificações e códigos de autorização.", 7.5f, Kp.slate400, Modifier.padding(top = 4.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { Campo("Província de Residência", true) { KpSelect(vm.geografia.provincias.map { it to it }, f.provincia, { v -> vm.mudar { it.copy(provincia = v, municipio = "") } }, altura = 34.dp, tamanho = 9f, raio = 12.dp) } }
                Box(Modifier.weight(1f)) { Campo("Município", true) { KpSelect(vm.geografia.municipios(f.provincia).map { it to it }, f.municipio.ifBlank { null }, { v -> vm.mudar { it.copy(municipio = v) } }, altura = 34.dp, tamanho = 9f, raio = 12.dp) } }
            }
            Campo("Bairro de Residência", true) { KpEntrada(f.bairro, { v -> vm.mudar { it.copy(bairro = v.take(100)) } }, altura = 41.dp, raio = 12.dp, padH = 10.dp) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { Campo("Telefone Principal", true) { KpEntrada(f.telefone, { v -> vm.mudar { it.copy(telefone = v.filter(Char::isDigit).take(9)) } }, altura = 41.dp, raio = 12.dp, padH = 10.dp, teclado = KeyboardType.Phone) } }
                Box(Modifier.weight(1f)) { Campo("Telefone Alternativo") { KpEntrada(f.telefoneAlt, { v -> vm.mudar { it.copy(telefoneAlt = v.filter(Char::isDigit).take(9)) } }, altura = 41.dp, raio = 12.dp, padH = 10.dp, teclado = KeyboardType.Phone) } }
            }
            Column(Modifier.fillMaxWidth().sup(16.dp, fundo = Color(0xFFFFFBEB), borda = Kp.amber100).padding(9.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { FaIcone(Fa.BELL, 9f, Kp.amber800); Spacer(Modifier.width(6.dp)); T("Alertas do Proprietário", 9.5f, Kp.amber900, 700) }
                T("Receba aviso quando um produto for reservado ou demonstrar interesse relevante de clientes.", 8.5f, Kp.amber700, linha = 14f)
                KpSelect(CanalAlerta.entries.map { it to it.rotulo }, f.canal, { v -> vm.mudar { it.copy(canal = v) } }, altura = 34.dp, tamanho = 9f, raio = 12.dp, borda = Kp.amber300)
            }
            f.erro?.let { T(it, 9f, Kp.red700, Modifier.fillMaxWidth().sup(12.dp, fundo = Kp.red50).padding(9.dp)) }
            if (f.ok) T("Cadastro guardado.", 9f, marca.c800, 700, Modifier.fillMaxWidth().sup(12.dp, fundo = marca.c50).padding(9.dp))
            KpBotao("Guardar Cadastro", vm::guardar, icone = Fa.FLOPPY_DISK, ocupado = f.aGuardar, altura = 37.dp, raio = 12.dp, tamanho = 9f)
        }
    }
}

@Composable
private fun Campo(rotulo: String, obrigatorio: Boolean = false, conteudo: @Composable ColumnScope.() -> Unit) {
    Column { KpRotulo(rotulo, 9f, obrigatorio); Spacer(Modifier.height(4.dp)); conteudo() }
}
