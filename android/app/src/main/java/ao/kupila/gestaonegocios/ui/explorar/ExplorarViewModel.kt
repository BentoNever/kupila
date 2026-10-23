package ao.kupila.gestaonegocios.ui.explorar

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ao.kupila.gestaonegocios.dados.ContaActiva
import ao.kupila.gestaonegocios.dados.catalogo.EstadoCatalogo
import ao.kupila.gestaonegocios.dados.local.Geografia
import ao.kupila.gestaonegocios.dominio.anuncio.AnuncioPublico
import ao.kupila.gestaonegocios.dominio.catalogo.Categoria
import ao.kupila.gestaonegocios.dominio.catalogo.FiltroCatalogo
import ao.kupila.gestaonegocios.dominio.catalogo.ItemVitrine
import ao.kupila.gestaonegocios.dominio.catalogo.RegrasVitrine
import ao.kupila.gestaonegocios.dominio.catalogo.Selo
import ao.kupila.gestaonegocios.dominio.catalogo.Vitrine
import ao.kupila.gestaonegocios.dominio.financeiro.Modalidade
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class CartaoAnuncio(val anuncio: AnuncioPublico, val selo: Selo)

/**
 * Home/Explorar (`tab-explore`, HTML 6696-6779): filtros combináveis, vitrines e catálogo. Os filtros vivem no
 * SavedStateHandle para regressar ao mesmo contexto ao voltar do detalhe ou após recriação (secção 10).
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class ExplorarViewModel @Inject constructor(conta: ContaActiva, val geografia: Geografia, private val guardado: SavedStateHandle) : ViewModel() {

    private val _filtro = MutableStateFlow(FiltroCatalogo(
        texto = guardado["texto"] ?: "", categoria = Categoria.de(guardado["categoria"]), modalidade = Modalidade.daOrigem(guardado["modalidade"]),
        provincia = guardado["provincia"], vitrine = Vitrine.entries.firstOrNull { it.name == guardado["vitrine"] } ?: Vitrine.DESTAQUES))
    val filtro: StateFlow<FiltroCatalogo> = _filtro

    private val recursos = conta.recursos
    val estadoCatalogo: StateFlow<EstadoCatalogo> = recursos.flatMapLatest { it?.catalogo?.estado ?: flowOf(EstadoCatalogo()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EstadoCatalogo())
    val actualizadoEm: StateFlow<Long?> = recursos.flatMapLatest { it?.catalogo?.actualizadoEm ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val offline: StateFlow<Boolean> = recursos.map { it?.offline == true }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Catálogo filtrado (texto com debounce) e ordenado pela vitrine escolhida. */
    val catalogo: StateFlow<List<CartaoAnuncio>?> = combine(recursos, _filtro.debounce(180)) { r, f -> r to f }
        .flatMapLatest { (r, f) ->
            val c = r?.catalogo ?: return@flatMapLatest flowOf(null)
            c.catalogo(f.copy(vitrine = Vitrine.DESTAQUES)).map { lista ->
                val agora = Instant.now()
                val itens = lista.map { ItemVitrine(it.id, it.categoria, it.criadoEm, it.envolvimento, it.promocao) to it }
                val porId = itens.associate { it.first.id to it.second }
                RegrasVitrine.ordenar(itens.map { it.first }, f.vitrine, agora).map { CartaoAnuncio(porId.getValue(it.id), RegrasVitrine.selo(it, agora)) }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private fun mudar(f: (FiltroCatalogo) -> FiltroCatalogo) {
        _filtro.update(f)
        val x = _filtro.value
        guardado["texto"] = x.texto; guardado["categoria"] = x.categoria?.codigo; guardado["modalidade"] = x.modalidade?.codigoOrigem
        guardado["provincia"] = x.provincia; guardado["vitrine"] = x.vitrine.name
    }
    fun texto(v: String) = mudar { it.copy(texto = v) }
    fun limparTexto() = mudar { it.copy(texto = "") }
    fun categoria(c: Categoria?) = mudar { it.copy(categoria = c) }
    fun modalidade(m: Modalidade?) = mudar { it.copy(modalidade = m) }
    fun provincia(p: String?) = mudar { it.copy(provincia = p) }
    fun vitrine(v: Vitrine) = mudar { it.copy(vitrine = v) }

    fun carregarMais() = viewModelScope.launch { recursos.value?.catalogo?.carregarMais() }
    /** Puxar para actualizar: pedido explícito (lê só o que mudou desde a última vez). */
    fun actualizar() = viewModelScope.launch { recursos.value?.catalogo?.sincronizar(automatica = false) }
}
