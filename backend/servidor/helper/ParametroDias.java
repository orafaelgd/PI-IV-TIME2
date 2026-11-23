// Author: Rafael Gaudencio Dias
// Ra: 25018953

package helper;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Lê o parâmetro "dias" da URL recebida pelo servidor de vencimentos.
 * Esta classe interpreta a query; a validação da rota fica no servidor.
 */
public class ParametroDias {

  /**
   * Retorna 7 quando o parâmetro está ausente e aceita períodos de
   * 1, 3, 7, 15 ou 30 dias. Valores vazios, não numéricos ou fora dessa
   * lista geram IllegalArgumentException ou sua subclasse NumberFormatException.
   */
  public static int obter(String recurso) {

    int dias = 7;

    // Separa o caminho da query, como em "/api/lotes/proximos-vencimento?dias=3".
    // Sem query, o período padrão é mantido até o retorno do método.
    String[] partesUrl = recurso.split("\\?", 2);

    if (partesUrl.length == 2) {

      String query = partesUrl[1];

      // Cada trecho separado por '&' é um parâmetro. A decodificação trata
      // valores codificados na URL, e apenas o parâmetro "dias" é utilizado.
      for (String parametro : query.split("&")) {

        String[] partes = parametro.split("=", 2);

        String nome = URLDecoder.decode(
            partes[0],
            StandardCharsets.UTF_8);

        if (nome.equals("dias")) {

          // "dias" sem '=' é inválido. Com '=', o parseInt rejeita tanto
          // o valor vazio quanto texto que não representa um número inteiro.
          if (partes.length != 2) {
            throw new IllegalArgumentException();
          }

          String valor = URLDecoder.decode(
              partes[1],
              StandardCharsets.UTF_8);

          dias = Integer.parseInt(valor);
        }
      }
    }

    // Ser um inteiro não basta: a consulta oferece somente estes cinco períodos.
    if (dias != 1 && dias != 3 && dias != 7 && dias != 15 && dias != 30) {
      throw new IllegalArgumentException();
    }

    return dias;
  }
}
