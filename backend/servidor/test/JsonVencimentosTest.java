// Author: Rafael Gaudencio Dias
// Ra: 25018953

package test;

import helper.JsonVencimentos;
import model.LoteProximoVencimento;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Confere o formato das respostas montadas por JsonVencimentos, sem iniciar o
 * servidor nem acessar o banco. As datas fixas tornam os resultados reproduzíveis
 * independentemente do dia em que os testes forem executados.
 */
public class JsonVencimentosTest {

    // Mesmo sem lotes, a resposta deve informar o período consultado e o total zero.
    @Test
    void deveGerarJsonComListaVazia() {

        LocalDate hoje = LocalDate.of(2026, 11, 19);

        List<LoteProximoVencimento> lotes = List.of();

        String resultado = JsonVencimentos.gerar(lotes, hoje, 7);

        String esperado = "{\"dataInicio\":\"2026-11-19\","
                + "\"dataFim\":\"2026-11-26\","
                + "\"total\":0,"
                + "\"lotes\":[]}";

        // A comparação completa também confere nomes de campos, aspas e separadores.
        assertEquals(esperado, resultado);
    }

    // Usa um lote conhecido para conferir todos os campos e a quantidade como texto decimal.
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
                + "\"dataFim\":\"2026-11-26\","
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

    // Aspas, barra invertida e quebra de linha precisam ser escapadas dentro do JSON.
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

        // Aqui basta localizar o trecho do nome com os escapes esperados.
        assertTrue(resultado.contains(esperado));
    }

    // Com dois lotes, verifica a contagem e a presença dos dois objetos na lista.
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

        // A vírgula entre objetos deve separar os lotes dentro do array.
        assertTrue(resultado.contains("},{"));
    }

    // Um código ausente deve ser escrito como null, sem aspas, na resposta.
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

    // "Hoje" mantém a data atual; os demais filtros incluem o último dia solicitado.
    @Test
    void deveCalcularDataFimCorretamente() {

        LocalDate hoje = LocalDate.of(2026, 11, 19);

        int[] periodos = { 1, 3, 7, 15, 30 };

        String[] datasEsperadas = {
                "2026-11-19",
                "2026-11-22",
                "2026-11-26",
                "2026-12-04",
                "2026-12-19"
        };

        // Cada posição associa um período à sua data final esperada, incluindo virada de mês.
        for (int i = 0; i < periodos.length; i++) {

            String resultado = JsonVencimentos.gerar(
                    List.of(), hoje, periodos[i]);

            assertTrue(resultado.contains(
                    "\"dataFim\":\"" + datasEsperadas[i] + "\""));
        }
    }

    @Test
    void deveIncluirTrigesimoDiaNoPeriodoInformado() {
        LocalDate hoje = LocalDate.of(2026, 11, 23);

        String resultado = JsonVencimentos.gerar(List.of(), hoje, 30);

        assertEquals(
                "{\"dataInicio\":\"2026-11-23\",\"dataFim\":\"2026-12-23\",\"total\":0,\"lotes\":[]}",
                resultado);
    }

}
