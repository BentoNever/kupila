package ao.kupila.gestaonegocios.dados.anuncios

import ao.kupila.gestaonegocios.dados.remoto.CentralSpark
import ao.kupila.gestaonegocios.dados.remoto.ErroCentral
import ao.kupila.gestaonegocios.dados.remoto.Origem
import ao.kupila.gestaonegocios.dominio.anuncio.DetalhesCategoria
import ao.kupila.gestaonegocios.dominio.anuncio.FormularioAnuncio
import ao.kupila.gestaonegocios.dominio.anuncio.MeioRecebimento
import ao.kupila.gestaonegocios.dominio.anuncio.PreferenciasAlertaProduto
import ao.kupila.gestaonegocios.dominio.catalogo.Categoria
import ao.kupila.gestaonegocios.dominio.duplicados.Identidade
import ao.kupila.gestaonegocios.dominio.financeiro.CondicoesAluguer
import ao.kupila.gestaonegocios.dominio.financeiro.Dinheiro
import ao.kupila.gestaonegocios.dominio.financeiro.Modalidade
import ao.kupila.gestaonegocios.dominio.financeiro.PrazoMinimo
import ao.kupila.gestaonegocios.dominio.financeiro.ResumoFinanceiro
import ao.kupila.gestaonegocios.dominio.financeiro.UnidadeDuracao
import ao.kupila.gestaonegocios.dominio.financeiro.UnidadePreco
import ao.kupila.gestaonegocios.dominio.perfis.CanalAlerta
import ao.kupila.gestaonegocios.dominio.perfis.Papel
import ao.kupila.gestaonegocios.dominio.perfis.Perfil
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** Comprovativo de titularidade a enviar (bytes já validados: JPG/PDF ≤ 2 MB). */
class DocumentoNovo(val nome: String, val mime: String, val bytes: ByteArray, val sha256: String)

/** Metadados de documento guardado em partes (`prepareOwnershipDocumentsForFirestore`, HTML 12176). */
data class DocumentoGuardado(val nome: String, val mime: String, val tamanho: Long, val fileId: String, val partes: Int, val sha256: String)

/** Anúncio carregado para edição: formulário + fotos (data URL/https) + documentos já guardados + versão. */
data class AnuncioEditavel(val id: String, val form: FormularioAnuncio, val fotos: List<String>, val documentos: List<DocumentoGuardado>,
                           val estado: String, val actualizadoEm: String, val donoId: String, val bruto: Map<String, Any?>)

/**
 * Escritas de anúncios no esquema da origem (D-29), dentro das regras de produção:
 *  · Proprietário: `listingDrafts` + `listingDraftPrivate` + comprovativos em `productFiles` → pagamento → o Administrador publica;
 *  · Administrador: publica directamente em `products` + `productPrivate` (isento de taxa, `managerPublicationIsAlwaysFree`);
 *  · Edição: `products`/`productPrivate` com verificação de versão (`updatedAt`) — sem apagar dados remotos ausentes.
 */
@Singleton
class AnunciosRepositorio @Inject constructor(private val central: CentralSpark) {
    private val db get() = FirebaseFirestore.getInstance()

    companion object {
        /** `ensureListingPayloadFitsFirestore` (HTML 12155): margem para o limite de ~1 MiB por documento. */
        const val LIMITE_DOC_BYTES = 780 * 1024
        const val PARTE_CARACTERES = 650_000
    }

