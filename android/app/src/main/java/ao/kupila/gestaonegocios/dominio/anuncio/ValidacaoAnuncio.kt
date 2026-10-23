package ao.kupila.gestaonegocios.dominio.anuncio

import ao.kupila.gestaonegocios.dominio.catalogo.Categoria
import ao.kupila.gestaonegocios.dominio.financeiro.CondicoesAluguer
import ao.kupila.gestaonegocios.dominio.financeiro.Dinheiro
import ao.kupila.gestaonegocios.dominio.financeiro.Modalidade
import ao.kupila.gestaonegocios.dominio.financeiro.RegrasAluguer
import ao.kupila.gestaonegocios.dominio.perfis.ValidacaoContacto

/** Formulário completo de anúncio (cadastro e edição). Campos privados vão para a projecção privada. */
data class FormularioAnuncio(
    val categoria: Categoria = Categoria.IMOVEL,
    val modalidade: Modalidade = Modalidade.VENDA,
    val titulo: String = "",
    val preco: Dinheiro? = null,
    val aluguer: CondicoesAluguer? = null,
    val detalhes: DetalhesCategoria = DetalhesCategoria.Imovel(),
    val diferenciais: Diferenciais = Diferenciais(),
    val provincia: String = "",
    val municipio: String = "",
    val bairro: String = "",
    val pontoReferencia: String = "",
    val referenciaDocumento: String = "",
    val numeroFotos: Int = 0,
    val numeroDocumentos: Int = 0,
    val recebimento: MeioRecebimento? = null,
    val alertas: PreferenciasAlertaProduto = PreferenciasAlertaProduto(),
    val politicaAceite: Boolean = false,
)

/**
 * Campos que um registo LEGADO não tinha e que, ao editar, não devem ser exigidos (1.0.193, HTML 25738-25747):
 * a edição não pode bloquear por um campo que nunca existiu, nem apagar dado remoto por vazio.
 */
data class CamposLegadoAusentes(val subcategoria: Boolean = false, val condicao: Boolean = false, val notas: Boolean = false)

object ValidacaoAnuncio {
    const val MAX_FOTOS = 5                       // MAX_LISTING_IMAGES (8496)
    const val MAX_BYTES_FOTO = 1_258_291L         // round(1.20 × 1024 × 1024) (8497)
    const val MAX_DOCUMENTOS = 10                 // MAX_OWNERSHIP_DOCUMENTS (8498)
    const val MAX_BYTES_DOCUMENTO = 2L * 1024 * 1024
    const val MAX_BYTES_COMPROVATIVO_PAGAMENTO = 4L * 1024 * 1024
    const val MAX_BYTES_EVIDENCIA_REEMBOLSO = 600L * 1024
    const val MAX_BYTES_FOTO_PERFIL = 2L * 1024 * 1024

    private val iban = Regex("^AO\\d{2}\\d{21}$")
    fun ibanValido(v: String) = iban.matches(v.replace(".", "").replace(" ", "").uppercase())

    /** Valida as características (mensagens exactas de `collectPropertyDetails`, 13650-13657). */
    fun detalhes(d: DetalhesCategoria, legado: CamposLegadoAusentes = CamposLegadoAusentes()): List<String> {
        val e = mutableListOf<String>()
        if (d.subcategoria.isBlank() && !legado.subcategoria) e += "Seleccione a subcategoria do produto."
        when (d) {
            is DetalhesCategoria.Imovel -> {
                if ((d.compartimentos ?: 0) < 1) e += "Informe uma quantidade válida de compartimentos do imóvel."
                if (d.estadoReparos.isBlank()) e += "Informe se o imóvel precisa ou não de reparos."
            }
            is DetalhesCategoria.Terreno -> {
                if (d.areaM2 == null || d.areaM2.signum() <= 0) e += "Informe a área do terreno em metros quadrados."
                if (d.finalidade.isBlank()) e += "Seleccione a finalidade do terreno."
            }
            is DetalhesCategoria.Viatura ->
                if (d.marca.isBlank() || d.modelo.isBlank() || (d.ano ?: 0) !in 1950..2100) e += "Informe marca, modelo e ano válidos da viatura."
            is DetalhesCategoria.Motorizada ->
                if (d.marca.isBlank() || d.modelo.isBlank() || (d.ano ?: 0) !in 1950..2100) e += "Informe marca, modelo e ano válidos da motorizada."
            is DetalhesCategoria.Outro ->
                if ((d.condicao.isBlank() && !legado.condicao) || (d.notas.isBlank() && !legado.notas)) e += "Informe o estado e uma descrição técnica do produto."
        }
        return e
    }

