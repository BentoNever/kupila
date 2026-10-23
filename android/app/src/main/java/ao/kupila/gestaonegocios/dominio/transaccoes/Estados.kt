package ao.kupila.gestaonegocios.dominio.transaccoes

/**
 * Estados financeiros e comerciais SEPARADOS (secção 21): anúncio ≠ intenção ≠ reserva ≠ liberação ≠ reembolso.
 * Códigos preservados da origem para compatibilidade com dados existentes. Só o servidor muda estes estados;
 * a app apresenta-os e pede comandos.
 */

enum class TipoIntencao(val codigo: String) { PUBLICACAO("LISTING"), RESERVA("RESERVATION");
    companion object { fun de(c: String?) = entries.firstOrNull { it.codigo == c } } }

/** `KUPILA_PAYMENT_OPEN_STATUSES` / `KUPILA_PAYMENT_TERMINAL_STATUSES` (HTML 15868-15869) + rejeições. */
enum class EstadoIntencao(val codigo: String, val rotulo: String, val aberto: Boolean) {
    AGUARDA_PAGAMENTO("AWAITING_PAYMENT", "A aguardar pagamento", true),
    A_VERIFICAR("VERIFYING", "Comprovativo em verificação", true),
    REFERENCIA_CORRESPONDE("RECEIPT_MATCHED", "Referência reconhecida · a confirmar com o provedor", true),
    AGUARDA_CONFIGURACAO("AWAITING_PROVIDER_CONFIGURATION", "Pagamento indisponível · provedor por configurar", true),
    AGUARDA_CONFIRMACAO_PROVEDOR("AWAITING_PROVIDER_CONFIRMATION", "A aguardar confirmação do provedor", true),
    EM_PROCESSAMENTO("PROCESSING", "Em processamento", true),
    REFERENCIA_REJEITADA("REJECTED_REFERENCE", "Referência não corresponde a esta cobrança", true),
    PAGAMENTO_REJEITADO("REJECTED_PAYMENT", "Pagamento não confirmado (montante/destino)", true),
    CONCLUIDO("COMPLETED", "Pagamento confirmado", false),
    PUBLICADO("PUBLISHED", "Pagamento confirmado · anúncio publicado", false),
    LIBERACAO_PENDENTE("PAYOUT_PENDING", "Confirmado · liberação pendente", false),
    CANCELADO("CANCELLED", "Cancelado", false),
    EXPIRADO("EXPIRED", "Prazo de pagamento expirado", false),
    ANULADO("VOIDED", "Anulado", false);

    companion object {
        fun de(c: String?) = when (c) { "CANCELED" -> CANCELADO; "PAYMENT_EXPIRED" -> EXPIRADO; else -> entries.firstOrNull { it.codigo == c } }
    }
    /** Confirmação financeira oficial (só estes estados autorizam publicar/reservar). */
    val confirmado get() = this == CONCLUIDO || this == PUBLICADO || this == LIBERACAO_PENDENTE
}

enum class EstadoReserva(val codigo: String, val rotulo: String) {
    ACTIVA("ACTIVE", "Reserva activa"),
    CONCLUIDA("COMPLETED", "Negócio concluído"),
    EXPIRADA("EXPIRED", "Reserva expirada"),
    CANCELADA("CANCELLED", "Reserva cancelada"),
    REEMBOLSO_PENDENTE("REFUND_PENDING", "Reembolso em processamento"),
    REEMBOLSADA("REFUNDED", "Reembolsada");
    companion object { fun de(c: String?) = entries.firstOrNull { it.codigo == c } }
}

/** Decisão do negócio após vistoria (`dealDecision`). */
enum class DecisaoNegocio(val codigo: String) {
    FECHO_CONFIRMADO("CLOSED_CONFIRMED"), NAO_FECHO_PEDIDO("NOT_CLOSED_REQUESTED"), NAO_FECHO_CONFIRMADO("NOT_CLOSED_CONFIRMED");
    companion object { fun de(c: String?) = entries.firstOrNull { it.codigo == c } }
}

/**
 * Liberação do líquido em custódia (secção 22). "Recebido" no painel só com [LIBERADO] confirmado pela fonte financeira.
 * A origem só tinha RELEASE_REQUESTED/RELEASED; os restantes tornam o estado explicável.
 */
enum class EstadoLiberacao(val codigo: String, val rotulo: String) {
    EM_CUSTODIA("HELD", "Líquido em custódia até à decisão da vistoria"),
    SOLICITADA("RELEASE_REQUESTED", "Liberação solicitada"),
    EM_PROCESSAMENTO("PROCESSING", "Liberação em processamento"),
    LIBERADO("RELEASED", "Liberado · confirmado"),
    FALHOU("FAILED", "Liberação falhou · pendente de resolução"),
    BLOQUEADA_POR_REEMBOLSO("BLOCKED_BY_REFUND", "Bloqueada: existe pedido de reembolso");
    companion object { fun de(c: String?) = entries.firstOrNull { it.codigo == c } }
}

/** `refundStatusLabel` (HTML 14933). */
enum class EstadoReembolso(val codigo: String, val rotulo: String) {
    AGUARDA_PROPRIETARIO("PENDING_OWNER_CONFIRMATION", "Aguardando confirmação do Proprietário"),
    PROPRIETARIO_CONFIRMOU("OWNER_CONFIRMED", "Proprietário confirmou: negócio não fechado"),
    CONTESTADO("CONTESTED_PENDING_ADMIN", "Proprietário contestou · análise administrativa"),
    APROVADO_PENDENTE("APPROVED_REFUND_PENDING", "Reembolso aprovado · processamento financeiro"),
    REEMBOLSADO("REFUNDED", "Reembolso confirmado"),
    RECUSADO("REJECTED", "Solicitação recusada");
    companion object { fun de(c: String?) = entries.firstOrNull { it.codigo == c } }
    val terminal get() = this == REEMBOLSADO || this == RECUSADO
}

