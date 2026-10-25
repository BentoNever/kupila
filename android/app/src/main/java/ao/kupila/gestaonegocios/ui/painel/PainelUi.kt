package ao.kupila.gestaonegocios.ui.painel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ao.kupila.gestaonegocios.dados.ContaActiva
import ao.kupila.gestaonegocios.dados.admin.AutorizacoesRepositorio
import ao.kupila.gestaonegocios.dados.anuncios.AnunciosRepositorio
import ao.kupila.gestaonegocios.dados.eventos.Conjunto
import ao.kupila.gestaonegocios.dados.eventos.Registo
import ao.kupila.gestaonegocios.dados.remoto.MensagensErro
import ao.kupila.gestaonegocios.dados.transaccoes.TransaccoesRepositorio
import ao.kupila.gestaonegocios.dominio.anuncio.AnuncioPublico
import ao.kupila.gestaonegocios.dominio.anuncio.EstadoAnuncio
import ao.kupila.gestaonegocios.dominio.autorizacoes.AccaoAutorizada
import ao.kupila.gestaonegocios.dominio.financeiro.Dinheiro
import ao.kupila.gestaonegocios.dominio.financeiro.FormatoAoa
import ao.kupila.gestaonegocios.dominio.perfis.Papel
import ao.kupila.gestaonegocios.dominio.transaccoes.Otp
import ao.kupila.gestaonegocios.dominio.transaccoes.ResultadoOtp
import ao.kupila.gestaonegocios.ui.comum.Fa
import ao.kupila.gestaonegocios.ui.comum.modeloImagem
import ao.kupila.gestaonegocios.ui.explorar.precoComUnidade
import ao.kupila.gestaonegocios.ui.kp.FaIcone
import ao.kupila.gestaonegocios.ui.kp.Kp
import ao.kupila.gestaonegocios.ui.kp.LocalMarca
import ao.kupila.gestaonegocios.ui.kp.T
import ao.kupila.gestaonegocios.ui.kp.gradiente
import ao.kupila.gestaonegocios.ui.kp.sup
import ao.kupila.gestaonegocios.ui.kp.toque
import coil3.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Painel do Proprietário / do Administrador (`#tab-my-listings`, HTML 7178-7312). Automatiza o que não é decisão humana:
 * o OTP é reconhecido entre as reservas activas sem escolher a reserva; os saldos são somados sozinhos.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PainelViewModel @Inject constructor(
    private val conta: ContaActiva,
    private val anuncios: AnunciosRepositorio,
    private val autorizacoes: AutorizacoesRepositorio,
    private val transaccoes: TransaccoesRepositorio,
) : ViewModel() {
    val perfil get() = conta.recursos.value?.perfil
    val admin get() = perfil?.papel == Papel.ADMINISTRADOR
    val lista = conta.recursos.flatMapLatest { r -> if (r == null) flowOf(emptyList()) else if (r.perfil.papel == Papel.ADMINISTRADOR) r.catalogo.todos() else r.catalogo.doDono(r.perfil.uid) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val reservas = conta.recursos.flatMapLatest { r -> r?.eventos?.observar(if (r.perfil.papel == Papel.ADMINISTRADOR) Conjunto.RESERVAS_TODAS else Conjunto.RESERVAS_DONO) ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val pedidos = conta.recursos.flatMapLatest { it?.eventos?.observar(Conjunto.AUTORIZACOES) ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val mensagem = MutableStateFlow<String?>(null)
    val ocupado = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            conta.recursos.value?.let { r ->
                if (r.perfil.papel != Papel.ADMINISTRADOR) r.catalogo.sincronizarMeus()
                r.eventos.actualizar(listOf(Conjunto.AUTORIZACOES, if (r.perfil.papel == Papel.ADMINISTRADOR) Conjunto.RESERVAS_TODAS else Conjunto.RESERVAS_DONO), automatica = true)
            }
        }
    }

    private fun accao(chave: String, bloco: suspend () -> String) = viewModelScope.launch {
        if (ocupado.value != null) return@launch
        ocupado.value = chave
        mensagem.value = runCatching { bloco() }.getOrElse { MensagensErro.de(it).takeIf { m -> m != MensagensErro.GENERICA } ?: (it.message ?: MensagensErro.GENERICA) }
        ocupado.value = null
        conta.recursos.value?.let { r -> r.eventos.actualizar(listOf(Conjunto.AUTORIZACOES, Conjunto.RESERVAS_DONO, Conjunto.RESERVAS_TODAS)); if (!admin) r.catalogo.sincronizarMeus() }
    }

    /** "Validar Vistoria com o Cliente": reconhece a reserva pelo próprio código (hash local), sem pedir para escolher. */
    fun validarOtp(codigo: String) = accao("otp") {
        val c = codigo.filter(Char::isDigit)
        require(c.length == 6) { "Informe o código OTP de 6 dígitos indicado pelo Cliente." }
        val activas = reservas.value.filter { it.s("status") == "ACTIVE" }
        require(activas.isNotEmpty()) { "Não existe nenhuma reserva activa nos seus produtos." }
        val alvo = activas.firstOrNull { Otp.confere(c, it.s("otpHash").ifBlank { null }, it.s("otpSalt").ifBlank { null }, it.s("otp").ifBlank { null }) }
            ?: throw IllegalStateException(ResultadoOtp.INVALIDO.mensagem)
        val r = transaccoes.validarOtp(alvo.id, c, perfil!!)
        if (r == ResultadoOtp.VALIDADO || r == ResultadoOtp.JA_VALIDADO) "${alvo.s("listingTitle")}: ${r.mensagem}" else throw IllegalStateException(r.mensagem)
    }

    fun pedir(a: AnuncioPublico, accao: AccaoAutorizada) = accao("aut:${a.id}") {
        // Zero burocracia: desanunciar um produto disponível e sem reserva activa é imediato (sem pedir autorização).
        // A exclusão (irreversível) continua a passar pelo Administrador.
        val semReserva = a.estado == EstadoAnuncio.ACTIVO && reservas.value.none { it.s("listingId") == a.id && it.s("status") == "ACTIVE" }
        if (accao == AccaoAutorizada.DESANUNCIAR && semReserva) { anuncios.desanunciar(a.id, "Desanunciado pelo Proprietário."); "Anúncio desanunciado. Pode reactivá-lo quando quiser." }
        else { autorizacoes.pedir(a.id, a.titulo, accao, perfil!!); "Pedido de ${accao.substantivo} enviado ao Administrador. Quando autorizado, conclui com um toque." }
    }
    fun desanunciar(a: AnuncioPublico) = accao("des:${a.id}") { anuncios.desanunciar(a.id, "Desanunciado pelo Administrador."); "Anúncio desanunciado." }
    fun reactivar(a: AnuncioPublico) = accao("rea:${a.id}") { anuncios.reactivar(a.id); "Anúncio reactivado." }
    fun eliminar(a: AnuncioPublico) = accao("eli:${a.id}") { anuncios.excluir(a.id, "Eliminado pelo Administrador."); "Anúncio enviado para a lixeira." }
    fun cancelarReserva(a: AnuncioPublico) = accao("can:${a.id}") {
        val r = reservas.value.firstOrNull { it.s("listingId") == a.id && it.s("status") == "ACTIVE" } ?: error("Reserva activa não encontrada para este produto.")
        transaccoes.cancelarReserva(r.id, "Cancelada pelo Administrador no painel.", perfil!!); "Reserva cancelada; produto disponível de novo."
    }
    /** Gesto "deslizar para actualizar": leitura explícita (ignora os intervalos automáticos). */
    val aActualizar = MutableStateFlow(false)
    fun actualizar() = viewModelScope.launch {
        val r = conta.recursos.value ?: return@launch
        aActualizar.value = true
        r.eventos.actualizar(listOf(Conjunto.AUTORIZACOES, if (admin) Conjunto.RESERVAS_TODAS else Conjunto.RESERVAS_DONO))
        if (admin) r.catalogo.sincronizar(automatica = false) else r.catalogo.sincronizarMeus()
        aActualizar.value = false
    }
    /** Conclui uma autorização recebida (o código chega na app do Proprietário — D-08). */
    fun concluir(p: Registo) = accao("usar:${p.id}") { autorizacoes.usar(p.id, p.s("code"), perfil!!); "Autorização aplicada." }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PainelEcra(vm: PainelViewModel, aoAbrir: (String) -> Unit, aoEditar: (String) -> Unit, aoPerfil: () -> Unit,
               aoUtilizadores: () -> Unit, aoCatalogo: () -> Unit, aoMonitor: () -> Unit, aoAdministradores: () -> Unit, aoAutorizacoes: () -> Unit, aoHistorico: () -> Unit,
               aoReservaProtegida: (String) -> Unit, aoConfianca: () -> Unit) {
    val lista by vm.lista.collectAsStateWithLifecycle()
    val reservas by vm.reservas.collectAsStateWithLifecycle()
    val pedidos by vm.pedidos.collectAsStateWithLifecycle()
    val ocupado by vm.ocupado.collectAsStateWithLifecycle()
    val msg by vm.mensagem.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(msg) { msg?.let { snack.showSnackbar(it); vm.mensagem.value = null } }
    val p = vm.perfil ?: return
    val admin = vm.admin
    val marca = LocalMarca.current
    var todos by rememberSaveable { mutableStateOf(false) }
    val l = lista.orEmpty()
    val agora = System.currentTimeMillis()
    val activas = reservas.filter { it.s("status") == "ACTIVE" }
    val porDecidir = pedidos.count { if (admin) it.s("status") == "PENDING_ADMIN" else it.s("status") == "CODE_SENT" }
    val custodia = activas.sumOf { it.n("ownerNetAmount") }
    // "Requer atenção": gerado sozinho a partir do que já está no telemóvel (zero leituras extra).
    val alertas = buildList {
        pedidos.filter { !admin && it.s("status") == "CODE_SENT" }.forEach { pd ->
            add(Alerta(Fa.KEY, "Autorização recebida", "${AccaoAutorizada.de(pd.s("action"))?.verbo} “${pd.s("listingTitle")}”", "Concluir", Kp.amber700, ocupado == "usar:${pd.id}") { vm.concluir(pd) })
        }
        if (admin && porDecidir > 0) add(Alerta(Fa.KEY, "$porDecidir pedido(s) de autorização", "Proprietários aguardam a sua decisão.", "Decidir", Kp.amber700, false, aoAutorizacoes))
        activas.filter { ((it.ms("expiresAt") ?: Long.MAX_VALUE) - agora) in 1..6 * 3_600_000L }.forEach { r ->
            val h = ((r.ms("expiresAt") ?: agora) - agora) / 3_600_000
            add(Alerta(Fa.CLOCK, "Reserva a expirar", "${r.s("listingTitle")} · ${if (h < 1) "menos de 1 h" else "$h h"} restantes", "Ver", Kp.red600, false) { aoAbrir(r.s("listingId")) })
        }
    }
    val sinc by vm.aActualizar.collectAsStateWithLifecycle()
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(sinc, vm::actualizar, Modifier.fillMaxSize().background(androidx.compose.material3.MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp, 12.dp, 12.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Cabeçalho da marca com os números que importam.
            Box(Modifier.fillMaxWidth().sup(22.dp, pincel = ao.kupila.gestaonegocios.ui.kp.GradienteMarca, sombra = Kp.slate900.copy(.18f), desfoque = 18.dp)) {
                ao.kupila.gestaonegocios.ui.kp.TracoSpotec(Modifier.matchParentSize().padding(start = 150.dp, top = 8.dp, bottom = 6.dp), alfa = 0.22f, espessura = 3f)
                Column(Modifier.fillMaxWidth().padding(16.dp, 14.dp)) {
                    T(if (admin) "Painel do Administrador" else "Painel do Proprietário", 8f, Color.White.copy(.65f), 800, espacamento = 1.2f, maiusculas = true)
                    T("${ao.kupila.gestaonegocios.ui.entrada.saudacao()}, ${p.nome.trim().substringBefore(" ")}", 14f, Color.White, 800, Modifier.padding(top = 2.dp), max = 1)
                    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Indicador(l.count { it.estado == EstadoAnuncio.ACTIVO }.toString(), "Activos", Modifier.weight(1f))
                        Indicador(l.count { it.estado == EstadoAnuncio.RESERVADO }.toString(), "Reservados", Modifier.weight(1f))
                        Indicador(porDecidir.toString(), "Por decidir", Modifier.weight(1f), destaque = porDecidir > 0)
                    }
                    Row(Modifier.padding(top = 8.dp).fillMaxWidth().sup(14.dp, fundo = Color.White.copy(.08f)).padding(12.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FaIcone(Fa.VAULT, 9f, marca.primaria); Spacer(Modifier.width(8.dp))
                        T("Em custódia", 9f, Color.White.copy(.75f), Modifier.weight(1f))
                        T(FormatoAoa.formatar(Dinheiro.de(java.math.BigDecimal.valueOf(custodia))), 11f, Color.White, 800)
                    }
                }
            }
            if (alertas.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Titulo("Requer atenção", alertas.size.toString())
                alertas.forEach { CartaoAlerta(it) }
            }
            Titulo("Acções rápidas")
            val accoes = if (admin) listOf(
                Atalho(Fa.USERS, "Utilizadores", null, aoUtilizadores), Atalho(Fa.CHART_LINE, "Monitor", null, aoMonitor), Atalho(Fa.KEY, "Autorizações", porDecidir, aoAutorizacoes),
                Atalho(Fa.LAYER_GROUP, "Catálogo", null, aoCatalogo), Atalho(Fa.CLOCK_ROTATE_LEFT, "Histórico", null, aoHistorico), Atalho(Fa.USER_SHIELD, "Equipa", null, aoAdministradores))
            else listOf(Atalho(Fa.ID_CARD, "Meu cadastro", null, aoPerfil), Atalho(Fa.KEY, "Autorizações", porDecidir, aoAutorizacoes), Atalho(Fa.SHIELD_HEART, "Credibilidade", null, aoConfianca))
            accoes.chunked(3).forEach { linha ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    linha.forEach { BotaoAtalho(it, Modifier.weight(1f)) }
                    repeat(3 - linha.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            ValidarOtp(ocupado == "otp", vm::validarOtp)
            Titulo(if (admin) "Anúncios da plataforma" else "Os seus pertences", l.size.toString())
            if (lista != null && l.isEmpty()) T("Ainda não tem produtos cadastrados. Toque em Anunciar.", 10f, Kp.slate400)
            (if (todos) l else l.take(3)).forEach { a ->
                val pendente = pedidos.any { it.s("listingId") == a.id && it.s("status") in setOf("PENDING_ADMIN", "CODE_SENT") }
                ItemPertence(a, reservas.firstOrNull { it.s("listingId") == a.id && it.s("status") == "ACTIVE" }, admin, pendente, ocupado, vm, aoAbrir, aoEditar)
            }
            if (l.size > 3) KpBotaoSecPainel(if (todos) "Mostrar menos" else "Ver todos (${l.size})") { todos = !todos }
            Escrow(reservas)
        }
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(bottom = 70.dp))
    }
}

private class Alerta(val icone: String, val titulo: String, val texto: String, val accao: String, val cor: Color, val ocupado: Boolean, val aoTocar: () -> Unit)
private class Atalho(val icone: String, val rotulo: String, val contador: Int?, val aoTocar: () -> Unit)

@Composable
private fun Indicador(valor: String, rotulo: String, modifier: Modifier, destaque: Boolean = false) {
    val marca = LocalMarca.current
    Column(modifier.sup(14.dp, fundo = Color.White.copy(.08f), borda = if (destaque) marca.primaria.copy(.6f) else Color.White.copy(.10f)).padding(10.dp, 8.dp)) {
        T(valor, 14f, if (destaque) marca.primaria else Color.White, 900, max = 1)
        T(rotulo, 7.5f, Color.White.copy(.7f), max = 1)
    }
}

@Composable
private fun Titulo(texto: String, contador: String? = null) = Row(verticalAlignment = Alignment.CenterVertically) {
    T(texto, 11f, Kp.slate900, 800, Modifier.weight(1f))
    contador?.let { T(it, 8f, LocalMarca.current.c700, 800, Modifier.sup(999.dp, fundo = LocalMarca.current.c50).padding(horizontal = 8.dp, vertical = 2.dp)) }
}

@Composable
private fun CartaoAlerta(a: Alerta) {
    Row(Modifier.fillMaxWidth().sup(16.dp, fundo = Color.White, borda = a.cor.copy(.25f)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).sup(12.dp, fundo = a.cor.copy(.10f)), contentAlignment = Alignment.Center) { FaIcone(a.icone, 10f, a.cor) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) { T(a.titulo, 10f, Kp.slate900, 800, max = 1); T(a.texto, 8.5f, Kp.slate500, max = 2) }
        Spacer(Modifier.width(8.dp))
        T(if (a.ocupado) "…" else a.accao, 9f, Color.White, 800, Modifier.sup(10.dp, fundo = a.cor).toque(!a.ocupado, a.aoTocar).padding(horizontal = 12.dp, vertical = 7.dp))
    }
}

@Composable
private fun BotaoAtalho(a: Atalho, modifier: Modifier) {
    val marca = LocalMarca.current
    Box(modifier) {
        Column(Modifier.fillMaxWidth().sup(16.dp, fundo = Color.White, borda = Kp.slate200, sombra = Kp.slate900.copy(.04f), desfoque = 6.dp).toque(aoTocar = a.aoTocar).padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(36.dp).sup(12.dp, fundo = marca.primaria.copy(.10f)), contentAlignment = Alignment.Center) { FaIcone(a.icone, 11f, marca.c700) }
            T(a.rotulo, 8.5f, Kp.slate800, 700, Modifier.padding(top = 6.dp), max = 1, centro = true)
        }
        a.contador?.takeIf { it > 0 }?.let { T(it.toString(), 7f, Color.White, 900, Modifier.align(Alignment.TopEnd).padding(6.dp).sup(999.dp, fundo = Kp.red500).padding(horizontal = 6.dp, vertical = 1.dp)) }
    }
}

@Composable
private fun Botao(texto: String, fundo: Color, cor: Color, borda: Color?, ocupado: Boolean, icone: String? = null, regular: Boolean = false, aoTocar: () -> Unit) {
    Row(Modifier.sup(10.dp, fundo = fundo, borda = borda).toque(!ocupado, aoTocar).padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        icone?.let { FaIcone(it, 9f, cor, regular = regular); Spacer(Modifier.width(6.dp)) }
        T(if (ocupado) "A processar…" else texto, 9f, cor, 700)
    }
}

@Composable
private fun KpBotaoSecPainel(texto: String, aoTocar: () -> Unit) =
    ao.kupila.gestaonegocios.ui.kp.KpBotaoSec(texto, aoTocar, icone = Fa.CHEVRON_DOWN, altura = 38.dp, tamanho = 9.5f)
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ItemPertence(a: AnuncioPublico, reserva: Registo?, admin: Boolean, pendente: Boolean, ocupado: String?, vm: PainelViewModel, aoAbrir: (String) -> Unit, aoEditar: (String) -> Unit) {
    val marca = LocalMarca.current
    Column(Modifier.fillMaxWidth().sup(16.dp, fundo = Color.White, borda = Kp.slate200).padding(13.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                T(a.titulo, 11f, Kp.slate800, 700, max = 2)
                T("${a.municipio.ifBlank { a.provincia }} • ${precoComUnidade(a)}", 10f, Kp.slate500, Modifier.padding(top = 2.dp))
                if (a.estado == EstadoAnuncio.RESERVADO) reserva?.ms("expiresAt")?.let { Contagem(it) }
            }
            val (txt, f, c) = when (a.estado) {
                EstadoAnuncio.ACTIVO -> Triple("Activo", Kp.emerald100.copy(alpha = .7f), Kp.emerald700)
                EstadoAnuncio.RESERVADO -> Triple("Reservado", Kp.amber100, Kp.amber800)
                EstadoAnuncio.DESANUNCIADO -> Triple("Desanunciado", Kp.slate100, Kp.slate600)
                else -> Triple(a.estado.rotulo, Kp.slate100, Kp.slate600)
            }
            T(txt, 11f, c, 700, Modifier.sup(6.dp, fundo = f).padding(horizontal = 8.dp, vertical = 2.dp))
        }
        Box(Modifier.padding(vertical = 10.dp).fillMaxWidth().height(1.dp).background(Kp.slate100))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Botao("Ver", Kp.slate100, Kp.slate700, null, false, Fa.EYE, regular = true) { aoAbrir(a.id) }
            when {
                a.estado == EstadoAnuncio.RESERVADO && admin -> Botao("Cancelar Reserva", Kp.amber50, Kp.amber800, null, ocupado == "can:${a.id}", Fa.ROTATE_LEFT) { vm.cancelarReserva(a) }
                a.estado == EstadoAnuncio.RESERVADO -> {}
                admin -> {
                    Botao("Editar", marca.c50, marca.c700, null, false, Fa.PEN_TO_SQUARE, regular = true) { aoEditar(a.id) }
                    if (a.estado == EstadoAnuncio.DESANUNCIADO) Botao("Reactivar", Kp.slate50, Kp.slate700, null, ocupado == "rea:${a.id}", Fa.EYE, regular = true) { vm.reactivar(a) }
                    else Botao("Desanunciar", Kp.slate50, Kp.slate700, null, ocupado == "des:${a.id}", Fa.EYE_SLASH) { vm.desanunciar(a) }
                    Botao("Eliminar", Kp.red50, Kp.red600, null, ocupado == "eli:${a.id}", Fa.TRASH_CAN, regular = true) { vm.eliminar(a) }
                }
                else -> {
                    Botao("Editar", marca.c50, marca.c700, null, false, Fa.PEN_TO_SQUARE, regular = true) { aoEditar(a.id) }
                    if (pendente) T("Autorização em curso", 10f, Kp.amber800, 700, Modifier.sup(10.dp, fundo = Kp.amber50).padding(horizontal = 10.dp, vertical = 7.dp))
                    else {
                        if (a.estado == EstadoAnuncio.ACTIVO) Botao("Desanunciar", Kp.slate50, Kp.slate700, null, ocupado == "aut:${a.id}", Fa.EYE_SLASH) { vm.pedir(a, AccaoAutorizada.DESANUNCIAR) }
                        if (a.estado == EstadoAnuncio.DESANUNCIADO) Botao("Reactivar", Kp.slate50, Kp.slate700, null, ocupado == "rea:${a.id}", Fa.EYE, regular = true) { vm.reactivar(a) }
                        Botao("Solicitar Exclusão", Kp.red50, Kp.red600, null, ocupado == "aut:${a.id}", Fa.TRASH_CAN, regular = true) { vm.pedir(a, AccaoAutorizada.EXCLUIR) }
                    }
                }
            }
        }
    }
}

