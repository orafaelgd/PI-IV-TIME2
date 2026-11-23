package test;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import service.DashboardService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DashboardVencimentosTest {

    // O último dia do filtro entra no indicador; o dia seguinte continua no
    // estoque, mas fica fora da lista de próximos vencimentos.
    @Test
    @SuppressWarnings("unchecked")
    void deveIncluirVencimentoEmSeteDiasSemIncluirOitavoDia() {
        LocalDate hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        ObjectId usuarioId = new ObjectId();
        ObjectId itemId = new ObjectId();

        Document produto = new Document("_id", itemId)
            .append("usuarioId", usuarioId)
            .append("nome", "Arroz")
            .append("status", "ativo")
            .append("unidadeMedida", "kg");

        Document ultimoDia = criarLote(itemId, usuarioId, hoje.plusDays(7), "DIA-7");
        Document diaSeguinte = criarLote(itemId, usuarioId, hoje.plusDays(8), "DIA-8");

        MongoCollection<Document> produtos = mock(MongoCollection.class);
        MongoCollection<Document> lotes = mock(MongoCollection.class);
        FindIterable<Document> buscaProdutos = mock(FindIterable.class);
        FindIterable<Document> buscaLotes = mock(FindIterable.class);
        MongoCursor<Document> cursorProdutos = mock(MongoCursor.class);
        MongoCursor<Document> cursorLotes = mock(MongoCursor.class);

        when(produtos.find(any(Bson.class))).thenReturn(buscaProdutos);
        when(buscaProdutos.iterator()).thenReturn(cursorProdutos);
        when(cursorProdutos.hasNext()).thenReturn(true, false);
        when(cursorProdutos.next()).thenReturn(produto);

        when(lotes.find(any(Bson.class))).thenReturn(buscaLotes);
        when(buscaLotes.iterator()).thenReturn(cursorLotes);
        when(cursorLotes.hasNext()).thenReturn(true, true, false);
        when(cursorLotes.next()).thenReturn(ultimoDia, diaSeguinte);

        Document resumo = new DashboardService(produtos, lotes).consultarResumo(usuarioId);

        assertEquals(1L, ((Number) resumo.get("lotesProximosVencimento")).longValue());
        List<Document> vencimentos = resumo.getList("proximosVencimentos", Document.class);
        assertEquals(1, vencimentos.size());
        assertEquals("DIA-7", vencimentos.get(0).getString("codigo"));
        assertEquals(hoje.plusDays(7).toString(), vencimentos.get(0).getString("dataValidade"));

        List<Document> saldos = resumo.getList("saldoPorProduto", Document.class);
        assertEquals(new BigDecimal("20"),
            saldos.get(0).get("quantidadeTotal", Decimal128.class).bigDecimalValue());
    }

    private Document criarLote(
        ObjectId itemId,
        ObjectId usuarioId,
        LocalDate validade,
        String codigo
    ) {
        return new Document("_id", new ObjectId())
            .append("usuarioId", usuarioId)
            .append("itemId", itemId)
            .append("codigo", codigo)
            .append("dataValidade", Date.from(validade.atStartOfDay(ZoneOffset.UTC).toInstant()))
            .append("quantidadeDisponivel", new Decimal128(new BigDecimal("10")));
    }
}