    // ------------------------------------------------------------------ payloads da origem
    private fun detalhesOrigem(d: DetalhesCategoria, f: FormularioAnuncio, mesesSinal: Int): Map<String, Any?> {
        val base = mutableMapOf<String, Any?>("subcategory" to d.subcategoria, "notes" to d.notas,
            "locationProfile" to f.diferenciais.perfilLocalizacao, "condominiumName" to f.diferenciais.condominio,
            "interestHighlights" to f.diferenciais.pontosFortes, "ownerRequirementDepositMonths" to mesesSinal)
        when (d) {
            is DetalhesCategoria.Imovel -> base += mapOf("compartments" to (d.compartimentos ?: 0), "qualities" to d.qualidades.toList(), "repairStatus" to d.estadoReparos,
                "rentalPeriod" to (f.aluguer?.prazo?.codigoOrigem ?: ""))
            is DetalhesCategoria.Terreno -> base += mapOf("areaM2" to (d.areaM2?.toDouble() ?: 0.0), "purpose" to d.finalidade, "topography" to d.topografia, "access" to d.acesso)
            is DetalhesCategoria.Viatura -> base += mapOf("brand" to d.marca, "model" to d.modelo, "year" to (d.ano ?: 0), "mileage" to (d.quilometragem ?: 0), "fuel" to d.combustivel, "transmission" to d.transmissao)
            is DetalhesCategoria.Motorizada -> base += mapOf("brand" to d.marca, "model" to d.modelo, "year" to (d.ano ?: 0), "cylinderCc" to (d.cilindrada ?: 0), "mileage" to (d.quilometragem ?: 0))
            is DetalhesCategoria.Outro -> base += mapOf("condition" to d.condicao, "quantity" to d.quantidade, "brand" to d.marca, "model" to d.modelo)
        }
        return base
    }

    /** `rentalTerms` com os campos de `collectRentalTerms` (HTML 12465) + equivalência mensal explícita (D-09). */
    private fun termosOrigem(c: CondicoesAluguer, r: ResumoFinanceiro?, meses: Int): Map<String, Any?> {
        val (qtd, unidade, dias) = c.prazo.normalizado()
        val t = r?.termosAluguer
        return mapOf("valid" to true, "error" to "", "priceUnit" to c.unidade.codigoOrigem,
            "priceUnitLabel" to if (c.unidade == UnidadePreco.PERSONALIZADA) c.nomeUnidadePersonalizada else c.unidade.rotulo,
            "otherPriceUnit" to c.nomeUnidadePersonalizada, "minimumPreset" to c.prazo.codigoOrigem, "minimumCount" to qtd, "minimumUnit" to unidade.codigoOrigem,
            "minimumDurationDays" to dias, "minimumDurationLabel" to c.prazo.rotulo(), "billingUnits" to (t?.unidadesCobranca ?: 0),
            "unitPrice" to c.precoUnidade.valor.toDouble(), "minimumContractValue" to (t?.baseMinima?.valor?.toDouble() ?: 0.0),
            "depositMonths" to meses, "monthlyUnitsEquivalent" to c.unidadesPersonalizadasPorMes?.toPlainString())
    }

    private fun promocoesOrigem(r: ResumoFinanceiro): List<Map<String, Any?>> = r.promocoes.map { (p, v) ->
        mapOf("code" to p.codigoOrigem, "label" to p.rotulo, "percent" to (r.entrada.parametros.promocoes[p]?.valor?.toDouble() ?: 0.0),
            "fee" to v.valor.toDouble(), "basePrice" to r.entrada.preco.valor.toDouble())
    }

    private fun alertasOrigem(a: PreferenciasAlertaProduto) = mapOf("enabled" to a.activo, "reservation" to a.reserva, "interest" to a.interesse,
        "channel" to (a.canal?.codigo ?: "PROFILE"))

