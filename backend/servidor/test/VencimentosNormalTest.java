// Código feito por Lucas David de Sousa RA: 25895152

package test;

import controller.ProximosVencimentoHandler;

import com.sun.net.httpserver.HttpServer;

import com.mongodb.MongoClientSettings;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonObjectId;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class VencimentosNormalTest {

    // Dados utilizados no cenário normal
    private final ObjectId usuarioId =
        new ObjectId("aaaaaaaaaaaaaaaaaaaaaaaa");

    private final ObjectId itemId =
        new ObjectId("bbbbbbbbbbbbbbbbbbbbbbbb");

    private final ObjectId loteId =
        new ObjectId("cccccccccccccccccccccccc");

    private final String token =
        "token-valido-de-teste";

    private final ZoneId fuso =
        ZoneId.of("America/Sao_Paulo");


    @Test
    @SuppressWarnings("unchecked")
    void deveRetornarVencimentosDoUsuarioAutenticado()
            throws Exception {

        // Simular as coleções do MongoDB
        MongoCollection<Document> usuarios =
            mock(MongoCollection.class);

        MongoCollection<Document> sessoes =
            mock(MongoCollection.class);

        MongoCollection<Document> lotes =
            mock(MongoCollection.class);

        MongoCollection<Document> produtos =
            mock(MongoCollection.class);


        // Simular as consultas do MongoDB
        FindIterable<Document> buscaSessoes =
            mock(FindIterable.class);

        FindIterable<Document> buscaUsuarios =
            mock(FindIterable.class);

        FindIterable<Document> buscaLotes =
            mock(FindIterable.class);

        FindIterable<Document> buscaProdutos =
            mock(FindIterable.class);

        MongoCursor<Document> cursor =
            mock(MongoCursor.class);


        // Preparar usuário autenticado
        Document usuario = new Document(
            "_id", usuarioId
        )
            .append("nome", "Usuário Teste")
            .append("email", "teste@example.com")
            .append("ativo", true);


        // Preparar sessão válida
        Document sessao = new Document(
            "token", token
        )
            .append("usuarioId", usuarioId)
            .append(
                "expiraEm",
                new Date(
                    System.currentTimeMillis() + 3600000
                )
            );


        // Simular autenticação da sessão
        when(sessoes.find(any(Bson.class)))
            .thenReturn(buscaSessoes);

        when(buscaSessoes.first())
            .thenReturn(sessao);


        // Simular consulta do usuário
        when(usuarios.find(any(Bson.class)))
            .thenReturn(buscaUsuarios);

        when(buscaUsuarios.first())
            .thenReturn(usuario);


        // Preparar lote com vencimento em dois dias
        LocalDate hoje = LocalDate.now(fuso);

        LocalDate vencimento = hoje.plusDays(2);

        Date dataValidade = Date.from(
            vencimento
                .atStartOfDay(fuso)
                .toInstant()
        );

        Document lote = new Document(
            "_id", loteId
        )
            .append("usuarioId", usuarioId)
            .append("itemId", itemId)
            .append("codigo", "LOTE001")
            .append("dataValidade", dataValidade)
            .append(
                "quantidadeDisponivel",
                new Decimal128(
                    new BigDecimal("5.00")
                )
            )
            .append("arquivado", false)
            .append("inativo", false);


        // Preparar produto do mesmo usuário
        Document produto = new Document(
            "_id", itemId
        )
            .append("usuarioId", usuarioId)
            .append("nome", "Arroz")
            .append("unidadeMedida", "kg")
            .append("status", "ativo");


        // Simular consulta dos lotes
        when(lotes.find(any(Bson.class)))
            .thenReturn(buscaLotes);

        when(buscaLotes.sort(any(Bson.class)))
            .thenReturn(buscaLotes);

        when(buscaLotes.iterator())
            .thenReturn(cursor);

        when(cursor.hasNext())
            .thenReturn(true, false);

        when(cursor.next())
            .thenReturn(lote);


        // Simular consulta do produto associado
        when(produtos.find(any(Bson.class)))
            .thenReturn(buscaProdutos);

        when(buscaProdutos.first())
            .thenReturn(produto);


        // Iniciar servidor HTTP temporário
        HttpServer servidor = HttpServer.create(
            new InetSocketAddress("127.0.0.1", 0),
            0
        );

        servidor.createContext(
            "/api/lotes/proximos-vencimento",
            new ProximosVencimentoHandler(
                lotes,
                produtos,
                usuarios,
                sessoes
            )
        );

        servidor.start();

        try {

            // Preparar a URL do endpoint
            int porta = servidor.getAddress().getPort();

            String endereco =
                "http://127.0.0.1:" + porta +
                "/api/lotes/proximos-vencimento?dias=7";


            // Criar requisição autenticada
            HttpRequest requisicao = HttpRequest
                .newBuilder(URI.create(endereco))
                .timeout(Duration.ofSeconds(10))
                .header(
                    "Authorization",
                    "Bearer " + token
                )
                .GET()
                .build();


            // Enviar requisição HTTP real
            HttpClient cliente = HttpClient
                .newHttpClient();

            HttpResponse<String> resposta =
                cliente.send(
                    requisicao,
                    HttpResponse.BodyHandlers.ofString(
                        StandardCharsets.UTF_8
                    )
                );


            // Verificar retorno HTTP 200
            assertEquals(
                200,
                resposta.statusCode()
            );


            // Converter resposta JSON
            Document json = Document.parse(
                resposta.body()
            );


            // Verificar período de sete dias
            assertEquals(
                hoje.toString(),
                json.getString("dataInicio")
            );

            assertEquals(
                hoje.plusDays(6).toString(),
                json.getString("dataFim")
            );


            // Verificar quantidade de lotes
            assertEquals(
                1,
                json.getInteger("total")
            );

            List<Document> lotesRetornados =
                json.getList("lotes", Document.class);

            assertNotNull(lotesRetornados);

            assertEquals(
                1,
                lotesRetornados.size()
            );


            // Verificar dados do lote retornado
            Document loteRetornado =
                lotesRetornados.get(0);

            assertEquals(
                loteId.toHexString(),
                loteRetornado.getString("loteId")
            );

            assertEquals(
                "LOTE001",
                loteRetornado.getString("codigo")
            );

            assertEquals(
                itemId.toHexString(),
                loteRetornado.getString("itemId")
            );

            assertEquals(
                "Arroz",
                loteRetornado.getString("produtoNome")
            );

            assertEquals(
                "5.00",
                loteRetornado.getString(
                    "quantidadeDisponivel"
                )
            );

            assertEquals(
                "kg",
                loteRetornado.getString("unidadeMedida")
            );

            assertEquals(
                vencimento.toString(),
                loteRetornado.getString("dataValidade")
            );


            // Verificar autenticação pelo token
            ArgumentCaptor<Bson> filtroSessao =
                ArgumentCaptor.forClass(Bson.class);

            verify(sessoes).find(
                filtroSessao.capture()
            );

            BsonDocument consultaSessao =
                converterFiltro(
                    filtroSessao.getValue()
                );

            assertEquals(
                new BsonString(token),
                obterCondicao(
                    consultaSessao,
                    "token"
                )
            );

            assertTrue(
                obterCondicao(
                    consultaSessao,
                    "expiraEm"
                ).asDocument().containsKey("$gt")
            );


            // Verificar usuário identificado pela sessão
            ArgumentCaptor<Bson> filtroUsuario =
                ArgumentCaptor.forClass(Bson.class);

            verify(usuarios).find(
                filtroUsuario.capture()
            );

            assertEquals(
                new BsonObjectId(usuarioId),
                converterFiltro(
                    filtroUsuario.getValue()
                ).get("_id")
            );


            // Verificar isolamento dos lotes por usuário
            ArgumentCaptor<Bson> filtroLotes =
                ArgumentCaptor.forClass(Bson.class);

            verify(lotes).find(
                filtroLotes.capture()
            );

            BsonDocument consultaLotes =
                converterFiltro(
                    filtroLotes.getValue()
                );

            assertEquals(
                new BsonObjectId(usuarioId),
                obterCondicao(
                    consultaLotes,
                    "usuarioId"
                )
            );


            // Verificar filtro de período
            BsonArray condicoes =
                consultaLotes.getArray("$and");

            assertNotNull(condicoes);

            boolean encontrouInicio = false;
            boolean encontrouFim = false;

            for (BsonValue condicao : condicoes) {

                BsonDocument documento =
                    condicao.asDocument();

                if (!documento.containsKey(
                    "dataValidade"
                )) {
                    continue;
                }

                BsonDocument data =
                    documento.getDocument(
                        "dataValidade"
                    );

                if (data.containsKey("$gte")) {
                    encontrouInicio = true;
                }

                if (data.containsKey("$lt")) {
                    encontrouFim = true;
                }
            }

            assertTrue(encontrouInicio);
            assertTrue(encontrouFim);


            // Verificar isolamento dos produtos por usuário
            ArgumentCaptor<Bson> filtroProdutos =
                ArgumentCaptor.forClass(Bson.class);

            verify(produtos).find(
                filtroProdutos.capture()
            );

            BsonDocument consultaProdutos =
                converterFiltro(
                    filtroProdutos.getValue()
                );

            assertEquals(
                new BsonObjectId(usuarioId),
                obterCondicao(
                    consultaProdutos,
                    "usuarioId"
                )
            );

            assertEquals(
                new BsonObjectId(itemId),
                obterCondicao(
                    consultaProdutos,
                    "_id"
                )
            );

        } finally {

            // Encerrar servidor após o teste
            servidor.stop(0);

        }
    }


    // Converter filtro MongoDB para BSON
    private BsonDocument converterFiltro(
        Bson filtro
    ) {

        return filtro.toBsonDocument(
            Document.class,
            MongoClientSettings.getDefaultCodecRegistry()
        );
    }


    // Recuperar condição de um filtro AND
    private BsonValue obterCondicao(
        BsonDocument filtro,
        String campo
    ) {

        if (filtro.containsKey(campo)) {
            return filtro.get(campo);
        }

        BsonArray condicoes =
            filtro.getArray("$and");

        if (condicoes != null) {

            for (BsonValue valor : condicoes) {

                BsonDocument condicao =
                    valor.asDocument();

                if (condicao.containsKey(campo)) {
                    return condicao.get(campo);
                }
            }
        }

        fail("Filtro não encontrado: " + campo);

        return null;
    }
}