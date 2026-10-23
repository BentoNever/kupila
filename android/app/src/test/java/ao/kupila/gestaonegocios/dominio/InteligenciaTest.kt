package ao.kupila.gestaonegocios.dominio

import ao.kupila.gestaonegocios.dominio.anuncio.Inteligencia
import ao.kupila.gestaonegocios.dominio.catalogo.Categoria
import ao.kupila.gestaonegocios.dominio.financeiro.Modalidade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InteligenciaTest {
    @Test fun viaturaComMarcaModeloAno() {
        val s = Inteligencia.deTitulo("Vende-se Toyota Hilux 2019 em Talatona")
        assertEquals(Categoria.VIATURA, s.categoria)
        assertEquals(Modalidade.VENDA, s.modalidade)
        assertEquals("Toyota", s.marca)
        assertEquals(2019, s.ano)
        assertEquals("Pick-up", s.subcategoria)
    }

    @Test fun apartamentoT3ParaArrendar() {
        val s = Inteligencia.deTitulo("Arrenda-se apartamento T3 no Kilamba")
        assertEquals(Categoria.IMOVEL, s.categoria)
        assertEquals(Modalidade.ARRENDAMENTO, s.modalidade)
        assertEquals("Apartamento", s.subcategoria)
        assertEquals(3, s.quartos)
    }

    @Test fun terrenoComArea() {
        val s = Inteligencia.deTitulo("Terreno 20x30 para venda em Viana")
        assertEquals(Categoria.TERRENO, s.categoria)
        assertEquals(600, s.areaM2?.toInt())
    }

    @Test fun motorizada() {
        assertEquals(Categoria.MOTORIZADA, Inteligencia.deTitulo("Mota Haojue para entrega").categoria)
    }

    @Test fun tituloVazioNaoSugereNada() {
        assertNull(Inteligencia.deTitulo("  ").categoria)
    }
}
