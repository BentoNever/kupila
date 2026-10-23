package ao.kupila.gestaonegocios.dados.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import ao.kupila.gestaonegocios.dominio.duplicados.IdentidadeBem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Cache PÚBLICO do catálogo (só [ao.kupila.gestaonegocios.dados.remoto.ProdutoDto]) — chave/âmbito próprios (secção 11). */
@Entity(tableName = "produto_publico", indices = [Index("status"), Index("categoria"), Index("provincia"), Index("donoId")])
data class ProdutoPublicoLocal(
    @PrimaryKey val id: String,
    val json: String,
    val status: String,
    val categoria: String,
    val modalidade: String,
    val provincia: String,
    val donoId: String,
    /** Texto normalizado (sem acentos) para pesquisa: título, município, marca, subcategoria. */
    val pesquisa: String,
    val precoCentimos: Long,
    val criadoEm: Long,
    val versao: Long,
    val recebidoEm: Long,
)

/** Cache PRIVADO (titular/Administrador) — cifrado com o Cofre; nunca misturado com o público. */
@Entity(tableName = "produto_privado")
data class ProdutoPrivadoLocal(@PrimaryKey val id: String, val jsonCifrado: String, val versao: Long, val recebidoEm: Long)

/** Rascunho de anúncio (até 5 + rascunhos de edição). Conteúdo = formulário serializado (sem segredos). */
@Entity(tableName = "rascunho", indices = [Index("actualizadoEm")])
data class RascunhoLocal(
    @PrimaryKey val id: String,
    val titulo: String,
    val conteudoJson: String,
    val percentagem: Int,
    val actualizadoEm: Long,
    val versao: Long,
    val versaoSincronizada: Long = 0,
    val edicaoDeAnuncioId: String? = null,
    /** Associado a pagamento em curso/evidência: não sai da lista automaticamente. */
    val protegido: Boolean = false,
    val removido: Boolean = false,
)

/** Anexo local de um rascunho (foto/documento) em ficheiro privado da app. */
@Entity(tableName = "anexo", indices = [Index("rascunhoId")])
data class AnexoLocal(
    @PrimaryKey val id: String,
    val rascunhoId: String,
    val tipo: String,
    val caminho: String,
    val mime: String,
    val tamanho: Long,
    val sha256: String,
    val ordem: Int,
    val dHash: String? = null,
    val sintetico: Boolean = false,
    val caminhoRemoto: String? = null,
)

/** Fila persistente de operações (secção 33): idempotente, por conta, com dependências e backoff. */
@Entity(tableName = "operacao", indices = [Index("estado"), Index("proximaEm")])
data class OperacaoLocal(
    @PrimaryKey val chaveIdempotencia: String,
    val tipo: String,
    val entidadeId: String,
    val payloadJson: String,
    val versaoPayload: Int,
    val estado: String,
    val tentativas: Int,
    val criadaEm: Long,
    val proximaEm: Long,
    val motivo: String? = null,
    val dependeDe: String? = null,
)

@Entity(tableName = "notificacao", indices = [Index("criadaEm"), Index("lida")])
data class NotificacaoLocal(
    @PrimaryKey val id: String, val chaveEvento: String, val titulo: String, val mensagem: String, val categoria: String,
    val severidade: String, val accao: String, val entidadeId: String?, val criadaEm: Long, val lida: Boolean, val cta: String?,
)

/**
 * Cache genérico das colecções de eventos (reservas, intenções, reembolsos, autorizações, utilizadores…) para mostrar offline e
 * sincronizar só o que mudou (delta por `updatedAt`) — economia de quotas (docs/principios-produto.md).
 */
@Entity(tableName = "registo", primaryKeys = ["colecao", "id"], indices = [Index("colecao"), Index("actualizadoEm")])
data class RegistoLocal(val colecao: String, val id: String, val json: String, val actualizadoEm: Long)

@Dao
interface RegistosDao {
    @Upsert suspend fun gravar(r: List<RegistoLocal>)
    @Query("SELECT * FROM registo WHERE colecao = :colecao ORDER BY actualizadoEm DESC") fun observar(colecao: String): Flow<List<RegistoLocal>>
    @Query("SELECT * FROM registo WHERE colecao = :colecao AND id = :id") suspend fun obter(colecao: String, id: String): RegistoLocal?
    @Query("DELETE FROM registo WHERE colecao = :colecao AND id IN (:ids)") suspend fun remover(colecao: String, ids: List<String>)
    @Query("DELETE FROM registo WHERE colecao = :colecao AND id NOT IN (:manter)") suspend fun manterSo(colecao: String, manter: List<String>)
}

