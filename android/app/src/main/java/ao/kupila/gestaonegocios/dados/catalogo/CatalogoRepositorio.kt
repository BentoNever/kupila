package ao.kupila.gestaonegocios.dados.catalogo

import ao.kupila.gestaonegocios.dados.local.BaseDadosConta
import ao.kupila.gestaonegocios.dados.local.MetaLocal
import ao.kupila.gestaonegocios.dados.local.ProdutoPublicoLocal
import ao.kupila.gestaonegocios.dados.remoto.MapeamentoProduto
import ao.kupila.gestaonegocios.dados.remoto.ProdutoDto
import ao.kupila.gestaonegocios.dados.sessao.OuvintesSessao
import ao.kupila.gestaonegocios.dominio.anuncio.AnuncioPublico
import ao.kupila.gestaonegocios.dominio.catalogo.FiltroCatalogo
import ao.kupila.gestaonegocios.dominio.duplicados.IdentidadeBem
import ao.kupila.gestaonegocios.dominio.perfis.Papel
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json

/** Estado da sincronização do catálogo para a UI (vazio/erro/cache antigo — secção 10). */
data class EstadoCatalogo(val aSincronizar: Boolean = false, val erro: String? = null, val actualizadoEm: Long? = null, val haMais: Boolean = true)

/**
 * Catálogo: a UI observa o Room; o Firestore alimenta-o. Escuta em tempo real a página mais recente
 * (actualização por eventos) e busca páginas anteriores por cursor só quando o utilizador pede (sem leitura integral repetida).
 */
class CatalogoRepositorio(private val base: BaseDadosConta, private val uid: String, private val papel: Papel) {
    private val ambito = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val fs get() = FirebaseFirestore.getInstance()
    private val _estado = MutableStateFlow(EstadoCatalogo())
    val estado: StateFlow<EstadoCatalogo> = _estado.asStateFlow()
    private var cursor: DocumentSnapshot? = null
    private val escutas = mutableListOf<ListenerRegistration>()

    companion object {
        const val PAGINA = 40L
        private val VISIVEIS = listOf("ACTIVE", "RESERVED", "COMPLETED")
    }

    private fun consultaPublica(): Query = fs.collection("products").whereIn("status", VISIVEIS)
        .orderBy("createdAt", Query.Direction.DESCENDING)

    fun iniciar() {
        parar()
        _estado.value = _estado.value.copy(aSincronizar = true, erro = null)
        escutas += consultaPublica().limit(PAGINA).addSnapshotListener { snap, erro ->
            if (erro != null) {
                // Erro de uma consulta não apaga o cache nem derruba o catálogo (secção 33).
                _estado.value = _estado.value.copy(aSincronizar = false, erro = ao.kupila.gestaonegocios.dados.remoto.MensagensErro.de(erro)); return@addSnapshotListener
            }
            snap ?: return@addSnapshotListener
            if (cursor == null) cursor = snap.documents.lastOrNull()
            val removidos = snap.documentChanges.filter { it.type == com.google.firebase.firestore.DocumentChange.Type.REMOVED }.map { it.document.id }
            ambito.launch {
                gravar(snap.documents)
                if (removidos.isNotEmpty() && !snap.metadata.isFromCache) {
                    // Saiu da projecção visível (ex.: desanunciado): só remove do cache se não for do próprio utilizador.
                    val alheios = removidos.filter { base.produtos().obter(it)?.donoId != uid }
                    if (alheios.isNotEmpty()) base.produtos().remover(alheios)
                }
                if (!snap.metadata.isFromCache) base.meta().gravar(MetaLocal("catalogo", "ok", System.currentTimeMillis()))
                _estado.value = _estado.value.copy(aSincronizar = false, erro = null, actualizadoEm = System.currentTimeMillis())
            }
        }
        // Os próprios anúncios (todos os estados) — painel do Proprietário; Administrador vê todos no Catálogo admin.
        if (papel != Papel.CLIENTE) {
            val q = if (papel == Papel.ADMINISTRADOR) fs.collection("products").orderBy("createdAt", Query.Direction.DESCENDING).limit(200)
            else fs.collection("products").whereEqualTo("ownerId", uid)
            escutas += q.addSnapshotListener { snap, _ -> snap?.let { s -> ambito.launch { gravar(s.documents) } } }
        }
        OuvintesSessao.registar(::parar)
    }

    fun parar() { escutas.forEach { it.remove() }; escutas.clear(); cursor = null }

    /** Página seguinte (paginação incremental, sem limitar o catálogo à primeira página). */
    suspend fun carregarMais(): Result<Int> = runCatching {
        val c = cursor ?: return@runCatching 0
        val s = consultaPublica().startAfter(c).limit(PAGINA).get().await()
        gravar(s.documents)
        cursor = s.documents.lastOrNull() ?: cursor
        _estado.value = _estado.value.copy(haMais = s.size() >= PAGINA)
        s.size()
    }

    private suspend fun gravar(docs: List<DocumentSnapshot>) {
        val agora = System.currentTimeMillis()
        val linhas = docs.mapNotNull { d -> d.data?.let { MapeamentoProduto.deFirestore(d.id, it) } }.map { dto -> paraLocal(dto, agora) }
        if (linhas.isNotEmpty()) base.produtos().gravar(linhas)
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
        base.produtos().catalogo(f.categoria?.codigo, f.modalidade?.codigoOrigem, f.provincia, IdentidadeBem.normalizar(f.texto), limite)
            .map { l -> l.mapNotNull(::deLocal) }

    fun doDono(dono: String): Flow<List<AnuncioPublico>> = base.produtos().doDono(dono).map { l -> l.mapNotNull(::deLocal) }
    fun todos(): Flow<List<AnuncioPublico>> = base.produtos().todos(1000).map { l -> l.mapNotNull(::deLocal) }

    fun observar(id: String): Flow<AnuncioPublico?> = base.produtos().observar(id).map { it?.let(::deLocal) }

    /** Abre o detalhe com dados frescos do servidor (o cache serve enquanto a resposta não chega). */
    suspend fun actualizarDetalhe(id: String): Result<Unit> = runCatching {
        val d = fs.collection("products").document(id).get().await()
        if (d.exists()) gravar(listOf(d))
    }

    val actualizadoEm: Flow<Long?> = base.meta().observar("catalogo").map { it?.em }
}
