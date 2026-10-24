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
        autorizacoes.pedir(a.id, a.titulo, accao, perfil!!); "Pedido de ${accao.substantivo} enviado ao Administrador. Quando autorizado, conclui com um toque."
    }
    fun desanunciar(a: AnuncioPublico) = accao("des:${a.id}") { anuncios.desanunciar(a.id, "Desanunciado pelo Administrador."); "Anúncio desanunciado." }
    fun reactivar(a: AnuncioPublico) = accao("rea:${a.id}") { anuncios.reactivar(a.id); "Anúncio reactivado." }
    fun eliminar(a: AnuncioPublico) = accao("eli:${a.id}") { anuncios.excluir(a.id, "Eliminado pelo Administrador."); "Anúncio enviado para a lixeira." }
    fun cancelarReserva(a: AnuncioPublico) = accao("can:${a.id}") {
        val r = reservas.value.firstOrNull { it.s("listingId") == a.id && it.s("status") == "ACTIVE" } ?: error("Reserva activa não encontrada para este produto.")
        transaccoes.cancelarReserva(r.id, "Cancelada pelo Administrador no painel.", perfil!!); "Reserva cancelada; produto disponível de novo."
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
    Box(Modifier.fillMaxSize().background(Kp.slate50)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp, 18.dp, 12.dp, 92.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column {
                T(if (admin) "Painel do Administrador" else "Painel do Proprietário", 18f, Kp.slate800, 900, linha = 22f)
                T(if (admin) "Gestão organizada da plataforma: utilizadores, segurança, financeiro, catálogo e operação." else "Gestão exclusiva dos seus bens, reservas activas e dados de perfil.",
                    11f, Kp.slate500, Modifier.padding(top = 4.dp), linha = 16f)
                Box(Modifier.padding(top = 12.dp).fillMaxWidth().height(1.dp).background(Kp.slate900))
            }
            // Autorizações recebidas: concluir com um toque
            pedidos.filter { it.s("status") == "CODE_SENT" && !admin }.forEach { pd ->
                Row(Modifier.fillMaxWidth().sup(16.dp, fundo = Kp.amber50, borda = Kp.amber200).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    FaIcone(Fa.KEY, 11f, Kp.amber800); Spacer(Modifier.width(8.dp))
                    T("Autorização recebida: ${AccaoAutorizada.de(pd.s("action"))?.verbo} “${pd.s("listingTitle")}”", 10f, Kp.amber900, 700, Modifier.weight(1f))
                    Botao("Concluir", Kp.amber400, Kp.slate900, null, ocupado == "usar:${pd.id}") { vm.concluir(pd) }
                }
            }
            if (admin) CentroAdministrativo(aoUtilizadores, aoCatalogo, aoMonitor, aoAdministradores, aoAutorizacoes, aoHistorico)
            else Row(Modifier.fillMaxWidth().sup(16.dp, fundo = Color.White, borda = Kp.slate200, sombra = Kp.slate900.copy(.08f), desfoque = 10.dp).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { FaIcone(Fa.ID_CARD, 12f, marca.c600); Spacer(Modifier.width(8.dp)); T("Cadastro do Proprietário", 11f, Kp.slate800, 700) }
                    T(listOf(p.nome, listOf(p.residencia.municipio, p.residencia.provincia).filter { it.isNotBlank() }.joinToString(", "),
                        p.telefonePrincipal.takeIf { it.isNotBlank() }?.let { "+244 $it" }).filter { !it.isNullOrBlank() }.joinToString(" • "), 10f, Kp.slate500, Modifier.padding(top = 4.dp))
                }
                Botao("Gerir", marca.c50, marca.c700, marca.c100, false, aoTocar = aoPerfil)
            }
            if (admin) Seccao(Fa.LAYER_GROUP, "Catálogo", "Produtos Cadastrados", "Consulta rápida e acesso aos anúncios publicados na plataforma.")
            CarrosselPainel(lista.orEmpty(), admin, aoAbrir, aoReservaProtegida, aoConfianca)
            if (admin) Seccao(Fa.RECTANGLE_LIST, "Gestão", "Lista de Anúncios", "Gestão detalhada dos produtos disponíveis no painel.")
            Column(Modifier.fillMaxWidth().then(if (admin) Modifier.sup(20.dp, fundo = Color.White, borda = Kp.slate200).padding(12.dp) else Modifier),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                T("Os Seus Pertences Cadastrados", 11f, Kp.slate800, 700)
                val l = lista
                if (l != null && l.isEmpty()) T("Ainda não tem produtos cadastrados. Toque em Anunciar.", 10f, Kp.slate400)
                l.orEmpty().forEach { a ->
                    val pendente = pedidos.any { it.s("listingId") == a.id && it.s("status") in setOf("PENDING_ADMIN", "CODE_SENT") }
                    ItemPertence(a, reservas.firstOrNull { it.s("listingId") == a.id && it.s("status") == "ACTIVE" }, admin, pendente, ocupado, vm, aoAbrir, aoEditar)
                }
            }
            if (admin) Seccao(Fa.QRCODE, "Operação", "Vistorias & Confirmação", "Validação presencial e acompanhamento operacional dos anúncios.")
            ValidarOtp(ocupado == "otp", vm::validarOtp)
            if (admin) Seccao(Fa.WALLET, "Financeiro", "Reservas & Liquidação", "Resumo dos sinais confirmados e valores convertidos/recebidos.")
            Escrow(reservas)
        }
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(bottom = 70.dp))
    }
}

