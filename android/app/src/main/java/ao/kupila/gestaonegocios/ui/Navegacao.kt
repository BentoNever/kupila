package ao.kupila.gestaonegocios.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import ao.kupila.gestaonegocios.ui.shell.Aba
import ao.kupila.gestaonegocios.ui.shell.AccoesCabecalho
import ao.kupila.gestaonegocios.ui.shell.BarraInferior
import ao.kupila.gestaonegocios.ui.shell.BarraLocalizacao
import ao.kupila.gestaonegocios.ui.shell.CabecalhoApp
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Handshake
import androidx.navigation.NavType
import androidx.navigation.navArgument
import ao.kupila.gestaonegocios.ui.anunciar.AnunciarEcra
import ao.kupila.gestaonegocios.ui.anunciar.AnunciarViewModel
import ao.kupila.gestaonegocios.ui.anunciar.RascunhosEcra
import ao.kupila.gestaonegocios.ui.checkout.CheckoutEcra
import ao.kupila.gestaonegocios.ui.negocios.NegociosEcra
import ao.kupila.gestaonegocios.ui.poupanca.PoupancaEcra
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ao.kupila.gestaonegocios.dados.RecursosConta
import ao.kupila.gestaonegocios.dados.sessao.EstadoSessao
import ao.kupila.gestaonegocios.dominio.perfis.Permissoes
import ao.kupila.gestaonegocios.ui.ajuda.FaqEcra
import ao.kupila.gestaonegocios.ui.ajuda.PoliticasEcra
import ao.kupila.gestaonegocios.ui.ajuda.ProtecaoEcra
import ao.kupila.gestaonegocios.ui.ajuda.SobreEcra
import ao.kupila.gestaonegocios.ui.comum.Carregando
import ao.kupila.gestaonegocios.ui.conta.ContaEcra
import ao.kupila.gestaonegocios.ui.detalhe.DetalheEcra
import ao.kupila.gestaonegocios.ui.entrada.CadastroEcra
import ao.kupila.gestaonegocios.ui.entrada.CadastroViewModel
import ao.kupila.gestaonegocios.ui.entrada.LoginEcra
import ao.kupila.gestaonegocios.ui.entrada.RecuperacaoEcra
import ao.kupila.gestaonegocios.ui.explorar.ExplorarEcra
import ao.kupila.gestaonegocios.ui.raiz.RaizViewModel
import ao.kupila.gestaonegocios.ui.simulador.SimuladorEcra
import kotlinx.coroutines.launch

/** Rotas. Um único motor de navegação (Navigation Compose), como no PDV Digital. */
object Rotas {
    const val LOGIN = "login"; const val CADASTRO = "cadastro"; const val RECUPERAR = "recuperar"; const val POLITICAS_CADASTRO = "politicas-cadastro"
    const val EXPLORAR = "explorar"; const val CONTA = "conta"
    const val DETALHE = "detalhe/{id}"; fun detalhe(id: String) = "detalhe/$id"
    const val FAQ = "faq"; const val POLITICAS = "politicas"; const val PROTECAO = "protecao"; const val SOBRE = "sobre"; const val SIMULADOR = "simulador"
    const val NEGOCIOS = "negocios"; const val RASCUNHOS = "rascunhos"; const val POUPANCA = "poupanca"; const val ACTUALIZACAO = "actualizacao"
    const val ANUNCIAR = "anunciar?rascunho={rascunho}&editar={editar}"; const val POLITICAS_ANUNCIAR = "politicas-anunciar"
    fun anunciar(rascunho: String? = null, editar: String? = null) = "anunciar?rascunho=${rascunho.orEmpty()}&editar=${editar.orEmpty()}"
    const val CHECKOUT = "checkout/{id}"; fun checkout(id: String) = "checkout/$id"
    const val MEUS = "meus-anuncios"; const val NOTIFICACOES = "notificacoes"; const val ADMIN = "admin"; const val PENDENTES = "pendentes"; const val CONFIANCA = "confianca"; const val PERFIL = "perfil-proprietario"; const val AJUSTES = "ajustes"
    const val UTILIZADORES = "utilizadores"; const val MONITOR = "monitor"; const val HISTORICO = "historico"; const val DIRECTORIO = "directorio"; const val MONETIZACAO = "monetizacao"; const val AUTORIZACOES = "autorizacoes"
    const val RECIBO = "recibo/{id}"; fun recibo(id: String) = "recibo/$id"
    const val MONITOR_DETALHE = "monitor-detalhe/{tipo}?c={c}&m={m}"; fun monitorDetalhe(t: String, c: String = "", m: String = "") = "monitor-detalhe/$t?c=$c&m=$m"
    const val FICHA = "ficha/{uid}"; fun ficha(uid: String) = "ficha/$uid"
}

