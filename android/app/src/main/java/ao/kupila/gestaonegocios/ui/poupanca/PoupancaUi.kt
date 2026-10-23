package ao.kupila.gestaonegocios.ui.poupanca

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ao.kupila.gestaonegocios.dados.actualizacao.ActualizadorApp
import ao.kupila.gestaonegocios.dados.rede.Economia
import ao.kupila.gestaonegocios.dados.rede.RedeFirestore
import ao.kupila.gestaonegocios.ui.comum.Fa
import ao.kupila.gestaonegocios.ui.entrada.formatarData
import ao.kupila.gestaonegocios.ui.kp.FaIcone
import ao.kupila.gestaonegocios.ui.kp.Kp
import ao.kupila.gestaonegocios.ui.kp.KpCabecalhoModal
import ao.kupila.gestaonegocios.ui.kp.LocalMarca
import ao.kupila.gestaonegocios.ui.kp.T
import ao.kupila.gestaonegocios.ui.kp.sup
import ao.kupila.gestaonegocios.ui.kp.toque

/**
 * "Sincronização do dispositivo" (`#modal-offline-sync`, HTML 17296; `openKupilaOfflineCenter`) + modo poupança e actualizador
 * (pedidos do utilizador, como no PDV Digital): contadores reais, pausa total, "Enviar agora" e versão do app.
 */
