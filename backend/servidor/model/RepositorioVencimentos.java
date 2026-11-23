// Author: Rafael Gaudencio Dias
// Ra: 25018953

package model;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;

import static com.mongodb.client.model.Filters.*;

import org.bson.Document;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import java.time.ZoneOffset;

/**
 * Consulta no MongoDB os lotes próximos do vencimento de uma conta.
 * Combina os dados do lote com nome e unidade de medida do produto associado.
 */
public class RepositorioVencimentos {

  private final MongoCollection<Document> lotes;
  private final MongoCollection<Document> produtos;

  // Recebe as coleções já disponíveis na aplicação; não abre uma nova conexão.
  public RepositorioVencimentos(
      MongoCollection<Document> lotes,
      MongoCollection<Document> produtos) {

    this.lotes = lotes;
    this.produtos = produtos;
  }

  /**
   * Busca os lotes de usuarioId durante o período de dias iniciado em hoje.
   * Devolve os registros elegíveis por ordem de validade, ou uma lista vazia.
   */
  public List<LoteProximoVencimento> buscar(
      int dias, LocalDate hoje, ObjectId usuarioId) {

    // A data de validade é tratada em UTC para preservar o dia salvo no banco.
    // "Hoje" (dias = 1) inclui somente a data atual. Os outros filtros
    // incluem o dia completo de hoje + dias; o corte fica na meia-noite seguinte.
    Date inicio = Date.from(
        hoje.atStartOfDay(ZoneOffset.UTC).toInstant());

    Date fim = Date.from(
        hoje.plusDays(dias == 1 ? 1 : dias + 1)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant());

    List<LoteProximoVencimento> resultado = new ArrayList<>();

    // O MongoDB aplica os filtros de conta, período, saldo positivo e lote
    // não arquivado. O cursor percorre os resultados em ordem de validade
    // e é fechado pelo try-with-resources mesmo se ocorrer uma exceção.
    try (MongoCursor<Document> cursor = lotes.find(
        and(
            eq("usuarioId", usuarioId),
            gte("dataValidade", inicio),
            lt("dataValidade", fim),
            gt("quantidadeDisponivel",
                new Decimal128(BigDecimal.ZERO)),
            ne("arquivado", true)))
        .sort(new Document("dataValidade", 1))
        .iterator()) {

      while (cursor.hasNext()) {

        Document lote = cursor.next();

        ObjectId itemId = lote.getObjectId("itemId");

        // Sem itemId não é possível localizar o produto desse lote.
        if (itemId == null) {
          continue;
        }

        // A consulta do produto também exige a mesma conta. Lotes sem produto
        // correspondente ou ligados a um produto inativo ficam fora da resposta.
        Document produto = produtos.find(and(eq("_id", itemId), eq("usuarioId", usuarioId))).first();

        if (produto == null ||
            !"ativo".equalsIgnoreCase(
                produto.getString("status"))) {
          continue;
        }

        ObjectId loteId = lote.getObjectId("_id");

        Date validade = lote.getDate("dataValidade");

        Decimal128 quantidade = lote.get(
            "quantidadeDisponivel",
            Decimal128.class);

        // Esses três campos são necessários para identificar o lote e converter
        // sua validade e quantidade para o modelo retornado.
        if (loteId == null ||
            validade == null ||
            quantidade == null) {
          continue;
        }

        // Converte os tipos do MongoDB para os usados na resposta: Date vira
        // LocalDate em UTC, ObjectId vira texto e Decimal128 vira BigDecimal.
        LocalDate dataValidade = validade
            .toInstant()
            .atZone(ZoneOffset.UTC)
            .toLocalDate();

        LoteProximoVencimento registro = new LoteProximoVencimento(
            loteId.toHexString(),
            lote.getString("codigo"),
            itemId.toHexString(),
            produto.getString("nome"),
            quantidade.bigDecimalValue(),
            produto.getString("unidadeMedida"),
            dataValidade);

        resultado.add(registro);
      }
    }

    return resultado;
  }
}