@Composable
fun Navegacao(raiz: RaizViewModel, estado: EstadoSessao, logo: String?) {
    when (estado) {
        is EstadoSessao.Activa -> {
            val recursos by raiz.conta.recursos.collectAsStateWithLifecycle()
            val r = recursos
            if (r == null || r.perfil.uid != estado.perfil.uid) Carregando("A preparar a sua área…") else AreaAutenticada(raiz, r)
        }
        is EstadoSessao.PerfilIncompleto -> {
            val nav = rememberNavController()
            NavHost(nav, startDestination = Rotas.CADASTRO) {
                composable(Rotas.CADASTRO) { CadastroEcra(hiltViewModel(), logo, aoVoltar = raiz::cancelarConflito, aoLerPoliticas = { nav.navigate(Rotas.POLITICAS_CADASTRO) }) }
                politicasCadastro(nav, raiz)
            }
        }
        else -> AreaEntrada(raiz, (estado as? EstadoSessao.SemSessao)?.aviso, logo)
    }
}

@Composable
private fun AreaEntrada(raiz: RaizViewModel, aviso: String?, logo: String?) {
    val nav = rememberNavController()
    NavHost(nav, startDestination = Rotas.LOGIN) {
        composable(Rotas.LOGIN) {
            LoginEcra(logo, aviso, aoCriarConta = { nav.navigate(Rotas.CADASTRO) }, aoRecuperar = { nav.navigate(Rotas.RECUPERAR + "?email=" + android.net.Uri.encode(it)) })
        }
        composable(Rotas.RECUPERAR + "?email={email}") { RecuperacaoEcra(aoVoltar = { nav.popBackStack() }) }
        composable(Rotas.CADASTRO) { CadastroEcra(hiltViewModel(), logo, aoVoltar = { nav.popBackStack() }, aoLerPoliticas = { nav.navigate(Rotas.POLITICAS_CADASTRO) }) }
        politicasCadastro(nav, raiz)
    }
}

/** Políticas no contexto do cadastro: partilham o ViewModel do formulário para registar a leitura (gate). */
private fun androidx.navigation.NavGraphBuilder.politicasCadastro(nav: NavHostController, raiz: RaizViewModel) {
    composable(Rotas.POLITICAS_CADASTRO) { entrada ->
        val pai = androidx.compose.runtime.remember(entrada) { nav.getBackStackEntry(Rotas.CADASTRO) }
        val cadastro: CadastroViewModel = hiltViewModel(pai)
        val p = raiz.conteudos.politicas
        PoliticasEcra(p.manualPoliticas, p.versao, comAceite = true, aoVoltar = { nav.popBackStack() },
            aoConfirmarLeitura = { cadastro.leituraConcluida(); nav.popBackStack() })
    }
}

/** Destino vazio do NavHost dos modais: deixa ver a aba por baixo. */
private const val BASE = "base"

/**
 * Área autenticada como a origem (`#app-container`): cabeçalho + barra "Explorar em" + aba (Home · Anunciar · Proprietário) + rodapé,
 * com os modais a abrir POR CIMA (NavHost transparente). Voltar fecha primeiro o modal e depois regressa à Home.
 */
