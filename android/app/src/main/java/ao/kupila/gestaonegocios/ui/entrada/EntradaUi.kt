package ao.kupila.gestaonegocios.ui.entrada

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ao.kupila.gestaonegocios.dados.sessao.ContaConhecida
import ao.kupila.gestaonegocios.dominio.perfis.Papel
import ao.kupila.gestaonegocios.ui.comum.Fa
import ao.kupila.gestaonegocios.ui.comum.modeloImagem
import ao.kupila.gestaonegocios.ui.kp.FaIcone
import ao.kupila.gestaonegocios.ui.kp.Kp
import ao.kupila.gestaonegocios.ui.kp.KpBotao
import ao.kupila.gestaonegocios.ui.kp.KpBotaoSec
import ao.kupila.gestaonegocios.ui.kp.KpCaixa
import ao.kupila.gestaonegocios.ui.kp.KpEntrada
import ao.kupila.gestaonegocios.ui.kp.KpFechar
import ao.kupila.gestaonegocios.ui.kp.KpFolha
import ao.kupila.gestaonegocios.ui.kp.KpRotulo
import ao.kupila.gestaonegocios.ui.kp.LocalMarca
import ao.kupila.gestaonegocios.ui.kp.T
import ao.kupila.gestaonegocios.ui.kp.alternarEcraCheio
import ao.kupila.gestaonegocios.ui.kp.gradiente
import ao.kupila.gestaonegocios.ui.kp.sup
import ao.kupila.gestaonegocios.ui.kp.toque
import coil3.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Logótipo da plataforma (`kupila-brand-logo-target`): imagem do Administrador ou o "K" de reserva. */
@Composable
fun LogoMarca(tamanho: Float, raio: Float, letra: Float, logo: String?, modifier: Modifier = Modifier) {
    val marca = LocalMarca.current
    Box(modifier.size(tamanho.dp).sup(raio.dp, fundo = Color.White), contentAlignment = Alignment.Center) {
        if (!logo.isNullOrBlank()) AsyncImage(modeloImagem(logo), null, Modifier.size(tamanho.dp).clip(RoundedCornerShape(raio.dp)), contentScale = ContentScale.Crop)
        else T("K", letra, marca.c700, 700)
    }
}

/** "Kupila.ao" com o ".ao" em amarelo (`text-yellow-400`). */
@Composable
fun NomeMarca(tamanho: Float, cor: Color = Color.White) {
    androidx.compose.material3.Text(buildAnnotatedString {
        append("Kupila"); withStyle(SpanStyle(color = Kp.yellow400)) { append(".ao") }
    }, color = cor, fontSize = tamanho.sp, fontWeight = FontWeight.Bold, letterSpacing = (-tamanho * .025f).sp, lineHeight = tamanho.sp)
}

