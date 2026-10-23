package ao.kupila.gestaonegocios.dados.admin

import ao.kupila.gestaonegocios.BuildConfig
import ao.kupila.gestaonegocios.dados.anuncios.AnunciosRepositorio
import ao.kupila.gestaonegocios.dados.remoto.ArranqueFirebase
import ao.kupila.gestaonegocios.dados.remoto.CentralSpark
import ao.kupila.gestaonegocios.dados.remoto.ErroCentral
import ao.kupila.gestaonegocios.dados.remoto.Origem
import ao.kupila.gestaonegocios.dominio.ajustes.AjustesPlataforma
import ao.kupila.gestaonegocios.dominio.aparencia.IdentidadeVisual
import ao.kupila.gestaonegocios.dominio.autorizacoes.AccaoAutorizada
import ao.kupila.gestaonegocios.dominio.autorizacoes.RegrasAutorizacao
import ao.kupila.gestaonegocios.dominio.financeiro.Promocao
import ao.kupila.gestaonegocios.dominio.notificacoes.Campanha
import ao.kupila.gestaonegocios.dominio.perfis.Papel
import ao.kupila.gestaonegocios.dominio.perfis.Perfil
import ao.kupila.gestaonegocios.dominio.testemunhos.ModoNome
import ao.kupila.gestaonegocios.dominio.testemunhos.RegrasTestemunho
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/** Autorizações de desanúncio/exclusão (`requestListingAuthorization`…, HTML 14613-14674). */
@Singleton
class AutorizacoesRepositorio @Inject constructor(private val central: CentralSpark, private val anuncios: AnunciosRepositorio) {
    private val db get() = FirebaseFirestore.getInstance()

    suspend fun pedir(listingId: String, titulo: String, accao: AccaoAutorizada, dono: Perfil): String {
        val emCurso = db.collection("authorizationRequests").whereEqualTo("ownerId", dono.uid).whereEqualTo("listingId", listingId).get().await().documents
            .any { it.getString("action") == accao.codigo && it.getString("status") in setOf("PENDING_ADMIN", "CODE_SENT") }
        if (emCurso) throw ErroCentral("already-exists", "Já existe um pedido de autorização em curso para este produto.")
        val id = Origem.id("AUT")
        db.document("authorizationRequests/$id").set(mapOf("id" to id, "listingId" to listingId, "listingTitle" to titulo, "ownerId" to dono.uid, "ownerName" to dono.nome,
            "action" to accao.codigo, "status" to "PENDING_ADMIN", "requestedAt" to Origem.agoraIso(), "deliveryChannel" to "APP", "deliveryDestination" to dono.email,
            "code" to "", "codeExpiresAt" to null, "source" to "ANDROID")).await()
        central.auditar("REQUEST_LISTING_AUTHORIZATION", "AUTHORIZATION", id, "Pedido para ${accao.verbo} “$titulo”.")
        return id
    }

    /**
     * Administrador gera o código de 6 dígitos (validade 30 min). Sem serviço de e-mail/SMS no Spark (D-08), a entrega é feita na
     * app do Proprietário (notificação) — registado com esse canal, sem fingir um envio externo.
     */
    suspend fun gerarCodigo(pedidoId: String, admin: Perfil): String {
        val ref = db.document("authorizationRequests/$pedidoId")
        val r = ref.get().await()
        if (r.getString("status") != "PENDING_ADMIN") throw ErroCentral("failed-precondition", "Este pedido já não aguarda o Administrador.")
        val codigo = (100_000 + SecureRandom().nextInt(900_000)).toString()
        val agora = Origem.agoraIso()
        ref.set(mapOf("code" to codigo, "status" to "CODE_SENT", "generatedBy" to admin.uid, "generatedAt" to agora,
            "codeExpiresAt" to Origem.iso(System.currentTimeMillis() + RegrasAutorizacao.VALIDADE_CODIGO_MIN * 60_000), "deliveryChannel" to "APP",
            "updatedAt" to agora), SetOptions.merge()).await()
        central.auditar("GENERATE_AUTHORIZATION_CODE", "AUTHORIZATION", pedidoId, "Código gerado e entregue na app do Proprietário.")
        return codigo
    }

