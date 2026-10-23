package ao.kupila.gestaonegocios.dados.catalogo

import ao.kupila.gestaonegocios.dados.local.BaseDadosConta
import ao.kupila.gestaonegocios.dados.local.MetaLocal
import ao.kupila.gestaonegocios.dados.local.ProdutoPublicoLocal
import ao.kupila.gestaonegocios.dados.local.maisAntigoCriado
import ao.kupila.gestaonegocios.dados.local.todosIds
import ao.kupila.gestaonegocios.dados.remoto.MapeamentoProduto
import ao.kupila.gestaonegocios.dados.remoto.MensagensErro
import ao.kupila.gestaonegocios.dados.remoto.ProdutoDto
import ao.kupila.gestaonegocios.dados.rede.Economia
import ao.kupila.gestaonegocios.dados.rede.EmPoupanca
import ao.kupila.gestaonegocios.dominio.anuncio.AnuncioPublico
import ao.kupila.gestaonegocios.dominio.catalogo.FiltroCatalogo
import ao.kupila.gestaonegocios.dominio.duplicados.IdentidadeBem
import ao.kupila.gestaonegocios.dominio.perfis.Papel
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json

/** Estado da sincronização do catálogo para a UI (vazio/erro/cache antigo — secção 10). */
data class EstadoCatalogo(val aSincronizar: Boolean = false, val erro: String? = null, val actualizadoEm: Long? = null, val haMais: Boolean = true, val poupanca: String? = null)

/**
 * Catálogo cache-first com SINCRONIZAÇÃO DELTA (economia de quotas — docs/principios-produto.md):
 *  · a UI observa sempre o Room (instantâneo, offline);
 *  · 1.ª carga: páginas de 40 por `createdAt` (uma vez por conta);
 *  · depois: só os anúncios NOVOS (`createdAt` > último) e ALTERADOS (`updatedAt` > último) — tipicamente 0 a poucas leituras;
 *  · sem escutas permanentes; leituras automáticas só com toque recente ([Economia]).
 */
class CatalogoRepositorio(private val base: BaseDadosConta, private val uid: String, private val papel: Papel) {
    private val ambito = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val fs get() = FirebaseFirestore.getInstance()
    private val _estado = MutableStateFlow(EstadoCatalogo())
    val estado: StateFlow<EstadoCatalogo> = _estado.asStateFlow()
    private var cursor: DocumentSnapshot? = null
    private val mutex = Mutex()

    companion object {
        const val PAGINA = 40L
        val DEMO_IDS = setOf("KPL-001", "KPL-002", "KPL-003")
        private const val META_CRIADO = "catalogo-ultimo-criado"
        private const val META_ACTUAL = "catalogo-ultimo-actualizado"
        private const val META_SYNC = "catalogo"
        /** Intervalo mínimo entre sincronizações automáticas (regressar ao app). */
        const val INTERVALO_AUTO_MS = 5 * 60_000L
    }

    /** Arranque da conta: sincroniza se o cache é antigo (não há escutas). */
    fun iniciar() = ambito.launch { sincronizar(automatica = true) }
    fun parar() { /* sem escutas permanentes */ }

