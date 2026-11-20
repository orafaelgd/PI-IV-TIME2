package test;

import helper.JsonVencimentos;
import model.LoteProximoVencimento;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JsonVencimentosTest {

    @Test
    void deveGerarJsonComListaVazia() {

        LocalDate hoje = LocalDate.of(2026, 11, 19);

        List<LoteProximoVencimento> lotes = List.of();

        String resultado = JsonVencimentos.gerar(lotes, hoje, 7);

        String esperado = "{\"dataInicio\":\"2026-11-19\","
                + "\"dataFim\":\"2026-11-25\","
                + "\"total\":0,"
                + "\"lotes\":[]}";

        assertEquals(esperado, resultado);
    }

    @Test
    void deveGerarJsonComUmLote() {

        LocalDate hoje = LocalDate.of(2026, 11, 19);

        LoteProximoVencimento lote = new LoteProximoVencimento(
                "123",
                "LOTE001",
                "456",
                "Arroz",
                new BigDecimal("2.50"),
                "kg",
                LocalDate.of(2026, 11, 20));

        String resultado = JsonVencimentos.gerar(
                List.of(lote), hoje, 7);

        String esperado = "{\"dataInicio\":\"2026-11-19\","
                + "\"dataFim\":\"2026-11-25\","
                + "\"total\":1,"
                + "\"lotes\":[{"
                + "\"loteId\":\"123\","
                + "\"codigo\":\"LOTE001\","
                + "\"itemId\":\"456\","
                + "\"produtoNome\":\"Arroz\","
                + "\"quantidadeDisponivel\":\"2.50\","
                + "\"unidadeMedida\":\"kg\","
                + "\"dataValidade\":\"2026-11-20\""
                + "}]}";

        assertEquals(esperado, resultado);
    }

    @Test
    void deveEscaparCaracteresEspeciais() {

        LocalDate hoje = LocalDate.of(2026, 11, 19);

        LoteProximoVencimento lote = new LoteProximoVencimento(
                "123",
                "LOTE001",
                "456",
                "Arroz \"Integral\" \\ Especial\nPremium",
                new BigDecimal("2.50"),
                "kg",
                LocalDate.of(2026, 11, 20));

        String resultado = JsonVencimentos.gerar(
                List.of(lote), hoje, 7);

        String esperado = "\"produtoNome\":\"Arroz \\\"Integral\\\" \\\\ Especial\\nPremium\"";

        assertTrue(resultado.contains(esperado));
    }

    @Test
    void deveGerarJsonComMultiplosLotes() {

        LocalDate hoje = LocalDate.of(2026, 11, 19);

        LoteProximoVencimento lote1 = new LoteProximoVencimento(
                "123",
                "LOTE001",
                "456",
                "Arroz",
                new BigDecimal("2.50"),
                "kg",
                LocalDate.of(2026, 11, 20));

        LoteProximoVencimento lote2 = new LoteProximoVencimento(
                "789",
                "LOTE002",
                "101",
                "Feijão",
                new BigDecimal("5.00"),
                "kg",
                LocalDate.of(2026, 11, 22));

        String resultado = JsonVencimentos.gerar(
                List.of(lote1, lote2), hoje, 7);

        assertTrue(resultado.contains("\"total\":2"));

        assertTrue(resultado.contains("\"produtoNome\":\"Arroz\""));

        assertTrue(resultado.contains("\"produtoNome\":\"Feijão\""));

        assertTrue(resultado.contains("},{"));
    }

    @Test
    void deveGerarJsonComAtributoNulo() {

        LocalDate hoje = LocalDate.of(2026, 11, 19);

        LoteProximoVencimento lote = new LoteProximoVencimento(
                "123",
                null,
                "456",
                "Arroz",
                new BigDecimal("2.50"),
                "kg",
                LocalDate.of(2026, 11, 20));

        String resultado = JsonVencimentos.gerar(
                List.of(lote), hoje, 7);

        assertTrue(resultado.contains("\"codigo\":null"));
    }

    @Test
    void deveCalcularDataFimCorretamente() {

        LocalDate hoje = LocalDate.of(2026, 11, 19);

        int[] periodos = { 1, 3, 7, 15, 30 };

        String[] datasEsperadas = {
                "2026-11-19",
                "2026-11-21",
                "2026-11-25",
                "2026-12-03",
                "2026-12-18"
        };

        for (int i = 0; i < periodos.length; i++) {

            String resultado = JsonVencimentos.gerar(
                    List.of(), hoje, periodos[i]);

            assertTrue(resultado.contains(
                    "\"dataFim\":\"" + datasEsperadas[i] + "\""));
        }
    }

}
