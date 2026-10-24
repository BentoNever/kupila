package ao.kupila.gestaonegocios.dados.rascunhos

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap

private val Context.dadosRascunhoAuto by preferencesDataStore("kupila_rascunho_auto")

/**
 * RASCUNHO AUTOMÁTICO (perda zero): cada campo de formulário é guardado no aparelho enquanto se escreve (400 ms depois da
 * última tecla) e também no instante em que a app sai do ecrã — sobrevive a fechar a app, rodar o ecrã, falta de memória,
 * bloqueio da sessão ou falha de rede. Ao concluir a operação, o formulário é limpo ([limpar]).
 *
 * As senhas NUNCA passam por aqui. Os rascunhos do Anunciar têm o seu repositório próprio (com nuvem e fotografias).
 */
object RascunhoAuto {
    private val ambito = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val memoria = ConcurrentHashMap<String, String>()
    @Volatile private var carregado = false
    private val pendentes = ConcurrentHashMap<String, Job>()
    private lateinit var app: Context

    private fun iniciar(c: Context) {
        if (carregado) return
        synchronized(this) {
            if (carregado) return
            app = c.applicationContext
            // Ficheiro pequeno: lido uma vez (antes do 1.º desenho do formulário) para os valores aparecerem logo.
            runBlocking { app.dadosRascunhoAuto.data.first().asMap().forEach { (k, v) -> memoria[k.name] = v.toString() } }
            carregado = true
        }
    }

    fun ler(c: Context, chave: String): String? { iniciar(c); return memoria[chave] }

    fun escrever(c: Context, chave: String, valor: String, imediato: Boolean = false) {
        iniciar(c)
        if (memoria[chave] == valor) return
        if (valor.isEmpty()) memoria.remove(chave) else memoria[chave] = valor
        pendentes.remove(chave)?.cancel()
        pendentes[chave] = ambito.launch {
            if (!imediato) delay(400)
            app.dadosRascunhoAuto.edit { if (valor.isEmpty()) it.remove(stringPreferencesKey(chave)) else it[stringPreferencesKey(chave)] = valor }
            pendentes.remove(chave)
        }
    }

    /** Grava já tudo o que estiver à espera (app a sair do ecrã). */
    fun gravarJa() {
        if (!carregado) return
        val chaves = pendentes.keys.toList()
        if (chaves.isEmpty()) return
        chaves.forEach { pendentes.remove(it)?.cancel() }
        ambito.launch { app.dadosRascunhoAuto.edit { p -> chaves.forEach { k -> memoria[k]?.let { p[stringPreferencesKey(k)] = it } ?: p.remove(stringPreferencesKey(k)) } } }
    }

    /** Operação concluída: apaga os campos do formulário (todas as chaves com este prefixo). */
    fun limpar(c: Context, prefixo: String) {
        iniciar(c)
        val chaves = memoria.keys.filter { it.startsWith(prefixo) }
        chaves.forEach { memoria.remove(it); pendentes.remove(it)?.cancel() }
        if (chaves.isNotEmpty()) ambito.launch { app.dadosRascunhoAuto.edit { p -> chaves.forEach { p.remove(stringPreferencesKey(it)) } } }
    }

    fun existe(c: Context, prefixo: String): Boolean { iniciar(c); return memoria.keys.any { it.startsWith(prefixo) } }
}

/** Estado de texto com rascunho automático: devolve o valor guardado (se houver) e grava cada alteração. */
private class EstadoRascunho(private val c: Context, private val chave: String, inicial: String) : MutableState<String> {
    private val interno = androidx.compose.runtime.mutableStateOf(RascunhoAuto.ler(c, chave) ?: inicial, structuralEqualityPolicy<String>())
    override var value: String
        get() = interno.value
        set(v) { interno.value = v; RascunhoAuto.escrever(c, chave, v) }
    override fun component1() = value
    override fun component2(): (String) -> Unit = { value = it }
}

/**
 * Campo de formulário que nunca perde o que foi escrito. `chave` deve identificar o formulário e o campo,
 * por exemplo "cadastro.nome" ou "perfil.${uid}.bairro".
 */
@Composable
fun rememberRascunho(chave: String, inicial: String = ""): MutableState<String> {
    val c = LocalContext.current
    val estado = remember(chave) { EstadoRascunho(c, chave, inicial) }
    val ciclo = LocalLifecycleOwner.current
    DisposableEffect(ciclo) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) RascunhoAuto.gravarJa() }
        ciclo.lifecycle.addObserver(o)
        onDispose { ciclo.lifecycle.removeObserver(o) }
    }
    return estado
}
