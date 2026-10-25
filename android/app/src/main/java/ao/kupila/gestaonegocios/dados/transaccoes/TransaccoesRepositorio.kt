package ao.kupila.gestaonegocios.dados.transaccoes

import ao.kupila.gestaonegocios.dados.remoto.CentralSpark
import ao.kupila.gestaonegocios.dados.remoto.ErroCentral
import ao.kupila.gestaonegocios.dados.remoto.Origem
import ao.kupila.gestaonegocios.dominio.duplicados.IdentidadeBem
import ao.kupila.gestaonegocios.dominio.financeiro.ResumoFinanceiro
import ao.kupila.gestaonegocios.dominio.perfis.Papel
import ao.kupila.gestaonegocios.dominio.perfis.Perfil
import ao.kupila.gestaonegocios.dominio.transaccoes.MotivoReembolso
import ao.kupila.gestaonegocios.dominio.transaccoes.Otp
import ao.kupila.gestaonegocios.dominio.transaccoes.ResultadoOtp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** Estados abertos/terminais de intenção (HTML 15868-15869). */
val INTENCAO_ABERTA = setOf("AWAITING_PAYMENT", "VERIFYING", "RECEIPT_MATCHED", "AWAITING_PROVIDER_CONFIGURATION", "AWAITING_PROVIDER_CONFIRMATION", "PROCESSING",
    "REJECTED_REFERENCE", "REJECTED_PAYMENT")

/** Validade da intenção de pagamento (30 min, `createPaymentIntent`, HTML 15902). */
const val VALIDADE_INTENCAO_MS = 30 * 60_000L

/**
 * Pagamentos, reservas, OTP, reembolsos e liberações no esquema da origem (D-29), dentro das regras de produção.
 * Confirmações financeiras: só o Administrador (validação humana auditada — D-27); o Cliente/Proprietário submete e acompanha.
 */
@Singleton
class TransaccoesRepositorio @Inject constructor(private val central: CentralSpark) {
    private val db get() = FirebaseFirestore.getInstance()

    // ------------------------------------------------------------------ intenção de pagamento
    /**
     * Cria (ou reaproveita) a intenção de pagamento — não cobra duas vezes o mesmo serviço por retry/abertura do checkout (secção 19).
     * A referência de identificação é do Kupila: sem provedor contratado (D-02), não é uma referência bancária emitida pelo provedor e
     * a confirmação é feita pelo Administrador com o comprovativo.
     */
    suspend fun intencao(tipo: String, cliente: Perfil, listingId: String, donoId: String, resumo: ResumoFinanceiro, entidade: String,
                         titulo: String, draftId: String = ""): Map<String, Any?> {
        val abertas = db.collection("paymentIntents").whereEqualTo("userId", cliente.uid).whereEqualTo("listingId", listingId).get().await().documents
            .filter { (it.getString("status") ?: "") in INTENCAO_ABERTA && it.getString("type") == tipo && (Origem.ms(it.get("expiresAt")) ?: 0) > System.currentTimeMillis() }
        abertas.maxByOrNull { Origem.ms(it.get("createdAt")) ?: 0 }?.let { return (it.data ?: emptyMap()) + ("id" to it.id) }
        val id = "PAY-$tipo-${System.currentTimeMillis()}-${(100..999).random()}"
        val referencia = (System.currentTimeMillis().toString().takeLast(7) + (0..99).random().toString().padStart(2, '0')).takeLast(9)
        val reserva = tipo == "RESERVATION"
        val rec = mapOf(
            "type" to tipo, "userId" to cliente.uid, "userName" to cliente.nome, "listingId" to listingId, "listingTitle" to titulo, "draftId" to draftId, "ownerId" to donoId,
            "amount" to (if (reserva) resumo.sinal else resumo.totalPublicacao).valor.toDouble(),
            "amountText" to (if (reserva) resumo.sinal else resumo.totalPublicacao).valor.toPlainString(),
            "dealBaseSnapshot" to resumo.baseNegocio.valor.toDouble(),
            "rentalTermsSnapshot" to resumo.termosAluguer?.let { mapOf("billingUnits" to it.unidadesCobranca, "minimumContractValue" to it.baseMinima.valor.toDouble(),
                "priceUnit" to it.condicoes.unidade.codigoOrigem, "depositMonths" to resumo.entrada.parametros.mesesSinalAluguer) },
            "reference" to referencia, "entity" to entidade, "method" to "REF",
            "status" to if (entidade.isBlank()) "AWAITING_PROVIDER_CONFIGURATION" else "AWAITING_PAYMENT",
            "commissionCalculationMode" to if (resumo.termosAluguer != null) "RENTAL_MINIMUM_CONTRACT" else "DEPOSIT",
            "commissionPercent" to resumo.entrada.parametros.comissao.valor.toDouble(), "commissionAmount" to resumo.comissao.valor.toDouble(),
            "ownerNetAmount" to resumo.liquidoCustodia.valor.toDouble(), "refundableAmount" to resumo.reembolsoElegivel.valor.toDouble(),
            "holdOwnerPayout" to reserva, "payoutReleaseTrigger" to if (reserva) "INSPECTION_DECISION" else "",
            "listingFeeAmount" to resumo.taxaAnuncio.valor.toDouble(), "promotionFeeAmount" to resumo.totalPromocoes.valor.toDouble(),
            "promotionServices" to resumo.promocoes.map { (p, v) -> mapOf("code" to p.codigoOrigem, "label" to p.rotulo, "fee" to v.valor.toDouble()) },
            "settingsVersionSnapshot" to resumo.entrada.parametros.versao, "referenceKind" to "KUPILA_IDENTIFICATION",
            "createdAt" to Origem.agoraIso(), "expiresAt" to Origem.iso(System.currentTimeMillis() + VALIDADE_INTENCAO_MS), "source" to "ANDROID",
        )
        db.document("paymentIntents/$id").set(rec).await()
        central.auditar("CREATE_PAYMENT_INTENT", "PAYMENT", id, "Intenção de pagamento ($tipo) de ${rec["amountText"]} AOA para “$titulo”.")
        return rec + ("id" to id)
    }

