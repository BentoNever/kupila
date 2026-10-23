package ao.kupila.gestaonegocios.ui.anunciar

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ao.kupila.gestaonegocios.dados.ContaActiva
import ao.kupila.gestaonegocios.dados.ajustes.AjustesRepositorio
import ao.kupila.gestaonegocios.dados.anuncios.AnunciosRepositorio
import ao.kupila.gestaonegocios.dados.anuncios.DocumentoGuardado
import ao.kupila.gestaonegocios.dados.anuncios.DocumentoNovo
import ao.kupila.gestaonegocios.dados.local.Geografia
import ao.kupila.gestaonegocios.dados.media.Imagens
import ao.kupila.gestaonegocios.dados.rascunhos.ConteudoRascunho
import ao.kupila.gestaonegocios.dados.rascunhos.DocRascunho
import ao.kupila.gestaonegocios.dados.rascunhos.RascunhosRepositorio
import ao.kupila.gestaonegocios.dados.rascunhos.SnapshotFormulario
import ao.kupila.gestaonegocios.dados.remoto.MensagensErro
import ao.kupila.gestaonegocios.dados.remoto.Origem
import ao.kupila.gestaonegocios.dados.transaccoes.TransaccoesRepositorio
import ao.kupila.gestaonegocios.dominio.anuncio.DetalhesCategoria
import ao.kupila.gestaonegocios.dominio.anuncio.FormularioAnuncio
import ao.kupila.gestaonegocios.dominio.anuncio.Inteligencia
import ao.kupila.gestaonegocios.dominio.anuncio.MeioRecebimento
import ao.kupila.gestaonegocios.dominio.anuncio.PreferenciasAlertaProduto
import ao.kupila.gestaonegocios.dominio.anuncio.ValidacaoAnuncio
import ao.kupila.gestaonegocios.dominio.anuncio.categoria
import ao.kupila.gestaonegocios.dominio.catalogo.Categoria
import ao.kupila.gestaonegocios.dominio.duplicados.AvaliacaoDuplicados
import ao.kupila.gestaonegocios.dominio.duplicados.DescritorBem
import ao.kupila.gestaonegocios.dominio.duplicados.IdentidadeBem
import ao.kupila.gestaonegocios.dominio.financeiro.CondicoesAluguer
import ao.kupila.gestaonegocios.dominio.financeiro.Dinheiro
import ao.kupila.gestaonegocios.dominio.financeiro.EntradaCalculo
import ao.kupila.gestaonegocios.dominio.financeiro.Isencoes
import ao.kupila.gestaonegocios.dominio.financeiro.Modalidade
import ao.kupila.gestaonegocios.dominio.financeiro.MotorFinanceiro
import ao.kupila.gestaonegocios.dominio.financeiro.PrazoMinimo
import ao.kupila.gestaonegocios.dominio.financeiro.Promocao
import ao.kupila.gestaonegocios.dominio.financeiro.ResumoFinanceiro
import ao.kupila.gestaonegocios.dominio.financeiro.UnidadePreco
import ao.kupila.gestaonegocios.dominio.perfis.Papel
import ao.kupila.gestaonegocios.dominio.perfis.Permissoes
import ao.kupila.gestaonegocios.dominio.rascunhos.ConclusaoRascunho
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

private val Context.preferenciasAnunciar by preferencesDataStore("kupila_anunciar")

data class FotoForm(val dataUrl: String, val dHash: String, val sintetica: Boolean = false)

sealed interface Destino { data class Pagamento(val intentId: String) : Destino; data class Publicado(val id: String) : Destino; data object Guardado : Destino }

