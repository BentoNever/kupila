package ao.kupila.gestaonegocios.dados.remoto

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Modo SPARK (D-27): operações directas no Firestore/Storage. As regras (`backend/firestore.rules`) validam papel,
 * estado, campos, versões e hora do servidor; o que o APK escreve aqui é exactamente o que as regras aceitam.
 */
@Singleton
class CentralSpark @Inject constructor() : ServicoCentral {
    override val modo = "spark"
    private val db get() = FirebaseFirestore.getInstance()
    private val uid get() = FirebaseAuth.getInstance().currentUser?.uid ?: throw ErroCentral("unauthenticated", "Sessão inválida. Entre novamente.")

    companion object {
        /** Parâmetros da origem (HTML 10200-10201). */
        const val SESSAO_VIVA_MS = 180_000L
    }

    override suspend fun meuPerfil(): PerfilRemoto? {
        val d = db.document("users/$uid").get().await()
        if (!d.exists()) return null
        fun s(k: String) = d.getString(k).orEmpty()
        return PerfilRemoto(d.id, s("role"), s("status"), s("name"), s("email"), s("phonePrimary"), s("phoneAlt"), s("province"),
            s("municipality"), s("neighborhood"), s("alertChannel").ifEmpty { "AUTO" }, d.getString("photoPath"), d.getString("createdBy"),
            (d.get("policyAcceptance") as? Map<*, *>)?.get("version") as? String, d.getLong("version") ?: 1)
    }

    override suspend fun registar(p: PedidoRegisto) {
        val u = uid
        val foto = if (p.papel == "PROPRIETARIO") {
            val bytes = p.fotoJpeg ?: throw ErroCentral("invalid-argument", "A fotografia do Proprietário é obrigatória.")
            val caminho = "privado/perfis/$u/foto.jpg"
            FirebaseStorage.getInstance().reference.child(caminho).putBytes(bytes, StorageMetadata.Builder().setContentType("image/jpeg").build()).await()
            caminho
        } else null
        val ref = db.document("users/$u")
        if (ref.get().await().exists()) throw ErroCentral("already-exists", "Este cadastro já foi concluído. Entre com o seu e-mail.")
        ref.set(mapOf(
            "id" to u, "role" to p.papel, "status" to "ACTIVE", "email" to p.email.trim().lowercase(), "createdBy" to null,
            "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp(), "version" to 1,
            "policyAcceptance" to mapOf("version" to p.politicaVersao, "purpose" to "REGISTER", "acceptedAt" to FieldValue.serverTimestamp(),
                "readToEnd" to p.politicaLidaAteFim),
            "name" to p.nome.trim(), "phonePrimary" to p.telefonePrincipal, "phoneAlt" to p.telefoneAlternativo,
            "province" to p.provincia, "municipality" to p.municipio, "neighborhood" to p.bairro, "alertChannel" to p.canal, "photoPath" to foto,
        )).await()
        auditar("REGISTER", "USER", u, depois = mapOf("role" to p.papel))
    }

    override suspend fun reivindicarSessao(instalacaoId: String, dispositivo: String, forcar: Boolean, sessaoActual: String?): ResultadoSessao {
        val u = uid
        val ref = db.document("activeSessions/$u")
        val papel = db.document("users/$u").get().await().getString("role")
        var tomada = false
        val r = db.runTransaction { tx ->
            val s = tx.get(ref)
            val viva = s.exists() && s.getString("status") == "ACTIVE" &&
                (System.currentTimeMillis() - (s.getTimestamp("lastSeenAt")?.toDate()?.time ?: 0L)) <= SESSAO_VIVA_MS
            val mesma = s.exists() && s.getString("installId") == instalacaoId && s.getString("sessionId") == sessaoActual
            when {
                mesma -> {
                    tx.update(ref, mapOf("lastSeenAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp(), "deviceLabel" to dispositivo, "status" to "ACTIVE"))
                    ResultadoSessao.Concedida(sessaoActual!!)
                }
                viva && s.getString("installId") != instalacaoId && !forcar ->
                    ResultadoSessao.Conflito(s.getString("deviceLabel") ?: "Outro dispositivo", s.getTimestamp("lastSeenAt")?.toDate()?.time)
                else -> {
                    tomada = viva && s.getString("installId") != instalacaoId
                    val id = "KSX-" + UUID.randomUUID().toString().replace("-", "")
                    tx.set(ref, mapOf("userId" to u, "role" to papel, "sessionId" to id, "installId" to instalacaoId, "deviceLabel" to dispositivo,
                        "status" to "ACTIVE", "startedAt" to FieldValue.serverTimestamp(), "lastSeenAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(), "takenOverAt" to if (tomada) FieldValue.serverTimestamp() else null,
                        "previousInstallId" to if (s.exists()) s.getString("installId") else null, "foreground" to true))
                    ResultadoSessao.Concedida(id)
                }
            }
        }.await()
        if (r is ResultadoSessao.Concedida && r.sessaoId != sessaoActual)
            auditar(if (tomada) "SESSION_TAKEOVER" else "SESSION_START", "SESSION", u, sessao = r.sessaoId)
        return r
    }

    override suspend fun sinalSessao(sessaoId: String): Boolean {
        val ref = db.document("activeSessions/$uid")
        return db.runTransaction { tx ->
            val s = tx.get(ref)
            if (!s.exists() || s.getString("sessionId") != sessaoId || s.getString("status") != "ACTIVE") false
            else { tx.update(ref, mapOf("lastSeenAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp(), "foreground" to true)); true }
        }.await()
    }

    override suspend fun terminarSessao(sessaoId: String) {
        val ref = db.document("activeSessions/$uid")
        val s = ref.get().await()
        if (s.getString("sessionId") != sessaoId) return
        ref.set(mapOf("status" to "ENDED", "endedAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge()).await()
        auditar("SESSION_END", "SESSION", uid, sessao = sessaoId)
    }

    /** Auditoria só de acréscimo, com a hora do servidor (regra `auditLog`). Falha de auditoria não desfaz a operação. */
    suspend fun auditar(accao: String, entidade: String, entidadeId: String, antes: Map<String, Any?>? = null, depois: Map<String, Any?>? = null,
                        motivo: String = "", sessao: String? = null) {
        runCatching {
            db.collection("auditLog").document().set(mapOf("actorId" to uid, "action" to accao, "entityType" to entidade, "entityId" to entidadeId,
                "before" to antes, "after" to depois, "reason" to motivo, "result" to "OK", "createdAt" to FieldValue.serverTimestamp(), "sessionId" to sessao)).await()
        }
    }
}