    /** Método escolhido (Referência / Multicaixa Express) — o titular pode actualizar a sua intenção. */
    suspend fun metodo(intentId: String, metodo: String, telefoneExpress: String) {
        db.document("paymentIntents/$intentId").set(mapOf("method" to metodo, "expressPhone" to telefoneExpress, "updatedAt" to Origem.agoraIso()), SetOptions.merge()).await()
    }

    /**
     * Submete o comprovativo (`submitPaymentReceipt`, HTML 15934): a imagem vai inline em `paymentSubmissions`; a intenção passa a
     * VERIFYING ou RECEIPT_MATCHED (referência lida = esperada). Nada disto confirma o pagamento (secção 20).
     */
    suspend fun submeterComprovativo(intent: Map<String, Any?>, cliente: Perfil, jpeg: ByteArray, textoOcr: String, referenciaLida: String, manual: Boolean, telefoneExpress: String): String {
        val intentId = intent["id"] as String
        val esperada = (intent["reference"] as? String).orEmpty()
        val confere = referenciaLida.filter(Char::isDigit).takeLast(9) == esperada
        val dataUrl = Origem.dataUrlJpeg(jpeg)
        val id = "SUB-$intentId-${System.currentTimeMillis()}"
        val b = db.batch()
        b.set(db.document("paymentSubmissions/$id"), mapOf("paymentIntentId" to intentId, "userId" to cliente.uid, "listingId" to intent["listingId"], "type" to intent["type"],
            "expectedReference" to esperada, "receiptReference" to referenciaLida, "receiptText" to textoOcr.take(5000), "receiptDataUrl" to dataUrl,
            "receiptHash" to IdentidadeBem.sha256(dataUrl), "method" to (intent["method"] ?: "REF"), "expressPhone" to telefoneExpress,
            "submissionMode" to if (manual) "MANUAL_CONFIRM" else "AUTO_REFERENCE_MATCH", "status" to "SUBMITTED", "createdAt" to Origem.agoraIso(), "source" to "ANDROID"))
        b.set(db.document("paymentIntents/$intentId"), mapOf("status" to if (confere) "RECEIPT_MATCHED" else "VERIFYING", "lastSubmissionId" to id,
            "receiptSubmittedAt" to Origem.agoraIso(), "updatedAt" to Origem.agoraIso()), SetOptions.merge())
        b.commit().await()
        central.auditar("SUBMIT_PAYMENT_RECEIPT", "PAYMENT", intentId, "Comprovativo submetido (${if (confere) "referência coincide" else "referência a validar"}).")
        return id
    }

    // ------------------------------------------------------------------ confirmação pelo Administrador (D-27/D-29)
    /** Rejeita uma submissão (referência/valor/destino não confirmados). O trabalho do pagador é preservado. */
    suspend fun rejeitarPagamento(intentId: String, motivo: String, referencia: Boolean, adminId: String) {
        db.document("paymentIntents/$intentId").set(mapOf("status" to if (referencia) "REJECTED_REFERENCE" else "REJECTED_PAYMENT",
            "rejectionReason" to motivo, "reviewedBy" to adminId, "reviewedAt" to Origem.agoraIso(), "updatedAt" to Origem.agoraIso()), SetOptions.merge()).await()
        central.auditar("REJECT_PAYMENT", "PAYMENT", intentId, motivo)
    }

