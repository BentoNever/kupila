package ao.kupila.gestaonegocios.dados.remoto

import android.util.Base64
import java.time.Instant
import java.util.Locale
import kotlin.random.Random

/**
 * Convenções do esquema da origem (HTML 1.0.239), partilhado com a app web em uso (decisão D-29):
 * datas em texto ISO-8601 UTC, ids com prefixo e carimbo temporal, imagens inline em data URL.
 */
object Origem {
    fun agoraIso(): String = Instant.now().toString()
    fun iso(ms: Long): String = Instant.ofEpochMilli(ms).toString()

    /** Ids no formato da origem: `PREFIXO-<ms>-<3 dígitos>` (ex.: KPL-…, PAY-…, RSV-…, AUD-…). */
    fun id(prefixo: String): String = "$prefixo-${System.currentTimeMillis()}-${Random.nextInt(100, 1000)}"

    fun dataUrlJpeg(bytes: ByteArray): String = "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    fun dataUrl(mime: String, bytes: ByteArray): String = "data:${mime.lowercase(Locale.ROOT)};base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    fun bytesDe(dataUrl: String): ByteArray? = runCatching { Base64.decode(dataUrl.substringAfter(','), Base64.DEFAULT) }.getOrNull()
    fun mimeDe(dataUrl: String): String = dataUrl.substringAfter("data:", "").substringBefore(';').ifBlank { "application/octet-stream" }

    /** `sanitizeFirestoreId` (HTML 12173). */
    fun idSeguro(v: String) = v.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(120).ifEmpty { "file" }

    fun texto(v: Any?): String = when (v) { null -> ""; is String -> v; is Number -> java.math.BigDecimal(v.toString()).stripTrailingZeros().toPlainString(); else -> v.toString() }
    fun numero(v: Any?): java.math.BigDecimal? = when (v) { is Number -> java.math.BigDecimal(v.toString()); is String -> v.replace(',', '.').toBigDecimalOrNull(); else -> null }
    fun ms(v: Any?): Long? = MapeamentoProduto.ms(v)

    /** Papel canónico da origem (`canonicalRole`): GESTOR/ADMINISTRADOR/ADMIN → GESTOR. */
    fun papel(v: Any?): String = when (texto(v).uppercase().replace("Á", "A")) {
        "GESTOR", "ADMINISTRADOR", "ADMIN" -> "GESTOR"; "PROPRIETARIO", "OWNER" -> "PROPRIETARIO"; "CLIENTE", "CLIENT" -> "CLIENTE"; else -> texto(v)
    }
}
