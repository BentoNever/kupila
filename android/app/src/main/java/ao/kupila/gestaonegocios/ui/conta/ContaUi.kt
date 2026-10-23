package ao.kupila.gestaonegocios.ui.conta

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import ao.kupila.gestaonegocios.dominio.aparencia.ModoAparencia
import ao.kupila.gestaonegocios.dominio.perfis.Papel
import ao.kupila.gestaonegocios.dominio.perfis.Perfil
import ao.kupila.gestaonegocios.ui.comum.Aviso
import ao.kupila.gestaonegocios.ui.comum.BotaoSecundario
import ao.kupila.gestaonegocios.ui.comum.LinhaInfo
import ao.kupila.gestaonegocios.ui.comum.Seccao
import ao.kupila.gestaonegocios.ui.comum.TipoAviso

/** "Perfil e Permissões" (`modal-session`, HTML 7991-8017) + ajuda e aparência do dispositivo. */
@Composable
fun ContaEcra(
    perfil: Perfil, offline: Boolean, modoLocal: ModoAparencia?, aoModo: (ModoAparencia?) -> Unit,
    aoFaq: () -> Unit, aoPoliticas: () -> Unit, aoSimulador: () -> Unit, aoSobre: () -> Unit, aoProtecao: () -> Unit,
    aoPerfilProprietario: (() -> Unit)?, aoSair: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (offline) Aviso("Sem ligação: sessão aberta em modo limitado. Confirmações, reservas e pagamentos aguardam a revalidação.", TipoAviso.AVISO)
        Seccao("Perfil e Permissões") {
            LinhaInfo("Nome", perfil.nome)
            LinhaInfo("E-mail", perfil.email)
            LinhaInfo("Perfil", perfil.papel.rotulo)
            LinhaInfo("Estado", perfil.estado.rotulo)
            Text(when (perfil.papel) {
                Papel.CLIENTE -> "Pode pesquisar, reservar com sinal validado, acompanhar reservas e pedir reembolso elegível."
                Papel.PROPRIETARIO -> "Pode também anunciar e gerir os seus próprios produtos, recebimentos, vistorias e autorizações."
                Papel.ADMINISTRADOR -> "Gestão autorizada da plataforma: utilizadores, catálogo, autorizações, configurações, monetização e auditoria."
            }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            aoPerfilProprietario?.let { BotaoSecundario("Meu perfil de Proprietário", it, icone = Icons.Filled.Person) }
        }
        Seccao("Aparência neste dispositivo", subtitulo = "A paleta e o logótipo são definidos pelo Administrador; aqui escolhe claro, escuro ou automático.") {
            val opcoes = listOf<Pair<ModoAparencia?, String>>(null to "Plataforma", ModoAparencia.CLARO to "Claro", ModoAparencia.ESCURO to "Escuro", ModoAparencia.AUTOMATICO to "Auto")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                opcoes.forEachIndexed { i, (m, r) -> SegmentedButton(selected = modoLocal == m, onClick = { aoModo(m) }, shape = SegmentedButtonDefaults.itemShape(i, opcoes.size)) { Text(r) } }
            }
        }
        Seccao("Ajuda") {
            ItemMenu(Icons.AutoMirrored.Filled.HelpOutline, "Perguntas frequentes", aoFaq)
            ItemMenu(Icons.Filled.Gavel, "Manual, Políticas e Intermediação", aoPoliticas)
            ItemMenu(Icons.Filled.Shield, "Reserva Protegida", aoProtecao)
            ItemMenu(Icons.Filled.Calculate, "Simulador de Intermediação", aoSimulador)
            ItemMenu(Icons.Filled.Info, "Sobre", aoSobre)
        }
        BotaoSecundario("Terminar Sessão", aoSair, icone = Icons.AutoMirrored.Filled.Logout)
    }
}

@Composable
fun ItemMenu(icone: ImageVector, texto: String, aoClicar: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = aoClicar).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icone, null, tint = MaterialTheme.colorScheme.primary)
        Text(texto, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
    }
}
