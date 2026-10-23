package ao.kupila.gestaonegocios.dominio.anuncio

import ao.kupila.gestaonegocios.dominio.catalogo.Categoria
import ao.kupila.gestaonegocios.dominio.catalogo.Envolvimento
import ao.kupila.gestaonegocios.dominio.catalogo.PromocaoActiva
import ao.kupila.gestaonegocios.dominio.financeiro.CondicoesAluguer
import ao.kupila.gestaonegocios.dominio.financeiro.Dinheiro
import ao.kupila.gestaonegocios.dominio.financeiro.Modalidade
import ao.kupila.gestaonegocios.dominio.perfis.CanalAlerta
import java.time.Instant

/** Estados do anúncio (separados de intenção, reserva e pagamento — secção 21). */
enum class EstadoAnuncio(val codigo: String, val rotulo: String) {
    PAGAMENTO_PENDENTE("PENDING_PAYMENT", "A aguardar pagamento"),
    ACTIVO("ACTIVE", "Activo"),
    RESERVADO("RESERVED", "Reservado"),
    CONCLUIDO("COMPLETED", "Concluído"),
    DESANUNCIADO("UNPUBLISHED", "Desanunciado"),
    ARQUIVADO("ARCHIVED", "Arquivado");
    companion object { fun de(c: String?) = entries.firstOrNull { it.codigo == c } }
    val disponivelParaReserva get() = this == ACTIVO
}

/** Estado/reparos do imóvel (`pub-repair-status`). */
val ESTADOS_REPARO = listOf("Não precisa de reparos", "Pequenos reparos", "Precisa de reparos", "Em reparação")
val QUALIDADES_IMOVEL = listOf("Mosaicada", "Pintada", "Climatizada", "Mobilada")
val FINALIDADES_TERRENO = listOf("Residencial", "Comercial", "Agrícola", "Industrial", "Mista")
val TOPOGRAFIAS = listOf("Plano", "Inclinado", "Irregular")
val ACESSOS_TERRENO = listOf("Asfaltado", "Terra batida", "Outro")
val COMBUSTIVEIS = listOf("Gasolina", "Gasóleo", "Híbrido", "Eléctrico")
val TRANSMISSOES = listOf("Manual", "Automática")
val CONDICOES_OUTRO = listOf("Novo", "Seminovo", "Usado - bom estado", "Usado - precisa reparação")
val PERFIS_LOCALIZACAO = listOf("Zona urbana", "Zona suburbana", "Zona rural", "Condomínio", "Zona residencial", "Zona comercial", "Centro urbano")

/** Características por categoria. Nomes de campos = chaves de `categoryDetails` da origem (HTML 13641-13648). */
sealed interface DetalhesCategoria {
    val subcategoria: String
    val notas: String

    data class Imovel(override val subcategoria: String = "", val compartimentos: Int? = null, val qualidades: Set<String> = emptySet(),
                      val estadoReparos: String = "", override val notas: String = "") : DetalhesCategoria
    data class Terreno(override val subcategoria: String = "", val areaM2: java.math.BigDecimal? = null, val finalidade: String = "",
                       val topografia: String = "", val acesso: String = "", override val notas: String = "") : DetalhesCategoria
    data class Viatura(override val subcategoria: String = "", val marca: String = "", val modelo: String = "", val ano: Int? = null,
                       val quilometragem: Long? = null, val combustivel: String = "", val transmissao: String = "", override val notas: String = "") : DetalhesCategoria
    data class Motorizada(override val subcategoria: String = "", val marca: String = "", val modelo: String = "", val ano: Int? = null,
                          val cilindrada: Int? = null, val quilometragem: Long? = null, override val notas: String = "") : DetalhesCategoria
    data class Outro(override val subcategoria: String = "", val condicao: String = "", val quantidade: Int = 1, val marca: String = "",
                     val modelo: String = "", override val notas: String = "") : DetalhesCategoria

