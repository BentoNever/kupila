package ao.kupila.gestaonegocios.dominio.actualizacao

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.net.URI

/** Distribuição oficial (repositório público decidido pelo utilizador — D-28). */
object DistribuicaoApp {
    const val DONO = "BentoNever"
    const val REPO = "kupila"
    const val RELEASES = "https://github.com/$DONO/$REPO/releases"
    const val API_ULTIMA = "https://api.github.com/repos/$DONO/$REPO/releases"
    /** Nome do APK publicado por pacote (produção ou variante .dev). */
    fun nomeApk(versao: String, pacote: String) = if (pacote.endsWith(".dev")) "kupila-gestao-negocios-$versao-dev.apk" else "kupila-gestao-negocios-$versao.apk"
}

/** Valida a publicação oficial antes de aceitar o endereço (portado de `PoliticaVersao` do PDV Digital). */
object PoliticaVersao {
    enum class Situacao { NOVA_VERSAO, ACTUALIZADA, PUBLICACAO_ANTERIOR }
    fun situacao(instalada: Long, publicada: Long) = when {
        publicada > instalada -> Situacao.NOVA_VERSAO
        publicada == instalada -> Situacao.ACTUALIZADA
        else -> Situacao.PUBLICACAO_ANTERIOR
    }

    fun validar(codigo: Long, nome: String, apk: String, tamanho: Long, sha256: String?, pacote: String) {
        require(codigo > 0 && nome.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+(-dev[0-9]*)?"))) { "Versão publicada inválida." }
        val uri = URI(apk)
        val tag = "v$nome"   // a variante .dev publica versionName com o sufixo "-dev" (ex.: v1.0.1-dev)
        require(uri.scheme == "https" && uri.host == "github.com" && uri.userInfo == null && uri.port == -1 && uri.rawQuery == null && uri.rawFragment == null &&
            uri.rawPath == "/${DistribuicaoApp.DONO}/${DistribuicaoApp.REPO}/releases/download/$tag/${DistribuicaoApp.nomeApk(nome.removeSuffix("-dev"), pacote)}") {
            "O endereço não corresponde à distribuição oficial desta versão."
        }
        require(tamanho in 1..250L * 1024 * 1024 && sha256?.matches(Regex("[a-fA-F0-9]{64}")) == true) { "Publicação sem tamanho ou assinatura de conteúdo válida." }
    }
}

/**
 * Lê um release do GitHub e devolve a linha `KUPILA-MANIFEST:` validada (portado de `PublicacaoGithub`). Produção só aceita releases
 * estáveis; a variante .dev aceita pré-releases do seu próprio pacote.
 */
object PublicacaoGithub {
    const val PREFIXO = "KUPILA-MANIFEST: "

    fun manifesto(release: kotlinx.serialization.json.JsonObject, pacote: String): String? = runCatching {
        val dev = pacote.endsWith(".dev")
        require(release["draft"]?.jsonPrimitive?.booleanOrNull == false)
        require(dev || release["prerelease"]?.jsonPrimitive?.booleanOrNull == false)
        val linha = release["body"]!!.jsonPrimitive.content.lineSequence().first { it.startsWith(PREFIXO) }.removePrefix(PREFIXO)
        val m = Json.parseToJsonElement(linha).jsonObject
        require(m["pacote"]!!.jsonPrimitive.content == pacote)
        val codigo = m["versionCode"]!!.jsonPrimitive.long
        val nome = m["versionName"]!!.jsonPrimitive.content
        val apk = m["apk"]!!.jsonPrimitive.content
        val tamanho = m["tamanho"]!!.jsonPrimitive.long
        val sha = m["sha256"]!!.jsonPrimitive.content
        PoliticaVersao.validar(codigo, nome, apk, tamanho, sha, pacote)
        require(release["tag_name"]!!.jsonPrimitive.content == "v$nome")
        val asset = release["assets"]!!.jsonArray.map { it.jsonObject }.single { it["name"]?.jsonPrimitive?.content == DistribuicaoApp.nomeApk(nome.removeSuffix("-dev"), pacote) }
        require(asset["browser_download_url"]!!.jsonPrimitive.content == apk && asset["size"]!!.jsonPrimitive.long == tamanho)
        asset["digest"]?.jsonPrimitive?.content?.let { require(it == "sha256:$sha") }
        linha
    }.getOrNull()

    /** Escolhe, entre os releases devolvidos pela API, o manifesto válido com maior versionCode. */
    fun melhor(texto: String, pacote: String): String? = runCatching {
        Json.parseToJsonElement(texto).jsonArray.mapNotNull { manifesto(it.jsonObject, pacote) }
            .maxByOrNull { Json.parseToJsonElement(it).jsonObject["versionCode"]!!.jsonPrimitive.long }
    }.getOrNull()
}

object ValidacaoActualizacao {
    fun verificar(pacote: String, esperado: String, versao: Long, instalada: Long, assinaturas: Set<String>, oficiais: Set<String>) {
        require(pacote == esperado) { "Este ficheiro não é uma actualização do Kupila-Gestão de negócios." }
        require(assinaturas.isNotEmpty() && assinaturas == oficiais) { "A assinatura não corresponde à versão oficial instalada." }
        require(versao > instalada) { "Esta versão já está instalada ou é anterior. Escolha uma versão mais recente." }
    }
}
