package model;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;

import static com.mongodb.client.model.Filters.*;

import org.bson.Document;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class RepositorioVencimentos {

  private final MongoCollection<Document> lotes;
  private final MongoCollection<Document> produtos;

  private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

  public RepositorioVencimentos(
      MongoCollection<Document> lotes,
      MongoCollection<Document> produtos) {

    this.lotes = lotes;
    this.produtos = produtos;
  }

  public List<LoteProximoVencimento> buscar(
      int dias, LocalDate hoje) {

    Date inicio = Date.from(
        hoje.atStartOfDay(FUSO).toInstant());

    Date fim = Date.from(
        hoje.plusDays(dias)
            .atStartOfDay(FUSO)
            .toInstant());

    List<LoteProximoVencimento> resultado = new ArrayList<>();

    try (MongoCursor<Document> cursor = lotes.find(
        and(
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

        if (itemId == null) {
          continue;
        }

        Document produto = produtos.find(
            eq("_id", itemId)).first();

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

        if (loteId == null ||
            validade == null ||
            quantidade == null) {
          continue;
        }

        LocalDate dataValidade = validade
            .toInstant()
            .atZone(FUSO)
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
