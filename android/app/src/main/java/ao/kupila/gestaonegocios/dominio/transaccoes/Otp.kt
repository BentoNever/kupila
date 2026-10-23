package ao.kupila.gestaonegocios.dominio.transaccoes

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * OTP da vistoria (incons. C, decisão D-29). Gerado com SecureRandom (nunca Math.random) e guardado na reserva apenas como
 * PBKDF2-SHA256 com sal; o código em claro só vai para o comprovativo do Cliente. Reservas antigas com `otp` em claro
 * continuam a ser validadas por comparação em tempo constante.
 */
object Otp {
    const val ALGORITMO = "PBKDF2-SHA256-120000"
    private const val ITERACOES = 120_000
    const val MAX_TENTATIVAS = 5
    const val BLOQUEIO_MS = 15 * 60_000L
    private val aleatorio = SecureRandom()

    fun gerar(): String = (100_000 + aleatorio.nextInt(900_000)).toString()
    fun sal(): String = ByteArray(16).also(aleatorio::nextBytes).joinToString("") { "%02x".format(it) }

    fun hash(otp: String, sal: String): String {
        val spec = PBEKeySpec(otp.toCharArray(), sal.toByteArray(Charsets.UTF_8), ITERACOES, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded.joinToString("") { "%02x".format(it) }
    }

    fun confere(informado: String, otpHash: String?, sal: String?, otpLegado: String?): Boolean {
        val i = informado.trim()
        if (!Regex("^\\d{6}$").matches(i)) return false
        if (!otpHash.isNullOrBlank() && !sal.isNullOrBlank()) return MessageDigest.isEqual(hash(i, sal).toByteArray(), otpHash.toByteArray())
        if (!otpLegado.isNullOrBlank()) return MessageDigest.isEqual(i.toByteArray(), otpLegado.trim().toByteArray())
        return false
    }
}