@Composable
private fun AreaAutenticada(raiz: RaizViewModel, r: RecursosConta) {
    val nav = rememberNavController()
    val ambito = rememberCoroutineScope()
    val modoLocal by raiz.modoLocal.collectAsStateWithLifecycle()
    val ajustes by raiz.ajustes.ajustes.collectAsStateWithLifecycle()
    val identidade by raiz.ajustes.identidade.collectAsStateWithLifecycle()
    val papel = r.perfil.papel
    val anuncia = papel != ao.kupila.gestaonegocios.dominio.perfis.Papel.CLIENTE
    val naoLidas by androidx.compose.runtime.remember(r) { r.base.notificacoes().naoLidas() }.collectAsStateWithLifecycle(0)
    val pendentes by androidx.compose.runtime.remember(r) { contarPendentes(r) }.collectAsStateWithLifecycle(0)
    var aba by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(Aba.EXPLORAR) }
    var avancarPara by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val explorar: ao.kupila.gestaonegocios.ui.explorar.ExplorarViewModel = hiltViewModel()
    val filtro by explorar.filtro.collectAsStateWithLifecycle()
    val actual by nav.currentBackStackEntryAsState()
    val rotaActual = actual?.destination?.route
    val modalAberto = rotaActual != null && rotaActual != BASE
    val abrir: (String) -> Unit = { rota -> avancarPara = null; nav.navigate(rota) { launchSingleTop = true } }
    val fechar: () -> Unit = { avancarPara = nav.currentBackStackEntry?.let(::rotaConcreta); nav.popBackStack() }
    androidx.activity.compose.BackHandler(enabled = !modalAberto && aba != Aba.EXPLORAR) { aba = Aba.EXPLORAR }

    androidx.compose.foundation.layout.Column(Modifier.fillMaxSize().background(ao.kupila.gestaonegocios.ui.kp.Kp.slate50)) {
        CabecalhoApp(papel, identidade.logoLocal, AccoesCabecalho(
            ecraCheio = ao.kupila.gestaonegocios.ui.kp.alternarEcraCheio(),
            voltar = { if (modalAberto) fechar() else aba = Aba.EXPLORAR }, podeVoltar = modalAberto || aba != Aba.EXPLORAR,
            avancar = { avancarPara?.let { nav.navigate(it) }; avancarPara = null }, podeAvancar = avancarPara != null && !modalAberto,
            sincronizar = { abrir(Rotas.POUPANCA) }, porSincronizar = 0,
            meusAnuncios = { nav.popBackStack(BASE, false); aba = Aba.PROPRIETARIO }, avisoMeusAnuncios = false,
            pendentes = { abrir(Rotas.PENDENTES) }, nPendentes = pendentes, ajustes = { abrir(Rotas.AJUSTES) },
            faq = { abrir(Rotas.FAQ) }, simulador = { abrir(Rotas.SIMULADOR) }, politicas = { abrir(Rotas.POLITICAS) },
            conta = { abrir(Rotas.CONTA) }, notificacoes = { abrir(Rotas.NOTIFICACOES) }, naoLidas = naoLidas,
            inicio = { nav.popBackStack(BASE, false); aba = Aba.EXPLORAR },
        ))
        ao.kupila.gestaonegocios.ui.actualizacao.FaixaActualizacao(abrirEcra = { abrir(Rotas.ACTUALIZACAO) })
        Box(Modifier.weight(1f).fillMaxWidth()) {
            androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
                BarraLocalizacao(r.perfil.nome, explorar.geografia.provincias, filtro.provincia, explorar::provincia)
                when (aba) {
                    Aba.EXPLORAR -> ao.kupila.gestaonegocios.ui.home.HomeEcra(explorar, aoAbrir = { abrir(Rotas.detalhe(it)) },
                        aoReservaProtegida = { abrir(Rotas.PROTECAO) }, aoConfianca = { abrir(Rotas.CONFIANCA) },
                        reservas = { ao.kupila.gestaonegocios.ui.negocios.ReservasHome(hiltViewModel(key = "home-reservas"), aoPagar = { abrir(Rotas.checkout(it)) }, aoProduto = { abrir(Rotas.detalhe(it)) }, aoRecibo = { abrir(Rotas.recibo(it)) }) }, nome = r.perfil.nome)
                    Aba.ANUNCIAR -> AnunciarEcra(hiltViewModel(key = "aba-anunciar"), aoVoltar = { aba = Aba.EXPLORAR }, aoLerPoliticas = { abrir(Rotas.POLITICAS) },
                        aoPagar = { aba = Aba.EXPLORAR; abrir(Rotas.checkout(it)) }, aoPublicado = { aba = Aba.EXPLORAR; abrir(Rotas.detalhe(it)) })
                    Aba.PROPRIETARIO -> ao.kupila.gestaonegocios.ui.painel.PainelEcra(hiltViewModel(key = "aba-painel"), aoAbrir = { abrir(Rotas.detalhe(it)) },
                        aoEditar = { abrir(Rotas.anunciar(editar = it)) }, aoPerfil = { abrir(Rotas.PERFIL) },
                        aoUtilizadores = { abrir(Rotas.UTILIZADORES) }, aoCatalogo = { abrir(Rotas.monitorDetalhe("PRODUCTS")) }, aoMonitor = { abrir(Rotas.MONITOR) },
                        aoAdministradores = { abrir(Rotas.DIRECTORIO) }, aoAutorizacoes = { abrir(Rotas.AUTORIZACOES) }, aoHistorico = { abrir(Rotas.HISTORICO) },
                        aoReservaProtegida = { abrir(Rotas.PROTECAO) }, aoConfianca = { abrir(Rotas.CONFIANCA) })
                }
            }
            NavHost(nav, startDestination = BASE, Modifier.fillMaxSize(),
                enterTransition = { androidx.compose.animation.slideInVertically { it / 3 } + androidx.compose.animation.fadeIn() },
                exitTransition = { androidx.compose.animation.fadeOut() }, popEnterTransition = { androidx.compose.animation.fadeIn() },
                popExitTransition = { androidx.compose.animation.slideOutVertically { it / 3 } + androidx.compose.animation.fadeOut() }) {
                composable(BASE) { }
                composable(Rotas.DETALHE) { Modal(fechar, cheio = true) {
                    DetalheEcra(hiltViewModel(), aoVoltar = fechar, aoReservar = { abrir(Rotas.checkout(it)) },
                        aoEditar = { abrir(Rotas.anunciar(editar = it)) }, aoPedirAutorizacao = { nav.popBackStack(BASE, false); aba = Aba.PROPRIETARIO })
                } }
                composable(Rotas.NEGOCIOS) { Modal(fechar) { NegociosEcra(hiltViewModel(), aoPagar = { abrir(Rotas.checkout(it)) }, aoProduto = { abrir(Rotas.detalhe(it)) }) } }
                composable(Rotas.ANUNCIAR, arguments = listOf(
                    navArgument("rascunho") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("editar") { type = NavType.StringType; nullable = true; defaultValue = null })) { Modal(fechar) {
                    AnunciarEcra(hiltViewModel(), aoVoltar = fechar, aoLerPoliticas = { abrir(Rotas.POLITICAS_ANUNCIAR) },
                        aoPagar = { nav.navigate(Rotas.checkout(it)) { popUpTo(Rotas.ANUNCIAR) { inclusive = true } } },
                        aoPublicado = { nav.navigate(Rotas.detalhe(it)) { popUpTo(Rotas.ANUNCIAR) { inclusive = true } } })
                } }
                composable(Rotas.POLITICAS_ANUNCIAR) { entrada -> Modal(fechar) {
                    val pai = androidx.compose.runtime.remember(entrada) { nav.getBackStackEntry(Rotas.ANUNCIAR) }
                    val anunciar: AnunciarViewModel = hiltViewModel(pai)
                    val p = raiz.conteudos.politicas
                    PoliticasEcra(p.manualPoliticas, p.versao, comAceite = true, aoVoltar = fechar, aoConfirmarLeitura = { anunciar.leituraPolitica(); fechar() })
                } }
                composable(Rotas.CHECKOUT) { Modal(fechar) {
                    CheckoutEcra(hiltViewModel(), aoVoltar = fechar, aoRegras = { abrir(Rotas.POLITICAS) }, aoConcluido = { tipo, _, listingId ->
                        nav.navigate(if (tipo == "RESERVATION") Rotas.NEGOCIOS else Rotas.detalhe(listingId)) { popUpTo(Rotas.CHECKOUT) { inclusive = true } }
                    })
                } }
                composable(Rotas.RASCUNHOS) { Modal(fechar) {
                    RascunhosEcra(hiltViewModel(), aoVoltar = fechar, aoAbrir = { abrir(Rotas.anunciar(rascunho = it)) }, aoNovo = { nav.popBackStack(BASE, false); aba = Aba.ANUNCIAR })
                } }
                composable(Rotas.POUPANCA) { Modal(fechar) { PoupancaEcra(aoVoltar = fechar, offline = r.offline,
                    aoSincronizar = { ambito.launch { r.eventos.actualizar(r.eventos.essenciais + r.eventos.administracao); r.catalogo.sincronizar(automatica = false) } },
                    aoActualizacao = { abrir(Rotas.ACTUALIZACAO) }) } }
                composable(Rotas.PENDENTES) { Modal(fechar) { ao.kupila.gestaonegocios.ui.pendentes.PendentesEcra(hiltViewModel(), aoVoltar = fechar) } }
                composable(Rotas.AJUSTES) { Modal(fechar) { ao.kupila.gestaonegocios.ui.admin.AjustesEcra(hiltViewModel(), aoFechar = fechar, aoHistorico = { abrir(Rotas.MONETIZACAO) }) } }
                composable(Rotas.RECIBO) { e -> Modal(fechar) {
                    ao.kupila.gestaonegocios.ui.negocios.ReciboEcra(hiltViewModel(), e.arguments?.getString("id").orEmpty(), aoFechar = { nav.popBackStack(BASE, false); aba = Aba.EXPLORAR },
                        aoReembolso = { abrir(Rotas.NEGOCIOS) })
                } }
                composable(Rotas.AUTORIZACOES) { Modal(fechar) { ao.kupila.gestaonegocios.ui.meus.AutorizacoesEcra(hiltViewModel(), aoFechar = fechar) } }
                composable(Rotas.MONETIZACAO) { Modal(fechar) { ao.kupila.gestaonegocios.ui.admin.MonetizacaoEcra(hiltViewModel(), aoFechar = fechar) } }
                composable(Rotas.UTILIZADORES) { Modal(fechar) { ao.kupila.gestaonegocios.ui.admin.UtilizadoresEcra(hiltViewModel(), aoFechar = fechar, aoFicha = { abrir(Rotas.ficha(it)) }) } }
                composable(Rotas.FICHA) { e -> Modal(fechar) { ao.kupila.gestaonegocios.ui.admin.FichaUtilizadorEcra(hiltViewModel(), e.arguments?.getString("uid").orEmpty(), aoFechar = fechar, aoHistorico = { abrir(Rotas.HISTORICO) }) } }
                composable(Rotas.MONITOR) { Modal(fechar) { ao.kupila.gestaonegocios.ui.admin.MonitorEcra(hiltViewModel(), aoFechar = fechar, aoDetalhe = { t, c, m -> abrir(Rotas.monitorDetalhe(t, c, m)) }) } }
                composable(Rotas.MONITOR_DETALHE, arguments = listOf(navArgument("c") { type = NavType.StringType; defaultValue = "" }, navArgument("m") { type = NavType.StringType; defaultValue = "" })) { e -> Modal(fechar) {
                    ao.kupila.gestaonegocios.ui.admin.MonitorDetalheEcra(hiltViewModel(), e.arguments?.getString("tipo").orEmpty(), e.arguments?.getString("c").orEmpty(), e.arguments?.getString("m").orEmpty(),
                        aoFechar = fechar, aoFicha = { abrir(Rotas.ficha(it)) }, aoProduto = { abrir(Rotas.detalhe(it)) })
                } }
                composable(Rotas.HISTORICO) { Modal(fechar) { ao.kupila.gestaonegocios.ui.admin.HistoricoEcra(hiltViewModel(), aoFechar = fechar) } }
                composable(Rotas.DIRECTORIO) { Modal(fechar) { ao.kupila.gestaonegocios.ui.admin.DirectorioEcra(hiltViewModel(), aoFechar = fechar, aoFicha = { abrir(Rotas.ficha(it)) }) } }
                composable(Rotas.PERFIL) { Modal(fechar) { ao.kupila.gestaonegocios.ui.perfil.PerfilProprietarioEcra(hiltViewModel(), aoFechar = fechar) } }
                composable(Rotas.CONFIANCA) { Modal(fechar) { ao.kupila.gestaonegocios.ui.confianca.ConfiancaEcra(hiltViewModel(), aoVoltar = fechar) } }
                composable(Rotas.MEUS) { Modal(fechar) {
                    ao.kupila.gestaonegocios.ui.meus.MeusAnunciosEcra(hiltViewModel(), aoVoltar = fechar, aoAbrir = { abrir(Rotas.detalhe(it)) }, aoEditar = { abrir(Rotas.anunciar(editar = it)) })
                } }
                composable(Rotas.ADMIN) { Modal(fechar) {
                    ao.kupila.gestaonegocios.ui.admin.AdminEcra(hiltViewModel(), aoVoltar = fechar, aoNegocios = { abrir(Rotas.NEGOCIOS) }, aoAnuncios = { abrir(Rotas.MEUS) })
                } }
                composable(Rotas.NOTIFICACOES) { Modal(fechar) {
                    ao.kupila.gestaonegocios.ui.notificacoes.NotificacoesEcra(hiltViewModel(), aoVoltar = fechar, aoAccao = { n ->
                        when (n.accao) {
                            "OPEN_LISTING" -> n.entidadeId?.let { abrir(Rotas.detalhe(it)) }
                            "OPEN_PAYMENT" -> n.entidadeId?.let { abrir(Rotas.checkout(it)) }
                            "OPEN_AUTHORIZATION" -> abrir(Rotas.AUTORIZACOES)
                            else -> abrir(Rotas.NEGOCIOS)
                        }
                    })
                } }
                composable(Rotas.ACTUALIZACAO) { Modal(fechar) { ao.kupila.gestaonegocios.ui.actualizacao.EcraActualizacao(voltar = fechar) } }
                composable(Rotas.CONTA) { Modal(fechar) {
                    ContaEcra(r.perfil, r.offline, modoLocal, raiz.ajustes::definirModoLocal,
                        aoFaq = { abrir(Rotas.FAQ) }, aoPoliticas = { abrir(Rotas.POLITICAS) }, aoSimulador = { abrir(Rotas.SIMULADOR) },
                        aoSobre = { abrir(Rotas.SOBRE) }, aoProtecao = { abrir(Rotas.PROTECAO) }, aoPerfilProprietario = { abrir(Rotas.PERFIL) },
                        aoSair = { ambito.launch { raiz.sessao.sair() } },
                        aoRascunhos = if (anuncia) ({ abrir(Rotas.RASCUNHOS) }) else null,
                        aoPoupanca = { abrir(Rotas.POUPANCA) }, aoActualizacao = { abrir(Rotas.ACTUALIZACAO) },
                        aoMeusAnuncios = if (anuncia) ({ nav.popBackStack(BASE, false); aba = Aba.PROPRIETARIO }) else null,
                        aoNotificacoes = { abrir(Rotas.NOTIFICACOES) }, naoLidas = naoLidas,
                        aoAdmin = if (Permissoes.eAdministrador(r.perfil)) ({ abrir(Rotas.MONITOR) }) else null,
                        aoFechar = fechar, aoNegocios = { abrir(Rotas.NEGOCIOS) })
                } }
                composable(Rotas.FAQ) { Modal(fechar) { FaqEcra(raiz.conteudos.perguntas(Permissoes.eAdministrador(r.perfil)), aoVoltar = fechar, aoPoliticas = { abrir(Rotas.POLITICAS) }) } }
                composable(Rotas.POLITICAS) { Modal(fechar) {
                    val p = raiz.conteudos.politicas
                    PoliticasEcra(p.manualPoliticas, p.versao, comAceite = false, aoVoltar = fechar, aoConfirmarLeitura = {}, aoSimular = { abrir(Rotas.SIMULADOR) })
                } }
                composable(Rotas.PROTECAO) { Modal(fechar) { ProtecaoEcra(raiz.conteudos.politicas.reservaProtegida, ajustes.prazoReservaMinutos, aoVoltar = fechar) } }
                composable(Rotas.SOBRE) { Modal(fechar) { SobreEcra(aoVoltar = fechar) } }
                composable(Rotas.SIMULADOR) { Modal(fechar) { SimuladorEcra(hiltViewModel(), aoVoltar = fechar) } }
            }
        }
        BarraInferior(aba, papel, aoInicio = { nav.popBackStack(BASE, false); aba = Aba.EXPLORAR },
            aoAnunciar = { nav.popBackStack(BASE, false); aba = Aba.ANUNCIAR }, aoProprietario = { nav.popBackStack(BASE, false); aba = Aba.PROPRIETARIO })
    }
}