    suspend fun recusar(pedidoId: String, motivo: String) {
        db.document("authorizationRequests/$pedidoId").set(mapOf("status" to "DENIED", "deniedReason" to motivo, "updatedAt" to Origem.agoraIso()), SetOptions.merge()).await()
        central.auditar("DENY_AUTHORIZATION", "AUTHORIZATION", pedidoId, motivo)
    }

    /** Uso único, vinculado ao produto e à acção; executa sem reembolso (`useAuthorizationCode`). */
    suspend fun usar(pedidoId: String, codigo: String, dono: Perfil) {
        val ref = db.document("authorizationRequests/$pedidoId")
        val r = ref.get().await()
        if (r.getString("ownerId") != dono.uid) throw ErroCentral("permission-denied", "Pedido inválido ou sem permissão.")
        if (r.getString("status") != "CODE_SENT") throw ErroCentral("failed-precondition", "Este pedido ainda não possui código válido.")
        if ((Origem.ms(r.get("codeExpiresAt")) ?: 0) < System.currentTimeMillis()) {
            ref.set(mapOf("status" to "EXPIRED", "updatedAt" to Origem.agoraIso()), SetOptions.merge()).await()
            throw ErroCentral("deadline-exceeded", "O código expirou. Solicite nova autorização.")
        }
        if (codigo.trim() != r.getString("code")) throw ErroCentral("invalid-argument", "Código de autorização inválido.")
        val listingId = r.getString("listingId").orEmpty()
        val p = db.document("products/$listingId").get().await()
        if (!p.exists() || p.getString("ownerId") != dono.uid) throw ErroCentral("not-found", "Produto não encontrado ou titularidade inválida.")
        when (AccaoAutorizada.de(r.getString("action"))) {
            AccaoAutorizada.DESANUNCIAR -> { if (p.getString("status") != "ACTIVE") throw ErroCentral("failed-precondition", "Este produto já não está activo."); anuncios.desanunciar(listingId, "Autorização $pedidoId.") }
            AccaoAutorizada.EXCLUIR -> { if (p.getString("status") in setOf("RESERVED", "COMPLETED")) throw ErroCentral("failed-precondition", "Este produto não pode ser eliminado no estado actual."); anuncios.excluir(listingId, "Autorização $pedidoId.") }
            null -> throw ErroCentral("invalid-argument", "Acção desconhecida.")
        }
        ref.set(mapOf("status" to "USED", "usedAt" to Origem.agoraIso(), "code" to "", "updatedAt" to Origem.agoraIso()), SetOptions.merge()).await()
        central.auditar("USE_AUTHORIZATION_CODE", "AUTHORIZATION", pedidoId, "Código validado para ${r.getString("action")} do produto $listingId.")
    }
}

/** Testemunhos verificados (`submitVerifiedTestimonial`/`moderateTestimonial`, HTML 13130-13131). */
@Singleton
class TestemunhosRepositorio @Inject constructor(private val central: CentralSpark) {
    private val db get() = FirebaseFirestore.getInstance()

    suspend fun submeter(reserva: Map<String, Any?>, reservaId: String, autor: Perfil, classificacao: Int, texto: String, modo: ModoNome) {
        if (reserva["status"] != "COMPLETED" || (reserva["userId"] != autor.uid && reserva["ownerId"] != autor.uid))
            throw ErroCentral("failed-precondition", "Este negócio não está elegível para testemunho verificado.")
        val id = "${Origem.idSeguro(reservaId)}_${Origem.idSeguro(autor.uid)}"
        val agora = Origem.agoraIso()
        db.document("testimonials/$id").set(mapOf("authorId" to autor.uid, "authorRole" to autor.papel.codigo, "authorName" to autor.nome,
            "publicName" to RegrasTestemunho.nomePublico(autor.nome, autor.papel, modo), "displayMode" to modo.codigo, "reservationId" to reservaId,
            "listingId" to reserva["listingId"], "listingTitle" to reserva["listingTitle"], "category" to reserva["category"], "intent" to reserva["intent"],
            "province" to reserva["province"], "rating" to classificacao, "text" to texto.trim(), "consent" to true, "verified" to true, "status" to "PENDING",
            "transactionCompletedAt" to (reserva["completedAt"] ?: agora), "createdAt" to agora, "updatedAt" to agora, "source" to "ANDROID")).await()
        central.auditar("SUBMIT_TESTIMONIAL", "TESTIMONIAL", id, "Testemunho enviado para moderação.")
    }

