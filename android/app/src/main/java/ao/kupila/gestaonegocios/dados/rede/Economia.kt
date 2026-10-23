package ao.kupila.gestaonegocios.dados.rede

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** O pedido ao Firestore não sai: a app mostra o guardado e põe as escritas na fila (não é um erro do utilizador). */
class EmPoupanca(mensagem: String) : Exception(mensagem)

/**
 * ECONOMIA DE QUOTAS + MODO POUPANÇA (adaptado do `ModoPoupanca` do PDV Digital — decisão do utilizador, docs/principios-produto.md).
 *
 *  1. POUPANÇA INTELIGENTE (sempre activa, como a "poupança da direcção" do PDV): nenhuma escuta permanente; uma LEITURA
 *     automática (temporizadores, regressar ao app, ecrã parado) só sai se a pessoa tocou no ecrã há menos de [JANELA_TOQUE_MS]
 *     — abrir um ecrã, puxar para actualizar ou agir conta como pedido. Escritas da pessoa nunca são travadas.
 *  2. PAUSA TOTAL (opcional, persistente): zero leituras e zero escritas; tudo fica guardado no telemóvel e segue sozinho
 *     quando a pausa termina ou num "Enviar agora" (janela curta que volta sozinha à pausa).
 *
 * Contadores reais (documentos lidos/escritos e pedidos evitados) mostram a poupança à pessoa.
 */
object Economia {
    const val JANELA_TOQUE_MS = 20_000L
    private var prefs: SharedPreferences? = null

    private val _pausada = MutableStateFlow(false); val pausada: StateFlow<Boolean> = _pausada.asStateFlow()
    private val _desde = MutableStateFlow<Long?>(null); val desde: StateFlow<Long?> = _desde.asStateFlow()
    private val _janela = MutableStateFlow(false); val janela: StateFlow<Boolean> = _janela.asStateFlow()
    @Volatile private var janelaAte = 0L

    private val _lidos = MutableStateFlow(0L); val lidos: StateFlow<Long> = _lidos.asStateFlow()
    private val _escritos = MutableStateFlow(0L); val escritos: StateFlow<Long> = _escritos.asStateFlow()
    private val _evitados = MutableStateFlow(0L); val evitados: StateFlow<Long> = _evitados.asStateFlow()
    /** Documentos que uma escuta permanente teria relido e que a sincronização delta evitou (estimativa conservadora). */
    private val _poupadosDelta = MutableStateFlow(0L); val poupadosDelta: StateFlow<Long> = _poupadosDelta.asStateFlow()

    @Volatile private var ultimoToque = 0L
    @Volatile private var entradaAte = 0L

    @Synchronized fun inicializar(c: Context) {
        if (prefs != null) return
        val p = c.applicationContext.getSharedPreferences("kupila_economia", Context.MODE_PRIVATE).also { prefs = it }
        _pausada.value = p.getBoolean("pausada", false)
        _desde.value = p.getLong("desde", 0).takeIf { it > 0 && _pausada.value }
        _lidos.value = p.getLong("lidos", 0); _escritos.value = p.getLong("escritos", 0)
        _evitados.value = p.getLong("evitados", 0); _poupadosDelta.value = p.getLong("delta", 0)
    }

    private fun gravar() {
        prefs?.edit()?.putLong("lidos", _lidos.value)?.putLong("escritos", _escritos.value)?.putLong("evitados", _evitados.value)?.putLong("delta", _poupadosDelta.value)?.apply()
    }

    /** A pessoa tocou no ecrã (MainActivity.onUserInteraction). */
    fun tocou(agora: Long = System.currentTimeMillis()) { ultimoToque = agora }
    /** Entrar/registar é sempre um pedido explícito (a pausa não bloqueia a autenticação). */
    fun permitirEntrada(ms: Long = 90_000L) { entradaAte = System.currentTimeMillis() + ms; tocou() }
    val emEntrada get() = System.currentTimeMillis() < entradaAte

    /** Pura (testada): esta leitura pode sair agora? */
    fun leituraPermitida(automatica: Boolean, pausada: Boolean, emJanela: Boolean, ultimoToque: Long, agora: Long, entrada: Boolean): Boolean = when {
        entrada -> true
        pausada && !emJanela -> false
        !automatica -> true
        else -> agora - ultimoToque in 0..JANELA_TOQUE_MS
    }

    /** Chamado antes de cada LEITURA ao Firestore. [automatica] = não foi pedida agora pela pessoa. */
    fun exigirLeitura(automatica: Boolean) {
        expirarJanela()
        if (leituraPermitida(automatica, _pausada.value, _janela.value, ultimoToque, System.currentTimeMillis(), emEntrada)) return
        evitou()
        throw EmPoupanca(if (_pausada.value) "Modo poupança: ligação em pausa — a mostrar o que está guardado." else "Actualização automática em pausa para poupar dados — toque para actualizar.")
    }

    /** Chamado antes de cada ESCRITA directa. Em pausa total, a operação vai para a fila local. */
    fun exigirEscrita() {
        expirarJanela()
        if (!_pausada.value || _janela.value || emEntrada) return
        evitou()
        throw EmPoupanca("Modo poupança: fica guardado no telemóvel e segue quando a ligação voltar.")
    }

    fun evitou() { _evitados.value += 1; if (_evitados.value % 10 == 0L) gravar() }
    fun leu(documentos: Int) { if (documentos <= 0) return; _lidos.value += documentos; if (_lidos.value % 20 < documentos) gravar() }
    fun escreveu(documentos: Int) { if (documentos <= 0) return; _escritos.value += documentos; gravar() }
    fun poupouDelta(documentos: Int) { if (documentos <= 0) return; _poupadosDelta.value += documentos; if (_poupadosDelta.value % 20 < documentos) gravar() }

    // ── Pausa total ──
    @Synchronized fun pausar() {
        if (_pausada.value) return
        _pausada.value = true; _desde.value = System.currentTimeMillis()
        prefs?.edit()?.putBoolean("pausada", true)?.putLong("desde", _desde.value ?: 0)?.apply()
    }

    @Synchronized fun ligar() {
        _janela.value = false; janelaAte = 0
        _pausada.value = false; _desde.value = null
        prefs?.edit()?.putBoolean("pausada", false)?.putLong("desde", 0)?.apply()
    }

    /** "Enviar agora": religa só o tempo de enviar o guardado e volta sozinho à pausa. */
    @Synchronized fun abrirJanela(ms: Long = 120_000L) {
        if (!_pausada.value) return
        janelaAte = System.currentTimeMillis() + ms; _janela.value = true
    }

    private fun expirarJanela() { if (_janela.value && System.currentTimeMillis() > janelaAte) { _janela.value = false; janelaAte = 0 } }

    val emPausa get() = _pausada.value && !_janela.value
}
