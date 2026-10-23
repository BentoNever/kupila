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
data class ContaConhecida(val uid: String, val nome: String, val email: String, val papel: String, val ultimoAcesso: Long, val fotoLocal: String? = null)

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
        p[chaveContas]?.let { cofre.decifrar(it) }?.let { runCatching { json.decodeFromString(ListSerializer(ContaConhecida.serializer()), it) }.getOrNull() } ?: emptyList()
    }

    suspend fun lembrar(c: ContaConhecida) {
        val lista = (listOf(c) + contasConhecidas.first().filter { it.uid != c.uid }).take(5)
        contexto.dadosDispositivo.edit { it[chaveContas] = cofre.cifrar(json.encodeToString(ListSerializer(ContaConhecida.serializer()), lista)) }
    }

    suspend fun esquecer(uid: String) {
        val lista = contasConhecidas.first().filter { it.uid != uid }
        contexto.dadosDispositivo.edit { it[chaveContas] = cofre.cifrar(json.encodeToString(ListSerializer(ContaConhecida.serializer()), lista)) }
    }
}