    /** Moderação + actualização das métricas públicas de confiança (`platformMetrics/trust`). */
    suspend fun moderar(id: String, aprovado: Boolean, admin: Perfil) {
        val agora = Origem.agoraIso()
        db.document("testimonials/$id").set(mapOf("status" to if (aprovado) "APPROVED" else "REJECTED", "moderatedAt" to agora, "moderatedBy" to admin.uid, "updatedAt" to agora), SetOptions.merge()).await()
        central.auditar("MODERATE_TESTIMONIAL", "TESTIMONIAL", id, if (aprovado) "Testemunho aprovado pelo Administrador." else "Testemunho rejeitado pelo Administrador.")
        recalcularMetricas()
    }

    suspend fun recalcularMetricas() {
        val aprovados = db.collection("testimonials").whereEqualTo("status", "APPROVED").get().await().documents
        val concluidos = db.collection("reservations").whereEqualTo("status", "COMPLETED").get().await().size()
        val media = aprovados.mapNotNull { it.getLong("rating") }.takeIf { it.isNotEmpty() }?.average() ?: 0.0
        db.document("platformMetrics/trust").set(mapOf("approvedTestimonials" to aprovados.size, "completedDeals" to concluidos,
            "averageRating" to Math.round(media * 10) / 10.0, "updatedAt" to Origem.agoraIso()), SetOptions.merge()).await()
    }
}

/** Gestão de utilizadores pelo Administrador (`saveManagedUser`, `setManagedUserAccountState`, HTML 14866-14908). */
@Singleton
class UtilizadoresRepositorio @Inject constructor(private val central: CentralSpark) {
    private val db get() = FirebaseFirestore.getInstance()

    /**
     * Cria a conta Auth numa instância Firebase SECUNDÁRIA (equivalente ao signUp REST da origem): a sessão do Administrador nunca é
     * trocada. Se a gravação do perfil falhar, a conta Auth criada é removida (rollback).
     */
    suspend fun criar(admin: Perfil, papel: Papel, estado: String, nome: String, email: String, senha: String, telefone: String, telefoneAlt: String,
                      provincia: String, municipio: String, bairro: String, fotoDataUrl: String?): String {
        if (papel == Papel.ADMINISTRADOR) throw ErroCentral("permission-denied", "A criação de Administradores usa a concessão de privilégios.")
        val principal = FirebaseApp.getInstance()
        val sec = FirebaseApp.getApps(principal.applicationContext).firstOrNull { it.name == "kupila-gestao-contas" }
            ?: FirebaseApp.initializeApp(principal.applicationContext, FirebaseOptions.Builder(principal.options).build(), "kupila-gestao-contas")
        val authSec = FirebaseAuth.getInstance(sec)
        if (BuildConfig.USAR_EMULADOR) (ArranqueFirebase.estado as? ArranqueFirebase.Estado.Emulador)?.let { runCatching { authSec.useEmulator(it.host, 9099) } }
        val criado = authSec.createUserWithEmailAndPassword(email.trim(), senha).await().user ?: throw ErroCentral("internal", "A conta não foi criada.")
        val agora = Origem.agoraIso()
        try {
            db.document("users/${criado.uid}").set(mapOf("role" to papel.codigo, "status" to estado, "name" to nome.trim(), "email" to (criado.email ?: email.trim().lowercase()),
                "phonePrimary" to telefone, "phoneAlt" to telefoneAlt, "province" to provincia, "municipality" to municipio, "neighborhood" to bairro,
                "photoDataUrl" to (fotoDataUrl ?: ""), "creationSource" to "ADMIN", "createdByAdminId" to admin.uid, "createdByAdminName" to admin.nome,
                "createdAt" to agora, "updatedAt" to agora, "authProvider" to "PASSWORD", "authProvisionedAt" to agora, "alertChannel" to "AUTO")).await()
        } catch (e: Exception) {
            runCatching { criado.delete().await() }
            throw e
        } finally { runCatching { authSec.signOut() } }
        central.auditar("CREATE_USER", "USER", criado.uid, "Conta ${papel.rotulo} criada pelo Administrador ($estado).")
        return criado.uid
    }

