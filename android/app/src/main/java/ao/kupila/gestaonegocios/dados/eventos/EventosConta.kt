package ao.kupila.gestaonegocios.dados.eventos

import ao.kupila.gestaonegocios.dados.local.BaseDadosConta
import ao.kupila.gestaonegocios.dados.local.MetaLocal
import ao.kupila.gestaonegocios.dados.local.NotificacaoLocal
import ao.kupila.gestaonegocios.dados.local.RegistoLocal
import ao.kupila.gestaonegocios.dados.remoto.MensagensErro
import ao.kupila.gestaonegocios.dados.remoto.Origem
import ao.kupila.gestaonegocios.dados.rede.Economia
import ao.kupila.gestaonegocios.dados.rede.EmPoupanca
import ao.kupila.gestaonegocios.dados.sessao.OuvintesSessao
import ao.kupila.gestaonegocios.dominio.perfis.Papel
import ao.kupila.gestaonegocios.dominio.perfis.Perfil
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

/** Documento remoto genérico (id + campos no esquema da origem). */
data class Registo(val id: String, val d: Map<String, Any?>) {
    fun s(k: String) = d[k] as? String ?: ""
    fun n(k: String) = Origem.numero(d[k])?.toDouble() ?: 0.0
    fun ms(k: String) = Origem.ms(d[k])
    val estado get() = s("status")
}

/** Conjunto lógico de documentos de uma conta. [delta] = colecção grande lida por `updatedAt` (só Administrador). */
enum class Conjunto(val colecao: String, val delta: Boolean = false) {
    RESERVAS_CLIENTE("reservations"), RESERVAS_DONO("reservations"), RESERVAS_TODAS("reservations", true),
    INTENCOES("paymentIntents"), INTENCOES_TODAS("paymentIntents", true), SUBMISSOES("paymentSubmissions"),
    REEMBOLSOS_CLIENTE("refundRequests"), REEMBOLSOS_DONO("refundRequests"), REEMBOLSOS_TODOS("refundRequests", true),
    AUTORIZACOES("authorizationRequests"), INTERESSE("productInterest"), TESTEMUNHOS_PUBLICOS("testimonials"), MEUS_TESTEMUNHOS("testimonials"),
    TESTEMUNHOS_PENDENTES("testimonials"), ALERTAS("ownerAlerts"), RASCUNHOS_PAGAMENTO("listingDrafts"), RASCUNHOS_TRABALHO("productWorkDrafts"),
    LIBERACOES("payoutReleaseRequests"), PAGAMENTOS_REEMBOLSO("refundPayoutRequests", true), UTILIZADORES("users", true),
    PRESENCA("appPresence"), AUDITORIA("auditLog", true), CONFIANCA("platformMetrics"),
}

/**
 * Eventos de UMA conta com ECONOMIA DE QUOTAS (docs/principios-produto.md): nada fica a escutar em segundo plano. Os ecrãs mostram
 * o cache Room da conta (offline, instantâneo) e pedem [actualizar] ao abrir/puxar; colecções grandes do Administrador são lidas
 * por delta (`updatedAt`). Só um documento pode ser seguido em tempo real enquanto o ecrã que precisa dele está visível ([seguir]).
 * As notificações são derivadas das mudanças de estado (chave idempotente) — D-29.
 */
class EventosConta(private val base: BaseDadosConta, private val perfil: Perfil) {
    private val db get() = FirebaseFirestore.getInstance()
    private val ambito = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val admin = perfil.papel == Papel.ADMINISTRADOR
    private val dono = perfil.papel == Papel.PROPRIETARIO
    private val u = perfil.uid
    private val mutex = Mutex()
    private val _aSincronizar = MutableStateFlow(false); val aSincronizar: StateFlow<Boolean> = _aSincronizar.asStateFlow()
    private val _erro = MutableStateFlow<String?>(null); val erro: StateFlow<String?> = _erro.asStateFlow()
    private val _actualizadoEm = MutableStateFlow<Long?>(null); val actualizadoEm: StateFlow<Long?> = _actualizadoEm.asStateFlow()

