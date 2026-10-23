package ao.kupila.gestaonegocios.ui.meus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ao.kupila.gestaonegocios.dados.ContaActiva
import ao.kupila.gestaonegocios.dados.admin.AutorizacoesRepositorio
import ao.kupila.gestaonegocios.dados.anuncios.AnunciosRepositorio
import ao.kupila.gestaonegocios.dados.eventos.Conjunto
import ao.kupila.gestaonegocios.dados.eventos.Registo
import ao.kupila.gestaonegocios.dados.remoto.MensagensErro
import ao.kupila.gestaonegocios.dominio.anuncio.AnuncioPublico
import ao.kupila.gestaonegocios.dominio.anuncio.EstadoAnuncio
import ao.kupila.gestaonegocios.dominio.autorizacoes.AccaoAutorizada
import ao.kupila.gestaonegocios.dominio.autorizacoes.EstadoAutorizacao
import ao.kupila.gestaonegocios.dominio.perfis.Papel
import ao.kupila.gestaonegocios.ui.ajuda.EcraComTopo
import ao.kupila.gestaonegocios.ui.comum.Aviso
import ao.kupila.gestaonegocios.ui.comum.BotaoPrincipal
import ao.kupila.gestaonegocios.ui.comum.EstadoVazio
import ao.kupila.gestaonegocios.ui.comum.TipoAviso
import ao.kupila.gestaonegocios.ui.explorar.precoComUnidade
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Meus anúncios + autorizações (secção 15). O código de autorização chega na app do Proprietário (D-08) e é aplicado com um toque —
 * sem copiar códigos entre ecrãs. O Administrador gera/recusa pedidos e gere estados directamente.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MeusAnunciosViewModel @Inject constructor(
    private val conta: ContaActiva,
    private val anuncios: AnunciosRepositorio,
    private val autorizacoes: AutorizacoesRepositorio,
) : ViewModel() {
    val papel get() = conta.recursos.value?.perfil?.papel
    val lista = conta.recursos.flatMapLatest { r -> r?.catalogo?.doDono(r.perfil.uid) ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val pedidos = conta.recursos.flatMapLatest { it?.eventos?.observar(Conjunto.AUTORIZACOES) ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val mensagem = MutableStateFlow<String?>(null)
    val ocupado = MutableStateFlow<String?>(null)

    init { viewModelScope.launch { conta.recursos.value?.let { r -> r.catalogo.sincronizarMeus(); r.eventos.actualizar(listOf(Conjunto.AUTORIZACOES)) } } }

    private fun accao(chave: String, bloco: suspend () -> String) = viewModelScope.launch {
        if (ocupado.value != null) return@launch
        ocupado.value = chave
        mensagem.value = runCatching { bloco() }.getOrElse { MensagensErro.de(it) }
        ocupado.value = null
        conta.recursos.value?.let { r -> r.catalogo.sincronizarMeus(); r.eventos.actualizar(listOf(Conjunto.AUTORIZACOES)) }
    }

    fun pedir(a: AnuncioPublico, accao: AccaoAutorizada) = accao("ped:${a.id}") {
        autorizacoes.pedir(a.id, a.titulo, accao, conta.recursos.value!!.perfil); "Pedido enviado. Quando o Administrador autorizar, conclui aqui com um toque."
    }
    fun concluir(p: Registo) = accao("usar:${p.id}") {
        autorizacoes.usar(p.id, p.s("code"), conta.recursos.value!!.perfil); "Concluído: ${AccaoAutorizada.de(p.s("action"))?.substantivo ?: "acção"} aplicada."
    }
    fun gerar(p: Registo) = accao("ger:${p.id}") { autorizacoes.gerarCodigo(p.id, conta.recursos.value!!.perfil); "Autorizado: o Proprietário conclui na app." }
    fun recusar(p: Registo) = accao("rec:${p.id}") { autorizacoes.recusar(p.id, "Recusado pelo Administrador."); "Pedido recusado." }
    fun desanunciar(a: AnuncioPublico) = accao("des:${a.id}") { anuncios.desanunciar(a.id, "Desanunciado pelo Administrador."); "Desanunciado." }
    fun reactivar(a: AnuncioPublico) = accao("rea:${a.id}") { anuncios.reactivar(a.id); "Anúncio reactivado." }
}

@Composable
fun MeusAnunciosEcra(vm: MeusAnunciosViewModel, aoVoltar: () -> Unit, aoAbrir: (String) -> Unit, aoEditar: (String) -> Unit) {
    val l by vm.lista.collectAsStateWithLifecycle()
    val pedidos by vm.pedidos.collectAsStateWithLifecycle()
    val ocupado by vm.ocupado.collectAsStateWithLifecycle()
    val msg by vm.mensagem.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(msg) { msg?.let { snack.showSnackbar(it); vm.mensagem.value = null } }
    val admin = vm.papel == Papel.ADMINISTRADOR
    EcraComTopo(if (admin) "Anúncios e autorizações" else "Meus anúncios", aoVoltar) { pad ->
      androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val prontos = pedidos.filter { it.s("status") == "CODE_SENT" && !admin }
            val aguardam = pedidos.filter { it.s("status") == "PENDING_ADMIN" }
            items(prontos, key = { "p" + it.id }) { p ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Autorização recebida: ${AccaoAutorizada.de(p.s("action"))?.verbo} “${p.s("listingTitle")}”", style = MaterialTheme.typography.titleSmall)
                        Text("Válida por pouco tempo. A acção não tem reembolso.", style = MaterialTheme.typography.bodySmall)
                        BotaoPrincipal("Concluir agora", { vm.concluir(p) }, ocupado = ocupado == "usar:${p.id}")
                    }
                }
            }
            if (admin && aguardam.isNotEmpty()) item { Text("Pedidos de autorização (${aguardam.size})", style = MaterialTheme.typography.titleMedium) }
            if (admin) items(aguardam, key = { "a" + it.id }) { p ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${p.s("ownerName")} quer ${AccaoAutorizada.de(p.s("action"))?.verbo} “${p.s("listingTitle")}”", style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BotaoPrincipal("Autorizar", { vm.gerar(p) }, Modifier.weight(1f), ocupado = ocupado == "ger:${p.id}")
                            TextButton(onClick = { vm.recusar(p) }) { Text("Recusar") }
                        }
                    }
                }
            }
            if (!admin && aguardam.isNotEmpty()) item { Aviso("${aguardam.size} pedido(s) a aguardar o Administrador.", TipoAviso.INFO) }
            val lista = l
            if (lista != null && lista.isEmpty()) item { EstadoVazio("Ainda não tem anúncios", "Toque em Anunciar: o formulário preenche-se a partir do título.") }
            items(lista.orEmpty(), key = { it.id }) { a ->
                val pend = pedidos.any { it.s("listingId") == a.id && it.s("status") in setOf(EstadoAutorizacao.PENDENTE_ADMIN.codigo, EstadoAutorizacao.CODIGO_ENVIADO.codigo) }
                Card(onClick = { aoAbrir(a.id) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(a.titulo, style = MaterialTheme.typography.titleMedium)
                        Text("${a.estado.rotulo} · ${precoComUnidade(a)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (a.estado == EstadoAnuncio.ACTIVO || a.estado == EstadoAnuncio.DESANUNCIADO) TextButton(onClick = { aoEditar(a.id) }) { Text("Editar") }
                            when {
                                admin && a.estado == EstadoAnuncio.ACTIVO -> TextButton(onClick = { vm.desanunciar(a) }) { Text("Desanunciar") }
                                admin && a.estado == EstadoAnuncio.DESANUNCIADO -> TextButton(onClick = { vm.reactivar(a) }) { Text("Reactivar") }
                                !admin && !pend && a.estado == EstadoAnuncio.ACTIVO -> TextButton(onClick = { vm.pedir(a, AccaoAutorizada.DESANUNCIAR) }) { Text("Desanunciar") }
                            }
                            if (!admin && !pend && a.estado in setOf(EstadoAnuncio.ACTIVO, EstadoAnuncio.DESANUNCIADO))
                                TextButton(onClick = { vm.pedir(a, AccaoAutorizada.EXCLUIR) }) { Text("Excluir") }
                        }
                    }
                }
            }
        }
        SnackbarHost(snack, Modifier.align(androidx.compose.ui.Alignment.BottomCenter).padding(pad))
      }
    }
}