    /** Estado de qualquer conta (excepto a própria), sem editar dados pessoais. */
    suspend fun alterarEstado(admin: Perfil, uid: String, estado: String, motivo: String) {
        if (uid == admin.uid) throw ErroCentral("permission-denied", "Não pode alterar o estado da sua própria conta.")
        db.document("users/$uid").set(mapOf("status" to estado, "statusChangedBy" to admin.uid, "statusChangedAt" to Origem.agoraIso(), "statusReason" to motivo,
            "updatedAt" to Origem.agoraIso()), SetOptions.merge()).await()
        central.auditar("CHANGE_USER_STATUS", "USER", uid, "Estado alterado para $estado sem edição dos dados pessoais. $motivo")
    }

    /** Dados pessoais: só contas criadas por ESTE Administrador (`isUserCreatedByCurrentAdmin`). */
    suspend fun editarDados(admin: Perfil, uid: String, dados: Map<String, Any?>) {
        val u = db.document("users/$uid").get().await()
        val fonte = (u.getString("creationSource") ?: u.getString("createdVia") ?: "").uppercase()
        if (u.getString("createdByAdminId") != admin.uid || fonte !in setOf("ADMIN", "ADMIN_CREATED"))
            throw ErroCentral("permission-denied", "A edição foi bloqueada porque esta conta não foi criada por este Administrador. Use apenas as acções de estado.")
        val permitidos = setOf("name", "phonePrimary", "phoneAlt", "province", "municipality", "neighborhood", "photoDataUrl", "role")
        val d = dados.filterKeys { it in permitidos }.toMutableMap()
        if (d["role"] == "GESTOR") d.remove("role")
        db.document("users/$uid").set(d + ("updatedAt" to Origem.agoraIso()), SetOptions.merge()).await()
        central.auditar("EDIT_USER_DATA", "USER", uid, "Dados pessoais actualizados pelo Administrador criador.")
    }

    suspend fun concederAdministrador(admin: Perfil, uid: String, motivo: String) {
        if (motivo.trim().length < 5) throw ErroCentral("invalid-argument", "Indique o motivo da concessão.")
        db.document("users/$uid").set(mapOf("role" to "GESTOR", "roleGrantedBy" to admin.uid, "roleGrantedAt" to Origem.agoraIso(), "roleGrantReason" to motivo,
            "updatedAt" to Origem.agoraIso()), SetOptions.merge()).await()
        central.auditar("GRANT_ADMIN", "USER", uid, motivo)
    }
}

/** Gravação dos ajustes globais em `settings/platform`, com os nomes da origem (`saveSettings`, HTML 12851-12927). */
@Singleton
class AjustesAdminRepositorio @Inject constructor(private val central: CentralSpark) {
    private val db get() = FirebaseFirestore.getInstance()
    private fun hex(c: Long) = "#%06x".format(c and 0xFFFFFF)

