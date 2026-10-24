package ao.kupila.gestaonegocios.ui.admin

import android.content.Context
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ao.kupila.gestaonegocios.dados.ContaActiva
import ao.kupila.gestaonegocios.dados.admin.AjustesAdminRepositorio
import ao.kupila.gestaonegocios.dados.ajustes.AjustesRepositorio
import ao.kupila.gestaonegocios.dados.eventos.Conjunto
import ao.kupila.gestaonegocios.dados.media.Imagens
import ao.kupila.gestaonegocios.dados.remoto.MensagensErro
import ao.kupila.gestaonegocios.dados.remoto.Origem
import ao.kupila.gestaonegocios.dominio.ajustes.AjustesPlataforma
import ao.kupila.gestaonegocios.dominio.ajustes.ModoAlerta
import ao.kupila.gestaonegocios.dominio.ajustes.ValidacaoAjustes
import ao.kupila.gestaonegocios.dominio.aparencia.IdentidadeVisual
import ao.kupila.gestaonegocios.dominio.aparencia.ModoAparencia
import ao.kupila.gestaonegocios.dominio.aparencia.PaletaVitrine
import ao.kupila.gestaonegocios.dominio.aparencia.TemaVisual
import ao.kupila.gestaonegocios.dominio.financeiro.Dinheiro
import ao.kupila.gestaonegocios.dominio.financeiro.FormatoAoa
import ao.kupila.gestaonegocios.dominio.financeiro.Promocao
import ao.kupila.gestaonegocios.dominio.notificacoes.AccaoCampanha
import ao.kupila.gestaonegocios.dominio.notificacoes.Campanha
import ao.kupila.gestaonegocios.dominio.notificacoes.PrioridadeCampanha
import ao.kupila.gestaonegocios.dominio.notificacoes.PublicoCampanha
import ao.kupila.gestaonegocios.dominio.notificacoes.RegrasCampanha
import ao.kupila.gestaonegocios.ui.comum.Fa
import ao.kupila.gestaonegocios.ui.entrada.LogoMarca
import ao.kupila.gestaonegocios.ui.kp.FaIcone
import ao.kupila.gestaonegocios.ui.kp.Kp
import ao.kupila.gestaonegocios.ui.kp.KpBotao
import ao.kupila.gestaonegocios.ui.kp.KpEntrada
import ao.kupila.gestaonegocios.ui.kp.KpFechar
import ao.kupila.gestaonegocios.ui.kp.KpRotulo
import ao.kupila.gestaonegocios.ui.kp.KpSelect
import ao.kupila.gestaonegocios.ui.kp.LocalMarca
import ao.kupila.gestaonegocios.ui.kp.T
import ao.kupila.gestaonegocios.ui.kp.gradiente
import ao.kupila.gestaonegocios.ui.kp.sup
import ao.kupila.gestaonegocios.ui.kp.toque
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/** Ajustes da Plataforma (`#modal-settings`, HTML 7791-7990; `saveSettings`): só o Administrador grava. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AjustesViewModel @Inject constructor(
    private val conta: ContaActiva, val ajustes: AjustesRepositorio, private val admin: AjustesAdminRepositorio, private val imagens: Imagens,
    @param:ApplicationContext private val contexto: Context,
) : ViewModel() {
    val mensagem = MutableStateFlow<String?>(null)
    val ocupado = MutableStateFlow(false)
    private val perfil get() = conta.recursos.value!!.perfil
    private fun lista(c: Conjunto) = conta.recursos.flatMapLatest { it?.eventos?.observar(c) ?: flowOf(emptyList()) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val intencoes = lista(Conjunto.INTENCOES_TODAS)
    val reservas = lista(Conjunto.RESERVAS_TODAS)
    private val prefs = contexto.getSharedPreferences("kupila_alertas_dispositivo", Context.MODE_PRIVATE)
    val modoAlerta = MutableStateFlow(ModoAlerta.de(prefs.getString("modo", null)))
    val notificacaoSistema = MutableStateFlow(prefs.getBoolean("sistema", true))
    val haptico = MutableStateFlow(prefs.getBoolean("haptico", true))
    fun dispositivo(modo: ModoAlerta? = null, sistema: Boolean? = null, hap: Boolean? = null) {
        modo?.let { modoAlerta.value = it }; sistema?.let { notificacaoSistema.value = it }; hap?.let { haptico.value = it }
        prefs.edit().putString("modo", modoAlerta.value.codigo).putBoolean("sistema", notificacaoSistema.value).putBoolean("haptico", haptico.value).apply()
    }

    private fun accao(bloco: suspend () -> String) = viewModelScope.launch {
        if (ocupado.value) return@launch
        ocupado.value = true
        mensagem.value = runCatching { bloco() }.getOrElse { MensagensErro.de(it).takeIf { m -> m != MensagensErro.GENERICA } ?: (it.message ?: MensagensErro.GENERICA) }
        ocupado.value = false
    }
    fun guardar(a: AjustesPlataforma) = accao { val e = ValidacaoAjustes.validar(a); require(e.isEmpty()) { e.joinToString("\n") }; admin.guardar(a, perfil); "Ajustes guardados e aplicados a novos cálculos." }
    fun identidade(id: IdentidadeVisual, logo: String? = null) = accao { admin.guardarIdentidade(id, logo, perfil); "Identidade visual aplicada e sincronizada." }
    fun logo(uri: Uri) = accao { val b = imagens.fotoPerfil(uri, 320); admin.guardarIdentidade(ajustes.identidade.value, Origem.dataUrlJpeg(b), perfil); "Logo actualizado." }
    fun restaurar() = accao { admin.restaurarPadroes(perfil); "Configurações padrão restauradas (dados operacionais intactos)." }
    fun campanha(titulo: String, mensagemTxt: String, publico: PublicoCampanha, prioridade: PrioridadeCampanha, dias: Long, accaoC: AccaoCampanha, cta: String) = accao {
        require(titulo.isNotBlank() && mensagemTxt.isNotBlank()) { "Indique título e mensagem da campanha." }
        val agora = Instant.now()
        val c = Campanha(Origem.id("MKT"), titulo.trim(), mensagemTxt.trim(), publico, prioridade, accaoC, cta.trim(), agora, agora.plus(dias.coerceIn(1, 90), ChronoUnit.DAYS), false, agora)
        admin.guardarCampanhas(listOf(c) + ajustes.campanhas.value, perfil, "CREATE_CAMPAIGN", c.id); "Campanha publicada."
    }
    fun alternarCampanha(c: Campanha) = accao {
        admin.guardarCampanhas(ajustes.campanhas.value.map { if (it.id == c.id) it.copy(desactivada = !it.desactivada) else it }, perfil, "TOGGLE_CAMPAIGN", c.id); "Campanha actualizada."
    }
}

@Composable
fun AjustesEcra(vm: AjustesViewModel, aoFechar: () -> Unit, aoHistorico: () -> Unit) {
    val marca = LocalMarca.current
    val a by vm.ajustes.ajustes.collectAsStateWithLifecycle()
    val id by vm.ajustes.identidade.collectAsStateWithLifecycle()
    val campanhas by vm.ajustes.campanhas.collectAsStateWithLifecycle()
    val msg by vm.mensagem.collectAsStateWithLifecycle()
    val ocupado by vm.ocupado.collectAsStateWithLifecycle()
    val intencoes by vm.intencoes.collectAsStateWithLifecycle()
    val reservas by vm.reservas.collectAsStateWithLifecycle()
    val f = a.financeiro
    var sinal by rememberSaveable(a.versao) { mutableStateOf(f.sinalVenda.valor.stripTrailingZeros().toPlainString()) }
    var meses by rememberSaveable(a.versao) { mutableStateOf(f.mesesSinalAluguer.toString()) }
    var comissao by rememberSaveable(a.versao) { mutableStateOf(f.comissao.valor.stripTrailingZeros().toPlainString()) }
    var horas by rememberSaveable(a.versao) { mutableStateOf((a.prazoReservaMinutos / 60).toString()) }
    var inact by rememberSaveable(a.versao) { mutableStateOf(a.inactividadeMinutos.toString()) }
    var taxa by rememberSaveable(a.versao) { mutableStateOf(f.taxaAnuncio.valor.stripTrailingZeros().toPlainString()) }
    var entidade by rememberSaveable(a.versao) { mutableStateOf(a.entidadePagamento) }
    var destaque by rememberSaveable(a.versao) { mutableStateOf(f.promocoes[Promocao.DESTAQUE]?.valor?.stripTrailingZeros()?.toPlainString().orEmpty()) }
    var top by rememberSaveable(a.versao) { mutableStateOf(f.promocoes[Promocao.TOP]?.valor?.stripTrailingZeros()?.toPlainString().orEmpty()) }
    var prioridade by rememberSaveable(a.versao) { mutableStateOf(f.promocoes[Promocao.PRIORIDADE]?.valor?.stripTrailingZeros()?.toPlainString().orEmpty()) }
    var dias by rememberSaveable(a.versao) { mutableStateOf(f.duracaoPromocaoDias.toString()) }
    var alReserva by rememberSaveable(a.versao) { mutableStateOf(a.alertas.reserva) }
    var alInteresse by rememberSaveable(a.versao) { mutableStateOf(a.alertas.interesse) }
    var alEmail by rememberSaveable(a.versao) { mutableStateOf(a.alertas.email) }
    var alSms by rememberSaveable(a.versao) { mutableStateOf(a.alertas.sms) }
    var limiar by rememberSaveable(a.versao) { mutableStateOf(a.alertas.limiarClientesDistintos.toString()) }
    var janela by rememberSaveable(a.versao) { mutableStateOf(a.alertas.janelaHoras.toString()) }
    var repetir by rememberSaveable(a.versao) { mutableStateOf(a.alertas.repetirAposHoras.toString()) }
    fun pct(s: String) = ValidacaoAjustes.percentagem(s)
    val novo = runCatching {
        a.copy(financeiro = f.copy(sinalVenda = pct(sinal)!!, mesesSinalAluguer = meses.toInt(), comissao = pct(comissao)!!, taxaAnuncio = pct(taxa)!!,
            promocoes = mapOf(Promocao.DESTAQUE to pct(destaque)!!, Promocao.TOP to pct(top)!!, Promocao.PRIORIDADE to pct(prioridade)!!), duracaoPromocaoDias = dias.toInt()),
            prazoReservaMinutos = horas.toLong() * 60, inactividadeMinutos = inact.toInt(), entidadePagamento = entidade.trim(),
            alertas = a.alertas.copy(reserva = alReserva, interesse = alInteresse, email = alEmail, sms = alSms, limiarClientesDistintos = limiar.toInt(), janelaHoras = janela.toInt(), repetirAposHoras = repetir.toInt()))
    }.getOrNull()
    val erros = novo?.let(ValidacaoAjustes::validar) ?: listOf("Preencha todos os campos com números válidos.")
    val escolherLogo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(vm::logo) }
    Column {
        Row(Modifier.fillMaxWidth().background(gradiente(135f, marca.c900, marca.c800)).padding(16.dp, 14.dp), verticalAlignment = Alignment.CenterVertically) {
            FaIcone(Fa.SLIDERS, 13f, Kp.yellow400); Spacer(Modifier.width(8.dp)); T("Ajustes da Plataforma", 11f, Color.White, 700, Modifier.weight(1f))
            KpFechar(aoFechar, fundo = Kp.slate900)
        }
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            T("Defina os parâmetros globais de reservas, monetização e segurança da sessão.", 10f, Kp.slate500, linha = 17f)
            Row(Modifier.fillMaxWidth().sup(12.dp, fundo = marca.c50, borda = marca.c100).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                FaIcone(Fa.CLOUD_ARROW_DOWN, 8f, marca.c700); Spacer(Modifier.width(6.dp)); T("Ajustes globais sincronizados entre dispositivos.", 7.5f, marca.c700)
            }
            Valor("Sinal de Reserva Requerido (%)", sinal, { sinal = it }, "%")
            Column(Modifier.fillMaxWidth().sup(16.dp, fundo = Color(0xFFF0F9FF), borda = Color(0xFFE0F2FE)).padding(9.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                T("Meses de sinal — Renda / Aluguer", 9f, Color(0xFF0C4A6E), 700)
                KpEntrada(meses, { meses = it.filter(Char::isDigit) }, altura = 41.dp, raio = 12.dp, tamanhoTexto = 13f, teclado = KeyboardType.Number,
                    direita = { T("mês(es)", 9f, Color(0xFF0EA5E9), 700) })
                T("Regra global. Proprietários visualizam este valor no cadastro/edição, mas não podem alterá-lo.", 8.5f, Color(0xFF0369A1), linha = 14f)
            }
            Valor("Comissão da Plataforma (% do Sinal)", comissao, { comissao = it }, "%")
            Valor("Prazo de Validade da Reserva (horas)", horas, { horas = it.filter(Char::isDigit) }, "horas", "O contador começa após a confirmação do pagamento.")
            Valor("Bloqueio por Inatividade (minutos)", inact, { inact = it.filter(Char::isDigit) }, "min", "Sem interacção durante este período, a sessão é encerrada e o login volta a ser obrigatório.")
            Valor("Taxa de Anúncio do Proprietário (% do Preço do Produto)", taxa, { taxa = it }, "%")
            Column(Modifier.fillMaxWidth().sup(16.dp, fundo = marca.c50, borda = marca.c100).padding(16.dp, 12.dp)) {
                T("Administrador sempre isento", 10f, marca.c800, 700)
                T("A taxa/comissão de publicação é exigida apenas ao Proprietário. O Administrador pode cadastrar e publicar produtos sem gerar cobrança ou referência de pagamento.", 8f, marca.c700, Modifier.padding(top = 4.dp), linha = 13f)
            }
            // Promoção paga e pagamentos
            Column(Modifier.fillMaxWidth().sup(16.dp, fundo = Kp.violet50, borda = Kp.violet100).padding(9.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { FaIcone(Fa.BULLHORN, 10f, Kp.violet800); Spacer(Modifier.width(6.dp)); T("Promoção paga e pagamentos", 10f, Kp.violet900, 700) }
                T("Percentagens configuráveis sobre o preço do produto; o valor em AOA é calculado automaticamente para cada anúncio. A entidade deve corresponder ao provedor real contratado.", 8f, Kp.violet600, linha = 12f)
                Campo("Entidade de pagamento") { KpEntrada(entidade, { entidade = it.take(20) }, dica = "Configurar entidade real", altura = 37.dp, raio = 12.dp, tamanhoTexto = 13f, teclado = KeyboardType.Number) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { Campo("Destaque (%)") { Pct(destaque) { destaque = it } } }
                    Box(Modifier.weight(1f)) { Campo("Top (%)") { Pct(top) { top = it } } }
                    Box(Modifier.weight(1f)) { Campo("Prioridade (%)") { Pct(prioridade) { prioridade = it } } }
                }
                Campo("Duração dos destaques (dias)") { KpEntrada(dias, { dias = it.filter(Char::isDigit) }, altura = 37.dp, raio = 12.dp, tamanhoTexto = 13f, teclado = KeyboardType.Number) }
            }
            // Alertas inteligentes aos Proprietários
            Column(Modifier.fillMaxWidth().sup(16.dp, fundo = Color(0xFFFFFBEB), borda = Kp.amber100).padding(9.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { FaIcone(Fa.BELL_CONCIERGE, 10f, Kp.amber800); Spacer(Modifier.width(6.dp)); T("Alertas Inteligentes aos Proprietários", 10f, Kp.amber900, 700) }
                T("O alerta no app é sempre criado. E-mail/SMS usam os contactos cadastrados e o canal preferido do Proprietário.", 9f, Kp.amber800, linha = 13f)
                Interruptor("Alertar quando houver reserva", alReserva) { alReserva = it }
                Interruptor("Alertar por interesse relevante", alInteresse) { alInteresse = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { Campo("Clientes distintos") { KpEntrada(limiar, { limiar = it.filter(Char::isDigit) }, altura = 37.dp, raio = 12.dp, tamanhoTexto = 13f, teclado = KeyboardType.Number) } }
                    Box(Modifier.weight(1f)) { Campo("Janela (h)") { KpEntrada(janela, { janela = it.filter(Char::isDigit) }, altura = 37.dp, raio = 12.dp, tamanhoTexto = 13f, teclado = KeyboardType.Number) } }
                    Box(Modifier.weight(1f)) { Campo("Repetir após (h)") { KpEntrada(repetir, { repetir = it.filter(Char::isDigit) }, altura = 37.dp, raio = 12.dp, tamanhoTexto = 13f, teclado = KeyboardType.Number) } }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { Interruptor("Permitir E-mail", alEmail, inicio = true) { alEmail = it } }
                    Box(Modifier.weight(1f)) { Interruptor("Permitir SMS", alSms, inicio = true) { alSms = it } }
                }
            }
            Identidade(id, vm, { escolherLogo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
            AlertasDispositivo(vm)
            Campanhas(campanhas, vm)
            // Resumo da monetização
            val anuncios = intencoes.filter { it.s("type") == "LISTING" && it.s("status") in setOf("PUBLISHED", "COMPLETED", "PAID") }.sumOf { it.n("amount") }
            val comissoes = reservas.filter { it.s("status") in setOf("ACTIVE", "COMPLETED", "REFUND_PENDING", "REFUNDED") }.sumOf { it.n("commissionAmount") }
            val saldo = reservas.filter { it.s("payoutStatus") in setOf("HELD", "RELEASE_REQUESTED", "PROCESSING") }.sumOf { it.n("ownerNetAmount") }
            Column(Modifier.fillMaxWidth().sup(16.dp, fundo = Kp.slate900).padding(9.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T("Resumo da monetização", 9.5f, Kp.slate300, 700, Modifier.weight(1f), maiusculas = true)
                    T("Rastreável", 8.5f, Kp.emerald300, Modifier.sup(999.dp, fundo = Kp.emerald900.copy(.6f)).padding(horizontal = 8.dp, vertical = 3.dp))
                }
                Row {
                    listOf(Triple("Anúncios", anuncios, Kp.yellow400), Triple("Comissões", comissoes, Kp.emerald300), Triple("Saldo", saldo, Color.White)).forEach { (r, v, c) ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            T(r, 8f, Kp.slate400); T(FormatoAoa.formatar(Dinheiro.de(java.math.BigDecimal.valueOf(v))), 9.5f, c, 800, Modifier.padding(top = 4.dp))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().height(29.dp).sup(15.dp, fundo = Kp.slate800, borda = Kp.slate700).toque(aoTocar = aoHistorico),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    FaIcone(Fa.CLOCK_ROTATE_LEFT, 9f, Color.White); Spacer(Modifier.width(6.dp)); T("Ver Histórico Detalhado", 9f, Color.White, 700)
                }
            }
            Row(Modifier.fillMaxWidth().sup(16.dp, fundo = marca.c50, borda = marca.c100).padding(9.dp)) {
                FaIcone(Fa.CIRCLE_INFO, 9f, marca.c800, Modifier.padding(top = 2.dp, end = 6.dp))
                T("Na venda, a comissão é calculada sobre o sinal; na renda/aluguer, sobre preço da unidade × unidades mínimas exigidas. O líquido permanece em custódia até à decisão da vistoria. A taxa de anúncio é calculada sobre o preço por unidade informado e o prazo configurado aplica-se apenas às novas reservas.",
                    9f, marca.c800, linha = 15f)
            }
            if (erros.isNotEmpty()) T(erros.joinToString("\n"), 8.5f, Kp.red700, Modifier.fillMaxWidth().sup(12.dp, fundo = Kp.red50).padding(9.dp), linha = 13f)
            msg?.let { T(it, 8.5f, marca.c800, 700, Modifier.fillMaxWidth().sup(12.dp, fundo = marca.c50).toque { vm.mensagem.value = null }.padding(9.dp)) }
            KpBotao("Guardar Ajustes", { novo?.let(vm::guardar) }, icone = Fa.FLOPPY_DISK, ocupado = ocupado, activo = erros.isEmpty(), altura = 37.dp, raio = 12.dp, tamanho = 9f)
        }
    }
}

@Composable
private fun Campo(rotulo: String, conteudo: @Composable ColumnScope.() -> Unit) { Column { KpRotulo(rotulo, 7.5f, cor = Kp.slate600); Spacer(Modifier.height(4.dp)); conteudo() } }

@Composable
private fun Valor(rotulo: String, v: String, aoMudar: (String) -> Unit, sufixo: String, ajuda: String? = null) {
    Column {
        KpRotulo(rotulo, 9f); Spacer(Modifier.height(6.dp))
        KpEntrada(v, aoMudar, altura = 41.dp, raio = 12.dp, tamanhoTexto = 13f, teclado = KeyboardType.Decimal, direita = { T(sufixo, 9f, Kp.slate400, 700) })
        ajuda?.let { T(it, 8f, Kp.slate400, Modifier.padding(top = 4.dp)) }
    }
}

@Composable
private fun Pct(v: String, aoMudar: (String) -> Unit) =
    KpEntrada(v, aoMudar, altura = 37.dp, raio = 12.dp, tamanhoTexto = 13f, teclado = KeyboardType.Decimal, direita = { T("%", 8f, Kp.slate400) })

@Composable
private fun Interruptor(rotulo: String, v: Boolean, inicio: Boolean = false, aoMudar: (Boolean) -> Unit) {
    val marca = LocalMarca.current
    Row(Modifier.fillMaxWidth().height(37.dp).sup(15.dp, fundo = Color.White, borda = Kp.amber100).toque { aoMudar(!v) }.padding(horizontal = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        if (inicio) { Checkbox(v, aoMudar, Modifier.size(18.dp), colors = CheckboxDefaults.colors(checkedColor = marca.c600)); Spacer(Modifier.width(12.dp)) }
        T(rotulo, 9.5f, Kp.slate800, 700, Modifier.weight(1f))
        if (!inicio) Checkbox(v, aoMudar, Modifier.size(18.dp), colors = CheckboxDefaults.colors(checkedColor = marca.c600))
    }
}

/** Identidade Visual: paleta do app, cor personalizada, Destaques e Pesquisa, tema e logótipo. */
@Composable
private fun Identidade(id: IdentidadeVisual, vm: AjustesViewModel, aoLogo: () -> Unit) {
    val marca = LocalMarca.current
    Column(Modifier.fillMaxWidth().sup(16.dp, fundo = Color.White, borda = Kp.emerald100).padding(9.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) { FaIcone(Fa.PALETTE, 10f, Kp.slate900); Spacer(Modifier.width(6.dp)); T("Identidade Visual", 11f, Kp.slate900, 800) }
                T("Exclusivo do Administrador · alterações aplicadas imediatamente e sincronizadas entre dispositivos.", 8f, marca.c700, Modifier.padding(top = 4.dp), linha = 13f)
            }
            Row(Modifier.sup(999.dp, fundo = marca.c50).padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                FaIcone(Fa.BOLT, 7f, marca.c800); Spacer(Modifier.width(4.dp)); T("Tempo real", 7.5f, marca.c800, 700)
            }
        }
        // Identidade deste telemóvel: Spotec (igual ao PDV Digital) ou a paleta da plataforma definida abaixo.
        val spotec by vm.ajustes.spotec.collectAsStateWithLifecycle()
        T("Identidade deste telemóvel", 9f, Kp.slate800, 700)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Amostra("Spotec", ao.kupila.gestaonegocios.ui.kp.Spotec.carvao.take(2) + ao.kupila.gestaonegocios.ui.kp.Spotec.laranja, spotec, Modifier.weight(1f)) { vm.ajustes.definirSpotec(true) }
            Amostra("Da plataforma", listOf(Color(id.primaria).let { c -> Color(c.red * .55f, c.green * .55f, c.blue * .55f) }, Color(id.primaria), Color(id.destaque)), !spotec, Modifier.weight(1f)) { vm.ajustes.definirSpotec(false) }
        }
        T("Paleta do app", 9f, Kp.slate800, 700)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TemaVisual.entries.filter { it != TemaVisual.PERSONALIZADO }.forEach { t ->
                Amostra(t.nome, listOf(Color(t.primaria).copy(alpha = 1f).let { c -> Color(c.red * .55f, c.green * .55f, c.blue * .55f) }, Color(t.primaria), Color(t.destaque)), id.tema == t, Modifier.weight(1f)) {
                    vm.identidade(id.copy(tema = t, primaria = t.primaria, destaque = t.destaque))
                }
            }
        }
        Row(Modifier.fillMaxWidth().sup(16.dp, fundo = Color.White, borda = Kp.slate100).padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { T("Cor personalizada", 8.5f, Kp.slate800, 700); T("Cria automaticamente tons claros e escuros coerentes.", 7f, Kp.slate400) }
            Box(Modifier.size(40.dp, 30.dp).sup(10.dp, fundo = Color(id.primaria), borda = Kp.slate200))
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(40.dp, 30.dp).sup(10.dp, fundo = Color(id.destaque), borda = Kp.slate200))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Kp.slate100))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) { FaIcone(Fa.WAND_MAGIC_SPARKLES, 9f, Kp.slate700); Spacer(Modifier.width(6.dp)); T("Destaques e Pesquisa", 9.5f, Kp.slate800, 700) }
                T("Paleta exclusiva da tela principal, optimizada automaticamente para modo claro e escuro.", 7f, Kp.slate500, Modifier.padding(top = 2.dp))
            }
            T(id.vitrine.nome, 8f, Kp.slate700, 700, Modifier.sup(999.dp, fundo = Color.White, borda = Kp.slate200).padding(horizontal = 9.dp, vertical = 4.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PaletaVitrine.entries.forEach { p ->
                Amostra(p.nome, listOf(Color(p.pesquisaPrimaria), Color(p.pesquisaDestaque), Color(p.vitrinePrimaria), Color(p.vitrineDestaque)), id.vitrine == p, Modifier.weight(1f)) {
                    vm.identidade(id.copy(vitrine = p, pesquisaPrimaria = p.pesquisaPrimaria, pesquisaDestaque = p.pesquisaDestaque, vitrinePrimaria = p.vitrinePrimaria, vitrineDestaque = p.vitrineDestaque))
                }
            }
        }
        Column(Modifier.fillMaxWidth().sup(16.dp, fundo = Color.White, borda = Kp.slate100).padding(11.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ParCores("Pesquisa", "Fundo, foco e categorias.", Color(id.pesquisaPrimaria), Color(id.pesquisaDestaque))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Kp.slate100))
            ParCores("Catálogo de destaques", "Cabeçalho, filtros e realces da vitrine.", Color(id.vitrinePrimaria), Color(id.vitrineDestaque))
        }
        Linha("Restaurar paleta da Home", Fa.ROTATE_LEFT) { val p = PaletaVitrine.AURORA
            vm.identidade(id.copy(vitrine = p, pesquisaPrimaria = p.pesquisaPrimaria, pesquisaDestaque = p.pesquisaDestaque, vitrinePrimaria = p.vitrinePrimaria, vitrineDestaque = p.vitrineDestaque)) }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Kp.slate100))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { T("Tema claro e escuro", 9.5f, Kp.slate800, 700); T("Aplicado a todos os módulos, modais e componentes do Kupila.", 7f, Kp.slate500) }
            T(when (id.modoPlataforma) { ModoAparencia.CLARO -> "Claro"; ModoAparencia.ESCURO -> "Escuro"; ModoAparencia.AUTOMATICO -> "Automático" }, 8f, Kp.slate700, 700,
                Modifier.sup(999.dp, fundo = Color.White, borda = Kp.slate200).padding(horizontal = 9.dp, vertical = 4.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Triple(ModoAparencia.CLARO, Fa.SUN, "Claro"), Triple(ModoAparencia.ESCURO, Fa.MOON, "Escuro"), Triple(ModoAparencia.AUTOMATICO, Fa.MOBILE_SCREEN_BUTTON, "Automático")).forEach { (m, ic, r) ->
                val on = id.modoPlataforma == m
                Row(Modifier.weight(1f).height(57.dp).sup(16.dp, fundo = if (on) marca.c700 else Color.White, borda = if (on) marca.c700 else Kp.slate200).toque { vm.identidade(id.copy(modoPlataforma = m)) },
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    FaIcone(ic, 9f, if (on) Color.White else Kp.slate900, regular = m == ModoAparencia.CLARO); T(r, 8.5f, if (on) Color.White else Kp.slate900, 700)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Kp.slate100))
        T("Logo da plataforma", 9.5f, Kp.slate800, 700)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp).sup(16.dp, fundo = Color.White, borda = Kp.slate200, sombra = Kp.slate900.copy(.08f), desfoque = 8.dp), contentAlignment = Alignment.Center) { LogoMarca(56f, 14f, 26f, id.logoLocal) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                KpBotao("Escolher novo logo", aoLogo, icone = Fa.IMAGE, fundo = Kp.slate900, altura = 33.dp, raio = 12.dp, tamanho = 9f, sombra = false)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KpBotao("Logo padrão", { vm.identidade(id, "") }, Modifier.weight(1f), icone = Fa.ROTATE_LEFT, fundo = Color.White, cor = Kp.slate700, borda = Kp.slate200, altura = 28.dp, raio = 12.dp, tamanho = 8f, sombra = false)
                    KpBotao("Tema padrão", { vm.identidade(IdentidadeVisual(logoLocal = id.logoLocal)) }, Modifier.weight(1f), icone = Fa.WAND_MAGIC_SPARKLES, fundo = Color.White, cor = Kp.slate700, borda = Kp.slate200, altura = 28.dp, raio = 12.dp, tamanho = 8f, sombra = false)
                }
            }
        }
        T("PNG, JPG ou WebP. O Kupila recorta proporcionalmente, reduz para até 320×320 px e optimiza antes de sincronizar.", 7f, Kp.slate500, linha = 11f)
        Box(Modifier.fillMaxWidth().height(1.dp).background(Kp.slate100))
        Row(verticalAlignment = Alignment.CenterVertically) { FaIcone(Fa.ROTATE_LEFT, 9f, Kp.slate900); Spacer(Modifier.width(6.dp)); T("Restauro", 9.5f, Kp.slate900, 800) }
        T("Recupere a identidade visual Kupila ou os parâmetros padrão sem eliminar dados operacionais.", 7f, Kp.slate500)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KpBotao("Restaurar cor padrão", { vm.identidade(id.copy(tema = TemaVisual.KUPILA, primaria = TemaVisual.KUPILA.primaria, destaque = TemaVisual.KUPILA.destaque)) }, Modifier.weight(1f),
                icone = Fa.PALETTE, fundo = Color.White, cor = marca.c700, borda = marca.c200, altura = 31.dp, raio = 15.dp, tamanho = 8f, sombra = false)
            KpBotao("Restaurar configurações", vm::restaurar, Modifier.weight(1f), icone = Fa.SLIDERS, fundo = Color(0xFFFFFBEB), cor = Kp.amber800, borda = Kp.amber300, altura = 31.dp, raio = 15.dp, tamanho = 8f, sombra = false)
        }
    }
}