@Composable
private fun Botao(texto: String, fundo: Color, cor: Color, borda: Color?, ocupado: Boolean, icone: String? = null, regular: Boolean = false, aoTocar: () -> Unit) {
    Row(Modifier.sup(10.dp, fundo = fundo, borda = borda).toque(!ocupado, aoTocar).padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        icone?.let { FaIcone(it, 11f, cor, regular = regular); Spacer(Modifier.width(6.dp)) }
        T(if (ocupado) "A processar…" else texto, 11f, cor, 700)
    }
}

/** Cabeçalho de secção do painel administrativo (ícone + kicker + título). */
@Composable
private fun Seccao(icone: String, kicker: String, titulo: String, sub: String) {
    val marca = LocalMarca.current
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(30.dp).sup(10.dp, fundo = Kp.slate50, borda = Kp.slate200), contentAlignment = Alignment.Center) { FaIcone(icone, 10f, marca.c700) }
        Column {
            T(kicker, 7f, marca.c600, 900, espacamento = 1.4f, maiusculas = true)
            T(titulo, 11f, Kp.slate900, 900, Modifier.padding(top = 4.dp))
            T(sub, 7.5f, Kp.slate500)
        }
    }
}

/** "CENTRO ADMINISTRATIVO · Gestão da Plataforma" + "Ferramentas administrativas". */
@Composable
private fun CentroAdministrativo(aoUtilizadores: () -> Unit, aoCatalogo: () -> Unit, aoMonitor: () -> Unit, aoAdministradores: () -> Unit, aoAutorizacoes: () -> Unit, aoHistorico: () -> Unit) {
    val marca = LocalMarca.current
    Column(Modifier.fillMaxWidth().sup(20.dp, pincel = gradiente(135f, marca.c900, marca.c800), borda = marca.c700, sombra = marca.c950.copy(.25f), desfoque = 20.dp).padding(13.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                T("Centro administrativo", 7f, Color.White.copy(.85f), 900, espacamento = 1.4f, maiusculas = true)
                T("Gestão da Plataforma", 13f, Color.White, 900, Modifier.padding(top = 6.dp))
                T("Acessos rápidos para utilizadores, catálogo e monitoramento. As demais ferramentas estão organizadas abaixo por finalidade.", 7.5f, marca.c100, Modifier.padding(top = 6.dp), linha = 13f)
            }
            Box(Modifier.size(36.dp).sup(12.dp, fundo = Color.White.copy(.12f), borda = Color.White.copy(.18f)), contentAlignment = Alignment.Center) { FaIcone(Fa.SHIELD_HALVED, 14f, Kp.yellow400) }
        }
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(Triple(Fa.USERS, "Utilizadores", aoUtilizadores), Triple(Fa.HOUSE, "Catálogo", aoCatalogo), Triple(Fa.CHART_LINE, "Monitoramento", aoMonitor)).forEach { (ic, r, f) ->
                Row(Modifier.weight(1f).height(25.dp).sup(10.dp, fundo = Color.White.copy(.10f), borda = Color.White.copy(.14f)).toque(aoTocar = f).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) { FaIcone(ic, 8f, Kp.yellow400); Spacer(Modifier.width(6.dp)); T(r, 7f, Color.White, 800, max = 1) }
            }
        }
    }
    Seccao(Fa.SHIELD_HALVED, "Administração", "Gestão & Segurança", "Utilizadores, permissões, autorizações, auditoria e supervisão operacional.")
    Column(Modifier.fillMaxWidth().sup(20.dp, fundo = Color.White, borda = Kp.slate200, sombra = Kp.slate900.copy(.05f), desfoque = 10.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) { T("Ferramentas administrativas", 11f, Kp.slate900, 900); T("Aceda a cada área conforme o objectivo da operação.", 7.5f, Kp.slate500, Modifier.padding(top = 4.dp)) }
            T("ADMIN", 7f, marca.c800, 900, Modifier.sup(999.dp, fundo = marca.c50, borda = marca.c200).padding(horizontal = 9.dp, vertical = 4.dp))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Kp.slate100))
        Grupo(Fa.USERS, "Utilizadores & Contas", listOf(Triple(Fa.ADDRESS_BOOK, "Clientes & Proprietários" to "Consultar fichas e gerir o estado das contas.", aoUtilizadores),
            Triple(Fa.USER_SHIELD, "Administradores" to "Consultar a equipa administrativa autorizada.", aoAdministradores)))
        Grupo(Fa.LOCK, "Segurança & Governança", listOf(Triple(Fa.KEY, "Autorizações" to "Desanúncio e exclusão de produtos.", aoAutorizacoes),
            Triple(Fa.CLOCK_ROTATE_LEFT, "Histórico" to "Auditoria e operações registadas.", aoHistorico)))
    }
}

