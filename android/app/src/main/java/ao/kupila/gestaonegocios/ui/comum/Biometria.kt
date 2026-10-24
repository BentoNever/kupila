package ao.kupila.gestaonegocios.ui.comum

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

private const val AUTENTICADORES = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK

/** Há impressão digital/rosto registado neste telemóvel (como no PDV Digital). */
fun biometriaDisponivel(contexto: Context): Boolean =
    BiometricManager.from(contexto).canAuthenticate(AUTENTICADORES) == BiometricManager.BIOMETRIC_SUCCESS

/** Pede a impressão digital/rosto (diálogo do sistema). "Usar a senha" fecha e fica a senha. */
fun pedirBiometria(contexto: Context, titulo: String, subtitulo: String, sucesso: () -> Unit) {
    val actividade = contexto as? FragmentActivity ?: return
    BiometricPrompt(actividade, ContextCompat.getMainExecutor(actividade), object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(r: BiometricPrompt.AuthenticationResult) = sucesso()
    }).authenticate(
        BiometricPrompt.PromptInfo.Builder().setTitle(titulo).setSubtitle(subtitulo).setNegativeButtonText("Usar a senha")
            .setAllowedAuthenticators(AUTENTICADORES).build(),
    )
}
