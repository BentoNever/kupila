package ao.kupila.gestaonegocios.dados.remoto

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Modo SPARK compatível com a app HTML em uso (D-27 + D-29): lê e escreve no Firestore real com o MESMO esquema
 * da origem e dentro das regras de produção (Kupila 1.0.188). Nada aqui exige alterar as regras publicadas.
 */
@Singleton
class CentralSpark @Inject constructor() : ServicoCentral {
    override val modo = "spark"
    private val db get() = FirebaseFirestore.getInstance()
    private val uid get() = FirebaseAuth.getInstance().currentUser?.uid ?: throw ErroCentral("unauthenticated", "Sessão inválida. Entre novamente.")

    companion object {
        /** `EXCLUSIVE_SESSION_STALE_MS` (HTML 10201). */
        const val SESSAO_VIVA_MS = 180_000L
        /** `KUPILA_POLICY_VERSION` (HTML 8874). */
        const val POLITICA = "2026.08.28-4"
    }

    override suspend fun meuPerfil(): PerfilRemoto? {
        val d = db.document("users/$uid").get().await()
        if (!d.exists()) return null
        return perfilDe(d.id, d.data ?: emptyMap())
    }

    /** Leitura tolerante dos perfis existentes (`normalizeUserRole`, HTML 9178: aliases de nome/papel). */
    fun perfilDe(id: String, m: Map<String, Any?>): PerfilRemoto {
        fun s(vararg k: String) = k.firstNotNullOfOrNull { (m[it] as? String)?.takeIf(String::isNotBlank) } ?: ""
        val foto = s("photoUrl").takeIf { it.startsWith("http") } ?: s("photoDataUrl", "photoInline").takeIf { it.startsWith("data:image") }
        return PerfilRemoto(id, Origem.papel(s("role", "profileRole", "perfil", "tipoPerfil")), s("status").ifEmpty { "ACTIVE" },
            s("name", "fullName", "nome", "nomeCompleto", "displayName"), s("email"), s("phonePrimary", "phone", "telefone"), s("phoneAlt"),
            s("province"), s("municipality"), s("neighborhood"), s("alertChannel").ifEmpty { "AUTO" }, foto,
            s("createdByAdminId").ifEmpty { null }?.takeIf { s("creationSource").uppercase() in setOf("ADMIN", "ADMIN_CREATED") || m["createdByAdminId"] != null },
            s("usagePolicyVersion").ifEmpty { null }, (m["version"] as? Number)?.toLong() ?: 1)
    }

    /** Cadastro público (`handleRegister`, HTML 11276-11300): perfil Firestore com foto inline para o Proprietário. */
    override suspend fun registar(p: PedidoRegisto) {
        val u = uid
        val ref = db.document("users/$u")
        if (ref.get().await().exists()) throw ErroCentral("already-exists", "Este cadastro já foi concluído. Entre com o seu e-mail.")
        val agora = Origem.agoraIso()
        val dono = p.papel == "PROPRIETARIO"
        val foto = if (dono) Origem.dataUrlJpeg(p.fotoJpeg ?: throw ErroCentral("invalid-argument", "A fotografia do Proprietário é obrigatória.")) else ""
        val perfil = mutableMapOf<String, Any?>(
            "role" to p.papel, "status" to "ACTIVE", "name" to p.nome.trim(),
            "email" to (FirebaseAuth.getInstance().currentUser?.email ?: p.email.trim().lowercase()),
            "phonePrimary" to p.telefonePrincipal, "phoneAlt" to p.telefoneAlternativo, "alertChannel" to if (dono) p.canal else "AUTO",
            "province" to p.provincia, "municipality" to p.municipio, "neighborhood" to p.bairro,
            "photoUrl" to "", "photoDataUrl" to foto, "photoStoragePath" to "", "photoPending" to false,
            "usagePolicyAccepted" to true, "usagePolicyVersion" to p.politicaVersao, "usagePolicyAcceptedAt" to agora,
            "createdAt" to agora, "updatedAt" to agora, "creationSource" to "SELF_REGISTER", "registeredVia" to "ANDROID",
        )
        if (dono) { perfil["photoInline"] = foto; perfil["photoInlineBytes"] = p.fotoJpeg!!.size; perfil["photoSource"] = "FIRESTORE_INLINE"; perfil["photoUpdatedAt"] = agora }
        ref.set(perfil).await()
        auditar("REGISTER", "USER", u, "Cadastro público como ${if (dono) "Proprietário" else "Cliente"} (Android).")
    }