@Composable
private fun Grupo(icone: String, titulo: String, itens: List<Triple<String, Pair<String, String>, () -> Unit>>) {
    val marca = LocalMarca.current
    Row(verticalAlignment = Alignment.CenterVertically) { FaIcone(icone, 7.5f, marca.c600); Spacer(Modifier.width(8.dp)); T(titulo, 7.5f, Kp.slate500, 800, espacamento = 1.2f, maiusculas = true) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itens.forEach { (ic, tx, f) ->
            Row(Modifier.weight(1f).heightIn(min = 66.dp).sup(16.dp, fundo = Color.White, borda = Kp.slate200).toque(aoTocar = f).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(30.dp).sup(10.dp, fundo = Color.White, borda = Kp.slate200, sombra = Kp.slate900.copy(.06f), desfoque = 4.dp), contentAlignment = Alignment.Center) { FaIcone(ic, 10f, marca.c800) }
                Column { T(tx.first, 9f, Kp.slate900, 800, max = 2); T(tx.second, 6.5f, Kp.slate500, Modifier.padding(top = 3.dp), linha = 9.5f, max = 3) }
            }
        }
    }
}

/** "Produtos Cadastrados": dois cartões visíveis por vez. */
@Composable
private fun CarrosselPainel(lista: List<AnuncioPublico>, admin: Boolean, aoAbrir: (String) -> Unit, aoReservaProtegida: (String) -> Unit, aoConfianca: () -> Unit) {
    val estado = rememberLazyListState()
    val ambito = rememberCoroutineScope()
    val actual by remember { derivedStateOf { estado.firstVisibleItemIndex + 1 } }
    Column(Modifier.fillMaxWidth().then(if (admin) Modifier.sup(20.dp, fundo = Color.White, borda = Kp.slate200).padding(12.dp) else Modifier), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { T("Produtos Cadastrados", 11f, Kp.slate800, 700); T("Dois anúncios visíveis por vez · actualização em tempo real", 8f, Kp.slate400, Modifier.padding(top = 2.dp)) }
            T("${if (lista.isEmpty()) 0 else actual}/${lista.size}", 7f, Kp.slate500, 850, Modifier.sup(999.dp, fundo = Kp.slate100, borda = Kp.slate200).padding(horizontal = 8.dp, vertical = 4.dp))
        }
        if (lista.isNotEmpty()) Box {
            LazyRow(state = estado, flingBehavior = rememberSnapFlingBehavior(estado), modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
                itemsIndexed(lista, key = { _, a -> a.id }) { i, a ->
                    CartaoPainel(a, i + 1, Modifier.fillParentMaxWidth(.5f).padding(horizontal = 1.dp), { aoAbrir(a.id) }, { aoReservaProtegida(a.id) }, aoConfianca)
                }
            }
            if (lista.size > 2) {
                Seta(Fa.CHEVRON_LEFT, Modifier.align(Alignment.CenterStart).padding(start = 3.dp)) { ambito.launch { estado.animateScrollToItem((estado.firstVisibleItemIndex - 1).coerceAtLeast(0)) } }
                Seta(Fa.CHEVRON_RIGHT, Modifier.align(Alignment.CenterEnd).padding(end = 3.dp)) { ambito.launch { estado.animateScrollToItem((estado.firstVisibleItemIndex + 1).coerceAtMost(lista.size - 1)) } }
            }
        }
    }
}