    /** Validação completa antes de preparar publicação. Devolve todas as falhas (o formulário é preservado). */
    fun formulario(f: FormularioAnuncio, legado: CamposLegadoAusentes = CamposLegadoAusentes(), exigirAceite: Boolean = true): List<String> {
        val e = mutableListOf<String>()
        if (f.detalhes.categoria() != f.categoria) e += "As características não correspondem à categoria escolhida."
        if (f.detalhes.subcategoria.isNotBlank() && f.detalhes.subcategoria !in f.categoria.subcategorias)
            e += "A subcategoria não pertence à categoria ${f.categoria.rotulo}."
        e += detalhes(f.detalhes, legado)
        if (f.titulo.isBlank() || f.preco == null || !f.preco.positivo || f.municipio.isBlank() || f.provincia.isBlank())
            e += "Preencha título, preço, Província e Município do produto."
        if (f.titulo.length > 120) e += "O título deve ter no máximo 120 caracteres."
        if (f.bairro.isBlank() || f.pontoReferencia.isBlank()) e += "Informe obrigatoriamente o Bairro e o Ponto de Referência do produto."
        if (f.modalidade == Modalidade.ARRENDAMENTO) {
            val c = f.aluguer
            if (c == null) e += "Defina as condições do aluguer."
            else {
                RegrasAluguer.termos(c).exceptionOrNull()?.message?.let { e += it }
                if (f.preco != null && c.precoUnidade != f.preco) e += "O preço por unidade não coincide com as condições de aluguer."
            }
        }
        if (f.numeroFotos < 1) e += "Adicione pelo menos uma fotografia do pertence."
        if (f.numeroFotos > MAX_FOTOS) e += "No máximo $MAX_FOTOS fotografias por produto."
        if (f.referenciaDocumento.isBlank()) e += "Informe o número/referência do documento de titularidade."
        if (f.numeroDocumentos < 1) e += "Anexe pelo menos um documento JPG/PDF que comprove a titularidade do produto. Pode carregar até 10 ficheiros, com máximo de 2 MB cada."
        if (f.numeroDocumentos > MAX_DOCUMENTOS) e += "No máximo $MAX_DOCUMENTOS comprovativos de titularidade."
        when (val r = f.recebimento) {
            null -> e += "Indique a forma de recebimento."
            is MeioRecebimento.Iban -> when {
                r.iban.isBlank() -> e += "Informe o IBAN para a forma de pagamento seleccionada."
                // Correcção registada: a origem só exigia campo não vazio; um IBAN mal formado impede a liberação.
                !ibanValido(r.iban) -> e += "O IBAN deve ter o formato angolano: AO seguido de 23 dígitos."
            }
            is MeioRecebimento.MulticaixaExpress -> if (!ValidacaoContacto.telefoneValido(r.telefone)) e += "Informe um número Multicaixa Express válido com 9 dígitos."
        }
        if (exigirAceite && !f.politicaAceite) e += "Leia e aceite as Políticas de Uso e as Regras de Intermediação aplicáveis a este anúncio."
        return e.distinct()
    }

    /**
     * Mudança de categoria/modalidade: indica que informação incompatível será descartada, para confirmar
     * antes (secção 12) em vez de manter campos ocultos inconsistentes.
     */
    fun perdasAoMudar(de: FormularioAnuncio, categoria: Categoria, modalidade: Modalidade): List<String> {
        val perdas = mutableListOf<String>()
        if (categoria != de.categoria && de.detalhes != DetalhesCategoria.vazio(de.categoria))
            perdas += "As características de ${de.categoria.rotulo} preenchidas serão removidas."
        if (modalidade == Modalidade.VENDA && de.modalidade == Modalidade.ARRENDAMENTO && de.aluguer != null)
            perdas += "As condições de aluguer (unidade e tempo mínimo) serão removidas."
        return perdas
    }
}