/** Motivos (`refund-request-reason`, 7580) com declaração adaptada (incons. E). */
enum class MotivoReembolso(val codigo: String, val rotulo: String, val declaracao: String) {
    PRODUTO_DIFERENTE("PRODUCT_MISMATCH", "Produto diferente do anúncio",
        "Declaro que houve vistoria presencial, que o produto era diferente do anunciado, que o negócio não foi fechado com o Proprietário e que as informações/evidências apresentadas são verdadeiras."),
    CONDICAO_INSATISFATORIA("CONDITION_UNSATISFACTORY", "Estado/condição insatisfatória",
        "Declaro que houve vistoria presencial, que o estado do produto não correspondia ao anunciado, que o negócio não foi fechado com o Proprietário e que as informações/evidências apresentadas são verdadeiras."),
    PROBLEMA_DOCUMENTAL("DOCUMENTATION_ISSUE", "Problema de documentação/titularidade",
        "Declaro que a documentação/titularidade apresentada não permitiu fechar o negócio com o Proprietário e que as informações/evidências apresentadas são verdadeiras."),
    PROPRIETARIO_AUSENTE("OWNER_DID_NOT_ATTEND", "Proprietário não compareceu",
        "Declaro que compareci ao encontro combinado dentro do prazo da reserva, que o Proprietário não compareceu, que o negócio não foi fechado e que as informações/evidências apresentadas são verdadeiras."),
    OUTRO("OTHER", "Outro motivo comprovável",
        "Declaro que o negócio não foi fechado com o Proprietário pelo motivo descrito e que as informações/evidências apresentadas são verdadeiras.");
    companion object { fun de(c: String?) = entries.firstOrNull { it.codigo == c } }
    /** Exige vistoria presencial (só os motivos em que ela ocorreu). */
    val houveVistoria get() = this == PRODUTO_DIFERENTE || this == CONDICAO_INSATISFATORIA
}

enum class Actor { CLIENTE, PROPRIETARIO, ADMINISTRADOR, PROVEDOR, SISTEMA }

/** Transições permitidas do reembolso (14954-14958). [contestadoAprovavel] = decisão D-07 (b). */
object TransicoesReembolso {
    fun permitida(de: EstadoReembolso, para: EstadoReembolso, actor: Actor, contestadoAprovavel: Boolean = false): Boolean = when (de) {
        EstadoReembolso.AGUARDA_PROPRIETARIO -> actor == Actor.PROPRIETARIO && (para == EstadoReembolso.PROPRIETARIO_CONFIRMOU || para == EstadoReembolso.CONTESTADO)
        EstadoReembolso.PROPRIETARIO_CONFIRMOU -> actor == Actor.ADMINISTRADOR && (para == EstadoReembolso.APROVADO_PENDENTE || para == EstadoReembolso.RECUSADO)
        EstadoReembolso.CONTESTADO -> actor == Actor.ADMINISTRADOR && (para == EstadoReembolso.RECUSADO || (contestadoAprovavel && para == EstadoReembolso.APROVADO_PENDENTE))
        // Só a confirmação do provedor encerra como reembolsado; aprovar não é pagar.
        EstadoReembolso.APROVADO_PENDENTE -> actor == Actor.PROVEDOR && para == EstadoReembolso.REEMBOLSADO
        EstadoReembolso.REEMBOLSADO, EstadoReembolso.RECUSADO -> false
    }
}

/** Elegibilidade para pedir reembolso (`openRefundRequest`, 14946). */
object ElegibilidadeReembolso {
    const val DESCRICAO_MINIMA = 20
    fun podePedir(estadoReserva: EstadoReserva, eClienteDaReserva: Boolean, pedidoExistente: Boolean) =
        eClienteDaReserva && !pedidoExistente && (estadoReserva == EstadoReserva.ACTIVA || estadoReserva == EstadoReserva.EXPIRADA)

    fun validarPedido(motivo: MotivoReembolso?, descricao: String, declaracaoAceite: Boolean, temEvidencia: Boolean, otpInformado: String): List<String> {
        val e = mutableListOf<String>()
        if (!Regex("^\\d{6}$").matches(otpInformado.trim())) e += "Informe o OTP de 6 dígitos da reserva."
        if (motivo == null || descricao.trim().length < DESCRICAO_MINIMA || !declaracaoAceite || !temEvidencia)
            e += "Preencha motivo, descrição, declaração e evidência antes de enviar."
        return e
    }
}

/**
 * Validação de OTP (incons. C): os resultados possíveis do comando central. Repetir um OTP já usado
 * não conclui nem transfere outra vez.
 */
enum class ResultadoOtp(val mensagem: String) {
    VALIDADO("Negócio confirmado. A liberação do líquido em custódia foi solicitada; o recebimento só aparece depois da confirmação financeira."),
    JA_VALIDADO("Este OTP já foi utilizado; o negócio já estava confirmado."),
    INVALIDO("Código OTP inválido ou reserva não encontrada."),
    EXPIRADO("Este OTP expirou. Se houve vistoria sem negócio, o Cliente ainda pode solicitar o reembolso."),
    SEM_PERMISSAO("Apenas o Proprietário titular ou Administrador pode validar este OTP."),
    REEMBOLSO_PENDENTE("Existe uma solicitação de reembolso associada a esta reserva. Conclua primeiro a análise de não fechamento."),
    TENTATIVAS_EXCEDIDAS("Demasiadas tentativas. Aguarde antes de tentar novamente."),
}
