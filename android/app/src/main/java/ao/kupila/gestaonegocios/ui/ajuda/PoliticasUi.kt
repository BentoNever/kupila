package ao.kupila.gestaonegocios.ui.ajuda

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ao.kupila.gestaonegocios.dados.local.SeccaoTexto
import ao.kupila.gestaonegocios.ui.comum.Fa
import ao.kupila.gestaonegocios.ui.kp.FaIcone
import ao.kupila.gestaonegocios.ui.kp.Kp
import ao.kupila.gestaonegocios.ui.kp.KpBotao
import ao.kupila.gestaonegocios.ui.kp.KpCabecalhoModal
import ao.kupila.gestaonegocios.ui.kp.LocalMarca
import ao.kupila.gestaonegocios.ui.kp.T
import ao.kupila.gestaonegocios.ui.kp.sup
import ao.kupila.gestaonegocios.ui.kp.toque
import kotlinx.coroutines.launch

/**
 * Manual, Políticas e Intermediação (`modal-kupila-policies`, HTML 8324-8391). Em contexto de aceite (cadastro ou anúncio),
 * "Li e compreendi estas regras" só fica disponível depois de ler até ao fim (gate da origem 8928-8970); o aceite em si é
 * a caixa explícita no formulário — a leitura não é tratada como consentimento.
 */
@Composable
fun PoliticasEcra(seccoes: List<SeccaoTexto>, versao: String, comAceite: Boolean, aoVoltar: () -> Unit, aoConfirmarLeitura: () -> Unit, aoSimular: (() -> Unit)? = null) {
    val marca = LocalMarca.current
    val lista = rememberLazyListState()
    val ambito = rememberCoroutineScope()
    val leuAteFim by remember { derivedStateOf { val i = lista.layoutInfo; i.totalItemsCount > 0 && (i.visibleItemsInfo.lastOrNull()?.index ?: 0) >= i.totalItemsCount - 1 } }
    val inicioPoliticas = seccoes.indexOfFirst { it.titulo.startsWith("Políticas de Uso") || it.titulo.uppercase().startsWith("POLÍTICAS") }
    Column {
        KpCabecalhoModal("Kupila AO", "Manual, Políticas e Intermediação", aoVoltar, icone = Fa.BOOK_OPEN, corIcone = Kp.yellow400, subtitulo = "Versão $versao")
        LazyColumn(Modifier.weight(1f, fill = false), state = lista, contentPadding = androidx.compose.foundation.layout.PaddingValues(10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Separador("Manual", Fa.COMPASS, marca.c50, marca.c100, marca.c700, Modifier.weight(1f)) { ambito.launch { lista.animateScrollToItem(1) } }
                    Separador("Políticas", Fa.SCALE_BALANCED, Color.White, Kp.slate300, Kp.slate700, Modifier.weight(1f)) { ambito.launch { lista.animateScrollToItem(2 + inicioPoliticas.coerceAtLeast(0)) } }
                    Separador("Simular", Fa.CALCULATOR, Kp.violet50, Kp.violet100, Kp.violet700, Modifier.weight(1f)) { aoSimular?.invoke() }
                }
            }
            item {
                Column(Modifier.fillMaxWidth().sup(16.dp, fundo = Kp.slate950).padding(10.dp)) {
                    T("Manual de instruções", 8f, Kp.yellow400, 900, espacamento = 1.12f, maiusculas = true)
                    T("Como usar o Kupila do início ao fim", 11f, Color.White, 900, Modifier.padding(top = 4.dp))
                    T("Guia simples com os principais passos para utilizar o Kupila, gerir os seus produtos, reservas e operações.", 8.5f, Kp.slate300, Modifier.padding(top = 4.dp), linha = 13f)
                }
            }
            itemsIndexed(seccoes) { i, s ->
                when {
                    i == inicioPoliticas -> Column {
                        Box(Modifier.padding(bottom = 14.dp).fillMaxWidth().height(1.dp).background(Kp.slate200))
                        T(s.titulo, 8f, Kp.slate400, 800, espacamento = 1.2f, maiusculas = true)
                        s.paragrafos.forEach { T(it, 8.5f, Kp.slate500, Modifier.padding(top = 4.dp), linha = 14f) }
                    }
                    i == inicioPoliticas + 1 -> Column(Modifier.fillMaxWidth().sup(16.dp, fundo = marca.c50, borda = marca.c100).padding(10.dp)) {
                        T(s.titulo, 10f, marca.c800, 700); s.paragrafos.forEach { T(it, 10f, Kp.slate600, Modifier.padding(top = 4.dp), linha = 17f) }
                    }
                    else -> Column {
                        if (s.titulo.isNotBlank()) T(s.titulo, 10f, Kp.slate900, 700)
                        s.paragrafos.forEach { T(it, 10f, Kp.slate600, Modifier.padding(top = 4.dp), linha = 17f) }
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FaIcone(Fa.CIRCLE_CHECK, 8f, marca.c600); Spacer(Modifier.width(6.dp)); T("Versão das políticas: $versao", 8f, Kp.slate500)
                }
            }
        }
        if (comAceite) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Kp.slate900))
            Column(Modifier.fillMaxWidth().background(Color.White).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FaIcone(if (leuAteFim) Fa.CIRCLE_CHECK else Fa.ARROW_DOWN, 7.5f, if (leuAteFim) Kp.emerald700 else Kp.amber700); Spacer(Modifier.width(4.dp))
                    T(if (leuAteFim) "Leitura concluída. Confirme e marque a caixa de aceite no formulário." else "Leia até ao final para confirmar a leitura.", 7.5f, if (leuAteFim) Kp.emerald700 else Kp.amber700)
                }
                KpBotao("Li e compreendi estas regras", aoConfirmarLeitura, icone = Fa.CHECK_DOUBLE, activo = leuAteFim, fundo = if (leuAteFim) marca.c600 else Kp.slate300,
                    cor = if (leuAteFim) Color.White else Kp.slate500, altura = 36.dp, raio = 12.dp, tamanho = 9f, sombra = leuAteFim)
            }
        }
    }
}

@Composable
private fun Separador(rotulo: String, icone: String, fundo: Color, borda: Color, cor: Color, modifier: Modifier, aoTocar: () -> Unit) {
    Row(modifier.height(27.dp).sup(999.dp, fundo = fundo, borda = borda).toque(aoTocar = aoTocar), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        FaIcone(icone, 8f, cor); Spacer(Modifier.width(6.dp)); T(rotulo, 8f, cor, 700)
    }
}
