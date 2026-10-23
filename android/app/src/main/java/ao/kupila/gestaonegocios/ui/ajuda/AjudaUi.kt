package ao.kupila.gestaonegocios.ui.ajuda

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ao.kupila.gestaonegocios.dados.local.PerguntaFaq
import ao.kupila.gestaonegocios.dados.local.SeccaoTexto
import ao.kupila.gestaonegocios.ui.comum.Fa
import ao.kupila.gestaonegocios.ui.kp.FaIcone
import ao.kupila.gestaonegocios.ui.kp.Kp
import ao.kupila.gestaonegocios.ui.kp.KpCabecalhoModal
import ao.kupila.gestaonegocios.ui.kp.LocalMarca
import ao.kupila.gestaonegocios.ui.kp.T
import ao.kupila.gestaonegocios.ui.kp.gradiente
import ao.kupila.gestaonegocios.ui.kp.sup
import ao.kupila.gestaonegocios.ui.kp.toque

/** Ecrã simples com barra superior (usado por ecrãs ainda não convertidos em modal). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcraComTopo(titulo: String, aoVoltar: () -> Unit, conteudo: @Composable (PaddingValues) -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text(titulo) }, navigationIcon = { IconButton(onClick = aoVoltar) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } }) }) { conteudo(it) }
}

/** PERGUNTAS FREQUENTES (`#modal-kupila-faq`, HTML 8212-8234): acordeão com "Ver mais". */
@Composable
fun FaqEcra(perguntas: List<PerguntaFaq>, aoVoltar: () -> Unit, aoPoliticas: () -> Unit) {
    val marca = LocalMarca.current
    var todas by rememberSaveable { mutableStateOf(false) }
    Column {
        Box(Modifier.fillMaxWidth().background(gradiente(135f, marca.c900, marca.c800))) {
            KpCabecalhoModal("Central de ajuda", "Perguntas Frequentes", aoVoltar, icone = Fa.CIRCLE_QUESTION, corIcone = Kp.yellow400,
                subtitulo = "Respostas rápidas sobre conta, anúncios, pagamentos e reservas.", fundo = Color.Transparent, corKicker = marca.c100)
        }
        Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            T("Perguntas gerais sobre utilização do Kupila.", 8.5f, Kp.slate500, Modifier.weight(1f))
            Row(Modifier.toque { todas = !todas }, verticalAlignment = Alignment.CenterVertically) {
                FaIcone(if (todas) Fa.ANGLES_UP else Fa.ANGLES_DOWN, 8.5f, marca.c700); Spacer(Modifier.width(4.dp)); T(if (todas) "Ver menos" else "Ver mais", 8.5f, marca.c700, 700)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Kp.slate200))
        Column(Modifier.fillMaxWidth().background(Kp.slate50).verticalScroll(rememberScrollState()).padding(10.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            perguntas.forEach { p -> PerguntaCartao(p, todas) }
            Row(Modifier.fillMaxWidth().sup(12.dp, fundo = Color.White, borda = marca.c200).toque(aoTocar = aoPoliticas).padding(12.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                FaIcone(Fa.BOOK_OPEN, 9f, marca.c700); Spacer(Modifier.width(6.dp)); T("Manual, Políticas e Regras de Intermediação", 9f, marca.c700, 700)
            }
        }
    }
}

@Composable
private fun PerguntaCartao(p: PerguntaFaq, todas: Boolean) {
    val marca = LocalMarca.current
    var aberta by remember(todas) { mutableStateOf(todas) }
    Column(Modifier.fillMaxWidth().sup(16.dp, fundo = Color.White, borda = Kp.slate200).toque { aberta = !aberta }.padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(31.dp).sup(10.dp, fundo = marca.c50), contentAlignment = Alignment.Center) { T("?", 10f, marca.c600, 900) }
            Column(Modifier.weight(1f)) {
                T(p.pergunta, 10f, Kp.slate900, 700, linha = 13f)
                T(if (aberta) "Ver menos" else "Ver mais", 8f, Kp.slate400, 700, Modifier.padding(top = 3.dp))
            }
            FaIcone(if (aberta) Fa.CHEVRON_UP else Fa.CHEVRON_DOWN, 9f, Kp.slate400)
        }
        AnimatedVisibility(aberta) { T(p.resposta, 9.5f, Kp.slate600, Modifier.padding(top = 10.dp, start = 39.dp), linha = 15f) }
    }
}

