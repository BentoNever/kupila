package ao.kupila.gestaonegocios.dados.rascunhos

import ao.kupila.gestaonegocios.dominio.anuncio.DetalhesCategoria
import ao.kupila.gestaonegocios.dominio.anuncio.Diferenciais
import ao.kupila.gestaonegocios.dominio.anuncio.FormularioAnuncio
import ao.kupila.gestaonegocios.dominio.anuncio.MeioRecebimento
import ao.kupila.gestaonegocios.dominio.anuncio.PreferenciasAlertaProduto
import ao.kupila.gestaonegocios.dominio.catalogo.Categoria
import ao.kupila.gestaonegocios.dominio.financeiro.CondicoesAluguer
import ao.kupila.gestaonegocios.dominio.financeiro.Dinheiro
import ao.kupila.gestaonegocios.dominio.financeiro.Modalidade
import ao.kupila.gestaonegocios.dominio.financeiro.PrazoMinimo
import ao.kupila.gestaonegocios.dominio.financeiro.Promocao
import ao.kupila.gestaonegocios.dominio.financeiro.UnidadeDuracao
import ao.kupila.gestaonegocios.dominio.financeiro.UnidadePreco
import ao.kupila.gestaonegocios.dominio.perfis.CanalAlerta
import org.json.JSONArray
import org.json.JSONObject

/**
 * Formulário ⇄ `formSnapshot` da origem (`collectSafeFormSnapshot`, HTML 9287: chaves = ids dos campos HTML, `__groups` para rádios e
 * caixas). Assim um rascunho começado na web continua no Android e vice-versa (D-29).
 */
object SnapshotFormulario {

    private fun campo(tipo: String, valor: Any?) = JSONObject().put("type", tipo).put("value", valor?.toString() ?: "")
    private fun caixa(v: Boolean) = JSONObject().put("type", "checkbox").put("checked", v)

    fun paraSnapshot(f: FormularioAnuncio, promocoes: Set<Promocao>): JSONObject {
        val s = JSONObject().put("__version", 2)
        val grupos = JSONObject()
        grupos.put("item_type", JSONObject().put("type", "radio").put("value", f.categoria.codigo))
        Categoria.entries.forEach { c -> s.put("pub-type-${c.codigo.lowercase()}", JSONObject().put("type", "radio").put("name", "item_type").put("value", c.codigo).put("checked", c == f.categoria)) }
        val d = f.detalhes
        s.put("pub-subcategory", campo("select-one", d.subcategoria)).put("pub-intent", campo("select-one", f.modalidade.codigoOrigem)).put("pub-title", campo("text", f.titulo))
        s.put("pub-price", campo("number", f.preco?.valor?.toPlainString()))
        f.aluguer?.let { a ->
            val (qtd, un, _) = a.prazo.normalizado()
            s.put("pub-rental-price-unit", campo("select-one", a.unidade.codigoOrigem)).put("pub-rental-period", campo("select-one", a.prazo.codigoOrigem))
                .put("pub-rental-min-count", campo("number", qtd)).put("pub-rental-min-unit", campo("select-one", un.codigoOrigem))
                .put("pub-rental-other-unit", campo("text", a.nomeUnidadePersonalizada)).put("pub-rental-monthly-units", campo("number", a.unidadesPersonalizadasPorMes?.toPlainString()))
        }
        when (d) {
            is DetalhesCategoria.Imovel -> { s.put("pub-compartments", campo("number", d.compartimentos)).put("pub-repair-status", campo("select-one", d.estadoReparos)).put("pub-property-notes", campo("textarea", d.notas))
                grupos.put("property_quality", JSONObject().put("type", "checkbox").put("values", JSONArray(d.qualidades.toList()))) }
            is DetalhesCategoria.Terreno -> s.put("pub-land-area", campo("number", d.areaM2?.toPlainString())).put("pub-land-purpose", campo("select-one", d.finalidade))
                .put("pub-land-topography", campo("select-one", d.topografia)).put("pub-land-access", campo("select-one", d.acesso)).put("pub-land-notes", campo("textarea", d.notas))
            is DetalhesCategoria.Viatura -> s.put("pub-vehicle-brand", campo("text", d.marca)).put("pub-vehicle-model", campo("text", d.modelo)).put("pub-vehicle-year", campo("number", d.ano))
                .put("pub-vehicle-mileage", campo("number", d.quilometragem)).put("pub-vehicle-fuel", campo("select-one", d.combustivel)).put("pub-vehicle-transmission", campo("select-one", d.transmissao))
                .put("pub-vehicle-notes", campo("textarea", d.notas))
            is DetalhesCategoria.Motorizada -> s.put("pub-moto-brand", campo("text", d.marca)).put("pub-moto-model", campo("text", d.modelo)).put("pub-moto-year", campo("number", d.ano))
                .put("pub-moto-cc", campo("number", d.cilindrada)).put("pub-moto-mileage", campo("number", d.quilometragem)).put("pub-moto-notes", campo("textarea", d.notas))
            is DetalhesCategoria.Outro -> s.put("pub-other-condition", campo("select-one", d.condicao)).put("pub-other-quantity", campo("number", d.quantidade))
                .put("pub-other-brand", campo("text", d.marca)).put("pub-other-model", campo("text", d.modelo)).put("pub-other-notes", campo("textarea", d.notas))
        }
        s.put("pub-location-profile", campo("select-one", f.diferenciais.perfilLocalizacao)).put("pub-condominium-name", campo("text", f.diferenciais.condominio))
            .put("pub-interest-highlights", campo("textarea", f.diferenciais.pontosFortes))
        s.put("pub-province", campo("select-one", f.provincia)).put("pub-location", campo("select-one", f.municipio)).put("pub-neighborhood", campo("text", f.bairro))
            .put("pub-reference-point", campo("text", f.pontoReferencia)).put("pub-doc", campo("text", f.referenciaDocumento))
        when (val r = f.recebimento) {
            is MeioRecebimento.Iban -> s.put("pub-payout-method", campo("select-one", "IBAN")).put("pub-iban", campo("text", r.iban))
            is MeioRecebimento.MulticaixaExpress -> s.put("pub-payout-method", campo("select-one", "EXPRESS")).put("pub-payout-express-phone", campo("tel", r.telefone))
            null -> {}
        }
        s.put("pub-alert-enabled", caixa(f.alertas.activo)).put("pub-alert-reservation", caixa(f.alertas.reserva)).put("pub-alert-interest", caixa(f.alertas.interesse))
            .put("pub-alert-channel", campo("select-one", f.alertas.canal?.codigo ?: "PROFILE"))
        s.put("pub-promo-featured", caixa(Promocao.DESTAQUE in promocoes)).put("pub-promo-top", caixa(Promocao.TOP in promocoes)).put("pub-promo-priority", caixa(Promocao.PRIORIDADE in promocoes))
        s.put("publish-policy-accept", caixa(f.politicaAceite))
        s.put("__groups", grupos)
        return s
    }