    /** Projecção pública: mesmos campos do objecto `listing` da origem (HTML 15944) SEM os privados (D-22). */
    fun publico(id: String, f: FormularioAnuncio, fotos: List<String>, r: ResumoFinanceiro, dono: Perfil, publicador: Papel, estado: String,
                identidade: Identidade?, agora: String): MutableMap<String, Any?> {
        val meses = r.entrada.parametros.mesesSinalAluguer
        val det = detalhesOrigem(f.detalhes, f, meses)
        val isento = publicador == Papel.ADMINISTRADOR
        return mutableMapOf(
            "id" to id, "title" to f.titulo.trim(), "category" to f.categoria.codigo, "subcategory" to f.detalhes.subcategoria, "intent" to f.modalidade.codigoOrigem,
            "price" to f.preco!!.valor.toDouble(), "priceText" to f.preco.valor.toPlainString(),
            "dealBaseSnapshot" to r.baseNegocio.valor.toDouble(), "rentalTerms" to f.aluguer?.let { termosOrigem(it, r, meses) },
            "deposit" to r.sinal.valor.toDouble(),
            "depositPercentSnapshot" to if (f.modalidade == Modalidade.VENDA) r.entrada.parametros.sinalVenda.valor.toDouble()
                else if (r.baseNegocio.positivo) r.sinal.valor.multiply(java.math.BigDecimal(100)).divide(r.baseNegocio.valor, 6, java.math.RoundingMode.HALF_UP).toDouble() else 0.0,
            "rentalDepositMonthsSnapshot" to if (f.modalidade == Modalidade.ARRENDAMENTO) meses else null,
            "commissionCalculationMode" to if (f.modalidade == Modalidade.ARRENDAMENTO) "RENTAL_MINIMUM_CONTRACT" else "DEPOSIT",
            "commissionPercentConfigured" to r.entrada.parametros.comissao.valor.toDouble(), "commissionAmountPreview" to r.comissao.valor.toDouble(),
            "listingPolicyAccepted" to true, "listingPolicyVersion" to CentralSpark.POLITICA, "listingPolicyAcceptedAt" to agora, "listingPolicyAcceptedBy" to dono.uid,
            "province" to f.provincia, "municipality" to f.municipio, "location" to "${f.municipio}, ${f.provincia}",
            "image" to fotos.firstOrNull().orEmpty(), "images" to fotos, "categoryDetails" to det,
            "propertyDetails" to if (f.categoria == Categoria.IMOVEL) det else null,
            "status" to estado, "ownerId" to dono.uid, "publisherRole" to publicador.codigo, "commissionExempt" to isento,
            "ownerAlertPreferences" to alertasOrigem(f.alertas),
            "listingFeePercentSnapshot" to r.entrada.parametros.taxaAnuncio.valor.toDouble(), "listingFeeAmount" to if (isento) 0.0 else r.taxaAnuncio.valor.toDouble(),
            "promotionServices" to promocoesOrigem(r), "promotionFeeAmount" to if (isento) 0.0 else r.totalPromocoes.valor.toDouble(),
            "promotionDurationDays" to r.entrada.parametros.duracaoPromocaoDias, "settingsVersionSnapshot" to r.entrada.parametros.versao,
            "duplicateClaimIds" to (identidade?.claims ?: emptyList<String>()), "verification" to if (identidade != null) "VERIFICADO_SEM_DUPLICADO" else "PENDENTE",
            "ownerSnapshot" to null, "updatedAt" to agora, "source" to "ANDROID",
        )
    }

    /** `privateListingData` (HTML 12273). */
    fun privado(f: FormularioAnuncio, dono: Perfil, documentos: List<DocumentoGuardado>, identidade: Identidade?, agora: String): MutableMap<String, Any?> = mutableMapOf(
        "ownerId" to dono.uid, "neighborhood" to f.bairro.trim(), "referencePoint" to f.pontoReferencia.trim(),
        "ownerSnapshot" to mapOf("name" to dono.nome, "phonePrimary" to dono.telefonePrincipal, "phoneAlt" to dono.telefoneAlternativo, "email" to dono.email,
            "photoDataUrl" to (dono.fotoUrl ?: "")),
        "ownershipDocuments" to documentos.map { mapOf("name" to it.nome, "type" to it.mime, "size" to it.tamanho, "fileId" to it.fileId, "storage" to "FIRESTORE_CHUNKS",
            "chunkCount" to it.partes, "contentHash" to it.sha256) },
        "payoutMethod" to when (f.recebimento) { is MeioRecebimento.Iban -> "IBAN"; is MeioRecebimento.MulticaixaExpress -> "EXPRESS"; null -> "" },
        "ownerIban" to (f.recebimento as? MeioRecebimento.Iban)?.iban.orEmpty(),
        "payoutExpressPhone" to (f.recebimento as? MeioRecebimento.MulticaixaExpress)?.telefone.orEmpty(),
        "doc" to f.referenciaDocumento.trim(),
        "duplicateIdentity" to identidade?.let { mapOf("version" to 8, "docHash" to it.docHash, "documentHashes" to it.hashesDocumentos, "visualHashes" to it.hashesVisuais,
            "coreHash" to it.coreHash, "claimIds" to it.claims, "generatedAt" to agora) },
        "assetStorageMode" to "FIRESTORE_FREE", "documentsSyncPending" to false, "updatedAt" to agora,
    )