    /**
     * Sincroniza. [automatica] = não pedida agora pela pessoa (respeita a janela de toque e o intervalo mínimo).
     * [completa] = "actualizar tudo" (recarrega a 1.ª página e detecta remoções nessa página).
     */
    suspend fun sincronizar(automatica: Boolean, completa: Boolean = false): Result<Int> = mutex.withLock {
        runCatching {
            val ultimaSync = base.meta().obter(META_SYNC)?.em ?: 0L
            if (automatica && !completa && System.currentTimeMillis() - ultimaSync < INTERVALO_AUTO_MS) return@runCatching 0
            Economia.exigirLeitura(automatica)
            _estado.value = _estado.value.copy(aSincronizar = true, erro = null, poupanca = null)
            val ultimoCriado = base.meta().obter(META_CRIADO)?.valor
            val ultimoActual = base.meta().obter(META_ACTUAL)?.valor
            val lidos: Int
            if (ultimoCriado == null || completa) {
                val s = fs.collection("products").orderBy("createdAt", Query.Direction.DESCENDING).limit(PAGINA).get(Source.SERVER).await()
                gravar(s.documents)
                if (completa) {
                    // Remoções detectáveis sem reler tudo: anúncios do cache na faixa desta página que já não vieram.
                    val ids = s.documents.map { it.id }.toSet()
                    val limite = s.documents.lastOrNull()?.let { MapeamentoProduto.ms(it.get("createdAt")) } ?: 0L
                    base.produtos().todosIds().filter { (id, criado) -> criado >= limite && id !in ids }.map { it.first }.takeIf { it.isNotEmpty() }?.let { base.produtos().remover(it) }
                }
                cursor = s.documents.lastOrNull()
                _estado.value = _estado.value.copy(haMais = s.size() >= PAGINA)
                lidos = s.size()
            } else {
                val novos = fs.collection("products").whereGreaterThan("createdAt", ultimoCriado).orderBy("createdAt").limit(200).get(Source.SERVER).await()
                val alterados = if (ultimoActual != null) fs.collection("products").whereGreaterThan("updatedAt", ultimoActual).orderBy("updatedAt").limit(200).get(Source.SERVER).await() else null
                gravar(novos.documents + (alterados?.documents ?: emptyList()))
                lidos = novos.size() + (alterados?.size() ?: 0)
                // Uma escuta permanente teria relido o catálogo visível a cada reabertura; o delta leu só o que mudou.
                Economia.poupouDelta((base.produtos().contar() - lidos).coerceAtLeast(0))
            }
            Economia.leu(lidos.coerceAtLeast(1))
            base.meta().gravar(MetaLocal(META_SYNC, "ok", System.currentTimeMillis()))
            _estado.value = _estado.value.copy(aSincronizar = false, erro = null, actualizadoEm = System.currentTimeMillis())
            lidos
        }.onFailure { e ->
            _estado.value = _estado.value.copy(aSincronizar = false, erro = if (e is EmPoupanca) null else MensagensErro.de(e), poupanca = (e as? EmPoupanca)?.message)
        }
    }

    /** Página seguinte (só quando a pessoa chega ao fim da lista). */
    suspend fun carregarMais(): Result<Int> = runCatching {
        Economia.exigirLeitura(automatica = false)
        val q = fs.collection("products").orderBy("createdAt", Query.Direction.DESCENDING)
        val c = cursor
        val s = (if (c != null) q.startAfter(c) else {
            val ultimo = base.produtos().maisAntigoCriado() ?: return@runCatching 0
            q.whereLessThan("createdAt", ultimo)
        }).limit(PAGINA).get(Source.SERVER).await()
        gravar(s.documents)
        cursor = s.documents.lastOrNull() ?: cursor
        Economia.leu(s.size().coerceAtLeast(1))
        _estado.value = _estado.value.copy(haMais = s.size() >= PAGINA)
        s.size()
    }

    private suspend fun gravar(docs: List<DocumentSnapshot>) {
        val agora = System.currentTimeMillis()
        val validos = docs.filter { it.id !in DEMO_IDS }
        val linhas = validos.mapNotNull { d -> d.data?.let { MapeamentoProduto.deFirestore(d.id, it) } }.map { dto -> paraLocal(dto, agora) }
        if (linhas.isNotEmpty()) base.produtos().gravar(linhas)
        // Marcas temporais (texto ISO da origem) para o próximo delta.
        validos.mapNotNull { it.get("createdAt")?.toString() }.maxOrNull()?.let { m ->
            val antes = base.meta().obter(META_CRIADO)?.valor
            if (antes == null || m > antes) base.meta().gravar(MetaLocal(META_CRIADO, m, agora))
        }
        validos.mapNotNull { (it.get("updatedAt") as? String) }.maxOrNull()?.let { m ->
            val antes = base.meta().obter(META_ACTUAL)?.valor
            if (antes == null || m > antes) base.meta().gravar(MetaLocal(META_ACTUAL, m, agora))
        }
        if (base.meta().obter(META_ACTUAL) == null && validos.isNotEmpty())
            base.meta().gravar(MetaLocal(META_ACTUAL, java.time.Instant.ofEpochMilli(agora).toString(), agora))
    }

