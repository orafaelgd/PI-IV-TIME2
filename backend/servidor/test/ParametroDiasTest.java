// Author: Rafael Gaudencio Dias
// Ra: 25018953

package test;

import helper.ParametroDias;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Testa a leitura do parâmetro dias da URL e os períodos aceitos pela consulta.
 * Cada @Test exercita o helper diretamente, sem requisição HTTP ou banco de dados.
 */
public class ParametroDiasTest {

  // Sem o parâmetro dias, o helper deve usar o período padrão de sete dias.
  @Test
  void deveRetornarSetDiasQuandoParametroAusente() {
    int resultado = ParametroDias.obter("/api/lotes/proximos-vencimento");

    assertEquals(7, resultado);
  }

  // Percorre todos os períodos permitidos e confere se cada valor é preservado.
  @Test
  void deveAceitarPeriodoPermitidos() {
    int[] periodos = { 1, 3, 7, 15, 30 };

    for (int dias : periodos) {
      int resultado = ParametroDias.obter("/api/lotes/proximos-vencimento?dias=" + dias);

      assertEquals(dias, resultado);
    }

  }

  // Dez é um número válido, mas está fora das opções oferecidas pelo sistema.
  @Test
  void deveRejeitarPeriodoNaoPermitido() {
    assertThrows(IllegalArgumentException.class, () -> {
      ParametroDias.obter("/api/lotes/proximos-vencimento?dias=10");
    });
  }

  // A conversão de abc para inteiro deve falhar. Este teste só interpreta a query da URL.
  @Test
  void deveRejeitarPeriodoNaoNumerico() {
    assertThrows(NumberFormatException.class, () -> {
      ParametroDias.obter("/api/lotes/proximos-vencimentos?dias=abc");
    });
  }

  // Cobre zero, negativo e outros números que não pertencem aos períodos permitidos.
  @Test
  void deveRejeitarPeriodosForaDosPrmitidos(){
    int[] periodosInvalidos = {0, -1, 2, 31};

    for(int dias : periodosInvalidos){
      assertThrows(IllegalArgumentException.class, () -> {
        ParametroDias.obter("/api/lotes/proximos-vencimento?dias=" + dias);
      });
    }
  }

  // assertThrows confirma que informar dias=, sem valor, gera a exceção esperada.
  @Test
  void deveRejeitarParametroVazio(){
    assertThrows(NumberFormatException.class, () -> {
      ParametroDias.obter("/api/lotes/proximos-vencimento?dias=");
    });
  }
}