    /** Tamanho aproximado do documento (JSON) para respeitar o limite do Firestore. */
    fun tamanho(m: Map<String, Any?>): Int = org.json.JSONObject(m.mapValues { it.value ?: org.json.JSONObject.NULL }).toString().toByteArray().size

    // ------------------------------------------------------------------ comprovativos em partes
    /** `persistOwnershipDocumentChunks` (HTML 12191): base64 em partes de 650 000 caracteres, um batch por ficheiro. */
    suspend fun guardarDocumentos(listingId: String, donoId: String, docs: List<DocumentoNovo>, progresso: (String) -> Unit = {}): List<DocumentoGuardado> =
        docs.mapIndexed { i, d ->
            val fileId = Origem.idSeguro("${listingId}_${System.currentTimeMillis()}_${i}_${(0..99999).random()}")
            val dataUrl = Origem.dataUrl(d.mime, d.bytes)
            val cabecalho = dataUrl.substringBefore(',') + ","
            val b64 = dataUrl.substringAfter(',')
            val partes = b64.chunked(PARTE_CARACTERES)
            progresso("A guardar comprovativo ${i + 1} de ${docs.size} (${partes.size} parte(s))…")
            val batch = db.batch()
            partes.forEachIndexed { idx, p ->
                batch.set(db.document("productFiles/" + Origem.idSeguro("${fileId}_${idx.toString().padStart(3, '0')}")), mapOf(
                    "listingId" to listingId, "ownerId" to donoId, "fileId" to fileId, "fileName" to d.nome, "fileType" to d.mime, "fileSize" to d.bytes.size,
                    "chunkIndex" to idx, "totalChunks" to partes.size, "dataHeader" to if (idx == 0) cabecalho else "", "dataChunk" to p,
                    "contentHash" to d.sha256, "createdAt" to Origem.agoraIso()), SetOptions.merge())
            }
            batch.commit().await()
            DocumentoGuardado(d.nome, d.mime, d.bytes.size.toLong(), fileId, partes.size, d.sha256)
        }

    /** Reconstitui um comprovativo (titular/Administrador — regras de `productFiles`). */
    suspend fun lerDocumento(fileId: String): Pair<String, ByteArray> {
        val partes = db.collection("productFiles").whereEqualTo("fileId", fileId).get().await().documents.sortedBy { it.getLong("chunkIndex") ?: 0 }
        if (partes.isEmpty()) throw ErroCentral("not-found", "O ficheiro solicitado já não está disponível.")
        val cab = partes.first().getString("dataHeader").orEmpty()
        val total = partes.first().getLong("totalChunks")?.toInt() ?: partes.size
        if (partes.size < total) throw ErroCentral("data-loss", "O comprovativo está incompleto no servidor (${partes.size}/$total partes).")
        val url = cab + partes.joinToString("") { it.getString("dataChunk").orEmpty() }
        return Origem.mimeDe(url) to (Origem.bytesDe(url) ?: throw ErroCentral("data-loss", "Comprovativo ilegível."))
    }

