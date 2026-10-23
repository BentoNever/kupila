package ao.kupila.gestaonegocios.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Explore
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
    val separadores = buildList {
        add(Separador(Rotas.EXPLORAR, "Explorar", Icons.Filled.Explore))
        add(Separador(Rotas.CONTA, "Conta", Icons.Filled.AccountCircle))
    }
    val actual by nav.currentBackStackEntryAsState()
    val rotaActual = actual?.destination?.route
    Scaffold(bottomBar = {
        if (separadores.any { it.rota == rotaActual }) NavigationBar {
            separadores.forEach { s ->
                NavigationBarItem(selected = rotaActual == s.rota, icon = { Icon(s.icone, null) }, label = { Text(s.rotulo) }, onClick = {
                    // Voltar a um separador restaura o seu estado (filtros, scroll); não acumula ciclos na pilha.
                    nav.navigate(s.rota) { popUpTo(nav.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true }
                })
            }
        }
    }) { pad ->
        NavHost(nav, startDestination = Rotas.EXPLORAR, modifier = Modifier.padding(pad)) {
            composable(Rotas.EXPLORAR) { ExplorarEcra(hiltViewModel(), aoAbrir = { nav.navigate(Rotas.detalhe(it)) }) }
            composable(Rotas.DETALHE) {
                DetalheEcra(hiltViewModel(), aoVoltar = { nav.popBackStack() }, aoReservar = null, aoEditar = null, aoPedirAutorizacao = null)
            }
            composable(Rotas.CONTA) {
                ContaEcra(r.perfil, r.offline, modoLocal, raiz.ajustes::definirModoLocal,
                    aoFaq = { nav.navigate(Rotas.FAQ) }, aoPoliticas = { nav.navigate(Rotas.POLITICAS) }, aoSimulador = { nav.navigate(Rotas.SIMULADOR) },
                    aoSobre = { nav.navigate(Rotas.SOBRE) }, aoProtecao = { nav.navigate(Rotas.PROTECAO) }, aoPerfilProprietario = null,
                    aoSair = { ambito.launch { raiz.sessao.sair() } })
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