    companion object {
        fun vazio(c: Categoria): DetalhesCategoria = when (c) {
            Categoria.IMOVEL -> Imovel(); Categoria.TERRENO -> Terreno(); Categoria.VIATURA -> Viatura()
            Categoria.MOTORIZADA -> Motorizada(); Categoria.OUTRO -> Outro()
        }
    }
}

fun DetalhesCategoria.categoria(): Categoria = when (this) {
    is DetalhesCategoria.Imovel -> Categoria.IMOVEL; is DetalhesCategoria.Terreno -> Categoria.TERRENO
    is DetalhesCategoria.Viatura -> Categoria.VIATURA; is DetalhesCategoria.Motorizada -> Categoria.MOTORIZADA
    is DetalhesCategoria.Outro -> Categoria.OUTRO
}

/** Diferenciais (`pub-location-profile`, `pub-condominium-name`, `pub-interest-highlights`). */
data class Diferenciais(val perfilLocalizacao: String = "", val condominio: String = "", val pontosFortes: String = "")

/** Preferências de alerta por produto (7104-7113). */
data class PreferenciasAlertaProduto(
    val activo: Boolean = true,
    val reserva: Boolean = true,
    val interesse: Boolean = true,
    /** null = herdar do perfil ("PROFILE"). */
    val canal: CanalAlerta? = null,
)

/** Fotografia pública do anúncio. [sintetica] = ilustração gerada por IA, sempre identificada (secção 14). */
data class Fotografia(val url: String, val miniatura: String? = null, val sintetica: Boolean = false, val ordem: Int = 0)

/**
 * Projecção PÚBLICA do anúncio: o que qualquer pessoa autenticada pode ver e o que pode ir para o cache público.
 * Não contém bairro, ponto de referência, contactos, titularidade nem dados de recebimento (secção 11).
 */
data class AnuncioPublico(
    val id: String,
    val donoId: String,
    val titulo: String,
    val categoria: Categoria,
    val modalidade: Modalidade,
    val preco: Dinheiro,
    val aluguer: CondicoesAluguer?,
    val provincia: String,
    val municipio: String,
    val detalhes: DetalhesCategoria,
    val diferenciais: Diferenciais,
    val fotos: List<Fotografia>,
    val estado: EstadoAnuncio,
    val criadoEm: Instant,
    val actualizadoEm: Instant,
    val versao: Long,
    val envolvimento: Envolvimento = Envolvimento(),
    val promocao: PromocaoActiva? = null,
    /** Referência ao snapshot financeiro aplicado na publicação (secção 18). */
    val snapshotFinanceiroId: String? = null,
    val publicadoPorAdministrador: Boolean = false,
    val verificacaoTitularidade: EstadoVerificacao = EstadoVerificacao.PENDENTE,
)

enum class EstadoVerificacao { PENDENTE, VERIFICADO_SEM_DUPLICADO, REVISAO_MANUAL }

/** Recebimento do Proprietário (`pub-payout-method`). Nunca público; nunca automático ao Cliente. */
sealed interface MeioRecebimento {
    data class Iban(val iban: String) : MeioRecebimento
    data class MulticaixaExpress(val telefone: String) : MeioRecebimento
}

/** Documento de titularidade anexado (privado; integridade por SHA-256). */
data class DocumentoTitularidade(val nome: String, val tipoMime: String, val tamanho: Long, val sha256: String, val caminhoRemoto: String?)

/** Projecção PRIVADA: só titular, Administrador competente e, parcialmente, Cliente com reserva confirmada. */
data class AnuncioPrivado(
    val id: String,
    val bairro: String,
    val pontoReferencia: String,
    val referenciaDocumento: String,
    val documentos: List<DocumentoTitularidade>,
    val recebimento: MeioRecebimento?,
    val alertas: PreferenciasAlertaProduto,
    val versao: Long,
)

/** Dados de encontro revelados ao participante de reserva confirmada (mínimo necessário — secção 11). */
data class DadosEncontro(val bairro: String, val pontoReferencia: String, val nomeProprietario: String, val contactoProprietario: String)
