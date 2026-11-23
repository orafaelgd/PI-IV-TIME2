// Author: Rafael Gaudencio Dias
// Ra: 25018953

package model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Reúne os dados de um lote e de seu produto para a consulta de vencimentos.
 * O repositório monta esse objeto, que depois é usado na geração do JSON.
 * Seus valores ficam fixos após a criação, sem métodos para alterá-los.
 */
public class LoteProximoVencimento {

  private final String loteId;
  private final String codigo;
  private final String itemId;
  private final String produtoNome;
  private final BigDecimal quantidadeDisponivel;
  private final String unidadeMedida;
  private final LocalDate dataValidade;

  // Apenas guarda os valores recebidos. As regras de seleção dos lotes
  // ficam no repositório; este modelo não valida nomes, quantidades ou datas.
  public LoteProximoVencimento(
      String loteId,
      String codigo,
      String itemId,
      String produtoNome,
      BigDecimal quantidadeDisponivel,
      String unidadeMedida,
      LocalDate dataValidade) {

    this.loteId = loteId;
    this.codigo = codigo;
    this.itemId = itemId;
    this.produtoNome = produtoNome;
    this.quantidadeDisponivel = quantidadeDisponivel;
    this.unidadeMedida = unidadeMedida;
    this.dataValidade = dataValidade;
  }

  // Os getters abaixo permitem consultar cada atributo ao montar a resposta,
  // sem expor operações que modifiquem os dados do lote.
  public String getLoteId() {
    return loteId;
  }

  public String getCodigo() {
    return codigo;
  }

  public String getItemId() {
    return itemId;
  }

  public String getProdutoNome() {
    return produtoNome;
  }

  public BigDecimal getQuantidadeDisponivel() {
    return quantidadeDisponivel;
  }

  public String getUnidadeMedida() {
    return unidadeMedida;
  }

  public LocalDate getDataValidade() {
    return dataValidade;
  }
}
