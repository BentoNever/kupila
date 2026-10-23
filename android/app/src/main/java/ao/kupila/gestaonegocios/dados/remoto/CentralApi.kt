package ao.kupila.gestaonegocios.dados.remoto

import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** Modo API (Blaze, futuro): as mesmas operações pelas Callable Functions de backend/functions. */
@Singleton
class CentralApi @Inject constructor(private val central: Central) : ServicoCentral {
    override val modo = "api"

    override suspend fun meuPerfil(): PerfilRemoto? {
        val r = central.chamar("contasMeuPerfil")
        if (r["existe"] != true) return null
        val p = r["perfil"] as Map<*, *>
        fun s(k: String) = p[k] as? String ?: ""
        return PerfilRemoto(s("uid"), s("role"), s("status"), s("name"), s("email"), s("phonePrimary"), s("phoneAlt"), s("province"),
            s("municipality"), s("neighborhood"), s("alertChannel").ifEmpty { "AUTO" }, p["photoPath"] as? String, p["createdBy"] as? String,
            p["policyVersion"] as? String, (p["version"] as? Number)?.toLong() ?: 1)
    }

    override suspend fun registar(p: PedidoRegisto) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: throw ErroCentral("unauthenticated", "Sessão inválida.")
        p.fotoJpeg?.let {
            FirebaseStorage.getInstance().reference.child("uploads/$uid/perfil/foto.jpg").putBytes(it, StorageMetadata.Builder().setContentType("image/jpeg").build()).await()
        }
        central.chamar("contasRegistar", mapOf("papel" to p.papel, "nome" to p.nome, "email" to p.email, "telefonePrincipal" to p.telefonePrincipal,
            "telefoneAlternativo" to p.telefoneAlternativo, "provincia" to p.provincia, "municipio" to p.municipio, "bairro" to p.bairro,
            "canalAlertas" to p.canal, "politicaVersao" to p.politicaVersao, "politicaLidaAteFim" to p.politicaLidaAteFim, "fotoCarregada" to (p.fotoJpeg != null)))
    }

    override suspend fun reivindicarSessao(instalacaoId: String, dispositivo: String, forcar: Boolean, sessaoActual: String?): ResultadoSessao {
        val s = central.chamar("sessoesReivindicar", mapOf("instalacaoId" to instalacaoId, "dispositivo" to dispositivo, "forcar" to forcar, "sessaoActual" to sessaoActual))
        (s["conflito"] as? Map<*, *>)?.let { c -> return ResultadoSessao.Conflito(c["dispositivo"] as? String ?: "Outro dispositivo", (c["vistoEm"] as? Number)?.toLong()) }
        return ResultadoSessao.Concedida(s["sessaoId"] as String)
    }

    override suspend fun sinalSessao(sessaoId: String): Boolean =
        central.chamar("sessoesSinal", mapOf("sessaoId" to sessaoId, "primeiroPlano" to true), 15)["vigente"] != false

    override suspend fun terminarSessao(sessaoId: String) { central.chamar("sessoesTerminar", mapOf("sessaoId" to sessaoId), 10) }
}
