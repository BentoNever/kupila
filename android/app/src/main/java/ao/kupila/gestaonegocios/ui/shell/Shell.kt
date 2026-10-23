package ao.kupila.gestaonegocios.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ao.kupila.gestaonegocios.dominio.perfis.Papel
import ao.kupila.gestaonegocios.ui.comum.Fa
import ao.kupila.gestaonegocios.ui.entrada.LogoMarca
import ao.kupila.gestaonegocios.ui.kp.FaIcone
import ao.kupila.gestaonegocios.ui.kp.Kp
import ao.kupila.gestaonegocios.ui.kp.KpSelect
import ao.kupila.gestaonegocios.ui.kp.LocalMarca
import ao.kupila.gestaonegocios.ui.kp.T
import ao.kupila.gestaonegocios.ui.kp.gradiente
import ao.kupila.gestaonegocios.ui.kp.sup
import ao.kupila.gestaonegocios.ui.kp.toque

/** Abas da origem (`switchTab`): Explorar, Anunciar (`tab-publish`) e Proprietário (`tab-my-listings`). */
enum class Aba { EXPLORAR, ANUNCIAR, PROPRIETARIO }

/** Acções do cabeçalho (`#app-header .header-actions`, HTML 6621-6653). */
class AccoesCabecalho(
    val ecraCheio: () -> Unit, val voltar: () -> Unit, val podeVoltar: Boolean, val avancar: () -> Unit, val podeAvancar: Boolean,
    val sincronizar: () -> Unit, val porSincronizar: Int, val meusAnuncios: () -> Unit, val avisoMeusAnuncios: Boolean,
    val pendentes: () -> Unit, val nPendentes: Int, val ajustes: () -> Unit, val faq: () -> Unit, val simulador: () -> Unit, val politicas: () -> Unit,
    val conta: () -> Unit, val notificacoes: () -> Unit, val naoLidas: Int, val inicio: () -> Unit,
)

/** HEADER: `bg-brand-700 px-3 pt-2.5 pb-[7px]` (52 px) com o logótipo e os botões permitidos ao papel. */
@Composable
fun CabecalhoApp(papel: Papel, logo: String?, a: AccoesCabecalho) {
    val marca = LocalMarca.current
    val dono = papel != Papel.CLIENTE
    val admin = papel == Papel.ADMINISTRADOR
    Column(Modifier.fillMaxWidth().shadow(4.dp, ambientColor = Color.Black.copy(.1f), spotColor = Color.Black.copy(.1f)).background(marca.c700).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(12.dp, 10.dp, 12.dp, 7.dp), verticalAlignment = Alignment.CenterVertically) {
            LogoMarca(29f, 9f, 15.2f, logo, Modifier.toque(aoTocar = a.inicio))
            Spacer(Modifier.width(3.dp))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Botao(Fa.EXPAND, a.ecraCheio)
                Botao(Fa.ARROW_LEFT, a.voltar, activo = a.podeVoltar)
                Botao(Fa.ARROW_RIGHT, a.avancar, activo = a.podeAvancar)
                Botao(Fa.ARROWS_ROTATE, a.sincronizar, insignia = a.porSincronizar.takeIf { it > 0 }?.toString(), corInsignia = Kp.amber400)
                if (dono) Botao(Fa.FOLDER_CLOSED, a.meusAnuncios, cor = marca.c50, ponto = a.avisoMeusAnuncios)
                if (admin) Botao(Fa.LIST_CHECK, a.pendentes, insignia = a.nPendentes.takeIf { it > 0 }?.let { if (it > 99) "99+" else it.toString() }, corInsignia = Kp.yellow400, textoInsignia = Kp.slate900)
                if (admin) Botao(Fa.GEAR, a.ajustes)
                Botao(Fa.CIRCLE_QUESTION, a.faq, regular = true)
                Botao(Fa.CALCULATOR, a.simulador)
                Botao(Fa.CIRCLE_INFO, a.politicas)
                Botao(Fa.USER_SHIELD, a.conta, ponto = true)
                Botao(Fa.BELL, a.notificacoes, regular = true, insignia = a.naoLidas.takeIf { it > 0 }?.let { if (it > 99) "99+" else it.toString() }, corInsignia = Kp.red500, textoInsignia = Color.White)
            }
        }
    }
}

@Composable
private fun Botao(glifo: String, aoTocar: () -> Unit, activo: Boolean = true, regular: Boolean = false, cor: Color = Color.White, ponto: Boolean = false,
                  insignia: String? = null, corInsignia: Color = Kp.yellow400, textoInsignia: Color = Kp.slate900) {
    val marca = LocalMarca.current
    Box(Modifier.size(25.3.dp).alpha(if (activo) 1f else .35f).toque(activo, aoTocar), contentAlignment = Alignment.Center) {
        FaIcone(glifo, 11.33f, cor, regular = regular)
        if (ponto) Box(Modifier.align(Alignment.BottomEnd).offset((-3).dp, (-3).dp).size(6.dp).sup(6.dp, fundo = Kp.yellow400, borda = marca.c700, forma = CircleShape))
        insignia?.let {
            Box(Modifier.align(Alignment.TopEnd).offset(3.dp, (-2).dp).height(14.dp).widthIn(min = 14.dp)
                .sup(14.dp, fundo = corInsignia, borda = marca.c700, forma = CircleShape).padding(horizontal = 3.dp), contentAlignment = Alignment.Center) {
                T(it, 6.5f, textoInsignia, 900, linha = 10f)
            }
        }
    }
}

