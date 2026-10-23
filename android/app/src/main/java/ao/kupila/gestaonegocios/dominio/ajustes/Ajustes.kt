package ao.kupila.gestaonegocios.dominio.ajustes

import ao.kupila.gestaonegocios.dominio.financeiro.ParametrosFinanceiros
import ao.kupila.gestaonegocios.dominio.financeiro.Percentagem
import ao.kupila.gestaonegocios.dominio.financeiro.Promocao
import java.math.BigDecimal

/** Modo do alerta no dispositivo (`setting-alert-mode`). */
enum class ModoAlerta(val codigo: String, val rotulo: String) {
    AUTOMATICO("AUTO", "Automático (celular)"), SOM("SOUND", "Som"), VIBRACAO("VIBRATE", "Vibração"),
    SOM_VIBRACAO("BOTH", "Som + vibração"), SILENCIOSO("SILENT", "Silencioso");
    companion object { fun de(c: String?) = entries.firstOrNull { it.codigo == c } ?: AUTOMATICO }
}

/** Alertas ao Proprietário (`settings-alerts`, HTML 7863-7872). */
data class AjustesAlertasProprietario(
    val reserva: Boolean = true,
    val interesse: Boolean = true,
    val email: Boolean = true,
    val sms: Boolean = true,
    val limiarClientesDistintos: Int = 3,
    val janelaHoras: Int = 24,
    val repetirAposHoras: Int = 12,
)

/**
 * Ajustes globais da plataforma, versionados. Só o Administrador os altera, via Central (secção 18).
 * Valores por omissão = fallback da origem para ambiente novo.
 */
data class AjustesPlataforma(
    val versao: Long = 0,
    val financeiro: ParametrosFinanceiros = ParametrosFinanceiros.inicial,
    val prazoReservaMinutos: Long = 1440,
    val inactividadeMinutos: Int = 10,
    val entidadePagamento: String = "",
    val alertas: AjustesAlertasProprietario = AjustesAlertasProprietario(),
    val actualizadoPor: String? = null,
    val actualizadoEm: java.time.Instant? = null,
)

object ValidacaoAjustes {
    private val CEM = BigDecimal(100)

    /** Mensagens e intervalos de `saveSettings` (12851-12882) e payments-v2 (15846). */
    fun validar(a: AjustesPlataforma): List<String> {
        val e = mutableListOf<String>()
        val f = a.financeiro
        if (f.sinalVenda.valor.signum() <= 0 || f.sinalVenda.valor > CEM) e += "Defina um Sinal de Reserva entre 0,01% e 100%."
        if (f.mesesSinalAluguer !in 1..12) e += "Defina os meses de sinal do aluguer entre 1 e 12."
        if (!f.comissao.dentroDe0a100) e += "Defina uma Comissão da Plataforma entre 0% e 100% do sinal."
        if (!f.taxaAnuncio.dentroDe0a100) e += "Defina uma Taxa de Anúncio entre 0% e 100% do preço do produto."
        if (a.prazoReservaMinutos <= 0 || a.prazoReservaMinutos > 8760L * 60) e += "Defina um prazo de reserva válido, superior a zero e até 8760 horas."
        if (a.inactividadeMinutos !in 1..1440) e += "Defina o bloqueio por inatividade entre 1 e 1440 minutos."
        val al = a.alertas
        if (al.limiarClientesDistintos !in 2..100 || al.janelaHoras !in 1..168 || al.repetirAposHoras !in 1..168)
            e += "Revise os parâmetros de alertas por interesse: clientes distintos entre 2 e 100, janela e repetição entre 1 e 168 horas."
        if (f.promocoes.values.any { !it.dentroDe0a100 } || f.duracaoPromocaoDias !in 1..90)
            e += "Revise as percentagens dos serviços promocionais (0% a 100%) e a duração entre 1 e 90 dias."
        if (Promocao.entries.any { it !in f.promocoes }) e += "Defina a percentagem de todos os serviços promocionais."
        if (a.entidadePagamento.length > 20) e += "A entidade de pagamento tem no máximo 20 caracteres."
        return e
    }

    /**
     * Consistência entre parâmetros (secção 18): no aluguer C = B·c e D = mensal·m. Para um contrato mínimo
     * de N meses, C ≤ D exige c ≤ m/N·100. Devolve os prazos padrão que ficariam bloqueados — aviso ao
     * Administrador antes de gravar, não bloqueio (anúncios com prazos menores continuam válidos).
     */
    fun prazosAluguerIncompativeis(f: ParametrosFinanceiros): List<Int> = listOf(3, 6, 12).filter { meses ->
        f.comissao.valor.multiply(BigDecimal(meses)) > BigDecimal(f.mesesSinalAluguer).multiply(CEM)
    }

    fun percentagem(v: String): Percentagem? = v.replace(',', '.').trim().toBigDecimalOrNull()?.let(Percentagem::de)
}
