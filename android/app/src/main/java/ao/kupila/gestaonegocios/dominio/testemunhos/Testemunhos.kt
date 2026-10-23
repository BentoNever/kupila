package ao.kupila.gestaonegocios.dominio.testemunhos

import ao.kupila.gestaonegocios.dominio.perfis.Papel
import ao.kupila.gestaonegocios.dominio.transaccoes.EstadoReserva

enum class EstadoTestemunho(val codigo: String) { PENDENTE("PENDING"), APROVADO("APPROVED"), REJEITADO("REJECTED");
    companion object { fun de(c: String?) = entries.firstOrNull { it.codigo == c } } }

/** Como o nome do autor aparece (`testimonialDisplayName`, HTML 13126). */
enum class ModoNome(val codigo: String, val rotulo: String) { ABREVIADO("SHORT", "Primeiro nome + inicial"), COMPLETO("FULL", "Nome completo"), ANONIMO("ANON", "Apenas “utilizador verificado”") }

/** Classificações do formulário da origem (13129). */
val ROTULOS_CLASSIFICACAO = mapOf(5 to "Excelente", 4 to "Muito bom", 3 to "Bom", 2 to "Regular", 1 to "Insatisfatório")
const val TEXTO_CONSENTIMENTO = "Autorizo a publicação deste testemunho no Kupila-Gestão de negócios. O app não publicará telefone, e-mail, documentos nem localização privada."

object RegrasTestemunho {
    fun nomePublico(nome: String, papel: Papel, modo: ModoNome): String {
        val n = nome.trim().ifEmpty { "Utilizador" }
        return when (modo) {
            ModoNome.ANONIMO -> if (papel == Papel.PROPRIETARIO) "Proprietário verificado" else "Cliente verificado"
            ModoNome.COMPLETO -> n
            ModoNome.ABREVIADO -> n.split(Regex("\\s+")).filter(String::isNotBlank).let { p ->
                if (p.size > 1) "${p.first()} ${p.last().first().uppercaseChar()}." else p.firstOrNull() ?: "Utilizador verificado"
            }
        }
    }

    /** Elegível: reserva CONCLUÍDA em que o utilizador participou e sem testemunho não rejeitado (13125). */
    fun elegivel(estado: EstadoReserva, participou: Boolean, jaTemTestemunhoActivo: Boolean) =
        estado == EstadoReserva.CONCLUIDA && participou && !jaTemTestemunhoActivo

    fun validar(classificacao: Int, texto: String, consentimento: Boolean): List<String> = buildList {
        if (classificacao !in 1..5) add("Escolha uma classificação de 1 a 5 estrelas.")
        if (texto.trim().length < 20) add("Descreva a experiência com pelo menos 20 caracteres.")
        if (texto.length > 600) add("O testemunho tem no máximo 600 caracteres.")
        if (!consentimento) add("Confirme que autoriza a publicação após moderação.")
    }
}
