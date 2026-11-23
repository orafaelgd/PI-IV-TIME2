package test;

import com.mongodb.MongoClientSettings;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import service.ProximosVencimentoService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Confere os limites da consulta HTTP e a preservação das datas gravadas em UTC.
 * As coleções são simuladas; nenhum banco de dados é acessado.
 */
public class ProximosVencimentoServiceTest {

    private final ObjectId usuarioId =
        new ObjectId("aaaaaaaaaaaaaaaaaaaaaaaa");

    private MongoCollection<Document> lotes;
    private FindIterable<Document> buscaProdutos;
    private MongoCursor<Document> cursor;
    private ProximosVencimentoService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void prepararTeste() {
        lotes = mock(MongoCollection.class);
        MongoCollection<Document> produtos = mock(MongoCollection.class);
        FindIterable<Document> buscaLotes = mock(FindIterable.class);
        buscaProdutos = mock(FindIterable.class);
        cursor = mock(MongoCursor.class);

        when(lotes.find(any(Bson.class))).thenReturn(buscaLotes);
        when(buscaLotes.sort(any(Bson.class))).thenReturn(buscaLotes);
        when(buscaLotes.iterator()).thenReturn(cursor);
        when(produtos.find(any(Bson.class))).thenReturn(buscaProdutos);

        service = new ProximosVencimentoService(lotes, produtos);
    }

    @Test
    void deveConsultarTrintaDiasPreservandoDataDeValidade() {
        ObjectId itemId = new ObjectId("bbbbbbbbbbbbbbbbbbbbbbbb");
        LocalDate validade = LocalDate.of(2026, 12, 23);

        // A data fixa verifica que meia-noite UTC não vira o dia anterior
        // ao ser formatada para a resposta. Os limites são conferidos no filtro.
        Document lote = new Document("_id", new ObjectId())
            .append("itemId", itemId)
            .append("usuarioId", usuarioId)
            .append("codigo", "LOTE-30")
            .append("dataValidade", Date.from(
                validade.atStartOfDay(ZoneOffset.UTC).toInstant()
            ))
            .append("quantidadeDisponivel", new Decimal128(new BigDecimal("5")));

        Document produto = new Document("_id", itemId)
            .append("usuarioId", usuarioId)
            .append("nome", "Leite")
            .append("unidadeMedida", "un")
            .append("status", "ativo");

        when(cursor.hasNext()).thenReturn(true, false);
        when(cursor.next()).thenReturn(lote);
        when(buscaProdutos.first()).thenReturn(produto);

        Document resposta = service.consultarProximosVencimentos(30, usuarioId);

        verificarIntervaloUTC(resposta, 30);
        assertEquals(1, resposta.getInteger("total"));

        List<Document> registros = resposta.getList("lotes", Document.class);
        assertEquals("2026-12-23", registros.get(0).getString("dataValidade"));
    }

    @Test
    void deveRestringirFiltroHojeAoDiaAtual() {
        Document resposta = service.consultarProximosVencimentos(1, usuarioId);

        // Hoje termina antes da meia-noite de amanhã, sem incluir seu vencimento.
        verificarIntervaloUTC(resposta, 0);
        assertEquals(0, resposta.getInteger("total"));
    }

    private void verificarIntervaloUTC(Document resposta, int diasAteUltimoDia) {
        // Usa o dia efetivamente consultado, evitando depender de uma segunda
        // leitura do relógio que poderia ocorrer após a virada da meia-noite.
        LocalDate hoje = LocalDate.parse(resposta.getString("dataInicio"));

        assertEquals(
            hoje.plusDays(diasAteUltimoDia).toString(),
            resposta.getString("dataFim")
        );

        ArgumentCaptor<Bson> captor = ArgumentCaptor.forClass(Bson.class);
        verify(lotes).find(captor.capture());

        BsonDocument filtro = captor.getValue().toBsonDocument(
            Document.class,
            MongoClientSettings.getDefaultCodecRegistry()
        );

        assertEquals(
            hoje.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            obterLimite(filtro, "$gte")
        );
        assertEquals(
            hoje.plusDays(diasAteUltimoDia + 1)
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            obterLimite(filtro, "$lt")
        );
    }

    private long obterLimite(BsonDocument filtro, String operador) {
        for (BsonValue valor : filtro.getArray("$and")) {
            BsonDocument condicao = valor.asDocument();

            if (condicao.containsKey("dataValidade")) {
                BsonDocument data = condicao.getDocument("dataValidade");

                if (data.containsKey(operador)) {
                    return data.getDateTime(operador).getValue();
                }
            }
        }

        throw new AssertionError("Limite de validade não encontrado: " + operador);
    }
}
