package ao.kupila.gestaonegocios.dominio

import ao.kupila.gestaonegocios.dominio.ajustes.AjustesPlataforma
import ao.kupila.gestaonegocios.dominio.ajustes.ValidacaoAjustes
import ao.kupila.gestaonegocios.dominio.anuncio.CamposLegadoAusentes
import ao.kupila.gestaonegocios.dominio.anuncio.DetalhesCategoria
import ao.kupila.gestaonegocios.dominio.anuncio.EstadoAnuncio
import ao.kupila.gestaonegocios.dominio.anuncio.FormularioAnuncio
import ao.kupila.gestaonegocios.dominio.anuncio.MeioRecebimento
import ao.kupila.gestaonegocios.dominio.anuncio.ValidacaoAnuncio
import ao.kupila.gestaonegocios.dominio.autorizacoes.AccaoAutorizada
import ao.kupila.gestaonegocios.dominio.autorizacoes.RegrasAutorizacao
import ao.kupila.gestaonegocios.dominio.catalogo.Categoria
import ao.kupila.gestaonegocios.dominio.catalogo.Envolvimento
import ao.kupila.gestaonegocios.dominio.catalogo.ItemVitrine
import ao.kupila.gestaonegocios.dominio.catalogo.PromocaoActiva
import ao.kupila.gestaonegocios.dominio.catalogo.RegrasVitrine
import ao.kupila.gestaonegocios.dominio.catalogo.Selo
import ao.kupila.gestaonegocios.dominio.catalogo.Vitrine
import ao.kupila.gestaonegocios.dominio.duplicados.AvaliacaoDuplicados
import ao.kupila.gestaonegocios.dominio.duplicados.DescritorBem
import ao.kupila.gestaonegocios.dominio.duplicados.IdentidadeBem
import ao.kupila.gestaonegocios.dominio.duplicados.Sinal
import ao.kupila.gestaonegocios.dominio.financeiro.Dinheiro
import ao.kupila.gestaonegocios.dominio.financeiro.Percentagem
import ao.kupila.gestaonegocios.dominio.financeiro.Promocao
import ao.kupila.gestaonegocios.dominio.indicadores.Crescimento
import ao.kupila.gestaonegocios.dominio.indicadores.Variacao
import ao.kupila.gestaonegocios.dominio.notificacoes.Campanha
import ao.kupila.gestaonegocios.dominio.notificacoes.AccaoCampanha
import ao.kupila.gestaonegocios.dominio.notificacoes.EstadoCampanha
import ao.kupila.gestaonegocios.dominio.notificacoes.PrioridadeCampanha
import ao.kupila.gestaonegocios.dominio.notificacoes.PublicoCampanha
import ao.kupila.gestaonegocios.dominio.notificacoes.RegraInteresse
import ao.kupila.gestaonegocios.dominio.notificacoes.RegrasCampanha
import ao.kupila.gestaonegocios.dominio.perfis.EstadoConta
import ao.kupila.gestaonegocios.dominio.perfis.Papel
import ao.kupila.gestaonegocios.dominio.perfis.Perfil
import ao.kupila.gestaonegocios.dominio.perfis.Permissoes
import ao.kupila.gestaonegocios.dominio.perfis.ValidacaoContacto
import ao.kupila.gestaonegocios.dominio.rascunhos.PoliticaRascunhos
import ao.kupila.gestaonegocios.dominio.rascunhos.ResumoRascunho
import ao.kupila.gestaonegocios.dominio.testemunhos.ModoNome
import ao.kupila.gestaonegocios.dominio.testemunhos.RegrasTestemunho
import ao.kupila.gestaonegocios.dominio.transaccoes.Actor
import ao.kupila.gestaonegocios.dominio.transaccoes.ElegibilidadeReembolso
import ao.kupila.gestaonegocios.dominio.transaccoes.EstadoIntencao
import ao.kupila.gestaonegocios.dominio.transaccoes.EstadoReembolso
import ao.kupila.gestaonegocios.dominio.transaccoes.EstadoReserva
import ao.kupila.gestaonegocios.dominio.transaccoes.MotivoReembolso
import ao.kupila.gestaonegocios.dominio.transaccoes.TransicoesReembolso
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class DominioTest {
    private val agora = Instant.parse("2026-10-23T10:00:00Z")
    private fun perfil(uid: String, papel: Papel, criadoPor: String? = null, estado: EstadoConta = EstadoConta.ACTIVA) =
        Perfil(uid, papel, estado, "Nome $uid", "$uid@exemplo.ao", criadoPor = criadoPor)

    @Test fun papeis_canonicos_e_cadastro_publico() {
        assertEquals(Papel.ADMINISTRADOR, Papel.canonico("Gestor"))
        assertEquals(Papel.ADMINISTRADOR, Papel.canonico("admin"))
        assertEquals(Papel.PROPRIETARIO, Papel.canonico("Proprietário"))
        assertNull(Papel.canonico("isManager"))
        assertFalse(Papel.ADMINISTRADOR in Papel.publicos)
    }

    @Test fun permissoes_por_autoria_e_titularidade() {
        val admin = perfil("a1", Papel.ADMINISTRADOR)
        val outroAdmin = perfil("a2", Papel.ADMINISTRADOR)
        val publico = perfil("c1", Papel.CLIENTE)
        val criado = perfil("p1", Papel.PROPRIETARIO, criadoPor = "a1")
        assertFalse(Permissoes.adminPodeEditarDadosPessoais(admin, publico))
        assertTrue(Permissoes.adminPodeMudarEstado(admin, publico))
        assertTrue(Permissoes.adminPodeEditarDadosPessoais(admin, criado))
        assertFalse(Permissoes.adminPodeEditarDadosPessoais(outroAdmin, criado))
        assertFalse(Permissoes.podeAnunciar(publico))
        assertFalse(Permissoes.podeGerirAnuncio(perfil("p2", Papel.PROPRIETARIO), "p1"))
        assertFalse(Permissoes.podeReservar(perfil("c2", Papel.CLIENTE, estado = EstadoConta.SUSPENSA)))
    }

    @Test fun telefone_sem_duplicar_prefixo() {
        assertEquals("923000000", ValidacaoContacto.normalizarTelefone("+244 923 000 000"))
        assertEquals("923000000", ValidacaoContacto.normalizarTelefone("00244923000000"))
        assertTrue(ValidacaoContacto.telefoneValido("923000000"))
        assertFalse(ValidacaoContacto.telefoneValido("823000000"))
    }

    @Test fun vitrines_e_selos() {
        val novo = ItemVitrine("n", Categoria.IMOVEL, agora.minus(Duration.ofDays(1)), Envolvimento(), null)
        val quente = ItemVitrine("q", Categoria.VIATURA, agora.minus(Duration.ofDays(10)), Envolvimento(interesse24h = 4, ultimoInteresse = agora.minusSeconds(60)), null)
        val pago = ItemVitrine("p", Categoria.OUTRO, agora.minus(Duration.ofDays(20)), Envolvimento(), PromocaoActiva(setOf(Promocao.TOP), agora.minusSeconds(10), agora.plus(Duration.ofDays(7))))
        val expirado = pago.copy(id = "x", promocao = PromocaoActiva(setOf(Promocao.TOP), agora.minus(Duration.ofDays(9)), agora.minus(Duration.ofDays(2))))
        assertEquals(listOf("p"), RegrasVitrine.ordenar(listOf(novo, quente, pago, expirado), Vitrine.PATROCINADOS, agora).map { it.id })
        assertEquals("q", RegrasVitrine.ordenar(listOf(novo, quente, pago), Vitrine.EM_ALTA, agora).first().id)
        assertEquals(Selo.NOVO, RegrasVitrine.selo(novo, agora))
        assertEquals(Selo.EM_ALTA, RegrasVitrine.selo(quente, agora))
        assertEquals(Selo.TOP_PATROCINADO, RegrasVitrine.selo(pago, agora))
        assertEquals(Selo.CATEGORIA, RegrasVitrine.selo(expirado, agora))
    }

    @Test fun validacao_por_categoria_com_mensagens_da_origem() {
        assertEquals(listOf("Seleccione a subcategoria do produto.", "Informe uma quantidade válida de compartimentos do imóvel.", "Informe se o imóvel precisa ou não de reparos."),
            ValidacaoAnuncio.detalhes(DetalhesCategoria.Imovel()))
        assertEquals(listOf("Informe marca, modelo e ano válidos da viatura."),
            ValidacaoAnuncio.detalhes(DetalhesCategoria.Viatura("Turismo", "Toyota", "Hilux", 1949)))
        // Registo legado sem estado/notas: a edição não bloqueia por campos que nunca existiram.
        assertTrue(ValidacaoAnuncio.detalhes(DetalhesCategoria.Outro("Eletrónicos"), CamposLegadoAusentes(condicao = true, notas = true)).isEmpty())
        assertEquals(1, ValidacaoAnuncio.detalhes(DetalhesCategoria.Outro("Eletrónicos")).size)
    }

    @Test fun formulario_completo_valido_e_iban() {
        val f = FormularioAnuncio(Categoria.TERRENO, titulo = "Terreno no Lubango", preco = Dinheiro.de(5_000_000),
            detalhes = DetalhesCategoria.Terreno("Residencial", java.math.BigDecimal(600), "Residencial"),
            provincia = "Huíla", municipio = "Lubango", bairro = "Tchioco", pontoReferencia = "Escola", referenciaDocumento = "RP-1",
            numeroFotos = 2, numeroDocumentos = 1, recebimento = MeioRecebimento.Iban("AO06.0040.0000.1234.5678.9012.3"), politicaAceite = true)
        assertEquals(emptyList<String>(), ValidacaoAnuncio.formulario(f))
        assertTrue(ValidacaoAnuncio.formulario(f.copy(recebimento = MeioRecebimento.Iban("AO06 123"))).any { "formato angolano" in it })
        assertTrue(ValidacaoAnuncio.formulario(f.copy(detalhes = DetalhesCategoria.Terreno("Apartamento", java.math.BigDecimal(600), "Residencial"))).any { "não pertence" in it })
    }

    @Test fun duplicados_prova_vs_semelhanca() {
        val d = DescritorBem("VIATURA", "pick up", "luanda", "talatona", "", IdentidadeBem.tokensTitulo("Toyota Hilux 2021 cabine dupla"), mapOf("brand" to "toyota"))
        val a = AvaliacaoDuplicados.construir(d, "LD-12345", emptyList(), emptyList())
        val mesmoDoc = AvaliacaoDuplicados.construir(d.copy(bairro = "x"), "ld 12345", emptyList(), emptyList())
        val semelhante = AvaliacaoDuplicados.construir(d.copy(detalhes = mapOf("brand" to "toyota", "year" to "2020")), "LD-99999", emptyList(), emptyList())
        assertEquals(Sinal.REFERENCIA_DOCUMENTO, AvaliacaoDuplicados.melhor(a, mapOf("b" to mesmoDoc))?.motivo)
        // Duas viaturas iguais no papel mas documentos distintos: semelhança, não duplicado comprovado.
        assertNull(AvaliacaoDuplicados.melhor(a, mapOf("c" to semelhante)))
        assertTrue(AvaliacaoDuplicados.comparar(a, semelhante, "c").sinais.contains(Sinal.TITULO_SEMELHANTE))
        assertEquals(1, IdentidadeBem.hamming("0f", "0e"))
        assertEquals(999, IdentidadeBem.hamming("0f", "0"))
    }

    @Test fun dhash_igual_ao_algoritmo_da_origem() {
        val lum = DoubleArray(72) { i -> (i % 9).toDouble() }   // crescente em cada linha → todos os bits 0
        assertEquals("0000000000000000", IdentidadeBem.dHash(lum))
        val desc = DoubleArray(72) { i -> 9.0 - (i % 9) }      // decrescente → todos 1
        assertEquals("ffffffffffffffff", IdentidadeBem.dHash(desc))
    }

    @Test fun rascunhos_limite_cinco_sem_tocar_protegidos() {
        val base = (1..5).map { ResumoRascunho("r$it", "T$it", agora.minusSeconds(it * 100L), 50) }
        assertEquals("r5", PoliticaRascunhos.aSubstituir(base)?.id)
        val comPagamento = base.map { if (it.id == "r5") it.copy(protegido = true) else it }
        assertEquals("r4", PoliticaRascunhos.aSubstituir(comPagamento)?.id)
        assertNull(PoliticaRascunhos.aSubstituir(base.take(4)))
        assertFalse(PoliticaRascunhos.podeCriarNovo(base.map { it.copy(protegido = true) }))
    }

    @Test fun crescimento_sem_percentagem_falsa_com_base_zero() {
        assertEquals(Variacao.SemBase(5), Crescimento.de(5, 0))
        assertEquals("+5 · sem base anterior", Crescimento.texto(Crescimento.de(5, 0)))
        assertEquals("+50,0%", Crescimento.texto(Crescimento.de(15, 10)))
        assertEquals("-100,0%", Crescimento.texto(Crescimento.de(0, 4)))
    }

    @Test fun reembolso_transicoes_e_declaracao_por_motivo() {
        assertTrue(TransicoesReembolso.permitida(EstadoReembolso.AGUARDA_PROPRIETARIO, EstadoReembolso.CONTESTADO, Actor.PROPRIETARIO))
        assertFalse(TransicoesReembolso.permitida(EstadoReembolso.AGUARDA_PROPRIETARIO, EstadoReembolso.APROVADO_PENDENTE, Actor.ADMINISTRADOR))
        assertFalse("contestado só se recusa, salvo D-07(b)", TransicoesReembolso.permitida(EstadoReembolso.CONTESTADO, EstadoReembolso.APROVADO_PENDENTE, Actor.ADMINISTRADOR))
        assertTrue(TransicoesReembolso.permitida(EstadoReembolso.CONTESTADO, EstadoReembolso.APROVADO_PENDENTE, Actor.ADMINISTRADOR, contestadoAprovavel = true))
        assertFalse("aprovar não é pagar", TransicoesReembolso.permitida(EstadoReembolso.APROVADO_PENDENTE, EstadoReembolso.REEMBOLSADO, Actor.ADMINISTRADOR))
        assertTrue(TransicoesReembolso.permitida(EstadoReembolso.APROVADO_PENDENTE, EstadoReembolso.REEMBOLSADO, Actor.PROVEDOR))
        assertFalse("vistoria presencial" in MotivoReembolso.PROPRIETARIO_AUSENTE.declaracao)
        assertTrue(ElegibilidadeReembolso.podePedir(EstadoReserva.EXPIRADA, true, false))
        assertFalse(ElegibilidadeReembolso.podePedir(EstadoReserva.CONCLUIDA, true, false))
        assertEquals(1, ElegibilidadeReembolso.validarPedido(MotivoReembolso.OUTRO, "curta", true, true, "123456").size)
    }

    @Test fun intencao_so_confirma_com_estado_oficial() {
        assertFalse(EstadoIntencao.REFERENCIA_CORRESPONDE.confirmado)  // OCR/ referência ≠ pagamento
        assertTrue(EstadoIntencao.de("PUBLISHED")!!.confirmado)
        assertEquals(EstadoIntencao.CANCELADO, EstadoIntencao.de("CANCELED"))
    }

    @Test fun autorizacoes_regras_da_origem() {
        assertEquals("A operação não pode ser solicitada no estado actual do produto.",
            RegrasAutorizacao.impedimento(AccaoAutorizada.EXCLUIR, EstadoAnuncio.RESERVADO, true, false, true))
        assertEquals("Este produto não está activo.", RegrasAutorizacao.impedimento(AccaoAutorizada.DESANUNCIAR, EstadoAnuncio.DESANUNCIADO, true, false, true))
        assertNull(RegrasAutorizacao.impedimento(AccaoAutorizada.EXCLUIR, EstadoAnuncio.DESANUNCIADO, true, false, true))
        assertEquals("jo***@kupila.ao", RegrasAutorizacao.mascarar("joao@kupila.ao"))
        assertEquals("+244 *** *** 000", RegrasAutorizacao.mascarar("923000000"))
    }

    @Test fun interesse_clientes_distintos_janela_e_cooldown() {
        val v = listOf("c1" to agora.minusSeconds(3600), "c1" to agora.minusSeconds(60), "c2" to agora.minusSeconds(100), "c3" to agora.minus(Duration.ofHours(30)))
        assertFalse(RegraInteresse.deveAlertar(v, agora, 3, 24, null, 12))
        val v2 = v + ("c3" to agora.minusSeconds(10))
        assertTrue(RegraInteresse.deveAlertar(v2, agora, 3, 24, null, 12))
        assertFalse(RegraInteresse.deveAlertar(v2, agora, 3, 24, agora.minus(Duration.ofHours(2)), 12))
    }

    @Test fun campanhas_publico_e_vigencia() {
        val c = Campanha("m", "Novidade", "Mensagem", PublicoCampanha.PROPRIETARIOS, PrioridadeCampanha.NORMAL, AccaoCampanha.ANUNCIOS, "Ver",
            agora.minusSeconds(10), agora.plus(Duration.ofDays(7)), false, agora)
        assertEquals(EstadoCampanha.ACTIVA, RegrasCampanha.estado(c, agora))
        assertEquals(EstadoCampanha.PAUSADA, RegrasCampanha.estado(c.copy(desactivada = true), agora))
        assertTrue(RegrasCampanha.destinada(c, Papel.PROPRIETARIO))
        assertFalse(RegrasCampanha.destinada(c, Papel.CLIENTE))
        assertFalse(RegrasCampanha.destinada(c.copy(publico = PublicoCampanha.TODOS), Papel.ADMINISTRADOR))
    }

    @Test fun ajustes_intervalos_e_consistencia_aluguer() {
        assertTrue(ValidacaoAjustes.validar(AjustesPlataforma()).isEmpty())
        assertEquals(listOf(12), ValidacaoAjustes.prazosAluguerIncompativeis(AjustesPlataforma().financeiro))   // 15% × 12 > 100%
        val f = AjustesPlataforma().financeiro.copy(comissao = Percentagem.de(40))
        assertEquals(listOf(3, 6, 12), ValidacaoAjustes.prazosAluguerIncompativeis(f))
        assertFalse(ValidacaoAjustes.validar(AjustesPlataforma(inactividadeMinutos = 0)).isEmpty())
    }

    @Test fun testemunho_nome_publico() {
        assertEquals("Ana S.", RegrasTestemunho.nomePublico("Ana Maria Sousa", Papel.CLIENTE, ModoNome.ABREVIADO))
        assertEquals("Proprietário verificado", RegrasTestemunho.nomePublico("Ana", Papel.PROPRIETARIO, ModoNome.ANONIMO))
        assertNotNull(RegrasTestemunho.validar(5, "curto", true).firstOrNull())
    }
}