    fun deSnapshot(s: JSONObject): Pair<FormularioAnuncio, Set<Promocao>> {
        fun v(id: String) = s.optJSONObject(id)?.optString("value").orEmpty()
        fun ck(id: String, padrao: Boolean = false) = s.optJSONObject(id)?.optBoolean("checked", padrao) ?: padrao
        val grupos = s.optJSONObject("__groups") ?: JSONObject()
        val categoria = Categoria.de(grupos.optJSONObject("item_type")?.optString("value"))
            ?: Categoria.entries.firstOrNull { ck("pub-type-${it.codigo.lowercase()}") } ?: Categoria.IMOVEL
        val modalidade = Modalidade.daOrigem(v("pub-intent")) ?: Modalidade.VENDA
        val preco = v("pub-price").replace(',', '.').toBigDecimalOrNull()?.takeIf { it.signum() > 0 }?.let(Dinheiro::de)
        fun int(id: String) = v(id).toBigDecimalOrNull()?.toInt()
        fun lng(id: String) = v(id).toBigDecimalOrNull()?.toLong()
        val sub = v("pub-subcategory")
        val det = when (categoria) {
            Categoria.IMOVEL -> DetalhesCategoria.Imovel(sub, int("pub-compartments"),
                grupos.optJSONObject("property_quality")?.optJSONArray("values")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet(),
                v("pub-repair-status"), v("pub-property-notes"))
            Categoria.TERRENO -> DetalhesCategoria.Terreno(sub, v("pub-land-area").toBigDecimalOrNull(), v("pub-land-purpose"), v("pub-land-topography"), v("pub-land-access"), v("pub-land-notes"))
            Categoria.VIATURA -> DetalhesCategoria.Viatura(sub, v("pub-vehicle-brand"), v("pub-vehicle-model"), int("pub-vehicle-year"), lng("pub-vehicle-mileage"),
                v("pub-vehicle-fuel"), v("pub-vehicle-transmission"), v("pub-vehicle-notes"))
            Categoria.MOTORIZADA -> DetalhesCategoria.Motorizada(sub, v("pub-moto-brand"), v("pub-moto-model"), int("pub-moto-year"), int("pub-moto-cc"), lng("pub-moto-mileage"), v("pub-moto-notes"))
            Categoria.OUTRO -> DetalhesCategoria.Outro(sub, v("pub-other-condition"), int("pub-other-quantity") ?: 1, v("pub-other-brand"), v("pub-other-model"), v("pub-other-notes"))
        }
        val aluguer = if (modalidade == Modalidade.ARRENDAMENTO && preco != null) {
            val prazo = when (v("pub-rental-period")) { "6_MONTHS" -> PrazoMinimo.SeisMeses; "12_MONTHS" -> PrazoMinimo.Anual
                "CUSTOM" -> PrazoMinimo.Personalizado(int("pub-rental-min-count") ?: 1, UnidadeDuracao.daOrigem(v("pub-rental-min-unit")) ?: UnidadeDuracao.MES); else -> PrazoMinimo.TresMeses }
            CondicoesAluguer(preco, UnidadePreco.daOrigem(v("pub-rental-price-unit")) ?: UnidadePreco.MES, prazo, v("pub-rental-other-unit"), v("pub-rental-monthly-units").toBigDecimalOrNull())
        } else null
        val receb = when (v("pub-payout-method")) { "IBAN" -> MeioRecebimento.Iban(v("pub-iban")); "EXPRESS" -> MeioRecebimento.MulticaixaExpress(v("pub-payout-express-phone")); else -> null }
        val promos = buildSet { if (ck("pub-promo-featured")) add(Promocao.DESTAQUE); if (ck("pub-promo-top")) add(Promocao.TOP); if (ck("pub-promo-priority")) add(Promocao.PRIORIDADE) }
        return FormularioAnuncio(categoria, modalidade, v("pub-title"), preco, aluguer, det,
            Diferenciais(v("pub-location-profile"), v("pub-condominium-name"), v("pub-interest-highlights")),
            v("pub-province"), v("pub-location"), v("pub-neighborhood"), v("pub-reference-point"), v("pub-doc"), 0, 0, receb,
            PreferenciasAlertaProduto(ck("pub-alert-enabled", true), ck("pub-alert-reservation", true), ck("pub-alert-interest", true),
                v("pub-alert-channel").takeIf { it.isNotBlank() && it != "PROFILE" }?.let(CanalAlerta::de)),
            ck("publish-policy-accept")) to promos
    }
}