/** Rota concreta (com argumentos) de uma entrada, para o botão "Avançar" reabrir o último modal fechado. */
private fun rotaConcreta(e: androidx.navigation.NavBackStackEntry): String? {
    val modelo = e.destination.route ?: return null
    var rota = modelo
    e.arguments?.keySet()?.forEach { k -> rota = rota.replace("{$k}", e.arguments?.getString(k).orEmpty()) }
    return rota.takeIf { it != BASE && !it.contains('{') }
}

/** Pendências do Administrador (`admin-pending-badge`): comprovativos por confirmar + reembolsos por decidir + autorizações. */
private fun contarPendentes(r: RecursosConta): kotlinx.coroutines.flow.Flow<Int> {
    if (!Permissoes.eAdministrador(r.perfil)) return kotlinx.coroutines.flow.flowOf(0)
    val e = r.eventos
    return kotlinx.coroutines.flow.combine(e.observar(ao.kupila.gestaonegocios.dados.eventos.Conjunto.INTENCOES_TODAS),
        e.observar(ao.kupila.gestaonegocios.dados.eventos.Conjunto.REEMBOLSOS_TODOS), e.observar(ao.kupila.gestaonegocios.dados.eventos.Conjunto.AUTORIZACOES)) { i, rf, au ->
        i.count { it.s("status") in setOf("VERIFYING", "RECEIPT_MATCHED") } + rf.count { it.s("status") in setOf("OWNER_CONFIRMED", "CONTESTED_PENDING_ADMIN", "APPROVED_REFUND_PENDING") } +
            au.count { it.s("status") == "PENDING_ADMIN" }
    }
}

/**
 * Modal da origem sobre a área da aba (`fixed inset-0 bg-black/55 flex items-center`): cartão flutuante com margem de 7 px e cantos de 24 px,
 * altura do conteúdo. [cheio] = folha de detalhe (`kupila-detail-shell`) a toda a área, cantos superiores de 18 px.
 */
@Composable
private fun Modal(aoFechar: () -> Unit, cheio: Boolean = false, conteudo: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = .55f)).then(Modifier.clickableSemEfeito(aoFechar)),
        contentAlignment = androidx.compose.ui.Alignment.Center) {
        val forma = if (cheio) androidx.compose.foundation.shape.RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp) else androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
        Box((if (cheio) Modifier.fillMaxSize().padding(top = 3.dp) else Modifier.fillMaxWidth().padding(7.dp).wrapContentHeight())
            .clip(forma).background(androidx.compose.ui.graphics.Color.White).then(Modifier.clickableSemEfeito { })) { conteudo() }
    }
}

private fun Modifier.clickableSemEfeito(f: () -> Unit) = this.clickable(interactionSource = null, indication = null, onClick = f)