/** TELA DE LOGIN (`#login-screen`, HTML 6498-6540) com o reinício rápido de contas reconhecidas. */
@Composable
fun LoginEcra(logo: String?, aviso: String?, aoCriarConta: () -> Unit, aoRecuperar: ((String) -> Unit)? = null, vm: LoginViewModel = hiltViewModel()) {
    val e by vm.estado.collectAsStateWithLifecycle()
    val contas by vm.contas.collectAsStateWithLifecycle()
    val marca = LocalMarca.current
    var senhaVisivel by rememberSaveable { mutableStateOf(false) }
    var recuperar by rememberSaveable { mutableStateOf(false) }
    val cheio = alternarEcraCheio()
    Box(Modifier.fillMaxSize().background(Kp.slate100).safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(12.dp, 12.dp, 12.dp, 16.dp),
        contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 384.dp).fillMaxWidth().padding(horizontal = 4.dp)
            .sup(15.dp, fundo = Color.White, borda = Kp.gray200, sombra = Color.Black.copy(alpha = .25f), desfoque = 50.dp)) {
            Box(Modifier.fillMaxWidth().background(marca.c700).padding(10.dp, 8.dp, 10.dp, 7.dp)) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    LogoMarca(56f, 16f, 30f, logo, Modifier.padding(bottom = 12.dp))
                    NomeMarca(13.76f)
                    T("Acesso seguro à plataforma", 6.8f, marca.c100, modifier = Modifier.padding(top = 1.dp))
                }
                Box(Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = 4.dp).size(40.dp).sup(12.dp, fundo = Color.White.copy(alpha = .10f)).toque(aoTocar = cheio),
                    contentAlignment = Alignment.Center) { FaIcone(Fa.EXPAND, 14f, Color.White) }
            }
            Column(Modifier.fillMaxWidth().padding(9.dp, 7.dp, 9.dp, 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                aviso?.let { Erro(it) }
                if (contas.isNotEmpty()) ReinicioRapido(contas, vm::escolherConta, vm::esquecerConta)
                Column {
                    KpRotulo("E-mail")
                    KpEntrada(e.email, vm::email, dica = "exemplo@kupila.ao", teclado = KeyboardType.Email)
                }
                Column {
                    KpRotulo("Palavra-passe")
                    KpEntrada(e.senha, vm::senha, senha = !senhaVisivel, ime = ImeAction.Go, aoConcluir = vm::entrar, direita = {
                        Box(Modifier.size(25.dp).toque { senhaVisivel = !senhaVisivel }, contentAlignment = Alignment.Center) {
                            FaIcone(if (senhaVisivel) Fa.EYE_SLASH else Fa.EYE, 9f, Kp.gray400, regular = true)
                        }
                    })
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).toque { recuperar = true }, verticalAlignment = Alignment.CenterVertically) {
                        FaIcone(Fa.KEY, 7.2f, marca.c700); Spacer(Modifier.width(4.dp)); T("Esqueceu as credenciais?", 7.2f, marca.c700, 700)
                    }
                    T("Recuperação segura", 6.2f, Kp.gray400)
                }
                e.erro?.let { Erro(it) }
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    FaIcone(Fa.BOLT, 6.2f, marca.c700); Spacer(Modifier.width(4.dp)); T(e.estadoAutomatico, 6.2f, marca.c700, centro = true)
                }
                KpBotao("Entrar", vm::entrar, icone = Fa.RIGHT_TO_BRACKET, ocupado = e.aEntrar)
                KpBotaoSec("Criar nova conta", aoCriarConta, icone = Fa.USER_PLUS)
            }
        }
    }
    if (recuperar) RecuperacaoFolha(e.email, contas, aoFechar = { recuperar = false })
}

@Composable
private fun Erro(texto: String) {
    KpCaixa(Kp.red50, Color(0xFFFEE2E2), raio = 12.dp, pad = 10.dp) { T(texto, 8f, Kp.red700, linha = 11f) }
}

/** "Reinício rápido": cartões arrastáveis das contas reconhecidas (`renderKnownDeviceAccounts`, HTML 10420). */
@Composable
private fun ReinicioRapido(contas: List<ContaConhecida>, aoEscolher: (ContaConhecida) -> Unit, aoEsquecer: (ContaConhecida) -> Unit) {
    val marca = LocalMarca.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) { FaIcone(Fa.BOLT, 7.3f, marca.c600); Spacer(Modifier.width(4.dp)); T("Reinício rápido", 7.3f, Color(0xFF1F2937), 700) }
                T("Arraste para os lados e toque numa conta para continuar.", 5.5f, Kp.gray400)
            }
            T(contas.size.toString(), 5.5f, marca.c800, 700, Modifier.sup(999.dp, fundo = marca.c50).padding(horizontal = 5.dp, vertical = 2.dp))
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(1.dp, 1.dp, 1.dp, 3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            contas.forEach { c -> CartaoContaRapida(c, { aoEscolher(c) }, { aoEsquecer(c) }) }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Kp.slate100))
    }
}

