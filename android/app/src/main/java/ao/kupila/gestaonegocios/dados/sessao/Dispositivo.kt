package ao.kupila.gestaonegocios.dados.sessao

import android.content.Context
import android.os.Build
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ao.kupila.gestaonegocios.dados.seguranca.Cofre
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

internal val Context.dadosDispositivo by preferencesDataStore("kupila_dispositivo")

/** Conta reconhecida no dispositivo (`loadKnownDeviceAccounts`, HTML 10175): identidade mínima, nunca palavra-passe. */
@Serializable
data class ContaConhecida(val uid: String, val nome: String, val email: String, val papel: String, val ultimoAcesso: Long, val fotoLocal: String? = null,
    /** Há credencial segura para "Entrar" imediato (`hasSecureQuickLogin`); calculado, não gravado. */
    @kotlinx.serialization.Transient val rapido: Boolean = false)

/** Identificação da instalação (sessão exclusiva) e contas reconhecidas. */
@Singleton
class Dispositivo @Inject constructor(@param:ApplicationContext private val contexto: Context, private val cofre: Cofre) {
    private val chaveInstalacao = stringPreferencesKey("instalacao")
    private val chaveContas = stringPreferencesKey("contas_conhecidas")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun instalacaoId(): String {
        contexto.dadosDispositivo.data.first()[chaveInstalacao]?.let { return it }
        val novo = "inst-" + UUID.randomUUID().toString()
        contexto.dadosDispositivo.edit { it[chaveInstalacao] = novo }
        return novo
    }

    val rotulo: String get() = listOf(Build.MANUFACTURER.replaceFirstChar { it.uppercase() }, Build.MODEL).distinct().joinToString(" ").take(80)

    val contasConhecidas = contexto.dadosDispositivo.data.map { p ->
        (p[chaveContas]?.let { cofre.decifrar(it) }?.let { runCatching { json.decodeFromString(ListSerializer(ContaConhecida.serializer()), it) }.getOrNull() } ?: emptyList())
            .map { it.copy(rapido = p[chaveRapido(it.email)] != null) }
    }

    /**
     * Reinício rápido (`saveSecureQuickLoginCredential`, HTML): a palavra-passe fica cifrada pelo Cofre (chave no Android Keystore,
     * não exportável) e só serve para "Entrar" nesta instalação. Esquecer a conta apaga-a.
     */
    private fun chaveRapido(email: String) = stringPreferencesKey("rapido_" + java.security.MessageDigest.getInstance("SHA-256")
        .digest(email.trim().lowercase().toByteArray()).joinToString("") { "%02x".format(it) }.take(24))

    suspend fun guardarAcessoRapido(email: String, senha: String) { contexto.dadosDispositivo.edit { it[chaveRapido(email)] = cofre.cifrar(senha) } }
    suspend fun acessoRapido(email: String): String? = contexto.dadosDispositivo.data.first()[chaveRapido(email)]?.let { cofre.decifrar(it) }
    suspend fun removerAcessoRapido(email: String) { contexto.dadosDispositivo.edit { it.remove(chaveRapido(email)) } }

    suspend fun lembrar(c: ContaConhecida) {
        val lista = (listOf(c) + contasConhecidas.first().filter { it.uid != c.uid }).take(5)
        contexto.dadosDispositivo.edit { it[chaveContas] = cofre.cifrar(json.encodeToString(ListSerializer(ContaConhecida.serializer()), lista)) }
    }

    suspend fun esquecer(uid: String) {
        contasConhecidas.first().firstOrNull { it.uid == uid }?.let { removerAcessoRapido(it.email) }
        val lista = contasConhecidas.first().filter { it.uid != uid }
        contexto.dadosDispositivo.edit { it[chaveContas] = cofre.cifrar(json.encodeToString(ListSerializer(ContaConhecida.serializer()), lista)) }
    }
}