    /**
     * Confirma o pagamento do SINAL e cria a reserva de forma atómica (secção 21): só se o produto continuar ACTIVE. Se outro
     * pagamento já reservou o bem, a intenção fica com conflito e reembolso necessário — nunca uma segunda reserva silenciosa.
     * O OTP é gerado aqui (SecureRandom); a reserva guarda só o hash; o código vai para o comprovativo do Cliente.
     */
    suspend fun confirmarSinal(intentId: String, admin: Perfil, prazoMinutos: Long, modo: String = "ADMIN_MANUAL"): String {
        val iRef = db.document("paymentIntents/$intentId")
        val otp = Otp.gerar(); val sal = Otp.sal(); val hash = Otp.hash(otp, sal)
        val resultado = db.runTransaction { tx ->
            val i = tx.get(iRef)
            if (!i.exists()) throw ErroCentral("not-found", "Intenção inexistente.")
            if (i.getString("type") != "RESERVATION") throw ErroCentral("failed-precondition", "Esta intenção não é de reserva.")
            if (i.getString("status") == "COMPLETED") return@runTransaction "JA:" + (i.getString("reservationId") ?: "")
            val listingId = i.getString("listingId").orEmpty()
            val pRef = db.document("products/$listingId")
            val prRef = db.document("productPrivate/$listingId")
            val p = tx.get(pRef); val pr = tx.get(prRef)
            val agora = Origem.agoraIso(); val agoraMs = System.currentTimeMillis()
            if (!p.exists() || p.getString("status") != "ACTIVE") {
                tx.set(iRef, mapOf("status" to "REJECTED_PAYMENT", "conflictReason" to "PRODUCT_UNAVAILABLE", "refundRequired" to true,
                    "rejectionReason" to "O produto já não estava disponível quando o pagamento foi confirmado. O sinal recebido tem de ser devolvido.",
                    "reviewedBy" to admin.uid, "reviewedAt" to agora, "updatedAt" to agora), SetOptions.merge())
                return@runTransaction "CONFLITO"
            }
            val rid = "RSV_$intentId"
            val expira = Origem.iso(agoraMs + prazoMinutos * 60_000)
            val dono = pr.get("ownerSnapshot") as? Map<*, *>
            tx.set(db.document("reservations/$rid"), mapOf(
                "id" to rid, "listingId" to listingId, "listingTitle" to p.getString("title"), "userId" to i.getString("userId"), "userName" to i.getString("userName"),
                "ownerId" to p.getString("ownerId"), "ownerName" to (dono?.get("name") ?: ""), "category" to p.getString("category"), "intent" to p.getString("intent"),
                "province" to p.getString("province"), "amount" to (i.getDouble("amount") ?: 0.0), "depositPercent" to (p.getDouble("depositPercentSnapshot") ?: 0.0),
                "commissionPercent" to (i.getDouble("commissionPercent") ?: 0.0), "commissionAmount" to (i.getDouble("commissionAmount") ?: 0.0),
                "ownerNetAmount" to (i.getDouble("ownerNetAmount") ?: 0.0), "refundableAmount" to (i.getDouble("refundableAmount") ?: 0.0),
                "dealBaseSnapshot" to (i.getDouble("dealBaseSnapshot") ?: 0.0), "commissionCalculationMode" to i.getString("commissionCalculationMode"),
                "reservedAt" to agora, "expiresAt" to expira, "durationMinutes" to prazoMinutos, "status" to "ACTIVE",
                "otpHash" to hash, "otpSalt" to sal, "otpAlgorithm" to Otp.ALGORITMO, "otpFailedAttempts" to 0,
                "location" to p.getString("location"), "neighborhood" to pr.getString("neighborhood"), "referencePoint" to pr.getString("referencePoint"),
                "ownerPhone" to (dono?.get("phonePrimary") ?: ""), "paymentIntentId" to intentId, "paymentSubmissionId" to i.getString("lastSubmissionId"),
                "holdOwnerPayout" to true, "payoutStatus" to "HELD", "payoutReleaseTrigger" to "INSPECTION_DECISION",
                "confirmedBy" to admin.uid, "confirmationMode" to modo, "createdAt" to agora, "updatedAt" to agora, "source" to "ANDROID"))
            val e = (p.get("engagementStats") as? Map<*, *>).orEmpty()
            tx.set(pRef, mapOf("status" to "RESERVED", "reservedAt" to agora, "reservationExpiresAt" to expira, "reservedByUserId" to i.getString("userId"),
                "reservedByName" to i.getString("userName"), "commissionPercentSnapshot" to (i.getDouble("commissionPercent") ?: 0.0),
                "commissionAmountSnapshot" to (i.getDouble("commissionAmount") ?: 0.0), "reservationDurationMinutesSnapshot" to prazoMinutos,
                "engagementStats" to e + mapOf("reservationCount" to ((e["reservationCount"] as? Number)?.toInt() ?: 0) + 1, "lastReservedAt" to agora),
                "updatedAt" to agora), SetOptions.merge())
            tx.set(iRef, mapOf("status" to "COMPLETED", "reservationId" to rid, "confirmedAt" to agora, "confirmedBy" to admin.uid, "confirmationMode" to modo,
                "updatedAt" to agora), SetOptions.merge())
            i.getString("lastSubmissionId")?.let { sid ->
                tx.set(db.document("paymentSubmissions/$sid"), mapOf("status" to "CONFIRMED", "reservationId" to rid, "otpForClient" to otp,
                    "confirmedAt" to agora, "confirmedBy" to admin.uid), SetOptions.merge())
            }
            "OK:$rid"
        }.await()
        when {
            resultado == "CONFLITO" -> { central.auditar("PAYMENT_CONFLICT", "PAYMENT", intentId, "Produto indisponível na confirmação: sinal a devolver."); throw ErroCentral("failed-precondition", "O produto já estava reservado ou indisponível. A intenção ficou marcada para devolução do sinal.") }
            resultado.startsWith("JA:") -> return resultado.removePrefix("JA:")
        }
        val rid = resultado.removePrefix("OK:")
        central.auditar("CONFIRM_DEPOSIT_PAYMENT", "RESERVATION", rid, "Pagamento do sinal confirmado pelo Administrador; reserva activa ($prazoMinutos min).")
        return rid
    }

