package ao.kupila.gestaonegocios.dados.rascunhos

import ao.kupila.gestaonegocios.dados.ContaActiva
import ao.kupila.gestaonegocios.dados.local.BaseDadosConta
import ao.kupila.gestaonegocios.dados.local.RascunhoLocal
import ao.kupila.gestaonegocios.dados.remoto.Origem
import ao.kupila.gestaonegocios.dados.rede.Economia
import ao.kupila.gestaonegocios.dados.sessao.Dispositivo
import ao.kupila.gestaonegocios.dominio.rascunhos.PoliticaRascunhos
import ao.kupila.gestaonegocios.dominio.rascunhos.ResumoRascunho
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Documento de rascunho guardado num ficheiro privado da app (comprovativos até 2 MB não vão para o Room). */
data class DocRascunho(val nome: String, val mime: String, val tamanho: Long, val sha256: String, val caminho: String?, val fileId: String?)

/** Conteúdo completo de um rascunho (formulário no formato da origem + fotos inline + documentos). */
data class ConteudoRascunho(val snapshot: JSONObject, val fotos: List<String>, val sinteticas: Set<Int>, val docs: List<DocRascunho>, val edicaoDe: String?)

/**
 * Rascunhos (secção 13; `saveProductDraftNow`, HTML 9704): até 5 por utilizador, autosave local imediato e sincronização com
 * `productWorkDrafts` no esquema da origem (compatível com a web). Economia de quotas: a nuvem só é escrita ao sair do formulário,
 * não a cada tecla.
 */
@Singleton
class RascunhosRepositorio @Inject constructor(private val conta: ContaActiva, private val dispositivo: Dispositivo) {
    private val db get() = FirebaseFirestore.getInstance()
    private fun base(): BaseDadosConta? = conta.recursos.value?.base
    private fun uid() = conta.recursos.value?.perfil?.uid

    fun lista(): Flow<List<ResumoRascunho>> = base()?.rascunhos()?.observar()?.map { l ->
        l.map { ResumoRascunho(it.id, it.titulo.ifBlank { "Anúncio sem título" }, Instant.ofEpochMilli(it.actualizadoEm), it.percentagem, it.protegido, it.edicaoDeAnuncioId) }
    } ?: emptyFlow()

    fun novoId() = "PD-${System.currentTimeMillis()}-${(1000..9999).random()}"

    /** Autosave local (instantâneo, sem rede). */
    suspend fun guardar(id: String, titulo: String, percentagem: Int, c: ConteudoRascunho, protegido: Boolean = false) {
        val b = base() ?: return
        val antes = b.rascunhos().obter(id)
        if (antes?.removido == true) return   // descartado: não volta por autosave atrasado
        b.rascunhos().gravar(RascunhoLocal(id, titulo, paraJson(c).toString(), percentagem, System.currentTimeMillis(), (antes?.versao ?: 0) + 1,
            antes?.versaoSincronizada ?: 0, c.edicaoDe, protegido || antes?.protegido == true))
    }

    suspend fun obter(id: String): ConteudoRascunho? = base()?.rascunhos()?.obter(id)?.takeIf { !it.removido }?.let { deJson(JSONObject(it.conteudoJson)) }

    /** Rascunho que sairá da lista se criar um novo (para avisar antes). */
    suspend fun aSubstituirAoCriar(): ResumoRascunho? {
        val l = base()?.rascunhos()?.listar().orEmpty().map { ResumoRascunho(it.id, it.titulo, Instant.ofEpochMilli(it.actualizadoEm), it.percentagem, it.protegido, it.edicaoDeAnuncioId) }
        return PoliticaRascunhos.aSubstituir(l)
    }

    suspend fun remover(ids: List<String>) {
        val b = base() ?: return
        b.rascunhos().marcarRemovidos(ids, System.currentTimeMillis())
        val u = uid() ?: return
        ids.forEach { id ->
            b.rascunhos().obter(id)?.let { r -> deJson(JSONObject(r.conteudoJson)).docs.mapNotNull { it.caminho }.forEach { java.io.File(it).delete() } }
            runCatching { Economia.exigirEscrita(); db.document("productWorkDrafts/${docId(u, id)}").delete().await(); Economia.escreveu(1) }
        }
    }

    /** Concluído (publicado/pago): sai da lista de pendentes sem apagar o histórico do anúncio. */
    suspend fun concluir(id: String) = remover(listOf(id))

    private fun docId(u: String, id: String) = "${u}__$id".replace(Regex("[^A-Za-z0-9_.-]"), "_")