/** Contagem decrescente (1/s) da reserva activa, como `reservation-countdown`. */
@Composable
private fun Contagem(expiraMs: Long) {
    val agora = ao.kupila.gestaonegocios.ui.comum.rememberAgora()
    val s = ((expiraMs - agora) / 1000).coerceAtLeast(0)
    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        FaIcone(Fa.CLOCK, 9f, Kp.amber700, regular = true); Spacer(Modifier.width(6.dp))
        T("%02d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60), 9f, Kp.amber700, 800)
    }
}

/** "Validar Vistoria com o Cliente". */
@Composable
private fun ValidarOtp(ocupado: Boolean, aoValidar: (String) -> Unit) {
    var codigo by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().sup(16.dp, fundo = Color(0xFFFFFBEB), borda = Kp.amber200).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { FaIcone(Fa.QRCODE, 13f, Kp.amber800); Spacer(Modifier.width(8.dp)); T("Validar Vistoria com o Cliente", 11f, Kp.amber900, 700) }
        T("No momento do encontro, solicite o Código OTP do cliente para confirmar a vistoria/entrega. O líquido do sinal já é encaminhado automaticamente após a confirmação do pagamento.",
            10f, Kp.amber700, Modifier.padding(top = 8.dp), linha = 14f)
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(codigo, { v -> codigo = v.filter(Char::isDigit).take(6); if (codigo.length == 6) aoValidar(codigo) }, Modifier.weight(1f).height(37.dp)
                .sup(12.dp, fundo = Color.White, borda = Kp.amber300), singleLine = true,
                textStyle = TextStyle(fontSize = 13.sp, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center, letterSpacing = 3.sp, color = Kp.slate900, fontWeight = FontWeight.Bold),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), cursorBrush = SolidColor(Kp.amber700),
                decorationBox = { campo -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { if (codigo.isEmpty()) T("CÓDIGO OTP DE 6 DÍGITOS", 13f, Kp.slate400, centro = true); campo() } })
            Box(Modifier.height(37.dp).sup(12.dp, fundo = Color(0xFFEA580C), sombra = Color(0xFFEA580C).copy(.3f), desfoque = 10.dp).toque(!ocupado) { aoValidar(codigo) }.padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center) { T(if (ocupado) "…" else "Validar", 11f, Color.White, 700) }
        }
    }
}

