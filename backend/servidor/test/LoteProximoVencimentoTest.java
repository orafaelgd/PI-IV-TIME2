// Código feito por Livia Carvalho Lucizano RA: 25017514
package test;

import model.LoteProximoVencimento;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

public class LoteProximoVencimentoTest {

    // TESTE 1: Verifica um nome comum de produto
    @Test
    void testarNomeProduto() {
        LoteProximoVencimento lote = criarLote(
            "Arroz", new BigDecimal("10.0"),
            "L001", LocalDate.of(2026, 12, 10));

        assertEquals("Arroz", lote.getProdutoNome());
    }

    // TESTE 2: Nome com caracteres especiais
    @Test
    void testarNomeComCaracteresEspeciais() {
        LoteProximoVencimento lote = criarLote(
            "Café & Açúcar", new BigDecimal("5.0"),
            "L002", LocalDate.of(2026, 12, 15));

        assertEquals("Café & Açúcar",
                     lote.getProdutoNome());
    }

    // TESTE 3: Verifica nome vazio
    @Test
    void testarNomeVazio() {
        LoteProximoVencimento lote = criarLote(
            "", new BigDecimal("3.0"),
            "L003", LocalDate.of(2026, 12, 20));

        assertEquals("", lote.getProdutoNome());
    }

    // TESTE 4: Quantidade positiva
    @Test
    void testarQuantidadePositiva() {
        LoteProximoVencimento lote = criarLote(
            "Leite", new BigDecimal("20"),
            "L004", LocalDate.of(2026, 12, 22));

        assertEquals(new BigDecimal("20"),
                     lote.getQuantidadeDisponivel());
    }

    // TESTE 5: Quantidade igual a zero
    @Test
    void testarQuantidadeZero() {
        LoteProximoVencimento lote = criarLote(
            "Pão", BigDecimal.ZERO,
            "L005", LocalDate.of(2026, 12, 25));

        assertEquals(BigDecimal.ZERO,
                     lote.getQuantidadeDisponivel());
    }

    // TESTE 6: Quantidade decimal
    @Test
    void testarQuantidadeDecimal() {
        LoteProximoVencimento lote = criarLote(
            "Queijo", new BigDecimal("2.5"),
            "L006", LocalDate.of(2026, 12, 30));

        assertEquals(new BigDecimal("2.5"),
                     lote.getQuantidadeDisponivel());
    }

    // TESTE 7: Código alfanumérico
    @Test
    void testarCodigoAlfanumerico() {
        LoteProximoVencimento lote = criarLote(
            "Manteiga", new BigDecimal("8"),
            "ABC123", LocalDate.of(2027, 1, 10));

        assertEquals("ABC123", lote.getCodigo());
    }

    // TESTE 8: Código com caracteres especiais
    @Test
    void testarCodigoEspecial() {
        LoteProximoVencimento lote = criarLote(
            "Iogurte", new BigDecimal("12"),
            "LOTE-@123", LocalDate.of(2027, 1, 15));

        assertEquals("LOTE-@123", lote.getCodigo());
    }

    // TESTE 9: Data futura
    @Test
    void testarDataFutura() {
        LocalDate data = LocalDate.now().plusDays(30);

        LoteProximoVencimento lote = criarLote(
            "Biscoito", new BigDecimal("10"),
            "L009", data);

        assertEquals(data, lote.getDataValidade());
    }

    // TESTE 10: Data passada
    @Test
    void testarDataPassada() {
        LocalDate data = LocalDate.now().minusDays(10);

        LoteProximoVencimento lote = criarLote(
            "Chocolate", new BigDecimal("5"),
            "L010", data);

        assertEquals(data, lote.getDataValidade());
    }

    // TESTE 11: Data atual
    @Test
    void testarDataAtual() {
        LocalDate data = LocalDate.now();

        LoteProximoVencimento lote = criarLote(
            "Suco", new BigDecimal("7"),
            "L011", data);

        assertEquals(data, lote.getDataValidade());
    }

    // TESTE 12: Identificadores e unidade de medida
    @Test
    void testarDemaisAtributos() {
        LoteProximoVencimento lote =
            new LoteProximoVencimento(
                "ID-LOTE-01",
                "COD001",
                "ID-ITEM-01",
                "Farinha",
                new BigDecimal("3.5"),
                "kg",
                LocalDate.of(2026, 12, 1));

        assertAll(
            () -> assertEquals("ID-LOTE-01",
                               lote.getLoteId()),
            () -> assertEquals("ID-ITEM-01",
                               lote.getItemId()),
            () -> assertEquals("kg",
                               lote.getUnidadeMedida())
        );
    }

    // MÉTODO AUXILIAR
    // Cria um lote com os atributos informados
    // para evitar repetição de código nos testes.
    private LoteProximoVencimento criarLote(
            String nome,
            BigDecimal quantidade,
            String codigo,
            LocalDate validade) {

        return new LoteProximoVencimento(
            "ID001",
            codigo,
            "ITEM001",
            nome,
            quantidade,
            "un",
            validade
        );
    }
}
