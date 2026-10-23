package ao.kupila.gestaonegocios.dados.remoto

import android.content.Context
import ao.kupila.gestaonegocios.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage

/**
 * Inicializa o Firebase sem `google-services.json` embutido (padrão do PDV Digital, `PdvDigitalApp`).
 * Três estados possíveis, sempre explícitos para a UI — nunca um "sucesso" sem serviço:
 *  - [Estado.Configurado]: valores de `kupila-firebase.properties` (projecto real ou de teste);
 *  - [Estado.Emulador]: build de depuração sem configuração → Firebase Emulator (projecto demo-kupila);
 *  - [Estado.NaoConfigurado]: release sem configuração → a app informa que o serviço não está disponível.
 */
object ArranqueFirebase {

    sealed interface Estado {
        data object Configurado : Estado
        data class Emulador(val host: String) : Estado
        data object NaoConfigurado : Estado
    }

    const val PROJECTO_EMULADOR = "demo-kupila"

    @Volatile var estado: Estado = Estado.NaoConfigurado
        private set

    fun iniciar(c: Context) {
        if (FirebaseApp.getApps(c).isNotEmpty()) return
        val configurado = !BuildConfig.USAR_EMULADOR && BuildConfig.FIREBASE_PROJECTO.isNotBlank() && BuildConfig.FIREBASE_APP_ID.isNotBlank() && BuildConfig.FIREBASE_API_KEY.isNotBlank()
        when {
            configurado -> {
                FirebaseApp.initializeApp(c, FirebaseOptions.Builder()
                    .setProjectId(BuildConfig.FIREBASE_PROJECTO)
                    .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                    .setApiKey(BuildConfig.FIREBASE_API_KEY)
                    .setStorageBucket(BuildConfig.FIREBASE_STORAGE.ifBlank { null })
                    .setGcmSenderId(BuildConfig.FIREBASE_SENDER.ifBlank { null })
                    .build())
                estado = Estado.Configurado
            }
            BuildConfig.USAR_EMULADOR -> {
                FirebaseApp.initializeApp(c, FirebaseOptions.Builder()
                    .setProjectId(PROJECTO_EMULADOR)
                    .setApplicationId("1:000000000000:android:0000000000000000")
                    .setApiKey("emulador-local")
                    .setStorageBucket("$PROJECTO_EMULADOR.appspot.com")
                    .build())
                val h = BuildConfig.EMULADOR_HOST
                FirebaseAuth.getInstance().useEmulator(h, 9099)
                FirebaseFirestore.getInstance().useEmulator(h, 8080)
                FirebaseStorage.getInstance().useEmulator(h, 9199)
                FirebaseFunctions.getInstance(BuildConfig.FUNCTIONS_REGIAO).useEmulator(h, 5001)
                estado = Estado.Emulador(h)
            }
            else -> estado = Estado.NaoConfigurado
        }
    }

    val disponivel get() = estado != Estado.NaoConfigurado
}