data class EstadoAnunciar(
    val form: FormularioAnuncio = FormularioAnuncio(),
    val fotos: List<FotoForm> = emptyList(),
    val documentos: List<DocRascunho> = emptyList(),
    val promocoes: Set<Promocao> = emptySet(),
    val rascunhoId: String = "",
    val edicaoDe: String? = null,
    val versaoEdicao: String = "",
    val politicaLida: Boolean = false,
    val aCarregar: Boolean = true,
    val aProcessarMedia: Int = 0,
    val aPublicar: Boolean = false,
    val progresso: String? = null,
    val erros: List<String> = emptyList(),
    val erroGeral: String? = null,
    val avisoSubstituicao: String? = null,
    val guardadoEm: Long? = null,
    val sugestao: String? = null,
    val destino: Destino? = null,
    val administrador: Boolean = false,
) {
    val percentagem get() = ConclusaoRascunho.percentagem(ConclusaoRascunho.Campos(form.titulo.isNotBlank(), form.preco != null, form.municipio.isNotBlank(),
        form.bairro.isNotBlank() && form.pontoReferencia.isNotBlank(), ValidacaoAnuncio.detalhes(form.detalhes).isEmpty(), fotos.isNotEmpty(), documentos.isNotEmpty(),
        form.recebimento != null, form.politicaAceite))
}

/**
 * Anunciar/Editar (secção 12; `handlePublish`, HTML 13723/15939) desenhado para o mínimo de toques (docs/principios-produto.md):
 * dedução pelo título, residência e recebimento pré-preenchidos, autosave contínuo, fotos optimizadas em segundo plano,
 * leitura das políticas lembrada por versão. Decisões humanas: confirmar dados, aceitar, pagar.
 */
