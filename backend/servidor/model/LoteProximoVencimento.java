package model;

import java.math.BigDecimal;
import java.time.LocalDate;

public class LoteProximoVencimento {

  private final String loteId;
  private final String codigo;
  private final String itemId;
  private final String produtoNome;
  private final BigDecimal quantidadeDisponivel;
  private final String unidadeMedida;
  private final LocalDate dataValidade;

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