    // ------------------------------------------------------------------ duplicados (claims)
    /**
     * Reivindica os claims de identidade por transacção (`claimDuplicateIdentity`): dois anúncios com o mesmo documento ou as
     * mesmas fotografias não podem ser publicados em simultâneo, mesmo que ambos tenham passado a verificação local.
     * @return ids de claims já pertencentes a outro anúncio (vazio = reivindicados com sucesso).
     */
    suspend fun reivindicarClaims(listingId: String, donoId: String, identidade: Identidade): List<String> {
        val conflitos = mutableListOf<String>()
        db.runTransaction { tx ->
            val refs = identidade.claims.map { db.document("productIdentityClaims/" + Origem.idSeguro(it)) }
            val existentes = refs.map { tx.get(it) }
            existentes.forEachIndexed { i, s -> if (s.exists() && s.getString("listingId") != listingId) conflitos += identidade.claims[i] }
            if (conflitos.isEmpty()) refs.forEachIndexed { i, r ->
                tx.set(r, mapOf("claimId" to identidade.claims[i], "listingId" to listingId, "ownerId" to donoId, "type" to identidade.claims[i].substringBefore('_'),
                    "createdAt" to Origem.agoraIso(), "status" to "ACTIVE"))
            }
            null
        }.await()
        return conflitos
    }

    suspend fun libertarClaims(listingId: String, claims: List<String>) {
        claims.forEach { c ->
            val r = db.document("productIdentityClaims/" + Origem.idSeguro(c))
            runCatching { if (r.get().await().getString("listingId") == listingId) r.delete().await() }
        }
    }

    // ------------------------------------------------------------------ publicação
    /** Proprietário: rascunho privado para pagamento (`saveListingDraftForPayment`, HTML 15857). */
    suspend fun prepararPublicacaoPaga(id: String, publico: MutableMap<String, Any?>, privado: MutableMap<String, Any?>, resumo: ResumoFinanceiro) {
        publico["status"] = "PENDING_PAYMENT"; publico["paymentStatus"] = "AWAITING_PAYMENT"; publico["createdAt"] = publico["updatedAt"]
        publico["feeSummary"] = mapOf("listingFeeAmount" to resumo.taxaAnuncio.valor.toDouble(), "promotionFeeAmount" to resumo.totalPromocoes.valor.toDouble(),
            "totalAmount" to resumo.totalPublicacao.valor.toDouble())
        val b = db.batch()
        b.set(db.document("listingDrafts/$id"), publico, SetOptions.merge())
        b.set(db.document("listingDraftPrivate/$id"), privado, SetOptions.merge())
        b.commit().await()
        central.auditar("PREPARE_LISTING_PAYMENT", "LISTING", id, "Anúncio preparado para pagamento da publicação (${resumo.totalPublicacao.valor.toPlainString()} AOA).")
    }

    /** Administrador: publicação directa sem cobrança, com motivo/snapshot da isenção (secção 19). */
    suspend fun publicarIsento(id: String, publico: MutableMap<String, Any?>, privado: MutableMap<String, Any?>, motivo: String) {
        val agora = publico["updatedAt"]
        publico["status"] = "ACTIVE"; publico["paymentStatus"] = "EXEMPT"; publico["createdAt"] = agora; publico["publishedAt"] = agora
        publico["exemption"] = mapOf("publicationFee" to true, "promotions" to true, "commission" to true, "reason" to motivo, "at" to agora)
        val b = db.batch()
        b.set(db.document("products/$id"), publico)
        b.set(db.document("productPrivate/$id"), privado)
        b.commit().await()
        central.auditar("PUBLISH_LISTING_EXEMPT", "LISTING", id, "Publicação pelo Administrador sem cobrança. Motivo: $motivo")
    }