/** "Selector de Província Instantâneo" (`#home-location-bar`, HTML 6657-6691). */
@Composable
fun BarraLocalizacao(nome: String, provincias: List<String>, provincia: String?, aoEscolher: (String?) -> Unit) {
    val marca = LocalMarca.current
    Row(Modifier.fillMaxWidth().height(40.dp).background(gradiente(90f, marca.c900, marca.c800)).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            T(nome.ifBlank { "Utilizador" }, 8f, Color.White, 800, max = 1, espacamento = .08f, modifier = Modifier.weight(1f, fill = false)
                .sup(999.dp, fundo = Color(0xFF29AA58).copy(alpha = .22f), borda = Color(0xFFB9E3C9).copy(alpha = .34f)).padding(horizontal = 7.dp, vertical = 3.dp))
            T("•", 8f, marca.c100.copy(alpha = .7f), modifier = Modifier.padding(horizontal = 6.dp))
            FaIcone(Fa.LOCATION_DOT, 8f, Kp.yellow400); Spacer(Modifier.width(6.dp))
            T("Explorar em", 8f, marca.c100, 700, espacamento = .2f, max = 1)
        }
        KpSelect(listOf<Pair<String?, String>>(null to "Todas (21 Províncias)") + provincias.map { it to it }, provincia, aoEscolher,
            Modifier.width(178.dp).shadow(6.dp, androidx.compose.foundation.shape.RoundedCornerShape(999.dp), ambientColor = Kp.slate950.copy(.12f), spotColor = Kp.slate950.copy(.12f)),
            altura = 27.dp, tamanho = 10f, raio = 999.dp, corTexto = marca.c900, borda = Color(0xFF5CBF80), peso = 750, centrar = true)
    }
}

/** NAV inferior (`#app-bottom-nav`, HTML 8160-8177): Home · Anunciar (botão central) · Proprietário. O Cliente vê só a Home. */
@Composable
fun BarraInferior(aba: Aba, papel: Papel, aoInicio: () -> Unit, aoAnunciar: () -> Unit, aoProprietario: () -> Unit) {
    val marca = LocalMarca.current
    val dono = papel != Papel.CLIENTE
    Box(Modifier.fillMaxWidth().shadow(11.dp, ambientColor = marca.c950.copy(.18f), spotColor = marca.c950.copy(.18f))
        .background(gradiente(135f, marca.c900, marca.c800)).navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(58.dp).padding(12.dp, 8.dp, 12.dp, 12.dp), verticalAlignment = Alignment.Top,
            horizontalArrangement = if (dono) Arrangement.SpaceBetween else Arrangement.Center) {
            ItemNav(Fa.HOUSE, "Home", aba == Aba.EXPLORAR, aoInicio, Modifier.weight(1f, fill = dono))
            if (dono) {
                // `-mt-5`: o círculo sobe 20 px acima da barra; no fluxo ocupa só 20 px, e o rótulo fica abaixo como na origem.
                Column(Modifier.weight(1f).toque(aoTocar = aoAnunciar), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.height(20.dp), contentAlignment = Alignment.BottomCenter) {
                        Box(Modifier.wrapContentHeight(Alignment.Bottom, unbounded = true).requiredSize(40.dp).sup(40.dp, fundo = marca.c600, borda = if (aba == Aba.ANUNCIAR) Kp.yellow400.copy(.75f) else marca.c900, bordaLargura = 4.dp,
                            forma = CircleShape, sombra = Color.Black.copy(.1f), desfoque = 6.dp), contentAlignment = Alignment.Center) { FaIcone(Fa.PLUS, 16f, Color.White) }
                    }
                    T("Anunciar", 10f, if (aba == Aba.ANUNCIAR) Kp.yellow400 else marca.c100, 500, Modifier.padding(top = 7.dp), linha = 15f)
                }
                ItemNav(Fa.USER_GEAR, "Proprietário", aba == Aba.PROPRIETARIO, aoProprietario, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ItemNav(glifo: String, rotulo: String, activo: Boolean, aoTocar: () -> Unit, modifier: Modifier) {
    val cor = if (activo) Kp.yellow400 else LocalMarca.current.c100
    Column(modifier.height(35.dp).toque(aoTocar = aoTocar).padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        FaIcone(glifo, 18f, cor)
        T(rotulo, 10f, cor, 500, Modifier.padding(top = 2.dp), linha = 15f)
    }
}
