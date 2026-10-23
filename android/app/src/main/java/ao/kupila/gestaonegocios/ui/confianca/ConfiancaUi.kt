package ao.kupila.gestaonegocios.ui.confianca

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ao.kupila.gestaonegocios.dados.ContaActiva
import ao.kupila.gestaonegocios.dados.eventos.Conjunto
import ao.kupila.gestaonegocios.ui.comum.Fa
import ao.kupila.gestaonegocios.ui.kp.FaIcone
import ao.kupila.gestaonegocios.ui.kp.Kp
import ao.kupila.gestaonegocios.ui.kp.KpCabecalhoModal
import ao.kupila.gestaonegocios.ui.kp.LocalMarca
import ao.kupila.gestaonegocios.ui.kp.T
import ao.kupila.gestaonegocios.ui.kp.sup
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Credibilidade: métricas públicas (`platformMetrics/trust`) e testemunhos aprovados, do cache; lê do servidor só ao abrir. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ConfiancaViewModel @Inject constructor(conta: ContaActiva) : ViewModel() {
    val metricas = conta.recursos.flatMapLatest { it?.eventos?.observar(Conjunto.CONFIANCA) ?: flowOf(emptyList()) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val testemunhos = conta.recursos.flatMapLatest { it?.eventos?.observar(Conjunto.TESTEMUNHOS_PUBLICOS) ?: flowOf(emptyList()) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    init { viewModelScope.launch { conta.recursos.value?.eventos?.actualizar(listOf(Conjunto.CONFIANCA, Conjunto.TESTEMUNHOS_PUBLICOS), automatica = true) } }
}

/** "Testemunhos & Negócios Verificados" (`openTrustCenter`, `#modal-testimonials`, HTML 7623-7643). */
@Composable
fun ConfiancaEcra(vm: ConfiancaViewModel, aoVoltar: () -> Unit) {
    val marca = LocalMarca.current
    val m by vm.metricas.collectAsStateWithLifecycle()
    val l by vm.testemunhos.collectAsStateWithLifecycle()
    val t = m.firstOrNull { it.id == "trust" }
    Column {
        KpCabecalhoModal("Credibilidade Kupila", "Testemunhos & Negócios Verificados", aoVoltar, icone = Fa.SHIELD_HALVED, corIcone = Kp.yellow400,
            subtitulo = "Só experiências ligadas a transacções concluídas podem ser publicadas.", corKicker = Kp.emerald300)
        Column(Modifier.weight(1f, fill = false).background(Kp.slate50).verticalScroll(rememberScrollState()).padding(10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val media = t?.n("averageRating") ?: 0.0
                Metrica((t?.n("completedDeals") ?: 0.0).toInt().toString(), "Negócios concluídos", Modifier.weight(1f))
                Metrica((t?.n("approvedTestimonials") ?: 0.0).toInt().toString(), "Testemunhos", Modifier.weight(1f))
                Metrica(if (media > 0) "%.1f ★".format(media) else "—", "Avaliação média", Modifier.weight(1f), if (media > 0) Kp.slate900 else Color(0xFFEA580C))
            }
            Row(Modifier.fillMaxWidth().sup(16.dp, fundo = marca.c50, borda = marca.c100).padding(9.dp)) {
                FaIcone(Fa.CERTIFICATE, 9f, marca.c700, Modifier.padding(top = 2.dp, end = 6.dp))
                androidx.compose.material3.Text(buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = marca.c800)) { append("Negócio verificado:") }
                    append(" o selo só aparece quando o testemunho está ligado a uma reserva concluída no Kupila e foi aprovado para publicação.")
                }, color = marca.c900, fontSize = 8.5.sp, lineHeight = 14.sp)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) { T("Experiências verificadas", 9.5f, Kp.slate900, 700); T("Publicadas com consentimento dos participantes.", 8.5f, Kp.slate400, Modifier.padding(top = 2.dp)) }
                T("${l.size} verificadas", 8f, marca.c700, 700)
            }
            if (l.isEmpty()) Row(Modifier.fillMaxWidth().tracejado(Kp.slate300).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                FaIcone(Fa.COMMENTS, 16f, Kp.slate400, regular = true); Spacer(Modifier.width(6.dp))
                T("Ainda não existem testemunhos reais aprovados. Eles aparecerão aqui depois de negócios concluídos e verificados.", 9f, Kp.slate400, linha = 20f, centro = true)
            }
            l.forEach { x ->
                Column(Modifier.fillMaxWidth().sup(16.dp, fundo = Color.White, borda = Kp.slate200).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        T("★".repeat(x.n("rating").toInt().coerceIn(0, 5)), 10f, Kp.amber400, 900); Spacer(Modifier.width(8.dp))
                        T(x.s("publicName"), 9f, Kp.slate900, 800, Modifier.weight(1f))
                        T("Negócio verificado", 6.5f, marca.c800, 900, Modifier.sup(999.dp, fundo = marca.c50, borda = marca.c100).padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                    T(x.s("text"), 9f, Kp.slate600, linha = 14f)
                    T(listOf(x.s("listingTitle"), x.s("province")).filter { it.isNotBlank() }.joinToString(" · "), 7.5f, Kp.slate400)
                }
            }
        }
    }
}

@Composable
private fun Metrica(valor: String, rotulo: String, modifier: Modifier, cor: Color = Kp.slate900) {
    Column(modifier.sup(16.dp, fundo = Color.White, borda = Kp.slate900).padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        T(valor, 13f, cor, 900); T(rotulo, 8f, Kp.slate400, Modifier.padding(top = 4.dp))
    }
}

private fun Modifier.tracejado(cor: Color) = this.drawBehind {
    val r = 16.dp.toPx()
    drawRoundRect(cor, cornerRadius = CornerRadius(r, r), style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))))
}

