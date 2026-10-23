package ao.kupila.gestaonegocios.ui

import androidx.compose.foundation.layout.padding
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
    const val MEUS = "meus-anuncios"; const val NOTIFICACOES = "notificacoes"; const val ADMIN = "admin"
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

private data class Separador(val rota: String, val rotulo: String, val icone: ImageVector)

@Composable
private fun AreaAutenticada(raiz: RaizViewModel, r: RecursosConta) {
    val nav = rememberNavController()
    val ambito = rememberCoroutineScope()
    val modoLocal by raiz.modoLocal.collectAsStateWithLifecycle()
    val ajustes by raiz.ajustes.ajustes.collectAsStateWithLifecycle()
    val anuncia = r.perfil.papel != ao.kupila.gestaonegocios.dominio.perfis.Papel.CLIENTE
    val naoLidas by androidx.compose.runtime.remember(r) { r.base.notificacoes().naoLidas() }.collectAsStateWithLifecycle(0)
    val separadores = buildList {
        add(Separador(Rotas.EXPLORAR, "Explorar", Icons.Filled.Explore))
        add(Separador(Rotas.NEGOCIOS, "Negócios", Icons.Filled.Handshake))
        if (anuncia) add(Separador(Rotas.anunciar(), "Anunciar", Icons.Filled.AddCircle))
        add(Separador(Rotas.CONTA, "Conta", Icons.Filled.AccountCircle))
    }
    val actual by nav.currentBackStackEntryAsState()
    val rotaActual = actual?.destination?.route
    val abas = setOf(Rotas.EXPLORAR, Rotas.NEGOCIOS, Rotas.CONTA)
    Scaffold(bottomBar = {
        if (rotaActual in abas) NavigationBar {
            separadores.forEach { s ->
                NavigationBarItem(selected = rotaActual == s.rota, icon = { Icon(s.icone, null) }, label = { Text(s.rotulo) }, onClick = {
                    // Anunciar abre o formulário por cima; os separadores restauram o seu estado (filtros, scroll) sem acumular ciclos.
                    if (s.rota !in abas) nav.navigate(s.rota)
                    else nav.navigate(s.rota) { popUpTo(nav.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true }
                })
            }
        }
    }) { pad ->
      androidx.compose.foundation.layout.Column(Modifier.padding(pad)) {
        ao.kupila.gestaonegocios.ui.actualizacao.FaixaActualizacao(abrirEcra = { nav.navigate(Rotas.ACTUALIZACAO) })
        NavHost(nav, startDestination = Rotas.EXPLORAR) {
            composable(Rotas.EXPLORAR) { ExplorarEcra(hiltViewModel(), aoAbrir = { nav.navigate(Rotas.detalhe(it)) }) }
            composable(Rotas.DETALHE) {
                DetalheEcra(hiltViewModel(), aoVoltar = { nav.popBackStack() }, aoReservar = { nav.navigate(Rotas.checkout(it)) },
                    aoEditar = { nav.navigate(Rotas.anunciar(editar = it)) }, aoPedirAutorizacao = { nav.navigate(Rotas.MEUS) })
            }
            composable(Rotas.NEGOCIOS) {
                NegociosEcra(hiltViewModel(), aoPagar = { nav.navigate(Rotas.checkout(it)) }, aoProduto = { nav.navigate(Rotas.detalhe(it)) })
            }
            composable(Rotas.ANUNCIAR, arguments = listOf(
                navArgument("rascunho") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("editar") { type = NavType.StringType; nullable = true; defaultValue = null })) {
                AnunciarEcra(hiltViewModel(), aoVoltar = { nav.popBackStack() }, aoLerPoliticas = { nav.navigate(Rotas.POLITICAS_ANUNCIAR) },
                    aoPagar = { nav.navigate(Rotas.checkout(it)) { popUpTo(Rotas.ANUNCIAR) { inclusive = true } } },
                    aoPublicado = { nav.navigate(Rotas.detalhe(it)) { popUpTo(Rotas.ANUNCIAR) { inclusive = true } } })
            }
            composable(Rotas.POLITICAS_ANUNCIAR) { entrada ->
                val pai = androidx.compose.runtime.remember(entrada) { nav.getBackStackEntry(Rotas.ANUNCIAR) }
                val anunciar: AnunciarViewModel = hiltViewModel(pai)
                val p = raiz.conteudos.politicas
                PoliticasEcra(p.manualPoliticas, p.versao, comAceite = true, aoVoltar = { nav.popBackStack() },
                    aoConfirmarLeitura = { anunciar.leituraPolitica(); nav.popBackStack() })
            }
            composable(Rotas.CHECKOUT) {
                CheckoutEcra(hiltViewModel(), aoVoltar = { nav.popBackStack() }, aoConcluido = { tipo, _, listingId ->
                    nav.navigate(if (tipo == "RESERVATION") Rotas.NEGOCIOS else Rotas.detalhe(listingId)) { popUpTo(Rotas.CHECKOUT) { inclusive = true } }
                })
            }
            composable(Rotas.RASCUNHOS) {
                RascunhosEcra(hiltViewModel(), aoVoltar = { nav.popBackStack() }, aoAbrir = { nav.navigate(Rotas.anunciar(rascunho = it)) }, aoNovo = { nav.navigate(Rotas.anunciar()) })
            }
            composable(Rotas.POUPANCA) { PoupancaEcra(aoVoltar = { nav.popBackStack() }) }
            composable(Rotas.MEUS) {
                ao.kupila.gestaonegocios.ui.meus.MeusAnunciosEcra(hiltViewModel(), aoVoltar = { nav.popBackStack() }, aoAbrir = { nav.navigate(Rotas.detalhe(it)) },
                    aoEditar = { nav.navigate(Rotas.anunciar(editar = it)) })
            }
            composable(Rotas.ADMIN) {
                ao.kupila.gestaonegocios.ui.admin.AdminEcra(hiltViewModel(), aoVoltar = { nav.popBackStack() }, aoNegocios = { nav.navigate(Rotas.NEGOCIOS) },
                    aoAnuncios = { nav.navigate(Rotas.MEUS) })
            }
            composable(Rotas.NOTIFICACOES) {
                ao.kupila.gestaonegocios.ui.notificacoes.NotificacoesEcra(hiltViewModel(), aoVoltar = { nav.popBackStack() }, aoAccao = { n ->
                    when (n.accao) {
                        "OPEN_LISTING" -> n.entidadeId?.let { nav.navigate(Rotas.detalhe(it)) }
                        "OPEN_PAYMENT" -> n.entidadeId?.let { nav.navigate(Rotas.checkout(it)) }
                        "OPEN_AUTHORIZATION" -> nav.navigate(Rotas.MEUS)
                        else -> nav.navigate(Rotas.NEGOCIOS)
                    }
                })
            }
            composable(Rotas.ACTUALIZACAO) { ao.kupila.gestaonegocios.ui.actualizacao.EcraActualizacao(voltar = { nav.popBackStack() }) }
            composable(Rotas.CONTA) {
                ContaEcra(r.perfil, r.offline, modoLocal, raiz.ajustes::definirModoLocal,
                    aoFaq = { nav.navigate(Rotas.FAQ) }, aoPoliticas = { nav.navigate(Rotas.POLITICAS) }, aoSimulador = { nav.navigate(Rotas.SIMULADOR) },
                    aoSobre = { nav.navigate(Rotas.SOBRE) }, aoProtecao = { nav.navigate(Rotas.PROTECAO) }, aoPerfilProprietario = null,
                    aoSair = { ambito.launch { raiz.sessao.sair() } },
                    aoRascunhos = if (anuncia) ({ nav.navigate(Rotas.RASCUNHOS) }) else null,
                    aoPoupanca = { nav.navigate(Rotas.POUPANCA) }, aoActualizacao = { nav.navigate(Rotas.ACTUALIZACAO) },
                    aoMeusAnuncios = if (anuncia) ({ nav.navigate(Rotas.MEUS) }) else null, aoNotificacoes = { nav.navigate(Rotas.NOTIFICACOES) }, naoLidas = naoLidas,
                    aoAdmin = if (Permissoes.eAdministrador(r.perfil)) ({ nav.navigate(Rotas.ADMIN) }) else null)
            }
            composable(Rotas.FAQ) { FaqEcra(raiz.conteudos.perguntas(Permissoes.eAdministrador(r.perfil)), aoVoltar = { nav.popBackStack() }, aoPoliticas = { nav.navigate(Rotas.POLITICAS) }) }
            composable(Rotas.POLITICAS) {
                val p = raiz.conteudos.politicas
                PoliticasEcra(p.manualPoliticas, p.versao, comAceite = false, aoVoltar = { nav.popBackStack() }, aoConfirmarLeitura = {},
                    aoSimular = { nav.navigate(Rotas.SIMULADOR) })
            }
            composable(Rotas.PROTECAO) { ProtecaoEcra(raiz.conteudos.politicas.reservaProtegida, ajustes.prazoReservaMinutos, aoVoltar = { nav.popBackStack() }) }
            composable(Rotas.SOBRE) { SobreEcra(aoVoltar = { nav.popBackStack() }) }
            composable(Rotas.SIMULADOR) { SimuladorEcra(hiltViewModel(), aoVoltar = { nav.popBackStack() }) }
        }
      }
    }
}