    // ------------------------------------------------------------------ consultas permitidas pelas regras para cada perfil
    private fun consulta(c: Conjunto): Query? = when (c) {
        Conjunto.RESERVAS_CLIENTE -> db.collection("reservations").whereEqualTo("userId", u)
        Conjunto.RESERVAS_DONO -> if (dono || admin) db.collection("reservations").whereEqualTo("ownerId", u) else null
        Conjunto.RESERVAS_TODAS -> if (admin) db.collection("reservations") else null
        Conjunto.INTENCOES -> db.collection("paymentIntents").whereEqualTo("userId", u)
        Conjunto.INTENCOES_TODAS -> if (admin) db.collection("paymentIntents") else null
        Conjunto.SUBMISSOES -> db.collection("paymentSubmissions").whereEqualTo("userId", u)
        Conjunto.REEMBOLSOS_CLIENTE -> db.collection("refundRequests").whereEqualTo("clientId", u)
        Conjunto.REEMBOLSOS_DONO -> if (dono || admin) db.collection("refundRequests").whereEqualTo("ownerId", u) else null
        Conjunto.REEMBOLSOS_TODOS -> if (admin) db.collection("refundRequests") else null
        Conjunto.AUTORIZACOES -> when { admin -> db.collection("authorizationRequests").limit(300); dono -> db.collection("authorizationRequests").whereEqualTo("ownerId", u); else -> null }
        Conjunto.INTERESSE -> if (dono) db.collection("productInterest").whereEqualTo("ownerId", u) else null
        Conjunto.TESTEMUNHOS_PUBLICOS -> db.collection("testimonials").whereEqualTo("status", "APPROVED").limit(100)
        Conjunto.MEUS_TESTEMUNHOS -> db.collection("testimonials").whereEqualTo("authorId", u)
        Conjunto.TESTEMUNHOS_PENDENTES -> if (admin) db.collection("testimonials").whereEqualTo("status", "PENDING") else null
        Conjunto.ALERTAS -> if (dono) db.collection("ownerAlerts").whereEqualTo("ownerId", u) else null
        Conjunto.RASCUNHOS_PAGAMENTO -> when { admin -> db.collection("listingDrafts").limit(300); dono -> db.collection("listingDrafts").whereEqualTo("ownerId", u); else -> null }
        Conjunto.RASCUNHOS_TRABALHO -> when { admin -> db.collection("productWorkDrafts").limit(300); dono -> db.collection("productWorkDrafts").whereEqualTo("userId", u); else -> null }
        Conjunto.LIBERACOES -> when { admin -> db.collection("payoutReleaseRequests").limit(300); dono -> db.collection("payoutReleaseRequests").whereEqualTo("ownerId", u); else -> null }
        Conjunto.PAGAMENTOS_REEMBOLSO -> if (admin) db.collection("refundPayoutRequests") else null
        Conjunto.UTILIZADORES -> if (admin) db.collection("users") else null
        Conjunto.PRESENCA -> if (admin) db.collection("appPresence").limit(500) else null
        Conjunto.AUDITORIA -> if (admin) db.collection("auditLog") else null
        Conjunto.CONFIANCA -> db.collection("platformMetrics")
    }

    /** Conjuntos que a Home/Proprietário/Reservas precisam ao abrir o app (pequenos, por utilizador). */
    val essenciais: List<Conjunto> get() = buildList {
        add(Conjunto.RESERVAS_CLIENTE); add(Conjunto.INTENCOES); add(Conjunto.SUBMISSOES); add(Conjunto.REEMBOLSOS_CLIENTE); add(Conjunto.CONFIANCA)
        if (dono || admin) { add(Conjunto.RESERVAS_DONO); add(Conjunto.REEMBOLSOS_DONO); add(Conjunto.AUTORIZACOES); add(Conjunto.RASCUNHOS_PAGAMENTO); add(Conjunto.LIBERACOES) }
        if (dono) { add(Conjunto.INTERESSE); add(Conjunto.ALERTAS) }
    }
    /** Filas do Administrador (pendências) — lidas por delta. */
    val administracao: List<Conjunto> get() = if (!admin) emptyList() else listOf(Conjunto.INTENCOES_TODAS, Conjunto.REEMBOLSOS_TODOS, Conjunto.RESERVAS_TODAS,
        Conjunto.PAGAMENTOS_REEMBOLSO, Conjunto.TESTEMUNHOS_PENDENTES, Conjunto.AUTORIZACOES, Conjunto.RASCUNHOS_PAGAMENTO, Conjunto.LIBERACOES)

    // ------------------------------------------------------------------ cache local (Room) por conjunto
    private fun chave(c: Conjunto) = "ev:${c.name}"

