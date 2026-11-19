package test;

import helper.ParametroDias;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ParametroDiasTest {

  @Test
  void deveRetornarSetDiasQuandoParametroAusente() {
    int resultado = ParametroDias.obter("/api/lotes/proximos-vencimento");

    assertEquals(7, resultado);
  }

  @Test
  void deveAceitarPeriodoPermitidos() {
    int[] periodos = { 1, 3, 7, 15, 30 };

    for (int dias : periodos) {
      int resultado = ParametroDias.obter("/api/lotes/proximos-vencimento?dias=" + dias);

      assertEquals(dias, resultado);
    }

  }

  @Test
  void deveRejeitarPeriodoNaoPermitido() {
    assertThrows(IllegalArgumentException.class, () -> {
      ParametroDias.obter("/api/lotes/proximos-vencimento?dias=10");
    });
  }

  @Test
  void deveRejeitarPeriodoNaoNumerico() {
    assertThrows(NumberFormatException.class, () -> {
      ParametroDias.obter("/api/lotes/proximos-vencimentos?dias=abc");
    });
  }

  @Test
  void deveRejeitarPeriodosForaDosPrmitidos(){
    int[] periodosInvalidos = {0, -1, 2, 31};

    for(int dias : periodosInvalidos){
      assertThrows(IllegalArgumentException.class, () -> {
        ParametroDias.obter("/api/lotes/proximos-vencimento?dias=" + dias);
      });
    }
  }

  @Test
  void deveRejeitarParametroVazio(){
    assertThrows(NumberFormatException.class, () -> {
      ParametroDias.obter("/api/lotes/proximos-vencimento?dias=");
    });
  }
}
