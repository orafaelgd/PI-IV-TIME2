
package test;

import com.mongodb.MongoClientSettings;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;

import model.LoteProximoVencimento;
import model.RepositorioVencimentos;

import org.bson.BsonArray;
import org.bson.BsonDateTime;
import org.bson.BsonDecimal128;
import org.bson.BsonDocument;
import org.bson.BsonObjectId;
import org.bson.BsonValue;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class RepositorioVencimentosTest {

    // =====================================================
    // DADOS FIXOS PARA OS TESTES
    // Utiliza datas e identificadores conhecidos para
    // garantir resultados reproduzíveis.
    // =====================================================

    private final LocalDate hoje =
        LocalDate.of(2026, 11, 19);

    private final ObjectId usuarioId =
        new ObjectId("aaaaaaaaaaaaaaaaaaaaaaaa");

    private final ObjectId itemId =
        new ObjectId("bbbbbbbbbbbbbbbbbbbbbbbb");

    private final ObjectId loteId =
        new ObjectId("cccccccccccccccccccccccc");

    // =====================================================
    // SIMULAÇÃO DAS COLEÇÕES DO MONGODB
    // Os mocks substituem as consultas reais ao banco.
    // Nenhuma conexão com o MongoDB é estabelecida.
    // =====================================================

    private MongoCollection<Document> lotes;
    private MongoCollection<Document> produtos;

    private FindIterable<Document> buscaLotes;
    private FindIterable<Document> buscaProdutos;

    private MongoCursor<Document> cursor;

    private RepositorioVencimentos repositorio;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void prepararTeste() {

        lotes = mock(MongoCollection.class);
        produtos = mock(MongoCollection.class);

        buscaLotes = mock(FindIterable.class);
        buscaProdutos = mock(FindIterable.class);

        cursor = mock(MongoCursor.class);

        // Simula o encadeamento da consulta de lotes.

        when(lotes.find(any(Bson.class)))
            .thenReturn(buscaLotes);

        when(buscaLotes.sort(any(Bson.class)))
            .thenReturn(buscaLotes);

        when(buscaLotes.iterator())
            .thenReturn(cursor);

        // Simula a consulta do produto associado.

        when(produtos.find(any(Bson.class)))
            .thenReturn(buscaProdutos);

        repositorio = new RepositorioVencimentos(
            lotes,
            produtos
        );
    }

    // =====================================================
    // MÉTODOS AUXILIARES
    // Criam documentos de teste e simulam os dados
    // encontrados pelas consultas ao MongoDB.
    // =====================================================

    private Document criarLote() {

        Date validade = Date.from(
            hoje.plusDays(2)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
        );

        return new Document("_id", loteId)
            .append("itemId", itemId)
            .append("usuarioId", usuarioId)
            .append("codigo", "LOTE001")
            .append("dataValidade", validade)
            .append(
                "quantidadeDisponivel",
                new Decimal128(new BigDecimal("5.00"))
            )
            .append("arquivado", false);
    }

    private Document criarProduto(String status) {

        return new Document("_id", itemId)
            .append("usuarioId", usuarioId)
            .append("nome", "Arroz")
            .append("unidadeMedida", "kg")
            .append("status", status);
    }

    private void simularConsulta(
        Document lote,
        Document produto
    ) {

        when(cursor.hasNext())
            .thenReturn(true, false);

        when(cursor.next())
            .thenReturn(lote);

        when(buscaProdutos.first())
            .thenReturn(produto);
    }

    // Converte os filtros MongoDB para um formato
    // que permite verificar suas condições.

    private BsonDocument capturarFiltroLotes() {

        ArgumentCaptor<Bson> captor =
            ArgumentCaptor.forClass(Bson.class);

        verify(lotes).find(captor.capture());

        return captor.getValue().toBsonDocument(
            Document.class,
            MongoClientSettings.getDefaultCodecRegistry()
        );
    }

    // Recupera uma condição do filtro AND utilizado
    // na consulta dos lotes.

    private BsonValue obterCondicao(
        BsonDocument filtro,
        String campo
    ) {

        BsonArray condicoes =
            filtro.getArray("$and");

        for (BsonValue valor : condicoes) {

            BsonDocument condicao =
                valor.asDocument();

            if (condicao.containsKey(campo)) {
                return condicao.get(campo);
            }
        }

        fail("Filtro não encontrado: " + campo);
        return null;
    }

    // =====================================================
    // T01 - PRODUTO ATIVO E LOTE VÁLIDO
    // Verifica se um lote retornado pela consulta,
    // associado a um produto ativo, é incluído
    // no resultado do repositório.
    // =====================================================

    @Test
    void deveRetornarLoteComProdutoAtivo() {

        simularConsulta(
            criarLote(),
            criarProduto("ativo")
        );

        List<LoteProximoVencimento> resultado =
            repositorio.buscar(7, hoje, usuarioId);

        assertEquals(1, resultado.size());
        assertNotNull(resultado.get(0));
    }

    // =====================================================
    // T02 - PRODUTO INATIVO
    // Verifica se o repositório descarta lotes
    // associados a produtos que não estão ativos.
    // =====================================================

    @Test
    void deveIgnorarProdutoInativo() {

        simularConsulta(
            criarLote(),
            criarProduto("inativo")
        );

        List<LoteProximoVencimento> resultado =
            repositorio.buscar(7, hoje, usuarioId);

        assertTrue(resultado.isEmpty());
    }

    // =====================================================
    // T03 - PRODUTO INEXISTENTE
    // Verifica se um lote é ignorado quando
    // a consulta não encontra seu produto.
    // =====================================================

    @Test
    void deveIgnorarProdutoInexistente() {

        simularConsulta(
            criarLote(),
            null
        );

        List<LoteProximoVencimento> resultado =
            repositorio.buscar(7, hoje, usuarioId);

        assertTrue(resultado.isEmpty());
    }

    // =====================================================
    // T04 - LOTE SEM IDENTIFICADOR DE PRODUTO
    // Verifica se registros incompletos são ignorados
    // antes da consulta à coleção de produtos.
    // =====================================================

    @Test
    void deveIgnorarLoteSemItemId() {

        Document lote = criarLote();
        lote.remove("itemId");

        when(cursor.hasNext())
            .thenReturn(true, false);

        when(cursor.next())
            .thenReturn(lote);

        List<LoteProximoVencimento> resultado =
            repositorio.buscar(7, hoje, usuarioId);

        assertTrue(resultado.isEmpty());

        verify(produtos, never())
            .find(any(Bson.class));
    }

    // =====================================================
    // T05 - LOTE ARQUIVADO
    // Verifica se a consulta exige que o campo
    // arquivado seja diferente de true.
    // =====================================================

    @Test
    void deveFiltrarLotesArquivados() {

        repositorio.buscar(7, hoje, usuarioId);

        BsonDocument filtro =
            capturarFiltroLotes();

        BsonValue arquivado =
            obterCondicao(filtro, "arquivado");

        assertEquals(
            true,
            arquivado.asDocument()
                .getBoolean("$ne")
                .getValue()
        );
    }

    // =====================================================
    // T06 - QUANTIDADE DISPONÍVEL
    // Verifica se a consulta exige quantidade maior
    // que zero, excluindo lotes sem saldo.
    // =====================================================

    @Test
    void deveExigirQuantidadeMaiorQueZero() {

        repositorio.buscar(7, hoje, usuarioId);

        BsonDocument filtro =
            capturarFiltroLotes();

        BsonValue quantidade =
            obterCondicao(
                filtro,
                "quantidadeDisponivel"
            );

        assertEquals(
            new BsonDecimal128(
                new Decimal128(BigDecimal.ZERO)
            ),
            quantidade.asDocument().get("$gt")
        );
    }

    // =====================================================
    // T07 - PERÍODO DE VENCIMENTO
    // Verifica se o início é inclusivo e o fim
    // é exclusivo, considerando a data informada.
    // =====================================================

    @Test
    void deveAplicarPeriodoDeVencimento() {

        repositorio.buscar(7, hoje, usuarioId);

        BsonDocument filtro =
            capturarFiltroLotes();

        BsonArray condicoes =
            filtro.getArray("$and");

        Date inicio = Date.from(
            hoje.atStartOfDay(ZoneOffset.UTC)
                .toInstant()
        );

        Date fim = Date.from(
            hoje.plusDays(7)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
        );

        boolean encontrouInicio = false;
        boolean encontrouFim = false;

        for (BsonValue valor : condicoes) {

            BsonDocument condicao =
                valor.asDocument();

            if (!condicao.containsKey("dataValidade")) {
                continue;
            }

            BsonDocument data =
                condicao.getDocument("dataValidade");

            if (data.containsKey("$gte")) {

                assertEquals(
                    new BsonDateTime(inicio.getTime()),
                    data.get("$gte")
                );

                encontrouInicio = true;
            }

            if (data.containsKey("$lt")) {

                assertEquals(
                    new BsonDateTime(fim.getTime()),
                    data.get("$lt")
                );

                encontrouFim = true;
            }
        }

        assertTrue(encontrouInicio);
        assertTrue(encontrouFim);
    }

    // =====================================================
    // T08 - ISOLAMENTO DOS LOTES POR USUÁRIO
    // Verifica se a consulta utiliza o identificador
    // do usuário autenticado como condição de busca.
    // =====================================================

    @Test
    void deveFiltrarLotesPeloUsuario() {

        repositorio.buscar(7, hoje, usuarioId);

        BsonDocument filtro =
            capturarFiltroLotes();

        assertEquals(
            new BsonObjectId(usuarioId),
            obterCondicao(filtro, "usuarioId")
        );
    }

    // =====================================================
    // T09 - ISOLAMENTO DOS PRODUTOS POR USUÁRIO
    // Confirma que a busca do produto associado
    // também utiliza o identificador do usuário,
    // evitando consultar produtos de outra conta.
    // =====================================================

    @Test
    void deveConsultarProdutoDaMesmaConta() {

        simularConsulta(
            criarLote(),
            criarProduto("ativo")
        );

        repositorio.buscar(7, hoje, usuarioId);

        ArgumentCaptor<Bson> captor =
            ArgumentCaptor.forClass(Bson.class);

        verify(produtos).find(captor.capture());

        BsonDocument filtro =
            captor.getValue().toBsonDocument(
                Document.class,
                MongoClientSettings.getDefaultCodecRegistry()
            );

        BsonArray condicoes =
            filtro.getArray("$and");

        assertTrue(
            condicoes.contains(
                new BsonDocument(
                    "usuarioId",
                    new BsonObjectId(usuarioId)
                )
            )
        );

        assertTrue(
            condicoes.contains(
                new BsonDocument(
                    "_id",
                    new BsonObjectId(itemId)
                )
            )
        );
    }
}