    /**
     * Administrador confirma o pagamento da publicação e publica o rascunho (o HTML aguardava um backend inexistente — D-29).
     * Promoções contratadas ficam activas pela duração configurada a partir da confirmação.
     */
    suspend fun publicarRascunhoPago(listingId: String, intentId: String, adminId: String) {
        val pub = db.document("listingDrafts/$listingId").get().await()
        val priv = db.document("listingDraftPrivate/$listingId").get().await()
        if (!pub.exists() || !priv.exists()) throw ErroCentral("not-found", "O rascunho do anúncio já não existe; verifique se foi publicado ou removido.")
        val agora = Origem.agoraIso()
        val p = (pub.data ?: emptyMap()).toMutableMap()
        p["status"] = "ACTIVE"; p["paymentStatus"] = "PAID"; p["publishedAt"] = agora; p["updatedAt"] = agora; p["listingFeePaidAt"] = agora
        p["paymentIntentId"] = intentId; p["publishedByAdminId"] = adminId; p.remove("feeSummary")
        val promos = (p["promotionServices"] as? List<*>).orEmpty()
        if (promos.isNotEmpty()) {
            val dias = (p["promotionDurationDays"] as? Number)?.toLong() ?: 7
            p["promotionPaidAt"] = agora; p["promotionExpiresAt"] = Origem.iso(System.currentTimeMillis() + dias * 86_400_000)
        }
        val b = db.batch()
        b.set(db.document("products/$listingId"), p)
        b.set(db.document("productPrivate/$listingId"), priv.data ?: emptyMap<String, Any?>())
        b.set(db.document("listingDrafts/$listingId"), mapOf("status" to "PUBLISHED", "paymentStatus" to "PAID", "publishedAt" to agora, "updatedAt" to agora), SetOptions.merge())
        b.set(db.document("paymentIntents/$intentId"), mapOf("status" to "PUBLISHED", "closedAt" to agora, "confirmedAt" to agora, "confirmedBy" to adminId,
            "confirmationMode" to "ADMIN_MANUAL", "updatedAt" to agora), SetOptions.merge())
        b.commit().await()
        central.auditar("PUBLISH_LISTING_PAID", "LISTING", listingId, "Pagamento da publicação confirmado pelo Administrador ($intentId) e anúncio publicado.")
    }

    /** Edição com verificação de versão (secção 12): falha se o anúncio mudou depois de aberto. */
    suspend fun guardarEdicao(id: String, publico: MutableMap<String, Any?>, privado: MutableMap<String, Any?>?, versaoAberta: String) {
        val ref = db.document("products/$id")
        db.runTransaction { tx ->
            val s = tx.get(ref)
            if (!s.exists()) throw ErroCentral("not-found", "O anúncio já não existe.")
            val actual = s.get("updatedAt")?.toString().orEmpty()
            if (versaoAberta.isNotEmpty() && actual.isNotEmpty() && actual != versaoAberta)
                throw ErroCentral("aborted", "O anúncio foi alterado noutro dispositivo depois de o abrir. Reveja os dados actuais; o seu rascunho foi preservado.", "CONFLITO_VERSAO")
            // Estado, titular, criação e snapshots de reserva nunca são alterados por uma edição.
            listOf("status", "ownerId", "createdAt", "reservedAt", "reservationExpiresAt", "reservedByUserId", "reservedByName", "paymentStatus",
                "promotionPaidAt", "promotionExpiresAt", "engagementStats").forEach { publico.remove(it) }
            tx.set(ref, publico, SetOptions.merge())
            privado?.let { tx.set(db.document("productPrivate/$id"), it, SetOptions.merge()) }
            null
        }.await()
        central.auditar("EDIT_LISTING", "LISTING", id, "Anúncio editado no Android.")
    }

    /** Carrega um anúncio (ou rascunho de pagamento) para edição, hidratando todos os campos (secção 12). */
    suspend fun carregarParaEdicao(id: String, colecao: String = "products"): AnuncioEditavel {
        val pub = db.document("$colecao/$id").get().await()
        if (!pub.exists()) throw ErroCentral("not-found", "Anúncio inexistente.")
        val privCol = if (colecao == "products") "productPrivate" else "listingDraftPrivate"
        val priv = runCatching { db.document("$privCol/$id").get().await().data }.getOrNull() ?: emptyMap()
        val d = pub.data ?: emptyMap()
        return AnuncioEditavel(id, formularioDe(d, priv), (d["images"] as? List<*>)?.mapNotNull { it as? String }.orEmpty(),
            (priv["ownershipDocuments"] as? List<*>)?.mapNotNull { m -> (m as? Map<*, *>)?.let {
                DocumentoGuardado(it["name"] as? String ?: "documento", it["type"] as? String ?: "", (it["size"] as? Number)?.toLong() ?: 0,
                    it["fileId"] as? String ?: return@mapNotNull null, (it["chunkCount"] as? Number)?.toInt() ?: 0, it["contentHash"] as? String ?: "") } }.orEmpty(),
            d["status"] as? String ?: "", d["updatedAt"]?.toString().orEmpty(), d["ownerId"] as? String ?: "", d)
    }

