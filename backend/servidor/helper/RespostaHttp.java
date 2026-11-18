package helper;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class RespostaHttp {

  public static void enviar(
      OutputStream saida,
      int status,
      String descricao,
      String json) throws IOException {

    enviar(saida, status, descricao, json, null);
  }

  public static void enviar(
      OutputStream saida,
      int status,
      String descricao,
      String json,
      String origem) throws IOException {

    byte[] corpo = json.getBytes(StandardCharsets.UTF_8);

    StringBuilder cabecalho = new StringBuilder();

    cabecalho.append("HTTP/1.1 ")
        .append(status)
        .append(" ")
        .append(descricao)
        .append("\r\n");

    cabecalho.append(
        "Content-Type: application/json; charset=UTF-8\r\n");

    cabecalho.append("Content-Length: ")
        .append(corpo.length)
        .append("\r\n");

    cabecalho.append("Cache-Control: no-store\r\n");

    // Permite somente as origens autorizadas
    if (origem != null &&
        (origem.equals("http://localhost:5500") ||
            origem.equals("http://127.0.0.1:5500"))) {

      cabecalho.append("Access-Control-Allow-Origin: ")
          .append(origem)
          .append("\r\n");

      cabecalho.append("Vary: Origin\r\n");
    }

    cabecalho.append("Connection: close\r\n");
    cabecalho.append("\r\n");

    saida.write(
        cabecalho.toString().getBytes(StandardCharsets.UTF_8));

    saida.write(corpo);
    saida.flush();
  }
}
