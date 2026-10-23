package ao.kupila.gestaonegocios.ui.conta

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ao.kupila.gestaonegocios.dominio.aparencia.ModoAparencia
import ao.kupila.gestaonegocios.dominio.perfis.EstadoConta
import ao.kupila.gestaonegocios.dominio.perfis.Papel
import ao.kupila.gestaonegocios.dominio.perfis.Perfil
import ao.kupila.gestaonegocios.ui.comum.Fa
import ao.kupila.gestaonegocios.ui.entrada.iniciais
import ao.kupila.gestaonegocios.ui.kp.FaIcone
import ao.kupila.gestaonegocios.ui.kp.Kp
import ao.kupila.gestaonegocios.ui.kp.KpBotao
import ao.kupila.gestaonegocios.ui.kp.KpFechar
import ao.kupila.gestaonegocios.ui.kp.LocalMarca
import ao.kupila.gestaonegocios.ui.kp.T
import ao.kupila.gestaonegocios.ui.kp.sup
import ao.kupila.gestaonegocios.ui.kp.toque

/**
 * "Perfil e Permissões" (`#modal-session`, HTML 7991-8019; `renderSessionModal`, 14697): identidade, permissões do papel (cada linha leva à
 * área correspondente), legenda dos perfis e "Terminar Sessão".
 */
@Composable
fun ContaEcra(
    perfil: Perfil, offline: Boolean, modoLocal: ModoAparencia?, aoModo: (ModoAparencia?) -> Unit,
    aoFaq: () -> Unit, aoPoliticas: () -> Unit, aoSimulador: () -> Unit, aoSobre: () -> Unit, aoProtecao: () -> Unit,
    aoPerfilProprietario: (() -> Unit)?, aoSair: () -> Unit,
    aoRascunhos: (() -> Unit)? = null, aoPoupanca: (() -> Unit)? = null, aoActualizacao: (() -> Unit)? = null,
    aoMeusAnuncios: (() -> Unit)? = null, aoNotificacoes: (() -> Unit)? = null, naoLidas: Int = 0, aoAdmin: (() -> Unit)? = null,
    aoFechar: () -> Unit = {}, aoAutorizacoes: (() -> Unit)? = null, aoNegocios: (() -> Unit)? = null,
) {
    val marca = LocalMarca.current
    Column {
        Row(Modifier.fillMaxWidth().background(marca.c700).padding(16.dp, 16.dp, 16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
            FaIcone(Fa.USER_SHIELD, 14f, Kp.yellow400); Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                T("Perfil e Permissões", 14f, Color.White, 700, max = 1)
                T("Sessão, identidade e nível de acesso", 8f, marca.c100, Modifier.padding(top = 2.dp))
            }
            if (perfil.papel == Papel.ADMINISTRADOR && aoAdmin != null) {
                Box(Modifier.size(36.dp).sup(36.dp, fundo = Color.White.copy(.10f), forma = CircleShape).toque(aoTocar = aoAdmin), contentAlignment = Alignment.Center) {
                    FaIcone(Fa.USER_TIE, 13f, Kp.yellow300)
                }
                Spacer(Modifier.width(6.dp))
            }
            KpFechar(aoFechar, fundo = Kp.slate900, cor = Color.White)
        }
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (offline) Row(Modifier.fillMaxWidth().sup(12.dp, fundo = Kp.amber50, borda = Kp.amber200).padding(10.dp)) {
                T("Sem ligação: sessão aberta em modo limitado. Confirmações, reservas e pagamentos aguardam a revalidação.", 9f, Kp.amber800, linha = 13f)
            }
            // Identidade
            Row(Modifier.fillMaxWidth().sup(16.dp, fundo = Kp.slate900).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(56.dp).sup(16.dp, fundo = marca.c100), contentAlignment = Alignment.Center) { T(iniciais(perfil.nome), 16f, marca.c800, 700) }
                Column(Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Pilula(perfil.papel.rotulo)
                        Pilula(if (perfil.estado == EstadoConta.ACTIVA) "ACTIVO" else "INACTIVO", activo = perfil.estado == EstadoConta.ACTIVA)
                    }
                    T(perfil.nome.ifBlank { "Utilizador" }, 14f, Color.White, 700, Modifier.padding(top = 6.dp), max = 1)
                    T(perfil.email, 9f, Kp.slate400, max = 1)
                    val local = listOfNotNull(perfil.residencia.bairro, perfil.residencia.municipio, perfil.residencia.provincia).filter { it.isNotBlank() }.joinToString(", ")
                    T(listOfNotNull(perfil.telefonePrincipal.takeIf { it.isNotBlank() }?.let { "+244 $it" }, local.ifBlank { null }).joinToString(" • "), 8f, Kp.slate500, Modifier.padding(top = 2.dp))
                    T("ID ${perfil.uid}", 7f, Kp.slate500, Modifier.padding(top = 4.dp), max = 1)
                }
            }
            // Permissões desta sessão
            Column(Modifier.fillMaxWidth().sup(16.dp, fundo = Color.White, borda = Kp.gray200)) {
                Row(Modifier.fillMaxWidth().background(Color(0xFFF9FAFB)).padding(14.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    FaIcone(Fa.SHIELD_HALVED, 9f, marca.c600); Spacer(Modifier.width(6.dp)); T("Permissões desta sessão", 9f, Color(0xFF6B7280), 700, espacamento = .45f, maiusculas = true)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Kp.gray900))
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    permissoes(perfil.papel, aoPerfilProprietario, aoAutorizacoes, aoMeusAnuncios, aoNegocios, aoAdmin).forEach { (ic, texto, accao) ->
                        Row(Modifier.fillMaxWidth().sup(12.dp, fundo = Color(0xFFF9FAFB)).toque(accao != null) { accao?.invoke() }.padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                            Box(Modifier.size(28.dp).sup(8.dp, fundo = Color.White, borda = marca.c600), contentAlignment = Alignment.Center) { FaIcone(ic, 10f, marca.c700) }
                            T(texto, 10f, Kp.gray700, Modifier.weight(1f).padding(top = 6.dp), linha = 16f)
                            FaIcone(Fa.CHEVRON_RIGHT, 8f, Kp.gray300, Modifier.padding(top = 10.dp))
                        }
                    }
                }
            }
            // Legenda dos perfis
            Column(Modifier.fillMaxWidth().sup(12.dp, fundo = marca.c50, borda = marca.c100).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("Cliente:" to " consulta produtos, visualiza detalhes permitidos e efectua reservas.",
                    "Proprietário:" to " possui as permissões de Cliente e gere somente o próprio perfil, anúncios e pedidos de autorização.",
                    "Administrador:" to " possui visão global, monitoramento, Ajustes, utilizadores, fichas administrativas, autorizações e auditoria.").forEach { (b, r) ->
                    androidx.compose.material3.Text(buildAnnotatedString { withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(b) }; append(r) },
                        color = marca.c900, fontSize = 10.sp, lineHeight = 15.sp)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Kp.gray200))
        Box(Modifier.fillMaxWidth().background(Color.White).padding(12.dp)) {
            KpBotao("Terminar Sessão", aoSair, icone = Fa.RIGHT_FROM_BRACKET, fundo = Kp.slate900, altura = 40.dp, raio = 12.dp, tamanho = 12f, sombra = false)
        }
    }
}