    /** Documento remoto (origem) → formulário, com tolerância a registos legados (`legacyDetails`, HTML 25645). */
    fun formularioDe(d: Map<String, Any?>, priv: Map<String, Any?>): FormularioAnuncio {
        val raizes = listOf("categoryDetails", "propertyDetails", "technicalDetails", "attributes", "characteristics", "specifications", "features")
        val det = mutableMapOf<String, Any?>()
        raizes.forEach { k -> (d[k] as? Map<*, *>)?.forEach { (kk, v) -> if (det[kk.toString()].let { it == null || it == "" } && v != null && v != "") det[kk.toString()] = v } }
        fun s(vararg k: String) = k.firstNotNullOfOrNull { det[it]?.let(Origem::texto)?.takeIf(String::isNotBlank) } ?: ""
        fun i(k: String) = Origem.numero(det[k])?.toInt()?.takeIf { it > 0 }
        val categoria = Categoria.de(d["category"] as? String) ?: Categoria.OUTRO
        val sub = (d["subcategory"] as? String)?.takeIf(String::isNotBlank) ?: s("subcategory", "subCategory", "subcategoria")
        val notas = s("notes", "technicalDescription", "description", "descricaoTecnica", "observations", "observacoes")
        val detalhes = when (categoria) {
            Categoria.IMOVEL -> DetalhesCategoria.Imovel(sub, i("compartments"), (det["qualities"] as? List<*>)?.mapNotNull { it as? String }?.toSet().orEmpty(), s("repairStatus"), notas)
            Categoria.TERRENO -> DetalhesCategoria.Terreno(sub, Origem.numero(det["areaM2"])?.takeIf { it.signum() > 0 }, s("purpose"), s("topography"), s("access"), notas)
            Categoria.VIATURA -> DetalhesCategoria.Viatura(sub, s("brand"), s("model"), i("year"), Origem.numero(det["mileage"])?.toLong(), s("fuel"), s("transmission"), notas)
            Categoria.MOTORIZADA -> DetalhesCategoria.Motorizada(sub, s("brand"), s("model"), i("year"), i("cylinderCc"), Origem.numero(det["mileage"])?.toLong(), notas)
            Categoria.OUTRO -> DetalhesCategoria.Outro(sub, s("condition", "state", "productCondition", "estado"), i("quantity") ?: 1, s("brand"), s("model"), notas)
        }
        val modalidade = Modalidade.daOrigem(d["intent"] as? String) ?: Modalidade.VENDA
        val preco = (d["priceText"] as? String)?.let { runCatching { Dinheiro.de(it) }.getOrNull() } ?: Origem.numero(d["price"])?.let(Dinheiro::de)
        val rt = d["rentalTerms"] as? Map<*, *>
        val aluguer = if (modalidade == Modalidade.ARRENDAMENTO && preco != null) {
            val prazo = when (rt?.get("minimumPreset")) { "3_MONTHS", null -> PrazoMinimo.TresMeses; "6_MONTHS" -> PrazoMinimo.SeisMeses; "12_MONTHS" -> PrazoMinimo.Anual
                else -> PrazoMinimo.Personalizado(Origem.numero(rt["minimumCount"])?.toInt() ?: 1, UnidadeDuracao.daOrigem(rt["minimumUnit"] as? String) ?: UnidadeDuracao.MES) }
            CondicoesAluguer(preco, UnidadePreco.daOrigem(rt?.get("priceUnit") as? String) ?: UnidadePreco.MES, prazo, rt?.get("otherPriceUnit") as? String ?: "",
                Origem.numero(rt?.get("monthlyUnitsEquivalent")))
        } else null
        val alert = d["ownerAlertPreferences"] as? Map<*, *>
        val metodo = priv["payoutMethod"] as? String
        val prov = d["province"] as? String ?: ""
        val loc = d["location"] as? String ?: ""
        return FormularioAnuncio(
            categoria = categoria, modalidade = modalidade, titulo = d["title"] as? String ?: "", preco = preco, aluguer = aluguer, detalhes = detalhes,
            diferenciais = ao.kupila.gestaonegocios.dominio.anuncio.Diferenciais(s("locationProfile"), s("condominiumName"), s("interestHighlights")),
            provincia = prov, municipio = (d["municipality"] as? String)?.takeIf(String::isNotBlank) ?: loc.removeSuffix(", $prov"),
            bairro = priv["neighborhood"] as? String ?: "", pontoReferencia = priv["referencePoint"] as? String ?: "", referenciaDocumento = priv["doc"] as? String ?: "",
            numeroFotos = (d["images"] as? List<*>)?.size ?: 0, numeroDocumentos = (priv["ownershipDocuments"] as? List<*>)?.size ?: 0,
            recebimento = when (metodo) { "IBAN" -> MeioRecebimento.Iban(priv["ownerIban"] as? String ?: ""); "EXPRESS" -> MeioRecebimento.MulticaixaExpress(priv["payoutExpressPhone"] as? String ?: ""); else -> null },
            alertas = PreferenciasAlertaProduto(alert?.get("enabled") != false, alert?.get("reservation") != false, alert?.get("interest") != false,
                (alert?.get("channel") as? String)?.takeIf { it != "PROFILE" }?.let(CanalAlerta::de)),
            politicaAceite = d["listingPolicyVersion"] == CentralSpark.POLITICA,
        )
    }

