package ao.kupila.gestaonegocios.dados.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable data class SeccaoTexto(val titulo: String, val paragrafos: List<String>)
@Serializable data class Politicas(val versao: String, val fonte: String, val manualPoliticas: List<SeccaoTexto>, val reservaProtegida: List<SeccaoTexto>)
@Serializable data class PerguntaFaq(val pergunta: String, val resposta: String)
@Serializable data class Faq(val versao: String, val fonte: String, val administrador: List<PerguntaFaq>, val publico: List<PerguntaFaq>)

/**
 * Manual, políticas e FAQ extraídos da origem (`tools/extrair_conteudos.py`, `tools/extrair_faq.js`).
 * A designação histórica "Kupila" no texto é apresentada como o nome oficial do produto (secção 0).
 */
@Singleton
class Conteudos @Inject constructor(@param:ApplicationContext private val contexto: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private fun ler(caminho: String) = contexto.assets.open(caminho).bufferedReader(Charsets.UTF_8).use { it.readText() }

    val politicas: Politicas by lazy { json.decodeFromString(Politicas.serializer(), ler("conteudo/politicas-v1.json")).let(::renomear) }
    val faq: Faq by lazy { json.decodeFromString(Faq.serializer(), ler("conteudo/faq-v1.json")) }

    /** Versão das políticas a aceitar (mesma do servidor: `POLITICA_VIGENTE`). */
    val versaoPoliticas get() = politicas.versao

    private fun nome(t: String) = t.replace("Kupila AO", "Kupila-Gestão de negócios").replace(Regex("\\bKupila\\.ao\\b"), "Kupila-Gestão de negócios")
        .replace(Regex("\\bo Kupila\\b"), "o Kupila-Gestão de negócios").replace(Regex("\\bdo Kupila\\b"), "do Kupila-Gestão de negócios")
        .replace(Regex("\\bno Kupila\\b"), "no Kupila-Gestão de negócios")

    private fun renomear(p: Politicas) = p.copy(
        manualPoliticas = p.manualPoliticas.map { s -> SeccaoTexto(nome(s.titulo), s.paragrafos.map(::nome)) },
        reservaProtegida = p.reservaProtegida.filter { it.paragrafos.isNotEmpty() }.map { s -> SeccaoTexto(nome(s.titulo), s.paragrafos.map(::nome)) },
    )
    fun perguntas(administrador: Boolean): List<PerguntaFaq> = (if (administrador) faq.administrador else faq.publico)
        .map { PerguntaFaq(nome(it.pergunta), nome(it.resposta)) }
}