    suspend fun guardar(a: AjustesPlataforma, admin: Perfil) {
        val f = a.financeiro
        val agora = Origem.agoraIso()
        db.document("settings/platform").set(mapOf(
            "depositPercent" to f.sinalVenda.valor.toDouble(), "rentalDepositMonths" to f.mesesSinalAluguer, "commissionPercent" to f.comissao.valor.toDouble(),
            "listingFeePercent" to f.taxaAnuncio.valor.toDouble(), "chargeManagerListingFees" to false, "reservationDurationMinutes" to a.prazoReservaMinutos,
            "sessionInactivityMinutes" to a.inactividadeMinutos, "paymentEntity" to a.entidadePagamento,
            "promoFeaturedPercent" to f.promocoes.getValue(Promocao.DESTAQUE).valor.toDouble(), "promoTopPercent" to f.promocoes.getValue(Promocao.TOP).valor.toDouble(),
            "promoPriorityPercent" to f.promocoes.getValue(Promocao.PRIORIDADE).valor.toDouble(), "promoDurationDays" to f.duracaoPromocaoDias,
            "ownerAlertReservationEnabled" to a.alertas.reserva, "ownerAlertInterestEnabled" to a.alertas.interesse, "ownerAlertEmailEnabled" to a.alertas.email,
            "ownerAlertSmsEnabled" to a.alertas.sms, "ownerInterestThreshold" to a.alertas.limiarClientesDistintos, "ownerInterestWindowHours" to a.alertas.janelaHoras,
            "ownerInterestCooldownHours" to a.alertas.repetirAposHoras, "updatedAt" to agora, "updatedBy" to admin.uid, "settingsRevision" to System.currentTimeMillis(),
        ), SetOptions.merge()).await()
        central.auditar("UPDATE_SETTINGS", "SETTINGS", "PLATFORM", "Sinal ${f.sinalVenda}, meses ${f.mesesSinalAluguer}, comissão ${f.comissao}, anúncio ${f.taxaAnuncio}, " +
            "prazo ${a.prazoReservaMinutos} min, inactividade ${a.inactividadeMinutos} min, Administrador ISENTO.")
    }

    suspend fun guardarIdentidade(id: IdentidadeVisual, logoDataUrl: String?, admin: Perfil) {
        val agora = Origem.agoraIso()
        val m = mutableMapOf<String, Any?>("visualTheme" to id.tema.codigoOrigem, "visualPrimary" to hex(id.primaria), "visualAccent" to hex(id.destaque),
            "visualAppearance" to id.modoPlataforma.codigoOrigem, "homeVisualPreset" to id.vitrine.name, "homeSearchPrimary" to hex(id.pesquisaPrimaria),
            "homeSearchAccent" to hex(id.pesquisaDestaque), "homeShowcasePrimary" to hex(id.vitrinePrimaria), "homeShowcaseAccent" to hex(id.vitrineDestaque),
            "visualUpdatedAt" to agora, "updatedAt" to agora, "settingsRevision" to System.currentTimeMillis())
        if (logoDataUrl != null) m["appLogoDataUrl"] = logoDataUrl
        db.document("settings/platform").set(m, SetOptions.merge()).await()
        central.auditar("UPDATE_VISUAL_IDENTITY", "SETTINGS", "PLATFORM", "Identidade visual: ${id.tema.nome}, vitrine ${id.vitrine.nome}.")
    }

    /** Campanhas em `settings.marketingMessages` (máx. 100, como a origem). */
    suspend fun guardarCampanhas(lista: List<Campanha>, admin: Perfil, accao: String, alvo: String) {
        db.document("settings/platform").set(mapOf("marketingMessages" to lista.take(100).map {
            mapOf("id" to it.id, "title" to it.titulo, "message" to it.mensagem, "audience" to it.publico.codigo, "priority" to it.prioridade.codigo, "action" to it.accao.codigo,
                "cta" to it.cta, "startAt" to it.inicio.toString(), "endAt" to it.fim.toString(), "disabled" to it.desactivada, "createdAt" to it.criadaEm.toString(), "createdBy" to admin.uid)
        }, "updatedAt" to Origem.agoraIso()), SetOptions.merge()).await()
        central.auditar(accao, "MARKETING", alvo, "Campanhas actualizadas.")
    }

    /** Restaurar padrões (`FACTORY`, HTML 23840): só ajustes/identidade; não apaga operações nem histórico. */
    suspend fun restaurarPadroes(admin: Perfil) {
        guardar(AjustesPlataforma(), admin)
        guardarIdentidade(IdentidadeVisual(), "", admin)
        central.auditar("RESTORE_FACTORY_SETTINGS", "SETTINGS", "PLATFORM", "Ajustes e identidade visual restaurados para os padrões.")
    }
}
