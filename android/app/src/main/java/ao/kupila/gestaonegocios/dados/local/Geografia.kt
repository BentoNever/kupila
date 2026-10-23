package ao.kupila.gestaonegocios.dados.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable private data class GeoFicheiro(val versao: Int, val fonte: String, val provincias: List<GeoProvincia>)
@Serializable data class GeoProvincia(val nome: String, val municipios: List<String>)

/**
 * Divisão administrativa versionada (21 províncias, 326 municípios — Lei n.º 14/24), extraída da origem
 * por `tools/extrair_conteudos.py`. A mesma versão é validada pelo Central.
 */
@Singleton
class Geografia @Inject constructor(@param:ApplicationContext private val contexto: Context) {
    private val dados: GeoFicheiro by lazy {
        Json { ignoreUnknownKeys = true }.decodeFromString(GeoFicheiro.serializer(),
            contexto.assets.open("geografia-ao-v1.json").bufferedReader(Charsets.UTF_8).use { it.readText() })
    }
    val versao get() = dados.versao
    val provincias: List<String> get() = dados.provincias.map { it.nome }
    fun municipios(provincia: String): List<String> = dados.provincias.firstOrNull { it.nome == provincia }?.municipios ?: emptyList()
    fun valido(provincia: String, municipio: String) = municipio in municipios(provincia)
}
