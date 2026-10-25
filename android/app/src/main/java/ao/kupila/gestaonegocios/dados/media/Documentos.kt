package ao.kupila.gestaonegocios.dados.media

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Linha de documento gerado: rótulo + valor (ou título de secção quando valor nulo). */
data class LinhaDoc(val rotulo: String, val valor: String? = null, val destaque: Boolean = false)

/**
 * OCR do comprovativo (ML Kit no aparelho, substitui o Tesseract.js da origem — D-16) e documentos JPG/PDF (recibo da reserva,
 * ficha de utilizador) partilhados por FileProvider só a partir de `cache/documentos` (nunca anexos privados).
 */
@Singleton
class Documentos @Inject constructor(@param:ApplicationContext private val contexto: Context) {

    /** Texto reconhecido + melhor referência de 9 dígitos (`bestReferenceFromText`, HTML 15930). */
    suspend fun lerReferencia(jpeg: ByteArray, esperada: String): Pair<String, String> {
        val bmp = android.graphics.BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: return "" to ""
        val texto = runCatching { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(InputImage.fromBitmap(bmp, 0)).await().text }.getOrDefault("")
        val candidatos = Regex("(?:\\d[\\s.\\-]*){9,12}").findAll(texto).map { it.value.filter(Char::isDigit).let { d -> if (d.length > 9) d.takeLast(9) else d } }
            .filter { it.length == 9 }.toList()
        val ref = if (esperada in candidatos) esperada else candidatos.firstOrNull().orEmpty()
        return texto to ref
    }

    /** Texto de uma imagem (ML Kit no aparelho, sem rede). */
    suspend fun lerTexto(jpeg: ByteArray): String {
        val bmp = android.graphics.BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: return ""
        return runCatching { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(InputImage.fromBitmap(bmp, 0)).await().text }.getOrDefault("")
    }

    /** Primeira página de um PDF como JPEG (para ler o número do documento de titularidade). */
    suspend fun primeiraPaginaPdf(pdf: ByteArray): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val f = File(pasta(), "ocr-${System.nanoTime()}.pdf").apply { writeBytes(pdf) }
            android.os.ParcelFileDescriptor.open(f, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                android.graphics.pdf.PdfRenderer(fd).use { r ->
                    r.openPage(0).use { p ->
                        val esc = 1600f / maxOf(p.width, p.height)
                        val b = Bitmap.createBitmap((p.width * esc).toInt(), (p.height * esc).toInt(), Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
                        p.render(b, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        java.io.ByteArrayOutputStream().use { o -> b.compress(Bitmap.CompressFormat.JPEG, 90, o); o.toByteArray() }
                    }
                }
            }.also { f.delete() }
        }.getOrNull()
    }

    /**
     * Número/referência de um documento de titularidade lido por OCR: primeiro o valor a seguir a uma palavra-chave
     * (N.º, Número, Registo, Matrícula, Livrete, Título, Certidão, Processo, Ref.), depois uma matrícula angolana, depois o
     * código mais longo com dígitos. Nunca inventa: sem candidato, devolve null e o campo fica para a pessoa.
     */
    fun referenciaDeDocumento(texto: String): String? {
        val t = texto.replace('\n', ' ')
        Regex("(?i)(n\\.?\\s?[ºo°]|n[uú]mero|registo|matr[ií]cula|livrete|t[ií]tulo|certid[aã]o|processo|ref\\.?|refer[eê]ncia)\\s*[:.\\-]?\\s*([A-Z0-9][A-Z0-9/\\-.]{3,24})")
            .findAll(t).map { it.groupValues[2].trimEnd('.', '-', '/') }.firstOrNull { c -> c.count(Char::isDigit) >= 3 }?.let { return it }
        Regex("\\b[A-Z]{2}-\\d{2}-\\d{2}-[A-Z]{2}\\b").find(t)?.let { return it.value }
        return Regex("\\b[A-Z0-9][A-Z0-9/\\-.]{5,24}\\b").findAll(t.uppercase()).map { it.value }.filter { c -> c.count(Char::isDigit) >= 4 }.maxByOrNull { it.length }
    }