    // ------------------------------------------------------------------ OTP / vistoria
    /**
     * Validação do OTP pelo titular ou Administrador (`validateOTP`, HTML 15948, com as correcções da secção 22): decisão positiva +
     * pedido de liberação, nunca "pago". Repetição não conclui outra vez; tentativas limitadas; expirado ou com reembolso bloqueia.
     */
    suspend fun validarOtp(reservaId: String, informado: String, actor: Perfil): ResultadoOtp {
        val rRef = db.document("reservations/$reservaId")
        val r = rRef.get().await()
        if (!r.exists()) return ResultadoOtp.INVALIDO
        val dono = r.getString("ownerId")
        val admin = actor.papel == Papel.ADMINISTRADOR
        if (actor.uid != dono && !admin) return ResultadoOtp.SEM_PERMISSAO
        if (r.getString("status") == "COMPLETED") return ResultadoOtp.JA_VALIDADO
        if (r.getString("status") != "ACTIVE") return ResultadoOtp.INVALIDO
        if (!r.getString("refundRequestId").isNullOrBlank()) return ResultadoOtp.REEMBOLSO_PENDENTE
        val bloqueio = Origem.ms(r.get("otpLockedUntil")) ?: 0
        if (bloqueio > System.currentTimeMillis()) return ResultadoOtp.TENTATIVAS_EXCEDIDAS
        if ((Origem.ms(r.get("expiresAt")) ?: Long.MAX_VALUE) <= System.currentTimeMillis()) return ResultadoOtp.EXPIRADO
        if (!Otp.confere(informado, r.getString("otpHash"), r.getString("otpSalt"), r.getString("otp"))) {
            val falhas = ((r.getLong("otpFailedAttempts") ?: 0) + 1).toInt()
            rRef.set(mapOf("otpFailedAttempts" to falhas, "otpLastFailureAt" to Origem.agoraIso(),
                "otpLockedUntil" to if (falhas >= Otp.MAX_TENTATIVAS) Origem.iso(System.currentTimeMillis() + Otp.BLOQUEIO_MS) else null), SetOptions.merge()).await()
            central.auditar("OTP_FAILED", "RESERVATION", reservaId, "OTP incorrecto ($falhas tentativa(s)).")
            return if (falhas >= Otp.MAX_TENTATIVAS) ResultadoOtp.TENTATIVAS_EXCEDIDAS else ResultadoOtp.INVALIDO
        }
        val agora = Origem.agoraIso()
        val liquido = r.getDouble("ownerNetAmount") ?: ((r.getDouble("amount") ?: 0.0) - (r.getDouble("commissionAmount") ?: 0.0))
        val releaseId = "REL-" + Origem.idSeguro(reservaId)
        val b = db.batch()
        b.set(rRef, mapOf("status" to "COMPLETED", "completedAt" to agora, "dealDecision" to "CLOSED_CONFIRMED", "payoutStatus" to "RELEASE_REQUESTED",
            "payoutReleaseRequestId" to releaseId, "ownerNetAmount" to liquido, "otpValidatedBy" to actor.uid, "otpValidatedAt" to agora, "otpFailedAttempts" to 0, "updatedAt" to agora), SetOptions.merge())
        b.set(db.document("products/${r.getString("listingId")}"), mapOf("status" to "COMPLETED", "completedAt" to agora, "updatedAt" to agora), SetOptions.merge())
        // As regras só deixam o Proprietário criar o pedido de liberação; validada pelo Administrador, a liberação segue pela própria reserva.
        if (actor.uid == dono) b.set(db.document("payoutReleaseRequests/$releaseId"), mapOf("id" to releaseId, "reservationId" to reservaId, "listingId" to r.getString("listingId"),
            "ownerId" to dono, "amount" to liquido, "commissionRetained" to (r.getDouble("commissionAmount") ?: 0.0), "status" to "AWAITING_PROVIDER_CONFIRMATION",
            "trigger" to "OTP_CLOSED_DEAL", "requestedBy" to actor.uid, "createdAt" to agora, "updatedAt" to agora))
        b.commit().await()
        central.auditar("COMPLETE_RESERVATION", "RESERVATION", reservaId, "OTP validado; negócio confirmado e liberação do líquido solicitada (pendente de confirmação financeira).")
        return ResultadoOtp.VALIDADO
    }

