package ao.kupila.gestaonegocios.dados.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import ao.kupila.gestaonegocios.dominio.duplicados.IdentidadeBem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

class ImagemInvalida(m: String) : Exception(m)

/**
 * Compressão e assinaturas de imagens fora da thread da UI (secção 5). A compressão conserva legibilidade e
 * respeita os limites da origem; nunca transforma um ficheiro ilegível em prova válida.
 */
@Singleton
class Imagens @Inject constructor(@param:ApplicationContext private val contexto: Context) {

    private fun descodificar(uri: Uri, maxLado: Int): Bitmap {
        val bmp = if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(contexto.contentResolver, uri)) { d, info, _ ->
                val lado = maxOf(info.size.width, info.size.height)
                if (lado > maxLado) { val f = maxLado.toFloat() / lado; d.setTargetSize((info.size.width * f).toInt().coerceAtLeast(1), (info.size.height * f).toInt().coerceAtLeast(1)) }
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val opc = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contexto.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opc) }
            var amostra = 1
            while (maxOf(opc.outWidth, opc.outHeight) / amostra > maxLado * 2) amostra *= 2
            val b = contexto.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = amostra }) }
                ?: throw ImagemInvalida("Não foi possível ler a imagem seleccionada.")
            val rot = contexto.contentResolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
            if (rot == 0) b else Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { postRotate(rot.toFloat()) }, true)
        }
        return bmp
    }

    private fun jpeg(b: Bitmap, qualidade: Int): ByteArray = ByteArrayOutputStream().use { o -> b.compress(Bitmap.CompressFormat.JPEG, qualidade, o); o.toByteArray() }

    /** Foto de perfil: quadrado centrado (como `compressPhoto`, 12666), fundo branco. */
    suspend fun fotoPerfil(uri: Uri, lado: Int = 384): ByteArray = withContext(Dispatchers.Default) {
        val src = runCatching { descodificar(uri, lado * 2) }.getOrElse { throw ImagemInvalida("Seleccione um ficheiro de imagem válido.") }
        val saida = Bitmap.createBitmap(lado, lado, Bitmap.Config.ARGB_8888)
        val escala = maxOf(lado.toFloat() / src.width, lado.toFloat() / src.height)
        Canvas(saida).apply {
            drawColor(Color.WHITE)
            val w = src.width * escala; val h = src.height * escala
            drawBitmap(src, null, android.graphics.RectF((lado - w) / 2, (lado - h) / 2, (lado + w) / 2, (lado + h) / 2), null)
        }
        listOf(82, 72, 62).map { jpeg(saida, it) }.firstOrNull { it.size <= 200_000 } ?: jpeg(saida, 55)
    }

    /**
     * Foto de anúncio: até [maxBytes] (1,20 MB da origem), lado máximo 1600 px, qualidade decrescente.
     * Devolve bytes JPEG e o dHash 9×8 para a prevenção de duplicados.
     */
    suspend fun fotoAnuncio(uri: Uri, maxBytes: Long): Pair<ByteArray, String> = withContext(Dispatchers.Default) {
        val src = runCatching { descodificar(uri, 1600) }.getOrElse { throw ImagemInvalida("Não foi possível ler a fotografia seleccionada.") }
        val bytes = listOf(86, 78, 70, 62, 54).asSequence().map { jpeg(src, it) }.firstOrNull { it.size <= maxBytes }
            ?: throw ImagemInvalida("A fotografia continua acima de ${maxBytes / 1024} KB depois de optimizada. Escolha outra imagem.")
        bytes to dHash(src)
    }

    /** Comprovativo (pagamento/titularidade/evidência): mantém legibilidade (lado até 2000 px). */
    suspend fun comprovativo(uri: Uri, maxBytes: Long): ByteArray = withContext(Dispatchers.Default) {
        val src = runCatching { descodificar(uri, 2000) }.getOrElse { throw ImagemInvalida("Não foi possível ler o ficheiro.") }
        listOf(90, 82, 74, 66).asSequence().map { jpeg(src, it) }.firstOrNull { it.size <= maxBytes }
            ?: throw ImagemInvalida("O ficheiro excede o limite de ${maxBytes / 1024} KB mesmo depois de optimizado.")
    }

    fun dHash(b: Bitmap): String {
        val p = Bitmap.createScaledBitmap(b, 9, 8, true)
        val lum = DoubleArray(72) { i -> val c = p.getPixel(i % 9, i / 9); Color.red(c) * 0.299 + Color.green(c) * 0.587 + Color.blue(c) * 0.114 }
        return IdentidadeBem.dHash(lum)
    }
}
