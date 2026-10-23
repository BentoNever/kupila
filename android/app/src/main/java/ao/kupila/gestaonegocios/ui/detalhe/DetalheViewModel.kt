package ao.kupila.gestaonegocios.ui.detalhe

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ao.kupila.gestaonegocios.dados.ContaActiva
import ao.kupila.gestaonegocios.dados.ajustes.AjustesRepositorio
import ao.kupila.gestaonegocios.dados.remoto.MensagensErro
import ao.kupila.gestaonegocios.dominio.anuncio.AnuncioPublico
import ao.kupila.gestaonegocios.dominio.anuncio.DadosEncontro
import ao.kupila.gestaonegocios.dominio.financeiro.EntradaCalculo
import ao.kupila.gestaonegocios.dominio.financeiro.Isencoes
import ao.kupila.gestaonegocios.dominio.financeiro.MotorFinanceiro
import ao.kupila.gestaonegocios.dominio.financeiro.ResumoFinanceiro
import ao.kupila.gestaonegocios.dominio.perfis.Papel
import ao.kupila.gestaonegocios.dominio.perfis.Permissoes
import ao.kupila.gestaonegocios.dominio.perfis.ValidacaoContacto
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/** Acesso aos dados protegidos do anúncio (secção 11). */
sealed interface AcessoPrivado {
    data object Protegido : AcessoPrivado
    data object ACarregar : AcessoPrivado
    data class Titular(val dados: DadosEncontro) : AcessoPrivado
    data class Administrador(val dados: DadosEncontro) : AcessoPrivado
    data class ReservaConfirmada(val dados: DadosEncontro) : AcessoPrivado
    /** Falha de leitura privada: não apaga as características públicas (secção 11). */
    data class Falhou(val mensagem: String) : AcessoPrivado
}

data class AccoesDetalhe(val podeEditar: Boolean, val podeExcluirDirecto: Boolean, val podePedirAutorizacao: Boolean, val podeReservar: Boolean, val motivoSemReserva: String?)

/**
 * Detalhe do anúncio. O id vem da rota; cada abertura tem o seu próprio ViewModel (back stack entry), por isso uma resposta
 * atrasada de outro produto não pode preencher este ecrã (secção 11 / 1.0.213-1.0.216).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DetalheViewModel @Inject constructor(
    private val conta: ContaActiva,
    private val ajustes: AjustesRepositorio,
    private val transaccoes: ao.kupila.gestaonegocios.dados.transaccoes.TransaccoesRepositorio,
    guardado: SavedStateHandle,
) : ViewModel() {
    /** Validade da reserva após confirmação (details-reservation-validity), dos ajustes globais. */
    fun prazoReservaHoras(): Long = (ajustes.ajustes.value.prazoReservaMinutos / 60).coerceAtLeast(1)

    private val _aReservar = MutableStateFlow(false)
    val aReservar: StateFlow<Boolean> = _aReservar.asStateFlow()

    /** Um toque: cria (ou reaproveita) a intenção do sinal e abre o checkout. */
    fun reservar(aoPagar: (String) -> Unit) = viewModelScope.launch {
        val a = anuncio.value ?: return@launch
        val r = resumo.value ?: return@launch
        val p = conta.recursos.value?.perfil ?: return@launch
        if (_aReservar.value) return@launch
        _aReservar.value = true; _erroActualizacao.value = null
        runCatching { transaccoes.intencao("RESERVATION", p, a.id, a.donoId, r, ajustes.ajustes.value.entidadePagamento, a.titulo) }
            .onSuccess { aoPagar(it["id"] as String) }
            .onFailure { _erroActualizacao.value = MensagensErro.de(it) }
        _aReservar.value = false
    }

    val id: String = checkNotNull(guardado["id"])

    val anuncio: StateFlow<AnuncioPublico?> = conta.recursos.flatMapLatest { it?.catalogo?.observar(id) ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _erroActualizacao = MutableStateFlow<String?>(null)
    val erroActualizacao: StateFlow<String?> = _erroActualizacao.asStateFlow()
    private val _privado = MutableStateFlow<AcessoPrivado>(AcessoPrivado.ACarregar)
    val privado: StateFlow<AcessoPrivado> = _privado.asStateFlow()

    /** Mesmo motor do cadastro/checkout/simulador. Valores oficiais do snapshot vêm do Central no checkout (Etapa 4). */
    val resumo: StateFlow<ResumoFinanceiro?> = combine(anuncio, ajustes.ajustes) { a, aj ->
        a?.let {
            val isento = it.publicadoPorAdministrador
            MotorFinanceiro.calcular(EntradaCalculo(it.modalidade, it.preco, it.aluguer, aj.financeiro,
                isencoes = if (isento) Isencoes(comissao = true, motivo = "Anúncio publicado pelo Administrador") else Isencoes.NENHUMA))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val accoes: StateFlow<AccoesDetalhe?> = combine(anuncio, conta.recursos) { a, r ->
        if (a == null || r == null) null else {
            val p = r.perfil
            val gere = Permissoes.podeGerirAnuncio(p, a.donoId)
            val dono = p.uid == a.donoId
            val motivo = when {
                dono -> "Este produto é seu."
                !Permissoes.podeReservar(p) -> "A sua conta não está activa para efectuar reservas."
                !a.estado.disponivelParaReserva -> "Este produto não está disponível para reserva."
                r.offline -> "A reserva exige ligação ao serviço."
                else -> null
            }
            AccoesDetalhe(podeEditar = gere, podeExcluirDirecto = p.papel == Papel.ADMINISTRADOR, podePedirAutorizacao = dono && p.papel == Papel.PROPRIETARIO,
                podeReservar = motivo == null, motivoSemReserva = motivo)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch {
            val r = conta.recursos.value ?: return@launch
            if (!r.offline) _erroActualizacao.value = r.catalogo.actualizarDetalhe(id).exceptionOrNull()?.let(MensagensErro::de)
        }
        viewModelScope.launch { carregarPrivado() }
    }

    private suspend fun carregarPrivado() {
        val r = conta.recursos.value ?: return
        val a = kotlinx.coroutines.withTimeoutOrNull(10_000) { r.catalogo.observar(id).filterNotNull().first() }
        if (a == null) { _privado.value = AcessoPrivado.Protegido; return }
        val dono = r.perfil.uid == a.donoId
        val admin = Permissoes.eAdministrador(r.perfil)
        if (!dono && !admin && a.estado != ao.kupila.gestaonegocios.dominio.anuncio.EstadoAnuncio.RESERVADO) { _privado.value = AcessoPrivado.Protegido; return }
        _privado.value = AcessoPrivado.ACarregar
        _privado.value = runCatching {
            val d = FirebaseFirestore.getInstance().document("productPrivate/$id").get().await()
            val dono2 = d.get("ownerSnapshot") as? Map<*, *>
            val tel = (dono2?.get("phonePrimary") as? String).orEmpty()
            val dados = DadosEncontro(d.getString("neighborhood").orEmpty(), d.getString("referencePoint").orEmpty(),
                (dono2?.get("name") as? String).orEmpty(), if (tel.isNotBlank()) ValidacaoContacto.telefoneInternacional(tel) else "")
            // Regras de produção: o Cliente com a reserva associada (reservedByMe) lê; mostram-se só os dados de encontro.
            when { dono -> AcessoPrivado.Titular(dados); admin -> AcessoPrivado.Administrador(dados); else -> AcessoPrivado.ReservaConfirmada(dados) }
        }.getOrElse { if (!dono && !admin) AcessoPrivado.Protegido else AcessoPrivado.Falhou(MensagensErro.de(it)) }
    }
}