    /**
     * Expiração (secção 21): sem tarefas agendadas no Spark, é aplicada por quem tem permissão quando a reserva é vista expirada.
     * Liberta o bem sem estorno automático; o Cliente pode ainda pedir reembolso comprovado.
     */
    suspend fun expirarSeNecessario(r: DocumentSnapshot, actor: Perfil) {
        if (r.getString("status") != "ACTIVE") return
        if ((Origem.ms(r.get("expiresAt")) ?: Long.MAX_VALUE) > System.currentTimeMillis()) return
        val agora = Origem.agoraIso()
        val b = db.batch()
        b.set(r.reference, mapOf("status" to "EXPIRED", "expiredAt" to agora, "updatedAt" to agora), SetOptions.merge())
        if (actor.papel == Papel.ADMINISTRADOR || actor.uid == r.getString("ownerId")) {
            b.set(db.document("products/${r.getString("listingId")}"), mapOf("status" to "ACTIVE", "reservedAt" to FieldValue.delete(), "reservationExpiresAt" to FieldValue.delete(),
                "reservedByUserId" to FieldValue.delete(), "reservedByName" to FieldValue.delete(), "commissionPercentSnapshot" to null, "commissionAmountSnapshot" to null,
                "updatedAt" to agora), SetOptions.merge())
        }
        runCatching { b.commit().await() }
        central.auditar("EXPIRE_RESERVATION", "RESERVATION", r.id, "Prazo expirado; produto libertado sem decisão financeira automática.")
    }

    // ------------------------------------------------------------------ reembolso
    /** `submitRefundRequest` (HTML 14954-14955). O hash da evidência é o SHA-256 do data URL, como na origem. */
    suspend fun pedirReembolso(r: DocumentSnapshot, cliente: Perfil, otpInformado: String, motivo: MotivoReembolso, descricao: String,
                               evidenciaNome: String, evidenciaMime: String, evidencia: ByteArray): String {
        if (r.getString("userId") != cliente.uid) throw ErroCentral("permission-denied", "Esta reserva não pertence à sua conta.")
        if (r.getString("status") !in setOf("ACTIVE", "EXPIRED")) throw ErroCentral("failed-precondition", "Esta reserva não está elegível para reembolso.")
        if (!r.getString("refundRequestId").isNullOrBlank()) throw ErroCentral("already-exists", "Já existe uma solicitação de reembolso para esta reserva.")
        val okOtp = Otp.confere(otpInformado, r.getString("otpHash"), r.getString("otpSalt"), r.getString("otp")) || otpCliente(r) == otpInformado.trim()
        if (!okOtp) throw ErroCentral("invalid-argument", "O OTP não corresponde à reserva. A vistoria não pode ser comprovada.")
        val dataUrl = Origem.dataUrl(evidenciaMime, evidencia)
        val hash = IdentidadeBem.sha256(dataUrl)
        val agora = Origem.agoraIso()
        val id = "RFD-${Origem.idSeguro(r.id)}-${System.currentTimeMillis()}"
        val valor = r.getDouble("amount") ?: 0.0
        val comissao = r.getDouble("commissionAmount") ?: 0.0
        val reembolsavel = r.getDouble("refundableAmount") ?: (valor - comissao)
        val b = db.batch()
        b.set(db.document("refundRequests/$id"), mapOf("id" to id, "reservationId" to r.id, "listingId" to r.getString("listingId"), "listingTitle" to r.getString("listingTitle"),
            "clientId" to cliente.uid, "clientName" to cliente.nome, "ownerId" to r.getString("ownerId"), "ownerName" to r.getString("ownerName"), "amount" to valor,
            "commissionAmount" to comissao, "refundableAmount" to reembolsavel, "reason" to motivo.codigo, "description" to descricao.trim(), "otpVerified" to true,
            "inspectionVerifiedAt" to agora, "evidenceName" to evidenciaNome, "evidenceType" to evidenciaMime, "evidenceSize" to evidencia.size, "evidenceHash" to hash,
            "declarationAccepted" to true, "declarationText" to motivo.declaracao, "inspectionOccurred" to motivo.houveVistoria,
            "status" to "PENDING_OWNER_CONFIRMATION", "createdAt" to agora, "updatedAt" to agora, "source" to "ANDROID"))
        b.set(db.document("refundEvidence/$id"), mapOf("requestId" to id, "reservationId" to r.id, "clientId" to cliente.uid, "ownerId" to r.getString("ownerId"),
            "name" to evidenciaNome, "type" to evidenciaMime, "size" to evidencia.size, "hash" to hash, "dataUrl" to dataUrl, "createdAt" to agora))
        b.set(r.reference, mapOf("refundRequestId" to id, "refundStatus" to "PENDING_OWNER_CONFIRMATION", "dealDecision" to "NOT_CLOSED_REQUESTED",
            "inspectionVerifiedAt" to agora, "updatedAt" to agora), SetOptions.merge())
        b.commit().await()
        central.auditar("REQUEST_REFUND", "REFUND", id, "Cliente solicitou reembolso comprovado de $reembolsavel AOA para “${r.getString("listingTitle")}”; comissão retida $comissao AOA.")
        return id
    }