    fun paraLocal(dto: ProdutoDto, agora: Long) = ProdutoPublicoLocal(
        id = dto.id, json = json.encodeToString(ProdutoDto.serializer(), dto), status = dto.status, categoria = dto.category,
        modalidade = dto.intent, provincia = dto.province, donoId = dto.ownerId,
        pesquisa = IdentidadeBem.normalizar(listOf(dto.title, dto.municipality, dto.subcategory, dto.categoryDetails["brand"].orEmpty(),
            dto.categoryDetails["model"].orEmpty(), dto.id).joinToString(" ")),
        precoCentimos = runCatching { java.math.BigDecimal(dto.priceText).movePointRight(2).toLong() }.getOrDefault(0),
        criadoEm = dto.createdAtMs, versao = dto.version, recebidoEm = agora,
    )

    fun deLocal(l: ProdutoPublicoLocal): AnuncioPublico? = runCatching { json.decodeFromString(ProdutoDto.serializer(), l.json) }.getOrNull()?.let(MapeamentoProduto::paraDominio)

    fun catalogo(f: FiltroCatalogo, limite: Int = 400): Flow<List<AnuncioPublico>> =
        base.produtos().catalogo(f.categoria?.codigo, f.modalidade?.codigoOrigem, f.provincia, IdentidadeBem.normalizar(f.texto), limite).map { l -> l.mapNotNull(::deLocal) }

    fun doDono(dono: String): Flow<List<AnuncioPublico>> = base.produtos().doDono(dono).map { l -> l.mapNotNull(::deLocal) }
    fun todos(): Flow<List<AnuncioPublico>> = base.produtos().todos(1000).map { l -> l.mapNotNull(::deLocal) }
    fun observar(id: String): Flow<AnuncioPublico?> = base.produtos().observar(id).map { it?.let(::deLocal) }

    /**
     * Detalhe: o cache mostra-se de imediato; só relê do servidor se a cópia tem mais de 2 min (ou se pedido) — abrir e fechar o mesmo
     * anúncio não gasta quota. Se o documento já não existe, sai do cache.
     */
    suspend fun actualizarDetalhe(id: String, forcar: Boolean = false): Result<Unit> = runCatching {
        val l = base.produtos().obter(id)
        if (!forcar && l != null && System.currentTimeMillis() - l.recebidoEm < 120_000) return@runCatching
        Economia.exigirLeitura(automatica = false)
        val d = fs.collection("products").document(id).get(Source.SERVER).await()
        Economia.leu(1)
        if (d.exists()) gravar(listOf(d)) else base.produtos().remover(listOf(id))
    }

    /** Anúncios do próprio Proprietário em todos os estados (painel), lidos sob pedido. */
    suspend fun sincronizarMeus(): Result<Int> = runCatching {
        Economia.exigirLeitura(automatica = false)
        val q = if (papel == Papel.ADMINISTRADOR) fs.collection("products").orderBy("createdAt", Query.Direction.DESCENDING).limit(300)
            else fs.collection("products").whereEqualTo("ownerId", uid)
        val s = q.get(Source.SERVER).await()
        gravar(s.documents); Economia.leu(s.size().coerceAtLeast(1)); s.size()
    }

    val actualizadoEm: Flow<Long?> = base.meta().observar(META_SYNC).map { it?.em }
}