@Composable
private fun Seta(glifo: String, modifier: Modifier, aoTocar: () -> Unit) {
    Box(modifier.size(29.dp, 36.dp).sup(12.dp, fundo = Color.White.copy(.94f), borda = Kp.slate200, sombra = Kp.slate900.copy(.13f), desfoque = 16.dp).toque(aoTocar = aoTocar),
        contentAlignment = Alignment.Center) { FaIcone(glifo, 9f, LocalMarca.current.c800) }
}

@Composable
private fun CartaoPainel(a: AnuncioPublico, numero: Int, modifier: Modifier, aoAbrir: () -> Unit, aoReserva: () -> Unit, aoConfianca: () -> Unit) {
    val marca = LocalMarca.current
    Box(modifier.sup(14.dp, fundo = Color.White, borda = Color(0xFFE9EEF3), sombra = Kp.slate900.copy(.055f), desfoque = 16.dp).toque(aoTocar = aoAbrir)) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(1.33f).background(Color(0xFFEEF2F7))) {
                a.fotos.firstOrNull()?.let { AsyncImage(modeloImagem(it.miniatura ?: it.url), a.titulo, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                T(numero.toString().padStart(2, '0'), 6.5f, Color.White, 900, Modifier.padding(5.dp).sup(7.dp, fundo = Kp.slate900.copy(.78f), borda = Color.White.copy(.58f)).padding(horizontal = 5.dp, vertical = 4.dp))
                val (txt, cor) = when (a.estado) { EstadoAnuncio.RESERVADO -> "Reservado" to Kp.amber700; EstadoAnuncio.ACTIVO -> "Activo" to marca.c700; else -> a.estado.rotulo to Kp.slate600 }
                T(txt, 6.5f, cor, 900, Modifier.align(Alignment.TopEnd).padding(5.dp).sup(999.dp, fundo = Color.White.copy(.95f)).padding(horizontal = 6.dp, vertical = 2.dp))
                T(a.detalhes.subcategoria.ifBlank { a.categoria.rotuloPlural }, 6.5f, Color.White, 850, Modifier.align(Alignment.BottomStart).padding(5.dp)
                    .sup(999.dp, fundo = Kp.slate900.copy(.8f)).padding(horizontal = 6.dp, vertical = 2.dp), max = 1)
            }
            Column(Modifier.padding(6.dp, 6.dp, 32.dp, 6.dp)) {
                T(a.titulo, 8.5f, Kp.slate900, 800, max = 1)
                Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) { FaIcone(Fa.LOCATION_DOT, 6f, marca.c600); Spacer(Modifier.width(3.dp)); T(a.municipio.ifBlank { a.provincia }, 6.5f, Kp.slate500, max = 1) }
                T(precoComUnidade(a), 8f, marca.c800, 900, Modifier.padding(top = 3.dp), max = 1)
            }
        }
        Column(Modifier.align(Alignment.BottomEnd).padding(5.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            listOf(Fa.SHIELD_HALVED to aoReserva, Fa.SHIELD_HEART to aoConfianca).forEach { (g, f) ->
                Box(Modifier.size(20.dp).sup(7.dp, fundo = Color.White.copy(.97f), borda = Kp.slate200).toque(aoTocar = f), contentAlignment = Alignment.Center) { FaIcone(g, 7.5f, marca.c800) }
            }
        }
    }
}

