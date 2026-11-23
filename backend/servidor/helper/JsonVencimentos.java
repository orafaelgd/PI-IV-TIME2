// Author: Rafael Gaudencio Dias
// Ra: 25018953

package helper;

import model.LoteProximoVencimento;

import java.time.LocalDate;
import java.util.List;

/**
 * Monta o JSON enviado pelo servidor de vencimentos ao frontend.
 * Recebe os lotes já consultados pelo repositório; não faz buscas nem aplica filtros.
 */
public class JsonVencimentos {

  // "Hoje" (dias = 1) termina hoje; os demais filtros incluem hoje + dias.
  public static String gerar(
      List<LoteProximoVencimento> lotes,
      LocalDate hoje,
      int dias) {

    StringBuilder json = new StringBuilder();

    json.append("{");

    // O resumo informa o intervalo consultado e o total, mesmo quando a lista está vazia.
    json.append("\"dataInicio\":")
        .append(texto(hoje.toString())).append(",");

    json.append("\"dataFim\":")
        .append(texto(hoje.plusDays(dias == 1 ? 0 : dias).toString()))
        .append(",");

    json.append("\"total\":")
        .append(lotes.size()).append(",");

    json.append("\"lotes\":[");

    // Cada lote vira um objeto do array. A montagem é manual, sem biblioteca de JSON.
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

      // A quantidade é enviada como texto decimal, sem converter o BigDecimal para double.
      // toPlainString evita notação científica e mantém a representação decimal do valor.
      json.append("\"quantidadeDisponivel\":")
          .append(texto(
              lote.getQuantidadeDisponivel().toPlainString()))
          .append(",");

      json.append("\"unidadeMedida\":")
          .append(texto(lote.getUnidadeMedida())).append(",");

      json.append("\"dataValidade\":")
          .append(texto(lote.getDataValidade().toString()));

      json.append("}");

      // A vírgula separa os objetos, mas não pode aparecer depois do último lote.
      if (i < lotes.size() - 1) {
        json.append(",");
      }
    }

    json.append("]}");

    return json.toString();
  }

  // Coloca aspas e escapa caracteres que quebrariam a sintaxe do JSON.
  // Um valor Java nulo vira o literal null, sem aspas, em vez do texto "null".
  private static String texto(String valor) {

    if (valor == null) {
      return "null";
    }

    StringBuilder resultado = new StringBuilder("\"");

    for (int i = 0; i < valor.length(); i++) {

      char caractere = valor.charAt(i);

      // Aspas, barras invertidas e quebras de linha precisam de uma sequência de escape.
      // Outros caracteres de controle são escritos no formato Unicode do JSON.
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