@Composable
private fun CartaoContaRapida(c: ContaConhecida, aoEscolher: () -> Unit, aoEsquecer: () -> Unit) {
    val marca = LocalMarca.current
    Box(Modifier.width(78.dp).height(66.dp)
        .sup(10.dp, pincel = gradiente(155f, Color.White, marca.c50), borda = Color(0xFFBDDBD2), sombra = marca.c900.copy(alpha = .08f), desfoque = 13.dp)
        .toque(aoTocar = aoEscolher).padding(4.dp)) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(24.dp).sup(7.dp, fundo = marca.c100, borda = marca.c600.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
                T(iniciais(c.nome.ifBlank { c.email }), 7f, marca.c800, 900)
            }
            T(c.nome.ifBlank { c.email }, 6.3f, Color(0xFF1F2937), 800, Modifier.padding(top = 2.dp), max = 1, centro = true)
            T(Papel.canonico(c.papel)?.rotulo ?: "Utilizador", 5f, Kp.gray400, max = 1, centro = true)
            Spacer(Modifier.height(2.dp))
            val (fundo, borda, cor) = if (c.rapido) Triple(Color.White, marca.c200, marca.c900) else Triple(Kp.amber50, Kp.amber200, Kp.amber900)
            Row(Modifier.sup(999.dp, fundo = fundo, borda = borda).padding(horizontal = 5.dp, vertical = 1.5.dp), verticalAlignment = Alignment.CenterVertically) {
                FaIcone(if (c.rapido) Fa.BOLT else Fa.KEY, 4.5f, cor); Spacer(Modifier.width(2.dp)); T(if (c.rapido) "Entrar" else "Confirmar", 4.8f, cor, 800)
            }
        }
        Box(Modifier.align(Alignment.TopEnd).size(14.dp).sup(5.dp, fundo = Color.White, borda = Kp.slate200).toque(aoTocar = aoEsquecer), contentAlignment = Alignment.Center) {
            FaIcone(Fa.XMARK, 7f, Kp.slate300)
        }
    }
}

fun iniciais(nome: String) = nome.split(" ").filter(String::isNotBlank).take(2).joinToString("") { it.take(1).uppercase() }.ifEmpty { "K" }

private val LUANDA: TimeZone = TimeZone.getTimeZone("Africa/Luanda")
fun formatarData(ms: Long, comHora: Boolean = true): String = SimpleDateFormat(if (comHora) "dd/MM/yyyy HH:mm" else "dd/MM/yyyy", Locale.forLanguageTag("pt-AO"))
    .apply { timeZone = LUANDA }.format(Date(ms))