/** "Sinais de Reservas Confirmadas" (escrow) e "Total Convertido / Recebido". */
@Composable
private fun Escrow(reservas: List<Registo>) {
    fun soma(f: (Registo) -> Boolean) = Dinheiro.de(java.math.BigDecimal.valueOf(reservas.filter(f).sumOf { it.n("ownerNetAmount") }))
    val custodia = soma { it.s("status") in setOf("ACTIVE", "COMPLETED") && it.s("payoutStatus") in setOf("HELD", "RELEASE_REQUESTED", "PROCESSING", "") }
    val recebido = soma { it.s("payoutStatus") == "RELEASED" }
    Column(Modifier.fillMaxWidth().sup(20.dp, pincel = ao.kupila.gestaonegocios.ui.kp.GradienteMarca, sombra = Kp.slate900.copy(.25f), desfoque = 16.dp).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T("Sinais de Reservas Confirmadas", 11f, Kp.slate400, Modifier.weight(1f))
            T("Escrow Ativo", 10f, Kp.amber400, 700, Modifier.sup(999.dp, fundo = Kp.amber400.copy(.15f)).padding(horizontal = 8.dp, vertical = 3.dp))
        }
        androidx.compose.material3.Text(FormatoAoa.formatar(custodia), Modifier.padding(top = 12.dp), color = Kp.yellow400, fontSize = 18.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
        Box(Modifier.padding(vertical = 12.dp).fillMaxWidth().height(1.dp).background(Kp.slate700))
        Row { T("Total Convertido / Recebido:", 11f, Kp.slate300, Modifier.weight(1f)); T(FormatoAoa.formatar(recebido), 11f, Color(0xFF4ADE80), 700) }
    }
}