    private fun pasta() = File(contexto.cacheDir, "documentos").apply { mkdirs() }

    /** Renderiza as linhas num bitmap A4 (595×842 pt a 2×) — mesmo conteúdo para JPG e PDF. */
    private fun desenhar(c: Canvas, titulo: String, subtitulo: String, linhas: List<LinhaDoc>, rodape: String, escala: Float) {
        val w = 595f * escala
        c.drawColor(Color.WHITE)
        val verde = Paint().apply { color = Color.rgb(21, 128, 61) }
        c.drawRect(0f, 0f, w, 92f * escala, verde)
        val pT = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 20f * escala; typeface = Typeface.DEFAULT_BOLD }
        val pS = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(250, 204, 21); textSize = 11f * escala }
        c.drawText("Kupila-Gestão de negócios", 32f * escala, 40f * escala, pT)
        c.drawText(titulo, 32f * escala, 62f * escala, pS.apply { color = Color.WHITE; textSize = 13f * escala })
        c.drawText(subtitulo, 32f * escala, 80f * escala, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(250, 204, 21); textSize = 10f * escala })
        val pR = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(75, 90, 84); textSize = 10.5f * escala }
        val pV = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(17, 24, 22); textSize = 10.5f * escala; typeface = Typeface.DEFAULT_BOLD }
        val pSec = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(21, 128, 61); textSize = 12f * escala; typeface = Typeface.DEFAULT_BOLD }
        var y = 122f * escala
        for (l in linhas) {
            if (l.valor == null) { y += 8f * escala; c.drawText(l.rotulo, 32f * escala, y, pSec); y += 18f * escala; continue }
            c.drawText(l.rotulo, 32f * escala, y, pR)
            val p = if (l.destaque) Paint(pV).apply { textSize = 12f * escala; color = Color.rgb(21, 128, 61) } else pV
            val partes = l.valor.chunked(52)
            partes.forEachIndexed { i, t -> c.drawText(t, 250f * escala, y + i * 14f * escala, p) }
            y += (16f + (partes.size - 1) * 14f) * escala
        }
        val pF = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(120, 130, 125); textSize = 8.5f * escala }
        rodape.chunked(105).forEachIndexed { i, t -> c.drawText(t, 32f * escala, 800f * escala + i * 11f * escala, pF) }
    }

    suspend fun pdf(nome: String, titulo: String, subtitulo: String, linhas: List<LinhaDoc>, rodape: String): File = withContext(Dispatchers.IO) {
        val doc = PdfDocument()
        val pag = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
        desenhar(pag.canvas, titulo, subtitulo, linhas, rodape, 1f)
        doc.finishPage(pag)
        val f = File(pasta(), "$nome.pdf")
        FileOutputStream(f).use(doc::writeTo); doc.close(); f
    }

    suspend fun jpg(nome: String, titulo: String, subtitulo: String, linhas: List<LinhaDoc>, rodape: String): File = withContext(Dispatchers.Default) {
        val b = Bitmap.createBitmap(1190, 1684, Bitmap.Config.ARGB_8888)
        desenhar(Canvas(b), titulo, subtitulo, linhas, rodape, 2f)
        val f = File(pasta(), "$nome.jpg")
        FileOutputStream(f).use { b.compress(Bitmap.CompressFormat.JPEG, 92, it) }; f
    }

    /** Guarda bytes (ex.: evidência autorizada) só na pasta de partilha e devolve o ficheiro. */
    suspend fun ficheiro(nome: String, bytes: ByteArray): File = withContext(Dispatchers.IO) { File(pasta(), nome).apply { writeBytes(bytes) } }

    fun partilhar(f: File, mime: String): Intent {
        val uri: Uri = FileProvider.getUriForFile(contexto, contexto.packageName + ".ficheiros", f)
        return Intent.createChooser(Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Partilhar")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun abrir(f: File, mime: String): Intent {
        val uri: Uri = FileProvider.getUriForFile(contexto, contexto.packageName + ".ficheiros", f)
        return Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