    /** OTP em claro do próprio Cliente (no comprovativo confirmado), para validar o pedido de reembolso. */
    private suspend fun otpCliente(r: DocumentSnapshot): String? = r.getString("paymentSubmissionId")?.let {
        runCatching { db.document("paymentSubmissions/$it").get().await().getString("otpForClient") }.getOrNull()
    }

    /** Evidência + verificação de integridade (`viewRefundEvidence`, HTML 14949). Regista a consulta (exigida antes de decidir). */
    suspend fun abrirEvidencia(pedidoId: String, actor: Perfil): Triple<String, ByteArray, Boolean> {
        val e = db.document("refundEvidence/$pedidoId").get().await()
        if (!e.exists()) throw ErroCentral("not-found", "A evidência protegida não foi encontrada.")
        val url = e.getString("dataUrl").orEmpty()
        val req = db.document("refundRequests/$pedidoId").get().await()
        val integra = url.isNotEmpty() && IdentidadeBem.sha256(url) == (req.getString("evidenceHash") ?: e.getString("hash"))
        val agora = Origem.agoraIso()
        val campos = mutableMapOf<String, Any?>("updatedAt" to agora)
        if (actor.uid == req.getString("ownerId")) campos["ownerEvidenceViewedAt"] = agora
        if (actor.papel == Papel.ADMINISTRADOR && integra) { campos["evidenceIntegrityVerifiedAt"] = agora; campos["evidenceIntegrityVerifiedBy"] = actor.uid }
        if (campos.size > 1) runCatching { db.document("refundRequests/$pedidoId").set(campos, SetOptions.merge()).await() }
        return Triple(Origem.mimeDe(url), Origem.bytesDe(url) ?: ByteArray(0), integra)
    }

    /** `reviewRefundByOwner` (HTML 14957): exige que o Proprietário tenha aberto a evidência para confirmar. */
    suspend fun reverReembolsoProprietario(pedidoId: String, confirmado: Boolean, dono: Perfil) {
        val req = db.document("refundRequests/$pedidoId").get().await()
        if (req.getString("ownerId") != dono.uid) throw ErroCentral("permission-denied", "Apenas o Proprietário titular desta reserva pode confirmar ou contestar a não conclusão.")
        if (req.getString("status") != "PENDING_OWNER_CONFIRMATION") throw ErroCentral("failed-precondition", "Esta solicitação já foi respondida.")
        // Zero burocracia (aprovado pelo utilizador em 2026-10-24): a evidência continua disponível, mas consultá-la deixou de ser
        // obrigatório para o Proprietário confirmar. A integridade é verificada automaticamente na aprovação.
        val agora = Origem.agoraIso()
        val estado = if (confirmado) "OWNER_CONFIRMED" else "CONTESTED_PENDING_ADMIN"
        val b = db.batch()
        b.set(req.reference, mapOf("status" to estado, "ownerConfirmedNoDeal" to confirmado, "ownerReviewedBy" to dono.uid, "ownerReviewedAt" to agora, "updatedAt" to agora), SetOptions.merge())
        b.set(db.document("reservations/${req.getString("reservationId")}"), mapOf("refundStatus" to estado, "ownerConfirmedNoDeal" to confirmado, "ownerReviewedAt" to agora, "updatedAt" to agora), SetOptions.merge())
        b.commit().await()
        central.auditar("OWNER_REVIEW_REFUND", "REFUND", pedidoId, if (confirmado) "Proprietário confirmou que o negócio não foi fechado." else "Proprietário contestou a solicitação de reembolso.")
    }

