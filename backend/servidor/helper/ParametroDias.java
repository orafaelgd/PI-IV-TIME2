package helper;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public class ParametroDias {

  public static int obter(String recurso) {

    int dias = 7;


    String[] partesUrl = recurso.split("\\?", 2);

    if (partesUrl.length == 2) {

      String query = partesUrl[1];

      for (String parametro : query.split("&")) {

        String[] partes = parametro.split("=", 2);

        String nome = URLDecoder.decode(
            partes[0],
            StandardCharsets.UTF_8);

        if (nome.equals("dias")) {

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

    // Validação dos valores permitidos
    if (dias != 1 && dias != 3 && dias != 7) {
      throw new IllegalArgumentException();
    }

    return dias;
  }
}