    /** Envia para a nuvem os rascunhos alterados desde a última sincronização (ao sair do formulário). */
    suspend fun sincronizarNuvem() {
        val b = base() ?: return
        val u = uid() ?: return
        if (Economia.emPausa) return
        for (r in b.rascunhos().porSincronizar()) {
            if (r.removido || r.versao <= r.versaoSincronizada) continue
            val c = deJson(JSONObject(r.conteudoJson))
            val s = c.snapshot
            fun v(k: String) = s.optJSONObject(k)?.optString("value").orEmpty()
            val doc = mapOf("kind" to "PRODUCT_LISTING_DRAFT", "draftId" to r.id, "userId" to u, "formSnapshot" to jsonParaMapa(s), "images" to c.fotos,
                "documents" to c.docs.filter { it.fileId != null }.map { mapOf("name" to it.nome, "type" to it.mime, "size" to it.tamanho, "fileId" to it.fileId, "storage" to "FIRESTORE_CHUNKS", "contentHash" to it.sha256) },
                "title" to r.titulo, "category" to (s.optJSONObject("__groups")?.optJSONObject("item_type")?.optString("value") ?: ""), "intent" to v("pub-intent"),
                "price" to (v("pub-price").toBigDecimalOrNull()?.toDouble() ?: 0.0), "reason" to "AUTOSAVE", "status" to if (r.protegido) "PAYMENT_PENDING" else "INCOMPLETE",
                "savedAt" to Origem.iso(r.actualizadoEm), "savedAtMs" to r.actualizadoEm, "sourceDeviceId" to dispositivo.instalacaoId(), "schemaVersion" to 5,
                "editingListingId" to c.edicaoDe, "source" to "ANDROID")
            runCatching {
                db.document("productWorkDrafts/${docId(u, r.id)}").set(doc).await()
                Economia.escreveu(1)
                b.rascunhos().gravar(r.copy(versaoSincronizada = r.versao))
            }
        }
    }

    /** Traz da nuvem rascunhos de outros dispositivos (ao abrir a lista; 1 leitura por rascunho, máx. 20). */
    suspend fun importarNuvem() {
        val b = base() ?: return
        val u = uid() ?: return
        runCatching { Economia.exigirLeitura(automatica = false) }.onFailure { return }
        val s = runCatching { db.collection("productWorkDrafts").whereEqualTo("userId", u).limit(20).get(Source.SERVER).await() }.getOrNull() ?: return
        Economia.leu(s.size().coerceAtLeast(1))
        for (d in s.documents) {
            val id = d.getString("draftId") ?: continue
            val local = b.rascunhos().obter(id)
            if (local?.removido == true) continue
            val ms = d.getLong("savedAtMs") ?: 0L
            if (local != null && local.actualizadoEm >= ms) continue
            val snap = JSONObject(mapaParaJson(d.get("formSnapshot") as? Map<*, *> ?: emptyMap<String, Any?>()).toString())
            val fotos = (d.get("images") as? List<*>)?.mapNotNull { it as? String }.orEmpty()
            val docs = (d.get("documents") as? List<*>)?.mapNotNull { m -> (m as? Map<*, *>)?.let { DocRascunho(it["name"] as? String ?: "documento", it["type"] as? String ?: "", (it["size"] as? Number)?.toLong() ?: 0,
                it["contentHash"] as? String ?: "", null, it["fileId"] as? String) } }.orEmpty()
            val titulo = d.getString("title") ?: ""
            b.rascunhos().gravar(RascunhoLocal(id, titulo, paraJson(ConteudoRascunho(snap, fotos, emptySet(), docs, d.getString("editingListingId"))).toString(),
                local?.percentagem ?: 40, ms, (local?.versao ?: 0) + 1, (local?.versao ?: 0) + 1, d.getString("editingListingId"), d.getString("status") == "PAYMENT_PENDING"))
        }
    }

    // ------------------------------------------------------------------ JSON
    private fun paraJson(c: ConteudoRascunho) = JSONObject().put("formSnapshot", c.snapshot).put("images", JSONArray(c.fotos)).put("sinteticas", JSONArray(c.sinteticas.toList()))
        .put("edicaoDe", c.edicaoDe ?: JSONObject.NULL)
        .put("documents", JSONArray(c.docs.map { JSONObject().put("name", it.nome).put("type", it.mime).put("size", it.tamanho).put("sha256", it.sha256)
            .put("path", it.caminho ?: JSONObject.NULL).put("fileId", it.fileId ?: JSONObject.NULL) }))

    private fun deJson(o: JSONObject): ConteudoRascunho {
        val f = o.optJSONArray("images") ?: JSONArray()
        val d = o.optJSONArray("documents") ?: JSONArray()
        val si = o.optJSONArray("sinteticas") ?: JSONArray()
        return ConteudoRascunho(o.optJSONObject("formSnapshot") ?: JSONObject(), (0 until f.length()).map { f.getString(it) }, (0 until si.length()).map { si.getInt(it) }.toSet(),
            (0 until d.length()).map { i -> d.getJSONObject(i).let { DocRascunho(it.optString("name"), it.optString("type"), it.optLong("size"), it.optString("sha256"),
                it.optString("path").takeIf { p -> p.isNotBlank() && p != "null" }, it.optString("fileId").takeIf { x -> x.isNotBlank() && x != "null" }) } },
            o.optString("edicaoDe").takeIf { it.isNotBlank() && it != "null" })
    }

    private fun jsonParaMapa(o: JSONObject): Map<String, Any?> = o.keys().asSequence().associateWith { k -> conv(o.get(k)) }
    private fun conv(v: Any?): Any? = when (v) { JSONObject.NULL -> null; is JSONObject -> jsonParaMapa(v); is JSONArray -> (0 until v.length()).map { conv(v.get(it)) }; else -> v }
    private fun mapaParaJson(m: Map<*, *>): JSONObject = JSONObject().apply { m.forEach { (k, v) -> put(k.toString(), inv(v)) } }
    private fun inv(v: Any?): Any? = when (v) { null -> JSONObject.NULL; is Map<*, *> -> mapaParaJson(v); is List<*> -> JSONArray(v.map { inv(it) }); else -> v }
}