    /**
     * `decideRefundRequest` (HTML 14958): aprovar só após confirmação do Proprietário (contestado: decisão D-07) e integridade verificada.
     * Aprovar cria a obrigação de pagamento (`refundPayoutRequests`) — não marca como pago — e liberta o produto.
     */
    suspend fun decidirReembolso(pedidoId: String, aprovado: Boolean, admin: Perfil, contestadoAprovavel: Boolean = false, fundamento: String = "") {
        val req = db.document("refundRequests/$pedidoId").get().await()
        val est = req.getString("status")
        if (aprovado && est != "OWNER_CONFIRMED" && !(contestadoAprovavel && est == "CONTESTED_PENDING_ADMIN"))
            throw ErroCentral("failed-precondition", "O reembolso só pode ser aprovado depois da confirmação do Proprietário de que o negócio não foi fechado.")
        if (aprovado && req.getString("evidenceIntegrityVerifiedAt").isNullOrBlank())
            throw ErroCentral("failed-precondition", "Abra a evidência e valide a sua integridade antes de aprovar.")
        if (est !in setOf("OWNER_CONFIRMED", "CONTESTED_PENDING_ADMIN")) throw ErroCentral("failed-precondition", "Esta solicitação não aguarda decisão administrativa.")
        val agora = Origem.agoraIso()
        val resId = req.getString("reservationId").orEmpty()
        val b = db.batch()
        b.set(req.reference, mapOf("status" to if (aprovado) "APPROVED_REFUND_PENDING" else "REJECTED", "adminDecisionBy" to admin.uid, "adminDecisionAt" to agora,
            "adminDecisionReason" to fundamento, "updatedAt" to agora), SetOptions.merge())
        b.set(db.document("reservations/$resId"), mapOf("status" to if (aprovado) "REFUND_PENDING" else null, "refundStatus" to if (aprovado) "APPROVED_REFUND_PENDING" else "REJECTED",
            "dealDecision" to if (aprovado) "NOT_CLOSED_CONFIRMED" else "REFUND_REJECTED", "refundableAmount" to (req.getDouble("refundableAmount") ?: 0.0),
            "commissionRetainedAmount" to (req.getDouble("commissionAmount") ?: 0.0), "adminDecisionBy" to admin.uid, "adminDecisionAt" to agora, "updatedAt" to agora)
            .filterValues { it != null }, SetOptions.merge())
        if (aprovado) {
            b.set(db.document("refundPayoutRequests/$pedidoId"), mapOf("id" to pedidoId, "refundRequestId" to pedidoId, "reservationId" to resId, "listingId" to req.getString("listingId"),
                "clientId" to req.getString("clientId"), "ownerId" to req.getString("ownerId"), "amount" to (req.getDouble("refundableAmount") ?: 0.0),
                "commissionRetained" to (req.getDouble("commissionAmount") ?: 0.0), "status" to "AWAITING_PROVIDER_CONFIRMATION",
                "evidenceIntegrityVerifiedAt" to req.getString("evidenceIntegrityVerifiedAt"), "evidenceIntegrityVerifiedBy" to (req.getString("evidenceIntegrityVerifiedBy") ?: admin.uid),
                "createdAt" to agora, "updatedAt" to agora))
            b.set(db.document("products/${req.getString("listingId")}"), mapOf("status" to "ACTIVE", "reservedAt" to FieldValue.delete(), "reservationExpiresAt" to FieldValue.delete(),
                "reservedByUserId" to FieldValue.delete(), "reservedByName" to FieldValue.delete(), "updatedAt" to agora), SetOptions.merge())
        }
        b.commit().await()
        central.auditar(if (aprovado) "APPROVE_REFUND" else "REJECT_REFUND", "REFUND", pedidoId,
            if (aprovado) "Reembolso aprovado e encaminhado para pagamento (pendente de confirmação financeira). $fundamento" else "Solicitação de reembolso recusada. $fundamento")
    }

