package ao.kupila.gestaonegocios.dados.seguranca

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AES-256-GCM com chave no Android Keystore (padrão `Cofre` do PDV Digital, alias próprio).
 * Protege perfil em cache, identificação da sessão e dados privados em cache. Sem a chave (reinstalação,
 * outro telemóvel) os dados não abrem e volta-se a entrar — nunca se guarda a palavra-passe.
 */
@Singleton
class Cofre @Inject constructor() {
    private val alias = "kupila_gn_cofre_v1"

    private fun chave(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        kg.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build())
        return kg.generateKey()
    }

    fun cifrar(texto: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, chave())
        val iv = c.iv
        return Base64.encodeToString(byteArrayOf(iv.size.toByte()) + iv + c.doFinal(texto.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    fun decifrar(b64: String?): String? = runCatching {
        val d = Base64.decode(b64 ?: return null, Base64.NO_WRAP)
        val n = d[0].toInt()
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, chave(), GCMParameterSpec(128, d.copyOfRange(1, 1 + n)))
        String(c.doFinal(d.copyOfRange(1 + n, d.size)), Charsets.UTF_8)
    }.getOrNull()
}