@Composable
private fun Amostra(nome: String, cores: List<Color>, on: Boolean, modifier: Modifier, aoTocar: () -> Unit) {
    val marca = LocalMarca.current
    Column(modifier.height(60.dp).sup(15.dp, fundo = if (on) marca.c700 else Color.White, borda = if (on) marca.c700 else Kp.slate200, sombra = if (on) marca.c900.copy(.2f) else null, desfoque = 10.dp)
        .toque(aoTocar = aoTocar).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
        Box(Modifier.fillMaxWidth().height(20.dp).sup(8.dp, pincel = gradiente(90f, cores)))
        T(nome, 7f, if (on) Color.White else Kp.slate900, 800, max = 1)
    }
}

@Composable
private fun ParCores(titulo: String, sub: String, a: Color, b: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { T(titulo, 8.5f, Kp.slate800, 700); T(sub, 7f, Kp.slate400) }
        Box(Modifier.size(40.dp, 30.dp).sup(10.dp, fundo = a, borda = Kp.slate200)); Spacer(Modifier.width(8.dp)); Box(Modifier.size(40.dp, 30.dp).sup(10.dp, fundo = b, borda = Kp.slate200))
    }
}

@Composable
private fun Linha(texto: String, icone: String, aoTocar: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(27.dp).sup(15.dp, fundo = Color.White, borda = Kp.slate200).toque(aoTocar = aoTocar), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        FaIcone(icone, 8.5f, Kp.slate700); Spacer(Modifier.width(6.dp)); T(texto, 8.5f, Kp.slate700, 700)
    }
}

