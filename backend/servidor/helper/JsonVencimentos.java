package helper;

import model.LoteProximoVencimento;

import java.time.LocalDate;
import java.util.List;

public class JsonVencimentos {

  public static String gerar(
      List<LoteProximoVencimento> lotes,
      LocalDate hoje,
      int dias) {

    StringBuilder json = new StringBuilder();

    json.append("{");

    json.append("\"dataInicio\":")
        .append(texto(hoje.toString())).append(",");

    json.append("\"dataFim\":")
        .append(texto(hoje.plusDays(dias - 1).toString()))
        .append(",");

    json.append("\"total\":")
        .append(lotes.size()).append(",");

    json.append("\"lotes\":[");

    for (int i = 0; i < lotes.size(); i++) {

      LoteProximoVencimento lote = lotes.get(i);

      json.append("{");

      json.append("\"loteId\":")
          .append(texto(lote.getLoteId())).append(",");

      json.append("\"codigo\":")
          .append(texto(lote.getCodigo())).append(",");

      json.append("\"itemId\":")
          .append(texto(lote.getItemId())).append(",");

      json.append("\"produtoNome\":")
          .append(texto(lote.getProdutoNome())).append(",");

      json.append("\"quantidadeDisponivel\":")
          .append(texto(
              lote.getQuantidadeDisponivel().toPlainString()))
          .append(",");

      json.append("\"unidadeMedida\":")
          .append(texto(lote.getUnidadeMedida())).append(",");

      json.append("\"dataValidade\":")
          .append(texto(lote.getDataValidade().toString()));

      json.append("}");

      if (i < lotes.size() - 1) {
        json.append(",");
      }
    }

    json.append("]}");

    return json.toString();
  }

  // Converte textos para strings JSON válidas
  private static String texto(String valor) {

    if (valor == null) {
      return "null";
    }

    StringBuilder resultado = new StringBuilder("\"");

    for (int i = 0; i < valor.length(); i++) {

      char caractere = valor.charAt(i);

      switch (caractere) {
        case '"':
          resultado.append("\\\"");
          break;
        case '\\':
          resultado.append("\\\\");
          break;
        case '\n':
          resultado.append("\\n");
          break;
        case '\r':
          resultado.append("\\r");
          break;
        case '\t':
          resultado.append("\\t");
          break;
        default:
          if (caractere < 0x20) {
            resultado.append(
                String.format("\\u%04x", (int) caractere));
          } else {
            resultado.append(caractere);
          }
      }
    }

    resultado.append("\"");

    return resultado.toString();
  }
}