    fun observar(c: Conjunto): Flow<List<Registo>> = base.registos().observar(chave(c)).map { l -> l.mapNotNull { r -> runCatching { Registo(r.id, deJson(JSONObject(r.json))) }.getOrNull() } }
    fun estado(c: Conjunto): StateFlow<List<Registo>> = observar(c).stateIn(ambito, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Lê do servidor os conjuntos indicados. Conjuntos por utilizador: leitura completa (poucos documentos). Conjuntos grandes do
     * Administrador: só os alterados desde a última vez (`updatedAt`). [automatica] respeita o modo poupança.
     */
    suspend fun actualizar(conjuntos: Collection<Conjunto>, automatica: Boolean = false): Result<Unit> = mutex.withLock {
        runCatching {
            Economia.exigirLeitura(automatica)
            // Quota: uma actualização automática ignora o que foi lido há pouco (vários ecrãs abertos seguidos = 1 leitura).
            // O gesto "deslizar para actualizar" e os botões explícitos (automatica = false) lêem sempre.
            val agora = System.currentTimeMillis()
            val alvo = conjuntos.distinct().filter { c -> !automatica || agora - (lidoEm[c] ?: 0L) >= intervaloMinimo(c) }
            if (alvo.isEmpty()) return@runCatching
            _aSincronizar.value = true; _erro.value = null
            alvo.forEach { lidoEm[it] = agora }
            alvo.map { c -> ambito.async { runCatching { lerConjunto(c) }.exceptionOrNull() } }.awaitAll()
                .filterNotNull().firstOrNull { it !is EmPoupanca }?.let { _erro.value = MensagensErro.de(it) }
            _actualizadoEm.value = System.currentTimeMillis()
            base.meta().gravar(MetaLocal("eventos", "ok", System.currentTimeMillis()))
        }.also { _aSincronizar.value = false }.onFailure { if (it !is EmPoupanca) _erro.value = MensagensErro.de(it) }
    }

    /** Última leitura de cada conjunto nesta sessão (memória — custo zero). */
    private val lidoEm = java.util.concurrent.ConcurrentHashMap<Conjunto, Long>()

    /** Intervalo mínimo entre leituras AUTOMÁTICAS: dados que mudam pouco esperam mais. */
    private fun intervaloMinimo(c: Conjunto): Long = when (c) {
        Conjunto.CONFIANCA, Conjunto.TESTEMUNHOS_PUBLICOS -> 6 * 3_600_000L
        Conjunto.UTILIZADORES, Conjunto.AUDITORIA, Conjunto.PRESENCA, Conjunto.MEUS_TESTEMUNHOS -> 5 * 60_000L
        else -> 90_000L
    }

    private suspend fun lerConjunto(c: Conjunto) {
        val q = consulta(c) ?: return
        val metaDelta = "delta:${c.name}"
        val ultimo = if (c.delta) base.meta().obter(metaDelta)?.valor else null
        val consulta = when {
            c == Conjunto.AUDITORIA && ultimo != null -> q.whereGreaterThan("createdAt", ultimo).orderBy("createdAt").limit(300)
            c == Conjunto.AUDITORIA -> q.orderBy("createdAt", Query.Direction.DESCENDING).limit(200)
            c.delta && ultimo != null -> q.whereGreaterThan("updatedAt", ultimo).orderBy("updatedAt").limit(500)
            c.delta -> q.limit(600)
            else -> q
        }
        val s = consulta.get(Source.SERVER).await()
        Economia.leu(s.size().coerceAtLeast(1))
        val docs = s.documents
        val linhas = docs.map { RegistoLocal(chave(c), it.id, paraJson(it.data ?: emptyMap()).toString(), Origem.ms(it.get("updatedAt") ?: it.get("createdAt") ?: it.get("requestedAt")) ?: 0) }
        if (!c.delta) {
            // Conjunto completo: o que já não vem saiu do âmbito (ex.: estado mudou) — mantém-se só o que veio.
            if (linhas.isEmpty()) base.registos().manterSo(chave(c), listOf("§nenhum§")) else base.registos().manterSo(chave(c), linhas.map { it.id })
        } else Economia.poupouDelta(100)
        if (linhas.isNotEmpty()) base.registos().gravar(linhas)
        if (c.delta) docs.mapNotNull { (it.get(if (c == Conjunto.AUDITORIA) "createdAt" else "updatedAt") as? String) }.maxOrNull()
            ?.let { base.meta().gravar(MetaLocal(metaDelta, it, System.currentTimeMillis())) }
        derivarNotificacoes(c, docs)
    }

    /**
     * Segue UM documento em tempo real enquanto o ecrã está visível (ex.: checkout à espera da confirmação). Devolve a função para parar.
     * Custa 1 leitura por mudança — nunca fica activo em segundo plano.
     */
    fun seguir(caminho: String, aoMudar: (Registo?) -> Unit): () -> Unit {
        if (Economia.emPausa) return {}
        val r: ListenerRegistration = db.document(caminho).addSnapshotListener { s, _ -> s?.let { Economia.leu(1); aoMudar(if (it.exists()) Registo(it.id, it.data ?: emptyMap()) else null) } }
        return { r.remove() }
    }

    /** Lê um documento sob pedido (ex.: evidência, rascunho), contando a quota. */
    suspend fun ler(caminho: String): DocumentSnapshot { Economia.exigirLeitura(false); return db.document(caminho).get(Source.SERVER).await().also { Economia.leu(1) } }

    // ------------------------------------------------------------------ notificações derivadas (idempotentes)
    private fun nota(chave: String, titulo: String, msg: String, cat: String, sev: String, accao: String, entidade: String, em: Long?) =
        NotificacaoLocal("NTF-" + Origem.idSeguro(chave), chave, titulo, msg, cat, sev, accao, entidade, em ?: System.currentTimeMillis(), false, null)

    private suspend fun derivarNotificacoes(c: Conjunto, docs: List<DocumentSnapshot>) {
        val baseline = base.meta().obter("notif-baseline")?.em ?: System.currentTimeMillis().also { base.meta().gravar(MetaLocal("notif-baseline", "ok", it)) }
        val novas = docs.mapNotNull { d -> val r = Registo(d.id, d.data ?: emptyMap()); notificacao(c, r) }
            .map { if (it.criadaEm < baseline) it.copy(lida = true) else it }
        if (novas.isEmpty()) return
        // Não reabre como "não lida" uma notificação já existente (mesma chave).
        val existentes = novas.mapNotNull { n -> base.notificacoes().obter(n.id)?.id }.toSet()
        base.notificacoes().gravar(novas.filter { it.id !in existentes })
    }

    private fun notificacao(c: Conjunto, r: Registo): NotificacaoLocal? = when (c) {
        Conjunto.RESERVAS_CLIENTE -> when (r.estado) {
            "ACTIVE" -> nota("RES:${r.id}:ACTIVE", "Reserva confirmada", "A reserva de “${r.s("listingTitle")}” está activa. O OTP está no comprovativo.", "RESERVATION", "INFO", "OPEN_RESERVATION", r.id, r.ms("reservedAt"))
            "COMPLETED" -> nota("RES:${r.id}:COMPLETED", "Negócio concluído", "O Proprietário confirmou o negócio de “${r.s("listingTitle")}”. Partilhe a sua experiência.", "RESERVATION", "INFO", "OPEN_RESERVATION", r.id, r.ms("completedAt"))
            "EXPIRED" -> nota("RES:${r.id}:EXPIRED", "Reserva expirada", "A reserva de “${r.s("listingTitle")}” expirou. Se houve vistoria sem negócio, pode pedir reembolso.", "RESERVATION", "AVISO", "OPEN_RESERVATION", r.id, r.ms("expiredAt"))
            "REFUNDED" -> nota("RES:${r.id}:REFUNDED", "Reembolso confirmado", "O reembolso de “${r.s("listingTitle")}” foi confirmado.", "MONETIZATION", "INFO", "OPEN_RESERVATION", r.id, r.ms("refundedAt"))
            else -> null
        }
        Conjunto.INTENCOES -> when (r.estado) {
            "PUBLISHED" -> nota("PAY:${r.id}:PUBLISHED", "Anúncio publicado", "O pagamento da publicação de “${r.s("listingTitle")}” foi confirmado.", "MONETIZATION", "INFO", "OPEN_LISTING", r.s("listingId"), r.ms("confirmedAt"))
            "REJECTED_PAYMENT", "REJECTED_REFERENCE" -> nota("PAY:${r.id}:${r.estado}", "Pagamento não confirmado", r.s("rejectionReason").ifBlank { "Reveja o comprovativo submetido." }, "MONETIZATION", "AVISO", "OPEN_PAYMENT", r.id, r.ms("reviewedAt"))
            else -> null
        }
        Conjunto.REEMBOLSOS_CLIENTE -> when (r.estado) {
            "APPROVED_REFUND_PENDING" -> nota("RFD:${r.id}:APPROVED", "Reembolso aprovado", "Aprovado; aguarda a confirmação financeira.", "MONETIZATION", "INFO", "OPEN_RESERVATION", r.s("reservationId"), r.ms("adminDecisionAt"))
            "REJECTED" -> nota("RFD:${r.id}:REJECTED", "Reembolso recusado", "A solicitação foi recusada após análise.", "MONETIZATION", "AVISO", "OPEN_RESERVATION", r.s("reservationId"), r.ms("adminDecisionAt"))
            else -> null
        }
        Conjunto.RESERVAS_DONO -> when {
            r.estado == "ACTIVE" -> nota("RESD:${r.id}:ACTIVE", "Produto reservado", "“${r.s("listingTitle")}” foi reservado. Combine a vistoria; valide o OTP no fecho.", "RESERVATION", "INFO", "OPEN_RESERVATION", r.id, r.ms("reservedAt"))
            r.s("payoutStatus") == "RELEASED" -> nota("RESD:${r.id}:RELEASED", "Liberação confirmada", "O líquido do sinal de “${r.s("listingTitle")}” foi liberado.", "MONETIZATION", "INFO", "OPEN_RESERVATION", r.id, r.ms("payoutReleasedAt"))
            else -> null
        }
        Conjunto.REEMBOLSOS_DONO -> if (r.estado == "PENDING_OWNER_CONFIRMATION") nota("RFDD:${r.id}", "Confirmação de reembolso pendente",
            "${r.s("clientName").ifBlank { "O Cliente" }} declarou que o negócio de “${r.s("listingTitle")}” não foi fechado. Confirme ou conteste.", "MONETIZATION", "AVISO", "OPEN_RESERVATION", r.s("reservationId"), r.ms("createdAt")) else null
        Conjunto.AUTORIZACOES -> when {
            !admin && r.estado == "CODE_SENT" -> nota("AUT:${r.id}:CODE", "Código de autorização", "Código ${r.s("code")} para ${if (r.s("action") == "DELETE") "excluir" else "desanunciar"} “${r.s("listingTitle")}”. Válido por 30 minutos.", "ADMIN", "IMPORTANTE", "OPEN_AUTHORIZATION", r.id, r.ms("generatedAt"))
            admin && r.estado == "PENDING_ADMIN" -> nota("AUTA:${r.id}", "Pedido de autorização", "${r.s("ownerName")} pediu ${if (r.s("action") == "DELETE") "exclusão" else "desanúncio"} de “${r.s("listingTitle")}”.", "ADMIN", "AVISO", "OPEN_PENDING", r.id, r.ms("requestedAt"))
            else -> null
        }
        Conjunto.ALERTAS -> nota("ALR:${r.id}", r.s("title").ifBlank { "Alerta do produto" }, r.s("message"), "INTEREST", "INFO", "OPEN_LISTING", r.s("listingId"), r.ms("createdAt"))
        Conjunto.INTENCOES_TODAS -> if (r.estado in setOf("VERIFYING", "RECEIPT_MATCHED")) nota("PAYA:${r.id}:${r.estado}", "Comprovativo a confirmar",
            "${r.s("userName")} · ${if (r.s("type") == "LISTING") "publicação" else "sinal"} de “${r.s("listingTitle")}”.", "MONETIZATION", "AVISO", "OPEN_PENDING", r.id, r.ms("receiptSubmittedAt")) else null
        Conjunto.REEMBOLSOS_TODOS -> if (r.estado in setOf("OWNER_CONFIRMED", "CONTESTED_PENDING_ADMIN")) nota("RFDA:${r.id}:${r.estado}", "Reembolso para decisão",
            "Reserva ${r.s("reservationId")} · ${r.s("listingTitle")}.", "MONETIZATION", "AVISO", "OPEN_PENDING", r.id, r.ms("ownerReviewedAt")) else null
        else -> null
    }

    fun parar() { /* sem escutas permanentes; seguimentos são parados pelos próprios ecrãs */ }
    init { OuvintesSessao.registar(::parar) }

    // ------------------------------------------------------------------ (de)serialização genérica dos campos
    private fun paraJson(m: Map<String, Any?>): JSONObject = JSONObject().apply { m.forEach { (k, v) -> put(k, valor(v)) } }
    private fun valor(v: Any?): Any? = when (v) {
        null -> JSONObject.NULL
        is Map<*, *> -> JSONObject().apply { v.forEach { (k, x) -> put(k.toString(), valor(x)) } }
        is List<*> -> JSONArray().apply { v.forEach { put(valor(it)) } }
        is com.google.firebase.Timestamp -> java.time.Instant.ofEpochMilli(v.toDate().time).toString()
        is String -> if (v.startsWith("data:") && v.length > 4096) "§dataurl§" else v   // anexos não vão para o cache de listas
        else -> v
    }
    private fun deJson(o: JSONObject): Map<String, Any?> = o.keys().asSequence().associateWith { k -> deValor(o.get(k)) }
    private fun deValor(v: Any?): Any? = when (v) { JSONObject.NULL -> null; is JSONObject -> deJson(v); is JSONArray -> (0 until v.length()).map { deValor(v.get(it)) }; else -> v }
}