/** Alertas do dispositivo (preferências locais deste telemóvel). */
@Composable
private fun AlertasDispositivo(vm: AjustesViewModel) {
    val modo by vm.modoAlerta.collectAsStateWithLifecycle()
    val sistema by vm.notificacaoSistema.collectAsStateWithLifecycle()
    val hap by vm.haptico.collectAsStateWithLifecycle()
    val marca = LocalMarca.current
    Column(Modifier.fillMaxWidth().sup(16.dp, fundo = Color.White, borda = Kp.slate900).padding(9.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) { FaIcone(Fa.BELL, 10f, Kp.slate900); Spacer(Modifier.width(6.dp)); T("Alertas do dispositivo", 10.5f, Kp.slate900, 800) }
                T("Define como o Kupila chama a atenção do utilizador quando chega um alerta. O modo Automático respeita as capacidades e o comportamento permitido pelo celular.", 8f, Kp.slate700, Modifier.padding(top = 4.dp), linha = 13f)
            }
            T("Vibração · Sistema", 7.5f, Kp.slate900, 800, Modifier.sup(999.dp, fundo = Color.White, borda = Kp.slate900).padding(horizontal = 8.dp, vertical = 4.dp))
        }
        Campo("Comportamento do alerta") { KpSelect(ModoAlerta.entries.map { it to it.rotulo }, modo, { vm.dispositivo(modo = it) }, altura = 41.dp, raio = 15.dp, tamanho = 14f, borda = Kp.slate900) }
        listOf(Triple("Notificação do sistema", "Quando o telemóvel permitir, usa a notificação nativa do dispositivo.", sistema),
            Triple("Feedback háptico nos botões", "No celular, cada toque confirmado num botão gera uma vibração curta e suave.", hap)).forEachIndexed { i, (t, s, v) ->
            Row(Modifier.fillMaxWidth().sup(15.dp, fundo = Color.White, borda = Kp.slate900).toque { if (i == 0) vm.dispositivo(sistema = !v) else vm.dispositivo(hap = !v) }.padding(11.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { T(t, 8.5f, Kp.slate800, 700); T(s, 6.5f, Kp.slate500, Modifier.padding(top = 2.dp)) }
                Checkbox(v, { if (i == 0) vm.dispositivo(sistema = it) else vm.dispositivo(hap = it) }, Modifier.size(18.dp), colors = CheckboxDefaults.colors(checkedColor = marca.c600))
            }
        }
    }
}