    // ------------------------------------------------------------------ remoção / desanúncio
    suspend fun excluir(id: String, motivo: String) {
        val priv = runCatching { db.document("productPrivate/$id").get().await() }.getOrNull()
        val claims = ((priv?.get("duplicateIdentity") as? Map<*, *>)?.get("claimIds") as? List<*>)?.mapNotNull { it as? String }.orEmpty()
        val b = db.batch()
        b.delete(db.document("products/$id")); b.delete(db.document("productPrivate/$id"))
        b.commit().await()
        libertarClaims(id, claims)
        central.auditar("DELETE_LISTING", "LISTING", id, "Produto eliminado. $motivo Sem reembolso de taxas/comissões.")
    }

    suspend fun desanunciar(id: String, motivo: String) {
        db.document("products/$id").set(mapOf("status" to "UNPUBLISHED", "unpublishedAt" to Origem.agoraIso(), "updatedAt" to Origem.agoraIso()), SetOptions.merge()).await()
        central.auditar("UNPUBLISH_LISTING", "LISTING", id, "Produto desanunciado. $motivo Sem reembolso de taxas/comissões.")
    }

    suspend fun reactivar(id: String) {
        db.document("products/$id").set(mapOf("status" to "ACTIVE", "updatedAt" to Origem.agoraIso()), SetOptions.merge()).await()
        central.auditar("REPUBLISH_LISTING", "LISTING", id, "Produto reactivado.")
    }

    // ------------------------------------------------------------------ interesse qualificado
    /** `trackQualifiedProductInterest` (HTML 14132): 1 por Cliente/produto/dia; nunca do titular. */
    suspend fun registarInteresse(produtoId: String, titulo: String, donoId: String, cliente: Perfil, categoria: String, modalidade: String, provincia: String) {
        if (cliente.papel != Papel.CLIENTE || cliente.uid == donoId) return
        val dia = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString().replace("-", "")
        val id = "INT_${Origem.idSeguro(produtoId)}_${Origem.idSeguro(cliente.uid)}_$dia"
        runCatching {
            db.document("productInterest/$id").set(mapOf("productId" to produtoId, "listingTitle" to titulo, "ownerId" to donoId, "viewerId" to cliente.uid,
                "viewerRole" to "CLIENTE", "category" to categoria, "intent" to modalidade, "province" to provincia, "viewedAt" to Origem.agoraIso(), "source" to "DETAIL_VIEW")).await()
        }
    }

}