@Composable
private fun Pilula(texto: String, activo: Boolean = true) {
    T(texto, 8f, if (activo) Kp.emerald300 else Kp.slate300, 700,
        Modifier.sup(999.dp, fundo = (if (activo) Color(0xFF34D399) else Kp.slate400).copy(.15f)).padding(horizontal = 8.dp, vertical = 4.dp))
}

/** Permissões por papel, exactamente como `renderSessionModal` (HTML 14704-14710); cada linha leva à área correspondente. */
private fun permissoes(papel: Papel, perfil: (() -> Unit)?, autorizacoes: (() -> Unit)?, anuncios: (() -> Unit)?, negocios: (() -> Unit)?, admin: (() -> Unit)?):
    List<Triple<String, String, (() -> Unit)?>> = when (papel) {
    Papel.ADMINISTRADOR -> listOf(
        Triple(Fa.CHART_LINE, "Centro de Monitoramento e visão consolidada", admin),
        Triple(Fa.USERS_GEAR, "Consultar fichas; gerir estado sem editar contas auto-cadastradas", admin),
        Triple(Fa.SLIDERS, "Alterar Ajustes globais da plataforma", admin),
        Triple(Fa.KEY, "Autorizar remoção/desanúncio de produtos", anuncios),
        Triple(Fa.CLOCK_ROTATE_LEFT, "Consultar auditoria e operações", admin),
        Triple(Fa.HOUSE_CIRCLE_CHECK, "Gerir e consultar todos os anúncios e reservas", negocios))
    Papel.PROPRIETARIO -> listOf(
        Triple(Fa.MAGNIFYING_GLASS, "Consultar e reservar produtos", null),
        Triple(Fa.BULLHORN, "Cadastrar e gerir os próprios anúncios", anuncios),
        Triple(Fa.ID_CARD, "Gerir exclusivamente o próprio perfil", perfil),
        Triple(Fa.KEY, "Solicitar autorizações dos próprios produtos", autorizacoes ?: anuncios),
        Triple(Fa.WALLET, "Consultar saldos e movimentos relacionados aos próprios anúncios", negocios))
    Papel.CLIENTE -> listOf(
        Triple(Fa.MAGNIFYING_GLASS, "Consultar produtos disponíveis", null),
        Triple(Fa.BOOKMARK, "Reservar produtos", null),
        Triple(Fa.RECEIPT, "Consultar os próprios comprovativos e reservas", negocios),
        Triple(Fa.USER_SHIELD, "Aceder somente aos dados permitidos para o perfil Cliente", null))
}