/** RECUPERAÇÃO SEGURA DE CREDENCIAIS (`modal-credential-recovery`, HTML 6584-6600): folha inferior sobre o login. */
@Composable
fun RecuperacaoFolha(emailInicial: String, contas: List<ContaConhecida>, aoFechar: () -> Unit, vm: RecuperacaoViewModel = hiltViewModel()) {
    val e by vm.estado.collectAsStateWithLifecycle()
    val marca = LocalMarca.current
    remember(emailInicial) { if (e.email.isBlank() && emailInicial.isNotBlank()) vm.email(emailInicial); 0 }
    KpFolha("Conta Kupila", "Recuperar credenciais", aoFechar, iconeTitulo = Fa.LOCK, alturaMax = .9f) {
        KpCaixa(marca.c50, marca.c100) {
            androidx.compose.material3.Text(buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Segurança:") }
                append(" o Kupila nunca revela nem guarda a sua palavra-passe. Será enviado um link oficial de redefinição para o e-mail da conta.")
            }, color = marca.c900, fontSize = 10.sp, lineHeight = 16.sp)
        }
        if (contas.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            T("Contas reconhecidas neste dispositivo", 9f, Kp.gray400, 700)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                contas.forEach { c ->
                    T(c.email, 9f, marca.c800, 700, Modifier.sup(10.dp, fundo = marca.c50, borda = marca.c100).toque { vm.email(c.email) }.padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }
        }
        Column {
            KpRotulo("E-mail da conta", 10f)
            KpEntrada(e.email, vm::email, dica = "seuemail@dominio.com", teclado = KeyboardType.Email, ime = ImeAction.Send, aoConcluir = { vm.enviar() }, altura = 38.dp, raio = 12.dp)
        }
        e.erro?.let { Erro(it) }
        if (e.enviado) KpCaixa(Kp.emerald50, Kp.emerald100) {
            T("Se o e-mail estiver associado a uma conta Kupila, o link de recuperação foi enviado. Verifique também Spam/Lixo e Promoções.", 10f, Kp.emerald900, linha = 15f)
        }
        val restante = ((e.esperarAte - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
        KpBotao(if (restante > 0) "Reenviar em ${restante}s" else "Enviar link de recuperação", { vm.enviar() }, icone = Fa.ENVELOPE_CIRCLE_CHECK, ocupado = e.aEnviar,
            activo = restante <= 0L, altura = 36.dp, raio = 12.dp, tamanho = 10f)
        KpCaixa(Kp.slate50, Kp.slate100, raio = 12.dp, pad = 10.dp) {
            Row { FaIcone(Fa.CIRCLE_INFO, 8f, marca.c600); Spacer(Modifier.width(4.dp))
                T("O Kupila usa o serviço oficial do Firebase Authentication. Por segurança, o app não confirma se um e-mail está ou não cadastrado. Se não receber a mensagem, verifique Spam/Lixo, Promoções e se digitou o endereço correcto.", 8f, Kp.slate500, linha = 13f) }
        }
        T("Se nunca iniciou sessão neste dispositivo e não recorda o e-mail, contacte o Administrador para confirmar a identificação da sua conta.", 9f, Kp.gray400, centro = true, linha = 14f)
    }
}

/** Compatibilidade de rota: abre a mesma folha. */
@Composable
fun RecuperacaoEcra(aoVoltar: () -> Unit) = RecuperacaoFolha("", emptyList(), aoVoltar)

/** Conflito de sessão (`modal-single-session-conflict`, HTML 6479-6492). */
@Composable
fun ConflitoSessaoDialogo(dispositivo: String, vistoEm: Long?, ocupado: Boolean, aoContinuar: () -> Unit, aoVoltar: () -> Unit) {
    val marca = LocalMarca.current
    Dialog(aoVoltar, DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Box(Modifier.fillMaxSize().background(Kp.slate950.copy(alpha = .70f)).padding(16.dp), contentAlignment = Alignment.Center) {
            Column(Modifier.widthIn(max = 340.dp).fillMaxWidth().sup(24.dp, fundo = Color.White, borda = Color.White.copy(alpha = .2f), sombra = Color.Black.copy(alpha = .3f), desfoque = 40.dp)) {
                Box(Modifier.fillMaxWidth().background(Kp.slate950).padding(20.dp, 20.dp, 20.dp, 16.dp)) {
                    Column {
                        Box(Modifier.size(44.dp).sup(16.dp, fundo = Kp.amber400.copy(alpha = .15f)), contentAlignment = Alignment.Center) { FaIcone(Fa.MOBILE_SCREEN_BUTTON, 18f, Kp.amber300) }
                        Spacer(Modifier.height(12.dp))
                        T("Conta em uso noutro dispositivo", 14f, Color.White, 700)
                        T("Para proteger a conta, o Kupila permite apenas uma sessão activa por utilizador.", 10f, Kp.slate400, Modifier.padding(top = 4.dp), linha = 16f)
                    }
                    Box(Modifier.align(Alignment.TopEnd)) { KpFechar(aoVoltar) }
                }
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    KpCaixa(Kp.amber50, Kp.amber100, raio = 16.dp, pad = 12.dp) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(36.dp).sup(12.dp, fundo = Color.White, borda = Kp.amber100), contentAlignment = Alignment.Center) { FaIcone(Fa.SHIELD_HALVED, 14f, Kp.amber700) }
                            Column {
                                T("Sessão actualmente activa", 10f, Kp.amber900, 700)
                                T(dispositivo.ifBlank { "Outro dispositivo" } + (vistoEm?.let { " · ${formatarData(it)}" } ?: ""), 10f, Kp.amber800, Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                    T("Para continuar neste dispositivo, encerre primeiro a sessão anterior. O outro dispositivo será desconectado imediatamente e este passará a ser o único autorizado.", 10f, Color(0xFF6B7280), linha = 16f)
                    KpBotao("Encerrar sessão anterior e continuar", aoContinuar, icone = Fa.ARROW_RIGHT_TO_BRACKET, ocupado = ocupado, fundo = marca.c700, altura = 40.dp, raio = 12.dp, tamanho = 12f, sombra = false)
                }
            }
        }
    }
}