    /** Confirmação financeira manual do reembolso (sem provedor no Spark): só então "Reembolsado". */
    suspend fun confirmarReembolsoPago(pedidoId: String, referenciaTransferencia: String, admin: Perfil) {
        val req = db.document("refundRequests/$pedidoId").get().await()
        if (req.getString("status") != "APPROVED_REFUND_PENDING") throw ErroCentral("failed-precondition", "Este reembolso não aguarda confirmação de pagamento.")
        val agora = Origem.agoraIso()
        val b = db.batch()
        b.set(req.reference, mapOf("status" to "REFUNDED", "refundedAt" to agora, "refundConfirmedBy" to admin.uid, "refundTransferReference" to referenciaTransferencia, "updatedAt" to agora), SetOptions.merge())
        b.set(db.document("refundPayoutRequests/$pedidoId"), mapOf("status" to "PAID_CONFIRMED", "paidAt" to agora, "transferReference" to referenciaTransferencia, "confirmedBy" to admin.uid, "updatedAt" to agora), SetOptions.merge())
        b.set(db.document("reservations/${req.getString("reservationId")}"), mapOf("status" to "REFUNDED", "refundStatus" to "REFUNDED", "refundedAt" to agora, "updatedAt" to agora), SetOptions.merge())
        b.commit().await()
        central.auditar("CONFIRM_REFUND_PAID", "REFUND", pedidoId, "Reembolso confirmado como pago. Ref.: $referenciaTransferencia")
    }

    // ------------------------------------------------------------------ liberação ao Proprietário
    /**
     * Confirmação manual da liberação do líquido em custódia (secção 22). Reembolso e liberação disputam o mesmo saldo: bloqueia se
     * a reserva tiver pedido de reembolso. "Recebido" no painel só muda com este evento.
     */
    suspend fun confirmarLiberacao(reservaId: String, referenciaTransferencia: String, admin: Perfil) {
        val r = db.document("reservations/$reservaId").get().await()
        if (r.getString("status") != "COMPLETED" || r.getString("payoutStatus") !in setOf("RELEASE_REQUESTED", "PROCESSING"))
            throw ErroCentral("failed-precondition", "Esta reserva não tem liberação pendente.")
        if (!r.getString("refundRequestId").isNullOrBlank()) throw ErroCentral("failed-precondition", "Existe um pedido de reembolso associado: o saldo não pode ser liberado.")
        val agora = Origem.agoraIso()
        val b = db.batch()
        b.set(r.reference, mapOf("payoutStatus" to "RELEASED", "payoutReleasedAt" to agora, "payoutConfirmedBy" to admin.uid, "payoutTransferReference" to referenciaTransferencia, "updatedAt" to agora), SetOptions.merge())
        r.getString("payoutReleaseRequestId")?.let { rel ->
            b.set(db.document("payoutReleaseRequests/$rel"), mapOf("status" to "RELEASED", "releasedAt" to agora, "transferReference" to referenciaTransferencia, "confirmedBy" to admin.uid, "updatedAt" to agora), SetOptions.merge())
        }
        b.commit().await()
        central.auditar("CONFIRM_PAYOUT_RELEASED", "RESERVATION", reservaId, "Liberação do líquido confirmada. Ref.: $referenciaTransferencia")
    }

    suspend fun falhaLiberacao(reservaId: String, motivo: String) {
        val r = db.document("reservations/$reservaId").get().await()
        val agora = Origem.agoraIso()
        val b = db.batch()
        b.set(r.reference, mapOf("payoutStatus" to "FAILED", "payoutFailureReason" to motivo, "updatedAt" to agora), SetOptions.merge())
        r.getString("payoutReleaseRequestId")?.let { b.set(db.document("payoutReleaseRequests/$it"), mapOf("status" to "FAILED", "failureReason" to motivo, "updatedAt" to agora), SetOptions.merge()) }
        b.commit().await()
        central.auditar("PAYOUT_FAILED", "RESERVATION", reservaId, motivo)
    }

    /** Cancelamento administrativo de uma reserva activa (`cancelReservation`), sem estorno automático. */
    suspend fun cancelarReserva(reservaId: String, motivo: String, admin: Perfil) {
        val r = db.document("reservations/$reservaId").get().await()
        if (r.getString("status") != "ACTIVE") throw ErroCentral("failed-precondition", "Só reservas activas podem ser canceladas.")
        val agora = Origem.agoraIso()
        val b = db.batch()
        b.set(r.reference, mapOf("status" to "CANCELLED", "cancelledAt" to agora, "cancelledBy" to admin.uid, "cancelReason" to motivo, "updatedAt" to agora), SetOptions.merge())
        b.set(db.document("products/${r.getString("listingId")}"), mapOf("status" to "ACTIVE", "reservedAt" to FieldValue.delete(), "reservationExpiresAt" to FieldValue.delete(),
            "reservedByUserId" to FieldValue.delete(), "reservedByName" to FieldValue.delete(), "updatedAt" to agora), SetOptions.merge())
        b.commit().await()
        central.auditar("CANCEL_RESERVATION", "RESERVATION", reservaId, "Reserva cancelada pelo Administrador: $motivo. Sem estorno automático.")
    }
}