@HiltViewModel
class AnunciarViewModel @Inject constructor(
    private val conta: ContaActiva,
    private val ajustes: AjustesRepositorio,
    private val anuncios: AnunciosRepositorio,
    private val transaccoes: TransaccoesRepositorio,
    private val rascunhos: RascunhosRepositorio,
    private val imagens: Imagens,
    val geografia: Geografia,
    @param:ApplicationContext private val contexto: Context,
    guardado: SavedStateHandle,
) : ViewModel() {
    private val _e = MutableStateFlow(EstadoAnunciar())
    val estado: StateFlow<EstadoAnunciar> = _e.asStateFlow()
    private val perfil get() = conta.recursos.value?.perfil
    private var manualCategoria = false
    private var autosave: Job? = null
    private val chavePolitica = stringPreferencesKey("politica_lida")
    private val chaveRecebimento = stringPreferencesKey("ultimo_recebimento")

    val resumo: StateFlow<ResumoFinanceiro?> = MutableStateFlow<ResumoFinanceiro?>(null)

    init {
        viewModelScope.launch {
            val rid: String? = guardado["rascunho"]
            val editar: String? = guardado["editar"]
            val p = perfil
            _e.update { it.copy(administrador = p?.papel == Papel.ADMINISTRADOR) }
            val lida = contexto.preferenciasAnunciar.data.first()[chavePolitica] == ao.kupila.gestaonegocios.dados.remoto.CentralSpark.POLITICA
            when {
                !editar.isNullOrBlank() -> runCatching { anuncios.carregarParaEdicao(editar) }.onSuccess { a ->
                    manualCategoria = true
                    _e.update { it.copy(form = a.form.copy(politicaAceite = true), fotos = a.fotos.map { f -> FotoForm(f, "") }, edicaoDe = a.id, versaoEdicao = a.actualizadoEm,
                        documentos = a.documentos.map { d -> DocRascunho(d.nome, d.mime, d.tamanho, d.sha256, null, d.fileId) }, rascunhoId = rascunhos.novoId(),
                        politicaLida = true, aCarregar = false) }
                }.onFailure { e -> _e.update { it.copy(aCarregar = false, erroGeral = MensagensErro.de(e)) } }
                !rid.isNullOrBlank() -> {
                    val c = rascunhos.obter(rid)
                    if (c != null) {
                        val (f, promos) = SnapshotFormulario.deSnapshot(c.snapshot)
                        manualCategoria = f.titulo.isNotBlank()
                        _e.update { it.copy(form = f, promocoes = promos, fotos = c.fotos.mapIndexed { i, x -> FotoForm(x, "", i in c.sinteticas) }, documentos = c.docs,
                            rascunhoId = rid, edicaoDe = c.edicaoDe, politicaLida = lida, aCarregar = false) }
                    } else novo(lida)
                }
                else -> novo(lida)
            }
            recalcular()
        }
    }

    /** Novo anúncio já preenchido com o que se sabe (perfil e último recebimento usado). */
    private suspend fun novo(lida: Boolean) {
        val p = perfil
        val ultimo = contexto.preferenciasAnunciar.data.first()[chaveRecebimento]
        val receb = ultimo?.let { if (it.startsWith("IBAN:")) MeioRecebimento.Iban(it.removePrefix("IBAN:")) else if (it.startsWith("EXPRESS:")) MeioRecebimento.MulticaixaExpress(it.removePrefix("EXPRESS:")) else null }
            ?: p?.telefonePrincipal?.takeIf { it.isNotBlank() }?.let { MeioRecebimento.MulticaixaExpress(it) }
        val subst = rascunhos.aSubstituirAoCriar()
        _e.update { it.copy(form = FormularioAnuncio(provincia = p?.residencia?.provincia.orEmpty().ifBlank { "Luanda" }, municipio = p?.residencia?.municipio.orEmpty(),
            bairro = p?.residencia?.bairro.orEmpty(), recebimento = receb), rascunhoId = rascunhos.novoId(), politicaLida = lida, aCarregar = false,
            avisoSubstituicao = subst?.let { s -> "Tem 5 cadastros inacabados: ao guardar este, “${s.titulo}” (o mais antigo) sai da lista." }) }
    }

    // ------------------------------------------------------------------ edição de campos
    private fun mudar(f: (FormularioAnuncio) -> FormularioAnuncio) {
        _e.update { it.copy(form = f(it.form), erros = emptyList(), erroGeral = null) }
        recalcular(); agendarAutosave()
    }

    fun titulo(v: String) {
        mudar { it.copy(titulo = v.take(120)) }
        val s = Inteligencia.deTitulo(v)
        val f = _e.value.form
        var novo = f
        val aplicadas = mutableListOf<String>()
        if (!manualCategoria && s.categoria != null && s.categoria != f.categoria && !temDetalhes(f)) { novo = novo.copy(categoria = s.categoria, detalhes = DetalhesCategoria.vazio(s.categoria)); aplicadas += s.categoria.rotulo }
        if (s.modalidade != null && s.modalidade != novo.modalidade && !manualCategoria) { novo = comModalidade(novo, s.modalidade); aplicadas += if (s.modalidade == Modalidade.VENDA) "venda" else "aluguer" }
        val d = novo.detalhes
        novo = novo.copy(detalhes = when (d) {
            is DetalhesCategoria.Viatura -> d.copy(subcategoria = d.subcategoria.ifBlank { s.subcategoria.orEmpty() }, marca = d.marca.ifBlank { s.marca.orEmpty() }, modelo = d.modelo.ifBlank { s.modelo.orEmpty() }, ano = d.ano ?: s.ano)
            is DetalhesCategoria.Motorizada -> d.copy(subcategoria = d.subcategoria.ifBlank { s.subcategoria.orEmpty() }, marca = d.marca.ifBlank { s.marca.orEmpty() }, modelo = d.modelo.ifBlank { s.modelo.orEmpty() }, ano = d.ano ?: s.ano)
            is DetalhesCategoria.Terreno -> d.copy(subcategoria = d.subcategoria.ifBlank { s.subcategoria.orEmpty() }, areaM2 = d.areaM2 ?: s.areaM2)
            is DetalhesCategoria.Imovel -> d.copy(subcategoria = d.subcategoria.ifBlank { s.subcategoria.orEmpty() })
            is DetalhesCategoria.Outro -> d.copy(subcategoria = d.subcategoria.ifBlank { s.subcategoria.orEmpty() }, marca = d.marca.ifBlank { s.marca.orEmpty() })
        })
        if (novo != f) { _e.update { it.copy(form = novo, sugestao = if (aplicadas.isNotEmpty()) "Preenchido pelo título: ${aplicadas.joinToString(", ")}. Pode alterar." else it.sugestao) }; recalcular() }
    }

    private fun temDetalhes(f: FormularioAnuncio) = f.detalhes != DetalhesCategoria.vazio(f.categoria)

    fun categoria(c: Categoria) { manualCategoria = true; mudar { if (it.categoria == c) it else it.copy(categoria = c, detalhes = DetalhesCategoria.vazio(c)) } }
    fun modalidade(m: Modalidade) { manualCategoria = true; mudar { comModalidade(it, m) } }
    private fun comModalidade(f: FormularioAnuncio, m: Modalidade) = if (m == Modalidade.ARRENDAMENTO)
        f.copy(modalidade = m, aluguer = f.aluguer ?: CondicoesAluguer(f.preco ?: Dinheiro.ZERO, UnidadePreco.MES, PrazoMinimo.TresMeses)) else f.copy(modalidade = m, aluguer = null)
    fun detalhes(d: DetalhesCategoria) = mudar { if (d.categoria() == it.categoria) it.copy(detalhes = d) else it }
    fun preco(texto: String) {
        val v = texto.filter { it.isDigit() || it == ',' || it == '.' }.replace(',', '.').toBigDecimalOrNull()?.takeIf { it.signum() > 0 }?.let(Dinheiro::de)
        mudar { it.copy(preco = v, aluguer = it.aluguer?.copy(precoUnidade = v ?: Dinheiro.ZERO)) }
    }
    fun aluguer(c: CondicoesAluguer) = mudar { it.copy(aluguer = c.copy(precoUnidade = it.preco ?: Dinheiro.ZERO)) }
    fun diferenciais(d: ao.kupila.gestaonegocios.dominio.anuncio.Diferenciais) = mudar { it.copy(diferenciais = d) }
    fun provincia(v: String) = mudar { it.copy(provincia = v, municipio = if (geografia.valido(v, it.municipio)) it.municipio else "") }
    fun municipio(v: String) = mudar { it.copy(municipio = v) }
    fun bairro(v: String) = mudar { it.copy(bairro = v.take(100)) }
    fun pontoReferencia(v: String) = mudar { it.copy(pontoReferencia = v.take(160)) }
    fun referenciaDocumento(v: String) = mudar { it.copy(referenciaDocumento = v.take(80)) }
    fun recebimento(r: MeioRecebimento) = mudar { it.copy(recebimento = r) }
    fun alertas(a: PreferenciasAlertaProduto) = mudar { it.copy(alertas = a) }
    fun promocao(p: Promocao, on: Boolean) { _e.update { it.copy(promocoes = if (on) it.promocoes + p else it.promocoes - p) }; recalcular(); agendarAutosave() }
    fun leituraPolitica() {
        _e.update { it.copy(politicaLida = true) }
        viewModelScope.launch { contexto.preferenciasAnunciar.edit { it[chavePolitica] = ao.kupila.gestaonegocios.dados.remoto.CentralSpark.POLITICA } }
    }
    fun aceitar(v: Boolean) { if (_e.value.politicaLida) mudar { it.copy(politicaAceite = v) } }
    fun limparSugestao() = _e.update { it.copy(sugestao = null) }

    // ------------------------------------------------------------------ media
    fun adicionarFotos(uris: List<Uri>) {
        val livres = ValidacaoAnuncio.MAX_FOTOS - _e.value.fotos.size
        if (livres <= 0) { _e.update { it.copy(erroGeral = "No máximo ${ValidacaoAnuncio.MAX_FOTOS} fotografias por produto.") }; return }
        uris.take(livres).forEach { uri ->
            _e.update { it.copy(aProcessarMedia = it.aProcessarMedia + 1) }
            viewModelScope.launch {
                runCatching { imagens.fotoInline(uri) }.onSuccess { (bytes, hash) ->
                    if (_e.value.fotos.any { it.dHash == hash && hash.isNotEmpty() }) _e.update { it.copy(erroGeral = "Esta fotografia já foi adicionada.") }
                    else _e.update { it.copy(fotos = (it.fotos + FotoForm(Origem.dataUrlJpeg(bytes), hash)).take(ValidacaoAnuncio.MAX_FOTOS)) }
                }.onFailure { e -> _e.update { it.copy(erroGeral = e.message) } }
                _e.update { it.copy(aProcessarMedia = it.aProcessarMedia - 1) }; agendarAutosave()
            }
        }
    }
    fun removerFoto(i: Int) { _e.update { it.copy(fotos = it.fotos.filterIndexed { j, _ -> j != i }) }; agendarAutosave() }
    fun capa(i: Int) { _e.update { e -> val l = e.fotos.toMutableList(); if (i in l.indices) { val f = l.removeAt(i); l.add(0, f) }; e.copy(fotos = l) }; agendarAutosave() }

    fun adicionarDocumentos(uris: List<Uri>) {
        val livres = ValidacaoAnuncio.MAX_DOCUMENTOS - _e.value.documentos.size
        uris.take(livres.coerceAtLeast(0)).forEach { uri ->
            _e.update { it.copy(aProcessarMedia = it.aProcessarMedia + 1) }
            viewModelScope.launch {
                runCatching {
                    val mime = contexto.contentResolver.getType(uri).orEmpty()
                    val nome = contexto.contentResolver.query(uri, null, null, null, null)?.use { c -> c.moveToFirst(); c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) } ?: "documento"
                    val bytes = when {
                        mime == "application/pdf" -> contexto.contentResolver.openInputStream(uri)!!.use { it.readBytes() }.also {
                            require(it.size <= ValidacaoAnuncio.MAX_BYTES_DOCUMENTO) { "O PDF “$nome” excede 2 MB." } }
                        mime.startsWith("image/") -> imagens.comprovativo(uri, ValidacaoAnuncio.MAX_BYTES_DOCUMENTO)
                        else -> error("Use JPG ou PDF para comprovar a titularidade.")
                    }
                    val f = File(contexto.filesDir, "rascunhos/${_e.value.rascunhoId}").apply { mkdirs() }.let { File(it, "${System.nanoTime()}.${if (mime == "application/pdf") "pdf" else "jpg"}") }
                    f.writeBytes(bytes)
                    DocRascunho(nome, if (mime == "application/pdf") "application/pdf" else "image/jpeg", bytes.size.toLong(), IdentidadeBem.sha256(bytes), f.absolutePath, null)
                }.onSuccess { d ->
                    if (_e.value.documentos.any { it.sha256 == d.sha256 }) _e.update { it.copy(erroGeral = "Este documento já foi adicionado.") }
                    else _e.update { it.copy(documentos = it.documentos + d) }
                }.onFailure { e -> _e.update { it.copy(erroGeral = e.message) } }
                _e.update { it.copy(aProcessarMedia = it.aProcessarMedia - 1) }; agendarAutosave()
            }
        }
    }
    fun removerDocumento(i: Int) { _e.update { e -> e.documentos.getOrNull(i)?.caminho?.let { File(it).delete() }; e.copy(documentos = e.documentos.filterIndexed { j, _ -> j != i }) }; agendarAutosave() }

    // ------------------------------------------------------------------ cálculo e autosave
    private fun recalcular() {
        val e = _e.value
        val p = e.form.preco ?: run { (resumo as MutableStateFlow).value = null; return }
        val admin = e.administrador
        (resumo as MutableStateFlow).value = MotorFinanceiro.calcular(EntradaCalculo(e.form.modalidade, p, e.form.aluguer, ajustes.ajustes.value.financeiro,
            if (admin) emptySet() else e.promocoes, if (admin) Isencoes(comissao = true, taxaAnuncio = true, promocoes = true, motivo = "Anúncio publicado pelo Administrador") else Isencoes.NENHUMA))
    }

    private fun agendarAutosave() {
        autosave?.cancel()
        autosave = viewModelScope.launch { delay(700); guardarRascunho() }
    }

    private suspend fun guardarRascunho() {
        val e = _e.value
        if (e.aCarregar || e.rascunhoId.isBlank()) return
        val util = e.form.titulo.isNotBlank() || e.form.preco != null || e.fotos.isNotEmpty() || e.documentos.isNotEmpty()
        if (!util) return
        val subst = rascunhos.aSubstituirAoCriar()
        if (subst != null && subst.id != e.rascunhoId && e.edicaoDe == null && e.guardadoEm == null) rascunhos.remover(listOf(subst.id))
        rascunhos.guardar(e.rascunhoId, e.form.titulo, e.percentagem,
            ConteudoRascunho(SnapshotFormulario.paraSnapshot(e.form, e.promocoes), e.fotos.map { it.dataUrl }, e.fotos.mapIndexedNotNull { i, f -> i.takeIf { f.sintetica } }.toSet(), e.documentos, e.edicaoDe))
        _e.update { it.copy(guardadoEm = System.currentTimeMillis()) }
    }

    /** Ao sair do formulário: guarda já e sincroniza com a nuvem (uma escrita, não uma por tecla). */
    fun aoSair() = viewModelScope.launch { autosave?.cancel(); guardarRascunho(); runCatching { rascunhos.sincronizarNuvem() } }

    // ------------------------------------------------------------------ publicar
    fun publicar() = viewModelScope.launch {
        val e = _e.value
        val p = perfil ?: return@launch
        if (e.aPublicar) return@launch
        val form = e.form.copy(numeroFotos = e.fotos.size, numeroDocumentos = e.documentos.size)
        val erros = ValidacaoAnuncio.formulario(form) + listOfNotNull("Aguarde: há fotografias ou documentos a processar.".takeIf { e.aProcessarMedia > 0 },
            "Complete o seu cadastro de Proprietário (foto, residência e contactos) antes de anunciar.".takeIf { p.papel == Papel.PROPRIETARIO && !Permissoes.perfilProprietarioCompleto(p) })
        val r = resumo.value
        val errosTodos = erros + listOfNotNull(r?.takeIf { !it.valido }?.let { "Condição financeira inválida com os ajustes actuais: ${it.problemas.first().mensagem}" })
        if (errosTodos.isNotEmpty() || r == null) { _e.update { it.copy(erros = errosTodos.ifEmpty { listOf("Indique o preço.") }) }; return@launch }
        _e.update { it.copy(aPublicar = true, erros = emptyList(), erroGeral = null, progresso = "A preparar o anúncio…") }
        runCatching {
            guardarRascunho()
            contexto.preferenciasAnunciar.edit { pr -> pr[chaveRecebimento] = when (val x = form.recebimento) { is MeioRecebimento.Iban -> "IBAN:${x.iban}"; is MeioRecebimento.MulticaixaExpress -> "EXPRESS:${x.telefone}"; null -> "" } }
            val agora = Origem.agoraIso()
            val id = e.edicaoDe ?: "KPL-${System.currentTimeMillis()}-${(100..999).random()}"
            // Identidade anti-duplicação (documento, ficheiros, fotos, descritor) e reivindicação central por transacção.
            val det = form.detalhes
            val descritor = DescritorBem(form.categoria.codigo, IdentidadeBem.normalizar(det.subcategoria), IdentidadeBem.normalizar(form.provincia), IdentidadeBem.normalizar(form.municipio),
                IdentidadeBem.normalizar(form.bairro), IdentidadeBem.tokensTitulo(form.titulo), descritorDetalhes(det))
            val identidade = AvaliacaoDuplicados.construir(descritor, form.referenciaDocumento, e.documentos.map { it.sha256 }, e.fotos.map { it.dHash }.filter { it.isNotBlank() })
            _e.update { it.copy(progresso = "A verificar duplicados…") }
            val conflitos = anuncios.reivindicarClaims(id, p.uid, identidade)
            if (conflitos.isNotEmpty()) error(if (conflitos.any { it.startsWith("D_") || it.startsWith("F_") })
                "A mesma referência ou o mesmo comprovativo de titularidade já está associado a outro anúncio. Se é o titular, peça revisão ao Administrador."
                else "As fotografias coincidem com as de outro anúncio. Use fotografias reais deste bem.")
            // Comprovativos novos → partes em productFiles (os já guardados mantêm o fileId).
            _e.update { it.copy(progresso = "A guardar comprovativos…") }
            val novos = e.documentos.filter { it.fileId == null && it.caminho != null }
            val guardados = anuncios.guardarDocumentos(id, p.uid, novos.map { DocumentoNovo(it.nome, it.mime, File(it.caminho!!).readBytes(), it.sha256) }) { m -> _e.update { s -> s.copy(progresso = m) } }
            val todosDocs = e.documentos.filter { it.fileId != null }.map { DocumentoGuardado(it.nome, it.mime, it.tamanho, it.fileId!!, 0, it.sha256) } + guardados
            var fotos = e.fotos.map { it.dataUrl }
            val papel = p.papel
            var pub = anuncios.publico(id, form, fotos, r, p, papel, if (e.edicaoDe != null) "" else "ACTIVE", identidade, agora)
            if (anuncios.tamanho(pub) > ao.kupila.gestaonegocios.dados.anuncios.AnunciosRepositorio.LIMITE_DOC_BYTES) {
                _e.update { it.copy(progresso = "A optimizar fotografias para caber no anúncio…") }
                fotos = fotos.map { f -> f.takeIf { it.startsWith("http") } ?: Origem.dataUrlJpeg(imagens.reduzirBytes(Origem.bytesDe(f) ?: ByteArray(0), 48 * 1024)) }
                pub = anuncios.publico(id, form, fotos, r, p, papel, if (e.edicaoDe != null) "" else "ACTIVE", identidade, agora)
                require(anuncios.tamanho(pub) <= 900 * 1024) { "O anúncio ficou demasiado grande. Reduza o número de fotografias." }
            }
            val priv = anuncios.privado(form, p, todosDocs, identidade, agora)
            when {
                e.edicaoDe != null -> { _e.update { it.copy(progresso = "A guardar alterações…") }; anuncios.guardarEdicao(id, pub, priv, e.versaoEdicao); Destino.Publicado(id) }
                papel == Papel.ADMINISTRADOR -> { anuncios.publicarIsento(id, pub, priv, "Anúncio publicado pelo Administrador (isenção de publicação)"); Destino.Publicado(id) }
                else -> {
                    _e.update { it.copy(progresso = "A preparar o pagamento da publicação…") }
                    anuncios.prepararPublicacaoPaga(id, pub, priv, r)
                    val i = transaccoes.intencao("LISTING", p, id, p.uid, r, ajustes.ajustes.value.entidadePagamento, form.titulo, draftId = e.rascunhoId)
                    rascunhos.guardar(e.rascunhoId, form.titulo, e.percentagem, ConteudoRascunho(SnapshotFormulario.paraSnapshot(form, e.promocoes), fotos, emptySet(), e.documentos, id), protegido = true)
                    Destino.Pagamento(i["id"] as String)
                }
            }
        }.onSuccess { d ->
            if (d is Destino.Publicado) rascunhos.concluir(e.rascunhoId)
            _e.update { it.copy(aPublicar = false, progresso = null, destino = d) }
        }.onFailure { x -> _e.update { it.copy(aPublicar = false, progresso = null, erroGeral = MensagensErro.de(x).takeIf { m -> m != MensagensErro.GENERICA } ?: (x.message ?: MensagensErro.GENERICA)) } }
    }

    fun destinoTratado() = _e.update { it.copy(destino = null) }

    private fun descritorDetalhes(d: DetalhesCategoria): Map<String, String> = when (d) {
        is DetalhesCategoria.Imovel -> mapOf("compartments" to (d.compartimentos?.toString() ?: ""), "repairStatus" to IdentidadeBem.normalizar(d.estadoReparos))
        is DetalhesCategoria.Terreno -> mapOf("areaM2" to (d.areaM2?.toPlainString() ?: ""), "purpose" to IdentidadeBem.normalizar(d.finalidade))
        is DetalhesCategoria.Viatura -> mapOf("brand" to IdentidadeBem.normalizar(d.marca), "model" to IdentidadeBem.normalizar(d.modelo), "year" to (d.ano?.toString() ?: ""))
        is DetalhesCategoria.Motorizada -> mapOf("brand" to IdentidadeBem.normalizar(d.marca), "model" to IdentidadeBem.normalizar(d.modelo), "year" to (d.ano?.toString() ?: ""))
        is DetalhesCategoria.Outro -> mapOf("brand" to IdentidadeBem.normalizar(d.marca), "model" to IdentidadeBem.normalizar(d.modelo))
    }

    override fun onCleared() { super.onCleared() }
}