    /** Sessão exclusiva no MESMO documento da web (`claimExclusiveSession`, HTML 10216-10226). */
    override suspend fun reivindicarSessao(instalacaoId: String, dispositivo: String, forcar: Boolean, sessaoActual: String?): ResultadoSessao {
        val u = uid
        val perfil = db.document("users/$u").get().await()
        val ref = db.document("activeSessions/$u")
        var tomada = false
        val r = db.runTransaction { tx ->
            val s = tx.get(ref)
            val agora = Origem.agoraIso()
            val visto = Origem.ms(s.get("lastSeenAt") ?: s.get("updatedAt") ?: s.get("startedAt")) ?: 0L
            val viva = s.exists() && (s.getString("status") ?: "ACTIVE") == "ACTIVE" && System.currentTimeMillis() - visto <= SESSAO_VIVA_MS
            val outroDispositivo = s.exists() && !s.getString("deviceId").isNullOrBlank() && s.getString("deviceId") != instalacaoId
            val mesma = s.exists() && s.getString("deviceId") == instalacaoId && s.getString("sessionId") == sessaoActual
            if (viva && outroDispositivo && !forcar) return@runTransaction ResultadoSessao.Conflito(s.getString("deviceLabel") ?: "Outro dispositivo", visto)
            tomada = viva && outroDispositivo
            val id = if (mesma) sessaoActual!! else "KSX-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(9)}"
            tx.set(ref, mapOf("userId" to u, "userName" to (perfil.getString("name") ?: "Utilizador"), "role" to Origem.papel(perfil.get("role")),
                "sessionId" to id, "deviceId" to instalacaoId, "deviceLabel" to dispositivo, "status" to "ACTIVE", "foreground" to true,
                "startedAt" to if (mesma) (s.getString("startedAt") ?: agora) else agora, "lastSeenAt" to agora, "updatedAt" to agora,
                "takenOverAt" to if (tomada) agora else s.getString("takenOverAt"),
                "previousDeviceId" to if (tomada) (s.getString("deviceId") ?: "") else (s.getString("previousDeviceId") ?: ""), "platform" to "ANDROID"))
            ResultadoSessao.Concedida(id)
        }.await()
        if (r is ResultadoSessao.Concedida && r.sessaoId != sessaoActual)
            auditar(if (tomada) "SESSION_TAKEOVER" else "LOGIN", "SESSION", u, if (tomada) "Sessão anterior encerrada em $dispositivo." else "Entrada no Android ($dispositivo).")
        return r
    }

    override suspend fun sinalSessao(sessaoId: String): Boolean {
        val ref = db.document("activeSessions/$uid")
        val s = ref.get().await()
        if (!s.exists() || s.getString("sessionId") != sessaoId) return false
        val agora = Origem.agoraIso()
        ref.set(mapOf("lastSeenAt" to agora, "updatedAt" to agora, "foreground" to true, "status" to "ACTIVE"), SetOptions.merge()).await()
        return true
    }

    override suspend fun terminarSessao(sessaoId: String) {
        val ref = db.document("activeSessions/$uid")
        val s = ref.get().await()
        if (s.getString("sessionId") != sessaoId) return
        val agora = Origem.agoraIso()
        // `releaseExclusiveSession`: marca a sessão como terminada sem apagar o histórico.
        ref.set(mapOf("status" to "ENDED", "endedAt" to agora, "updatedAt" to agora, "foreground" to false), SetOptions.merge()).await()
        auditar("LOGOUT", "SESSION", uid, "Sessão terminada no Android.")
    }

    /**
     * Presença por dispositivo (`writeKupilaPresence`, HTML 11466): heartbeat de 1.º plano, documento `{uid}_{dispositivo}`.
     * Mede actividade recente do app, não assiduidade (secção 29).
     */
    suspend fun presenca(instalacaoId: String, dispositivo: String, sessaoId: String, emPrimeiroPlano: Boolean, ecra: String) {
        runCatching {
            val u = uid
            val p = db.document("users/$u").get().await()
            val agora = Origem.agoraIso()
            val estado = if (emPrimeiroPlano) "ONLINE" else "AWAY"
            val m = mutableMapOf<String, Any?>("userId" to u, "name" to (p.getString("name") ?: "Utilizador"), "role" to Origem.papel(p.get("role")),
                "deviceId" to instalacaoId, "deviceLabel" to dispositivo, "sessionId" to sessaoId, "status" to estado, "foreground" to emPrimeiroPlano,
                "currentTab" to ecra, "lastSeenAt" to agora, "updatedAt" to agora, "platform" to "ANDROID")
            if (emPrimeiroPlano) m["lastActiveAt"] = agora
            db.document("appPresence/${Origem.idSeguro(u)}_${Origem.idSeguro(instalacaoId)}").set(m, SetOptions.merge()).await()
        }
    }

    /** Auditoria no formato da origem (`recordOperation`, HTML 12397): permitido pelas regras para o próprio actor. */
    suspend fun auditar(accao: String, entidade: String, entidadeId: String, detalhes: String = "") {
        runCatching {
            val u = uid
            val p = db.document("users/$u").get().await()
            val id = Origem.id("AUD")
            db.document("auditLog/$id").set(mapOf("createdAt" to Origem.agoraIso(), "actorId" to u, "actorName" to (p.getString("name") ?: "Utilizador"),
                "actorRole" to Origem.papel(p.get("role")), "action" to accao, "entity" to entidade, "entityId" to entidadeId, "details" to detalhes,
                "platform" to "ANDROID")).await()
        }
    }
}