@Composable
fun PoupancaEcra(aoVoltar: () -> Unit, offline: Boolean = false, aoSincronizar: () -> Unit = {}, aoActualizacao: (() -> Unit)? = null) {
    val marca = LocalMarca.current
    val pausada by Economia.pausada.collectAsStateWithLifecycle()
    val janela by Economia.janela.collectAsStateWithLifecycle()
    val desde by Economia.desde.collectAsStateWithLifecycle()
    val lidos by Economia.lidos.collectAsStateWithLifecycle()
    val escritos by Economia.escritos.collectAsStateWithLifecycle()
    val evitados by Economia.evitados.collectAsStateWithLifecycle()
    val delta by Economia.poupadosDelta.collectAsStateWithLifecycle()
    val nova by ActualizadorApp.disponivel.collectAsStateWithLifecycle()
    val alertas = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    Column {
        KpCabecalhoModal("Continuidade de trabalho", "Sincronização do dispositivo", aoVoltar, icone = Fa.CLOUD_ARROW_UP, corIcone = Kp.yellow400,
            subtitulo = "O Kupila sincroniza silenciosamente quando a ligação está disponível.", corKicker = Kp.emerald300)
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().background(Color.White).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val (txt, f, c) = when {
                    pausada && !janela -> Triple("Modo poupança · ligação em pausa", Kp.amber50, Kp.amber800)
                    offline -> Triple("Offline · as tarefas seguem quando houver rede", Kp.amber50, Kp.amber800)
                    else -> Triple("Online · tudo sincronizado", marca.c50, marca.c700)
                }
                Row(Modifier.weight(1f).height(31.dp).sup(15.dp, fundo = f, borda = marca.c100).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    FaIcone(Fa.CLOUD, 9f, c); Spacer(Modifier.width(8.dp)); T(txt, 8.5f, c, max = 1)
                }
                Row(Modifier.height(31.dp).sup(15.dp, fundo = marca.c600).toque(aoTocar = { if (pausada) RedeFirestore.enviarAgora() else aoSincronizar() }).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) { FaIcone(Fa.ARROWS_ROTATE, 8.5f, Color.White); Spacer(Modifier.width(6.dp)); T("Sincronizar", 8.5f, Color.White, 700) }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Kp.slate200))
            Row(Modifier.fillMaxWidth().background(Kp.slate50).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { FaIcone(Fa.MOBILE_SCREEN_BUTTON, 8.5f, marca.c700); Spacer(Modifier.width(6.dp)); T("Continuidade em segundo plano", 8.5f, Kp.slate800, 700) }
                    T("O Kupila mantém as tarefas persistidas e retoma-as automaticamente em segundo plano, sem interromper a utilização. Se o sistema suspender a execução, a sincronização é retomada assim que possível.",
                        6.5f, Kp.slate500, Modifier.padding(top = 4.dp), linha = 11f)
                }
                Spacer(Modifier.width(10.dp))
                Row(Modifier.height(41.dp).sup(15.dp, fundo = Color.White, borda = marca.c200)
                    .toque { if (Build.VERSION.SDK_INT >= 33) alertas.launch(Manifest.permission.POST_NOTIFICATIONS) }.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    FaIcone(Fa.BELL, 14f, marca.c700, regular = true); Spacer(Modifier.width(6.dp)); T("Ativar alertas", 14f, marca.c700, 900)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Kp.slate200))
            Row(Modifier.fillMaxWidth().background(Color(0xFFFFFDF2)).padding(16.dp, 12.dp)) {
                FaIcone(Fa.SHIELD_HALVED, 8f, Kp.amber900, Modifier.padding(top = 2.dp, end = 6.dp))
                T("As operações pendentes ficam protegidas neste dispositivo. A fila não bloqueia a navegação nem fecha modais.", 8f, Kp.amber900, linha = 13f)
            }
            Column(Modifier.fillMaxWidth().background(Kp.slate50).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(24.dp).sup(24.dp, fundo = marca.c600, forma = CircleShape), contentAlignment = Alignment.Center) { FaIcone(Fa.CHECK, 11f, Color.White) }
                    T("Tudo sincronizado", 10f, Kp.slate700, 700, Modifier.padding(top = 12.dp))
                    T("Não existem tarefas pendentes neste dispositivo.", 8f, Kp.slate400, Modifier.padding(top = 4.dp))
                }
                // Modo poupança (economia de quotas, igual ao PDV Digital)
                Column(Modifier.fillMaxWidth().sup(16.dp, fundo = Color.White, borda = Kp.slate200).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FaIcone(Fa.BOLT, 10f, marca.c600); Spacer(Modifier.width(6.dp)); T("Modo poupança", 10f, Kp.slate900, 800, Modifier.weight(1f))
                        T(if (pausada) "EM PAUSA" else "INTELIGENTE", 6.5f, if (pausada) Kp.amber800 else marca.c800, 900,
                            Modifier.sup(999.dp, fundo = if (pausada) Kp.amber100 else marca.c50).padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                    T(if (pausada) "Ligação em pausa" + (desde?.let { " desde ${formatarData(it)}" } ?: "") + ": nada é lido nem escrito; o app mostra o que está guardado e as suas acções seguem sozinhas ao religar."
                        else "Sempre activa: nada fica a escutar em segundo plano, o app só actualiza quando está a usá-lo e pede ao servidor apenas o que mudou.", 8f, Kp.slate500, linha = 12f)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (pausada) {
                            Accao("Ligar", Fa.PLAY, marca.c600, Color.White, Modifier.weight(1f), RedeFirestore::ligar)
                            Accao(if (janela) "A enviar…" else "Enviar agora", Fa.CLOUD_ARROW_UP, marca.c50, marca.c700, Modifier.weight(1f)) { RedeFirestore.enviarAgora() }
                        } else Accao("Pausar a ligação", Fa.PAUSE, Kp.slate100, Kp.slate700, Modifier.weight(1f), RedeFirestore::pausar)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Contador("Lidos", lidos, Modifier.weight(1f)); Contador("Escritos", escritos, Modifier.weight(1f))
                        Contador("Evitados", evitados, Modifier.weight(1f)); Contador("Poupados", delta, Modifier.weight(1f))
                    }
                    val total = lidos + delta + evitados
                    if (total > 0) T("Poupança de leituras: ${"%.1f".format(100.0 * (delta + evitados) / total)}%", 8.5f, marca.c700, 800)
                }
                // Actualização do app (igual ao PDV Digital)
                aoActualizacao?.let { abrir ->
                    Row(Modifier.fillMaxWidth().sup(16.dp, fundo = Color.White, borda = if (nova != null) Kp.amber200 else Kp.slate200).toque(aoTocar = abrir).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        FaIcone(Fa.DOWNLOAD, 10f, if (nova != null) Kp.amber700 else marca.c600); Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            T("Actualização do app", 10f, Kp.slate900, 800)
                            T(nova?.let { "Nova versão ${it.nome} disponível · toque para instalar" } ?: "Versão ${ao.kupila.gestaonegocios.BuildConfig.VERSION_NAME} · verificação automática", 8f, Kp.slate500)
                        }
                        FaIcone(Fa.CHEVRON_RIGHT, 8f, Kp.slate300)
                    }
                }
            }
        }
    }
}

@Composable
private fun Accao(texto: String, icone: String, fundo: Color, cor: Color, modifier: Modifier, aoTocar: () -> Unit) {
    Row(modifier.height(30.dp).sup(10.dp, fundo = fundo).toque(aoTocar = aoTocar), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        FaIcone(icone, 8f, cor); Spacer(Modifier.width(6.dp)); T(texto, 8.5f, cor, 700)
    }
}

@Composable
private fun Contador(rotulo: String, v: Long, modifier: Modifier) {
    Column(modifier.sup(10.dp, fundo = Kp.slate50, borda = Kp.slate100).padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        T("%,d".format(v).replace(',', ' '), 9f, Kp.slate900, 800); T(rotulo, 6.5f, Kp.slate500)
    }
}
