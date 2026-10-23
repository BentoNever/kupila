package ao.kupila.gestaonegocios.dados.remoto

import ao.kupila.gestaonegocios.BuildConfig
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/** Perfil tal como o titular o lê (nomes de campos da origem — D-20). */
data class PerfilRemoto(
    val uid: String, val role: String, val status: String, val name: String, val email: String, val phonePrimary: String,
    val phoneAlt: String, val province: String, val municipality: String, val neighborhood: String, val alertChannel: String,
    val photoPath: String?, val createdBy: String?, val policyVersion: String?, val version: Long,
)

sealed interface ResultadoSessao {
    data class Concedida(val sessaoId: String) : ResultadoSessao
    data class Conflito(val dispositivo: String, val vistoEm: Long?) : ResultadoSessao
}

/** Pedido de cadastro público (já validado no APK; revalidado pelas regras/Central). */
data class PedidoRegisto(
    val papel: String, val nome: String, val email: String, val telefonePrincipal: String, val telefoneAlternativo: String,
    val provincia: String, val municipio: String, val bairro: String, val canal: String, val politicaVersao: String,
    val politicaLidaAteFim: Boolean, val fotoJpeg: ByteArray?,
)

/**
 * O "Central" visto pela app. Duas formas, o mesmo comportamento para os ecrãs (padrão `Central` do PDV Digital):
 *  · SPARK (vigente, D-27): Firestore/Storage directos, com as regras como autoridade;
 *  · API (Blaze, futuro): Callable Functions de backend/functions.
 */
interface ServicoCentral {
    val modo: String
    suspend fun meuPerfil(): PerfilRemoto?
    suspend fun registar(p: PedidoRegisto)
    suspend fun reivindicarSessao(instalacaoId: String, dispositivo: String, forcar: Boolean, sessaoActual: String?): ResultadoSessao
    /** false = a sessão já não é a vigente (substituída noutro dispositivo). */
    suspend fun sinalSessao(sessaoId: String): Boolean
    suspend fun terminarSessao(sessaoId: String)
}

@Singleton
class SeletorCentral @Inject constructor(private val spark: Provider<CentralSpark>, private val api: Provider<CentralApi>) {
    /** `BuildConfig.MODO`: "spark" (plano gratuito) ou "api" (Blaze). Os ecrãs não mudam. */
    fun actual(): ServicoCentral = if (BuildConfig.MODO == "api") api.get() else spark.get()
}