/** RESERVA PROTEGIDA (`#modal-reservation-protection`, HTML 8184-8211). */
@Composable
fun ProtecaoEcra(seccoes: List<SeccaoTexto>, prazoReservaMinutos: Long, aoVoltar: () -> Unit) {
    val marca = LocalMarca.current
    Column {
        KpCabecalhoModal("Protecção da reserva", "Reserva Protegida", aoVoltar, icone = Fa.SHIELD_HALVED, subtitulo = "Informação geral do processo de reserva.", corKicker = Kp.emerald300)
        Column(Modifier.fillMaxWidth().background(Kp.slate50).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().sup(16.dp, fundo = Kp.emerald50, borda = Kp.emerald100).padding(12.dp)) {
                androidx.compose.material3.Text(buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Sinal protegido:") }
                    append(" o processo de reserva mantém os dados sensíveis e o fluxo financeiro sob as regras de intermediação do Kupila até à validação correspondente.")
                }, color = Kp.emerald900, fontSize = 9.sp, lineHeight = 15.sp)
            }
            Column(Modifier.fillMaxWidth().sup(16.dp, fundo = Color.White, borda = Kp.slate200).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(Triple("Reserva e confirmação", "O Cliente confirma a reserva conforme as condições e o sinal definidos para o produto.", marca.c50 to marca.c700),
                    Triple("Validação presencial", "Os dados necessários para a vistoria são disponibilizados conforme as permissões e o estado da reserva.", Kp.amber50 to Kp.amber700),
                    Triple("Conclusão ou reembolso elegível", "A conclusão do negócio ou eventual reembolso segue o fluxo e as validações aplicáveis da plataforma.", marca.c50 to marca.c700))
                    .forEachIndexed { i, (t, s, c) ->
                        Column {
                            Box(Modifier.size(28.dp).sup(10.dp, fundo = c.first), contentAlignment = Alignment.Center) { T("${i + 1}", 9f, c.second, 900) }
                            T(t, 9f, Kp.slate900, 700, Modifier.padding(top = 6.dp))
                            T(s, 8f, Kp.slate500, Modifier.padding(top = 2.dp), linha = 13f)
                        }
                    }
            }
            seccoes.forEach { s ->
                Column(Modifier.fillMaxWidth().sup(16.dp, fundo = Color.White, borda = Kp.slate200).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    T(s.titulo, 9.5f, Kp.slate900, 700); s.paragrafos.forEach { T(it, 8.5f, Kp.slate600, linha = 13f) }
                }
            }
            Row(Modifier.fillMaxWidth().sup(16.dp, fundo = Color.White, borda = Kp.slate200).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { T("Prazo configurado", 8f, Kp.slate400, 700, maiusculas = true); T(prazo(prazoReservaMinutos), 10f, Kp.slate900, 800, Modifier.padding(top = 4.dp)) }
                FaIcone(Fa.CLOCK, 14f, marca.c600, regular = true)
            }
        }
    }
}

fun prazo(min: Long): String = when {
    min % 1440 == 0L -> "${min / 1440} dia(s)"
    min % 60 == 0L -> "${min / 60} hora(s)"
    else -> "$min minuto(s)"
}

/** Sobre (versão e distribuição). */
@Composable
fun SobreEcra(aoVoltar: () -> Unit) {
    val marca = LocalMarca.current
    Column {
        KpCabecalhoModal("Kupila", "Sobre", aoVoltar, icone = Fa.CIRCLE_INFO)
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            T("Kupila · Intermediação & Reservas Garantidas", 11f, Kp.slate900, 700)
            T("Versão ${ao.kupila.gestaonegocios.BuildConfig.VERSION_NAME}", 9f, Kp.slate500)
            T("Marketplace angolano de anúncio, compra, aluguer e reserva protegida de bens.", 9f, Kp.slate600, linha = 14f)
            Box(Modifier.fillMaxWidth().height(1.dp).background(marca.c100))
        }
    }
}