/** Migração 1→2: cache genérico de registos (sem perder rascunhos, fila nem notificações já guardados). */
val MIGRACAO_1_2 = object : androidx.room.migration.Migration(1, 2) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `registo` (`colecao` TEXT NOT NULL, `id` TEXT NOT NULL, `json` TEXT NOT NULL, `actualizadoEm` INTEGER NOT NULL, PRIMARY KEY(`colecao`, `id`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_registo_colecao` ON `registo` (`colecao`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_registo_actualizadoEm` ON `registo` (`actualizadoEm`)")
    }
}

/** Metadados de sincronização (ex.: "catálogo actualizado em"), para mostrar a idade dos dados offline. */
@Entity(tableName = "meta")
data class MetaLocal(@PrimaryKey val chave: String, val valor: String, val em: Long)

@Dao
interface ProdutosDao {
    @Upsert suspend fun gravar(itens: List<ProdutoPublicoLocal>)
    @Query("DELETE FROM produto_publico WHERE id IN (:ids)") suspend fun remover(ids: List<String>)
    @Query("SELECT * FROM produto_publico WHERE id = :id") fun observar(id: String): Flow<ProdutoPublicoLocal?>
    @Query("SELECT * FROM produto_publico WHERE id = :id") suspend fun obter(id: String): ProdutoPublicoLocal?
    @Query("""SELECT * FROM produto_publico WHERE status IN ('ACTIVE','RESERVED')
        AND (:categoria IS NULL OR categoria = :categoria) AND (:modalidade IS NULL OR modalidade = :modalidade)
        AND (:provincia IS NULL OR provincia = :provincia) AND (:texto = '' OR pesquisa LIKE '%' || :texto || '%')
        ORDER BY criadoEm DESC LIMIT :limite""")
    fun catalogo(categoria: String?, modalidade: String?, provincia: String?, texto: String, limite: Int): Flow<List<ProdutoPublicoLocal>>
    @Query("SELECT * FROM produto_publico WHERE donoId = :dono ORDER BY criadoEm DESC") fun doDono(dono: String): Flow<List<ProdutoPublicoLocal>>
    @Query("SELECT * FROM produto_publico ORDER BY criadoEm DESC LIMIT :limite") fun todos(limite: Int): Flow<List<ProdutoPublicoLocal>>
    @Query("SELECT COUNT(*) FROM produto_publico WHERE status IN ('ACTIVE','RESERVED')") fun totalVisivel(): Flow<Int>
    @Query("SELECT COUNT(*) FROM produto_publico") suspend fun contar(): Int
    @Query("SELECT MIN(criadoEm) FROM produto_publico") suspend fun maisAntigoCriadoMs(): Long?
    @Query("SELECT id, criadoEm FROM produto_publico") suspend fun idsCriacao(): List<IdCriado>
}

data class IdCriado(val id: String, val criadoEm: Long)

/** Pares (id, criadoEm) e ISO do mais antigo, para paginação e detecção de remoções. */
suspend fun ProdutosDao.todosIds(): List<Pair<String, Long>> = idsCriacao().map { it.id to it.criadoEm }
suspend fun ProdutosDao.maisAntigoCriado(): String? = maisAntigoCriadoMs()?.takeIf { it > 0 }?.let { java.time.Instant.ofEpochMilli(it).toString() }

@Dao
interface PrivadosDao {
    @Upsert suspend fun gravar(p: ProdutoPrivadoLocal)
    @Query("SELECT * FROM produto_privado WHERE id = :id") suspend fun obter(id: String): ProdutoPrivadoLocal?
    @Query("DELETE FROM produto_privado WHERE id = :id") suspend fun remover(id: String)
}

@Dao
interface RascunhosDao {
    @Upsert suspend fun gravar(r: RascunhoLocal)
    @Query("SELECT * FROM rascunho WHERE removido = 0 ORDER BY actualizadoEm DESC") fun observar(): Flow<List<RascunhoLocal>>
    @Query("SELECT * FROM rascunho WHERE removido = 0 ORDER BY actualizadoEm DESC") suspend fun listar(): List<RascunhoLocal>
    @Query("SELECT * FROM rascunho WHERE id = :id") suspend fun obter(id: String): RascunhoLocal?
    @Query("UPDATE rascunho SET removido = 1, actualizadoEm = :em WHERE id IN (:ids)") suspend fun marcarRemovidos(ids: List<String>, em: Long)
    @Query("SELECT * FROM rascunho WHERE versao > versaoSincronizada OR removido = 1") suspend fun porSincronizar(): List<RascunhoLocal>
    @Query("DELETE FROM rascunho WHERE id = :id") suspend fun apagar(id: String)
    @Upsert suspend fun gravarAnexo(a: AnexoLocal)
    @Query("SELECT * FROM anexo WHERE rascunhoId = :rascunho ORDER BY tipo, ordem") suspend fun anexos(rascunho: String): List<AnexoLocal>
    @Query("SELECT * FROM anexo WHERE rascunhoId = :rascunho ORDER BY tipo, ordem") fun observarAnexos(rascunho: String): Flow<List<AnexoLocal>>
    @Query("DELETE FROM anexo WHERE id = :id") suspend fun apagarAnexo(id: String)
}