/** "Os Seus Pertences Cadastrados": cartão com estado e acções (Proprietário pede autorização; Administrador age). */
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
                    Botao("Editar", Kp.emerald50, marca.c700, null, false, Fa.PEN_TO_SQUARE, regular = true) { aoEditar(a.id) }
                    if (a.estado == EstadoAnuncio.DESANUNCIADO) Botao("Reactivar", Kp.slate50, Kp.slate700, null, ocupado == "rea:${a.id}", Fa.EYE, regular = true) { vm.reactivar(a) }
                    else Botao("Desanunciar", Kp.slate50, Kp.slate700, null, ocupado == "des:${a.id}", Fa.EYE_SLASH) { vm.desanunciar(a) }
                    Botao("Eliminar", Kp.red50, Kp.red600, null, ocupado == "eli:${a.id}", Fa.TRASH_CAN, regular = true) { vm.eliminar(a) }
                }
                else -> {
                    Botao("Editar", Kp.emerald50, marca.c700, null, false, Fa.PEN_TO_SQUARE, regular = true) { aoEditar(a.id) }
                    if (pendente) T("Autorização em curso", 10f, Kp.amber800, 700, Modifier.sup(10.dp, fundo = Kp.amber50).padding(horizontal = 10.dp, vertical = 7.dp))
                    else {
                        if (a.estado == EstadoAnuncio.ACTIVO) Botao("Solicitar Desanúncio", Kp.slate50, Kp.slate700, null, ocupado == "aut:${a.id}", Fa.EYE_SLASH) { vm.pedir(a, AccaoAutorizada.DESANUNCIAR) }
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
    val agora by produceState(System.currentTimeMillis(), expiraMs) { while (true) { value = System.currentTimeMillis(); delay(1000) } }
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
    Column(Modifier.fillMaxWidth().sup(20.dp, fundo = Kp.slate900, sombra = Kp.slate900.copy(.25f), desfoque = 16.dp).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T("Sinais de Reservas Confirmadas", 11f, Kp.slate400, Modifier.weight(1f))
            T("Escrow Ativo", 10f, Kp.amber400, 700, Modifier.sup(999.dp, fundo = Kp.amber400.copy(.15f)).padding(horizontal = 8.dp, vertical = 3.dp))
        }
        androidx.compose.material3.Text(FormatoAoa.formatar(custodia), Modifier.padding(top = 12.dp), color = Kp.yellow400, fontSize = 18.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
        Box(Modifier.padding(vertical = 12.dp).fillMaxWidth().height(1.dp).background(Kp.slate700))
        Row { T("Total Convertido / Recebido:", 11f, Kp.slate300, Modifier.weight(1f)); T(FormatoAoa.formatar(recebido), 11f, Color(0xFF4ADE80), 700) }
    }
}