/** Mensagens de Marketing: campanhas segmentadas com validade (`marketingMessages`). */
@Composable
private fun Campanhas(lista: List<Campanha>, vm: AjustesViewModel) {
    var titulo by rememberSaveable { mutableStateOf("") }
    var texto by rememberSaveable { mutableStateOf("") }
    var publico by remember { mutableStateOf(PublicoCampanha.TODOS) }
    var prioridade by remember { mutableStateOf(PrioridadeCampanha.NORMAL) }
    var accao by remember { mutableStateOf(AccaoCampanha.NENHUMA) }
    var cta by rememberSaveable { mutableStateOf("") }
    var dias by rememberSaveable { mutableStateOf("7") }
    val agora = Instant.now()
    val activas = lista.count { RegrasCampanha.estado(it, agora).name == "ACTIVA" }
    Column(Modifier.fillMaxWidth().sup(16.dp, fundo = Color.White, borda = Kp.slate900).padding(9.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) { FaIcone(Fa.BULLHORN, 10f, Kp.slate900); Spacer(Modifier.width(6.dp)); T("Mensagens de Marketing", 10.5f, Kp.slate900, 800) }
                T("Crie campanhas segmentadas e com validade. O Kupila entrega apenas campanhas activas ao público definido e evita repetição excessiva.", 8f, Kp.slate700, Modifier.padding(top = 4.dp), linha = 13f)
            }
            T("$activas activas", 7.5f, Kp.slate900, 800, Modifier.sup(999.dp, fundo = Color.White, borda = Kp.slate900).padding(horizontal = 8.dp, vertical = 4.dp))
        }
        Column(Modifier.fillMaxWidth().sup(15.dp, fundo = Color.White, borda = Kp.slate900).padding(11.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            KpEntrada(titulo, { titulo = it.take(80) }, dica = "Título da campanha", altura = 41.dp, raio = 12.dp, tamanhoTexto = 14f)
            KpEntrada(texto, { texto = it.take(240) }, dica = "Mensagem curta, clara e relevante para o utilizador", altura = 80.dp, raio = 12.dp, tamanhoTexto = 14f, linhas = 3)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { Campo("Público") { KpSelect(PublicoCampanha.entries.map { it to it.rotulo }, publico, { publico = it }, altura = 37.dp, raio = 12.dp, tamanho = 14f) } }
                Box(Modifier.weight(1f)) { Campo("Prioridade") { KpSelect(PrioridadeCampanha.entries.map { it to if (it == PrioridadeCampanha.ALTA) "Alta" else "Normal" }, prioridade, { prioridade = it }, altura = 37.dp, raio = 12.dp, tamanho = 14f) } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { Campo("Ação") { KpSelect(AccaoCampanha.entries.map { it to it.rotulo }, accao, { accao = it }, altura = 37.dp, raio = 12.dp, tamanho = 14f) } }
                Box(Modifier.weight(1f)) { Campo("Botão") { KpEntrada(cta, { cta = it.take(24) }, dica = "Ex.: Ver agora", altura = 37.dp, raio = 12.dp, tamanhoTexto = 14f) } }
            }
            Campo("Validade (dias)") { KpEntrada(dias, { dias = it.filter(Char::isDigit) }, altura = 37.dp, raio = 12.dp, tamanhoTexto = 14f, teclado = KeyboardType.Number) }
            KpBotao("Publicar campanha", { vm.campanha(titulo, texto, publico, prioridade, dias.toLongOrNull() ?: 7, accao, cta); titulo = ""; texto = ""; cta = "" },
                icone = Fa.BULLHORN, fundo = Kp.slate900, altura = 31.dp, raio = 12.dp, tamanho = 8.5f, sombra = false)
        }
        if (lista.isEmpty()) Box(Modifier.fillMaxWidth().height(32.dp).sup(15.dp, fundo = Color.White, borda = Kp.slate900), contentAlignment = Alignment.Center) { T("Ainda não existem campanhas.", 7.5f, Kp.slate700) }
        lista.take(20).forEach { c ->
            val est = RegrasCampanha.estado(c, agora)
            Row(Modifier.fillMaxWidth().sup(12.dp, fundo = Kp.slate50, borda = Kp.slate200).padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { T(c.titulo, 9f, Kp.slate900, 700); T("${c.publico.rotulo} · ${est.rotulo}", 7.5f, Kp.slate500) }
                T(if (c.desactivada) "Reactivar" else "Pausar", 8f, Kp.slate700, 700, Modifier.sup(10.dp, fundo = Color.White, borda = Kp.slate200).toque { vm.alternarCampanha(c) }.padding(horizontal = 8.dp, vertical = 5.dp))
            }
        }
    }
}