@Dao
interface OperacoesDao {
    @Upsert suspend fun gravar(o: OperacaoLocal)
    @Query("SELECT * FROM operacao WHERE estado IN ('PENDENTE','FALHOU_TEMPORARIO') AND proximaEm <= :agora ORDER BY criadaEm LIMIT 20") suspend fun prontas(agora: Long): List<OperacaoLocal>
    @Query("SELECT * FROM operacao WHERE estado != 'CONCLUIDA' ORDER BY criadaEm") fun pendentes(): Flow<List<OperacaoLocal>>
    @Query("SELECT * FROM operacao WHERE chaveIdempotencia = :chave") suspend fun obter(chave: String): OperacaoLocal?
    /** Recupera tarefas presas em "a enviar" após crash (secção 33). */
    @Query("UPDATE operacao SET estado = 'PENDENTE' WHERE estado = 'A_ENVIAR' AND proximaEm < :limite") suspend fun recuperarPresas(limite: Long)
    @Query("DELETE FROM operacao WHERE estado = 'CONCLUIDA' AND criadaEm < :antes") suspend fun limparConcluidas(antes: Long)
}

@Dao
interface NotificacoesDao {
    @Upsert suspend fun gravar(n: List<NotificacaoLocal>)
    @Query("SELECT * FROM notificacao WHERE (:categoria IS NULL OR categoria = :categoria) ORDER BY criadaEm DESC LIMIT 200")
    fun observar(categoria: String?): Flow<List<NotificacaoLocal>>
    @Query("SELECT COUNT(*) FROM notificacao WHERE lida = 0") fun naoLidas(): Flow<Int>
    @Query("UPDATE notificacao SET lida = 1 WHERE id IN (:ids)") suspend fun marcarLidas(ids: List<String>)
    @Query("UPDATE notificacao SET lida = 1") suspend fun marcarTodasLidas()
    @Query("SELECT * FROM notificacao WHERE id = :id") suspend fun obter(id: String): NotificacaoLocal?
}

@Dao
interface MetaDao {
    @Upsert suspend fun gravar(m: MetaLocal)
    @Query("SELECT * FROM meta WHERE chave = :chave") fun observar(chave: String): Flow<MetaLocal?>
    @Query("SELECT * FROM meta WHERE chave = :chave") suspend fun obter(chave: String): MetaLocal?
}

@Database(
    entities = [ProdutoPublicoLocal::class, ProdutoPrivadoLocal::class, RascunhoLocal::class, AnexoLocal::class,
        OperacaoLocal::class, NotificacaoLocal::class, MetaLocal::class, RegistoLocal::class],
    version = 2, exportSchema = true,
)
abstract class BaseDadosConta : RoomDatabase() {
    abstract fun produtos(): ProdutosDao
    abstract fun privados(): PrivadosDao
    abstract fun rascunhos(): RascunhosDao
    abstract fun operacoes(): OperacoesDao
    abstract fun notificacoes(): NotificacoesDao
    abstract fun meta(): MetaDao
    abstract fun registos(): RegistosDao
}

/**
 * Uma base por conta (decisão D-18): ao trocar de conta, a anterior é fechada e os seus dados privados ficam
 * inacessíveis ao novo utilizador; a fila de uma conta nunca é enviada em nome de outra.
 */
@Singleton
class GestorBases @Inject constructor(@param:ApplicationContext private val contexto: Context) {
    private var uidActual: String? = null
    private var base: BaseDadosConta? = null

    @Synchronized
    fun para(uid: String): BaseDadosConta {
        if (uid == uidActual) base?.let { return it }
        base?.close()
        val nome = "kupila-" + IdentidadeBem.sha256("conta:$uid").take(20) + ".db"
        return Room.databaseBuilder(contexto, BaseDadosConta::class.java, nome).addMigrations(MIGRACAO_1_2).build().also { base = it; uidActual = uid }
    }

    @Synchronized
    fun fechar() { base?.close(); base = null; uidActual = null }
}
