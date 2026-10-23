package ao.kupila.gestaonegocios.ui.simulador

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ao.kupila.gestaonegocios.dados.ajustes.AjustesRepositorio
import ao.kupila.gestaonegocios.dominio.financeiro.CondicoesAluguer
import ao.kupila.gestaonegocios.dominio.financeiro.Dinheiro
import ao.kupila.gestaonegocios.dominio.financeiro.EntradaCalculo
import ao.kupila.gestaonegocios.dominio.financeiro.Modalidade
import ao.kupila.gestaonegocios.dominio.financeiro.MotorFinanceiro
import ao.kupila.gestaonegocios.dominio.financeiro.ParametrosFinanceiros
import ao.kupila.gestaonegocios.dominio.financeiro.Percentagem
import ao.kupila.gestaonegocios.dominio.financeiro.PrazoMinimo
import ao.kupila.gestaonegocios.dominio.financeiro.Promocao
import ao.kupila.gestaonegocios.dominio.financeiro.ResumoFinanceiro
import ao.kupila.gestaonegocios.dominio.financeiro.UnidadeDuracao
import ao.kupila.gestaonegocios.dominio.financeiro.UnidadePreco
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject

private val Context.dadosSimulador by preferencesDataStore("kupila_simulador")

enum class ModoSimulacao(val rotulo: String) { COMPLETA("Simulação completa"), RESERVA("Reserva do Cliente"), PUBLICACAO("Publicação do Proprietário") }

/** Cenário (texto dos campos, como o utilizador escreveu). Guardado automaticamente no dispositivo. */
@Serializable
data class Cenario(
    val modo: String = ModoSimulacao.COMPLETA.name, val modalidade: String = "VENDA", val preco: String = "10000000",
    val unidade: String = "MONTHLY", val prazo: String = "3_MONTHS", val prazoQtd: String = "1", val prazoUnidade: String = "MONTH",
    val outraUnidade: String = "", val unidadesPorMes: String = "",
    val sinal: String = "5", val meses: String = "1", val comissao: String = "15", val taxa: String = "1",
    val promos: Set<String> = emptySet(), val pDestaque: String = "0.25", val pTop: String = "0.50", val pPrioridade: String = "0.35",
)

/**
 * Simulador (`modal-intermediation-simulator`, HTML 8253-8309). É local e informativo: não cria pagamento, reserva, anúncio
 * nem altera ajustes (secção 32). Usa o MESMO [MotorFinanceiro] — no aluguer, meses de sinal (decisão D-12).
 */
@HiltViewModel
class SimuladorViewModel @Inject constructor(@param:ApplicationContext private val contexto: Context, private val ajustes: AjustesRepositorio) : ViewModel() {
    private val chave = stringPreferencesKey("cenario")
    private val json = Json { ignoreUnknownKeys = true }
    private val _c = MutableStateFlow(Cenario())
    val cenario: StateFlow<Cenario> = _c

    init {
        viewModelScope.launch {
            val g = contexto.dadosSimulador.data.first()[chave]?.let { runCatching { json.decodeFromString(Cenario.serializer(), it) }.getOrNull() }
            _c.value = g ?: deAjustes(Cenario())
        }
    }

    val resultado: StateFlow<Result<ResumoFinanceiro>?> = _c.map { c -> runCatching { MotorFinanceiro.calcular(entrada(c)) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private fun entrada(c: Cenario): EntradaCalculo {
        fun num(s: String) = s.replace(',', '.').trim().toBigDecimalOrNull() ?: throw IllegalArgumentException("Valor numérico inválido.")
        val modalidade = Modalidade.daOrigem(c.modalidade) ?: Modalidade.VENDA
        val preco = Dinheiro.de(num(c.preco))
        val p = ParametrosFinanceiros("simulacao", Percentagem.de(num(c.sinal)), num(c.meses).toInt(), Percentagem.de(num(c.comissao)), Percentagem.de(num(c.taxa)),
            mapOf(Promocao.DESTAQUE to Percentagem.de(num(c.pDestaque)), Promocao.TOP to Percentagem.de(num(c.pTop)), Promocao.PRIORIDADE to Percentagem.de(num(c.pPrioridade))), 7)
        val aluguer = if (modalidade == Modalidade.ARRENDAMENTO) {
            val prazo = when (c.prazo) { "3_MONTHS" -> PrazoMinimo.TresMeses; "6_MONTHS" -> PrazoMinimo.SeisMeses; "12_MONTHS" -> PrazoMinimo.Anual
                else -> PrazoMinimo.Personalizado(num(c.prazoQtd).toInt(), UnidadeDuracao.daOrigem(c.prazoUnidade) ?: UnidadeDuracao.MES) }
            CondicoesAluguer(preco, UnidadePreco.daOrigem(c.unidade) ?: UnidadePreco.MES, prazo, c.outraUnidade, c.unidadesPorMes.takeIf { it.isNotBlank() }?.let { num(it) })
        } else null
        val promos = c.promos.mapNotNull { k -> Promocao.entries.firstOrNull { it.codigoOrigem == k } }.toSet()
        return EntradaCalculo(modalidade, preco, aluguer, p, promos)
    }

    fun mudar(f: (Cenario) -> Cenario) {
        _c.update(f)
        viewModelScope.launch { contexto.dadosSimulador.edit { it[chave] = json.encodeToString(Cenario.serializer(), _c.value) } }
    }

    private fun deAjustes(c: Cenario): Cenario {
        val f = ajustes.ajustes.value.financeiro
        fun t(p: Percentagem) = p.valor.stripTrailingZeros().toPlainString()
        return c.copy(sinal = t(f.sinalVenda), meses = f.mesesSinalAluguer.toString(), comissao = t(f.comissao), taxa = t(f.taxaAnuncio),
            pDestaque = t(f.promocoes.getValue(Promocao.DESTAQUE)), pTop = t(f.promocoes.getValue(Promocao.TOP)), pPrioridade = t(f.promocoes.getValue(Promocao.PRIORIDADE)))
    }

    /** "Usar percentagens actuais" — acção explícita; não altera os ajustes globais. */
    fun usarActuais() = mudar(::deAjustes)
    fun limpar() = mudar { deAjustes(Cenario()) }
}
