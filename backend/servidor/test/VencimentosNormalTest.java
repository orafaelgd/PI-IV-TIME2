// Código feito por Lucas David de Sousa RA: 25895152

package test;

import server.ServidorSocket;

import model.RepositorioVencimentos;
import service.SessaoService;

import com.mongodb.MongoClientSettings;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;

import org.bson.BsonArray;
import org.bson.BsonDateTime;
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

import java.lang.reflect.Method;
import java.math.BigDecimal;

import java.net.ServerSocket;
import java.net.Socket;

import java.nio.charset.StandardCharsets;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class VencimentosNormalTest {

    // Dados fixos do usuário autenticado
    private final ObjectId usuarioId =
        new ObjectId("aaaaaaaaaaaaaaaaaaaaaaaa");

    private final ObjectId itemId =
        new ObjectId("bbbbbbbbbbbbbbbbbbbbbbbb");

    private final ObjectId loteId =
        new ObjectId("cccccccccccccccccccccccc");

    private final String token = "token-valido-de-teste";

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


        // Simular os resultados das consultas
        FindIterable<Document> buscaUsuarios =
            mock(FindIterable.class);

        FindIterable<Document> buscaSessoes =
            mock(FindIterable.class);

        FindIterable<Document> buscaLotes =
            mock(FindIterable.class);

        FindIterable<Document> buscaProdutos =
            mock(FindIterable.class);

        MongoCursor<Document> cursor =
            mock(MongoCursor.class);


        // Preparar usuário autenticado
        Document usuario = new Document("_id", usuarioId)
            .append("nome", "Usuário Teste")
            .append("email", "teste@example.com")
            .append("ativo", true);


        // Preparar sessão válida
        Document sessao = new Document("token", token)
            .append("usuarioId", usuarioId)
            .append(
                "expiraEm",
                new Date(
                    System.currentTimeMillis() + 3600000
                )
            );


        // Simular consulta da sessão
        when(sessoes.find(any(Bson.class)))
            .thenReturn(buscaSessoes);

        when(buscaSessoes.first())
            .thenReturn(sessao);


        // Simular consulta do usuário
        when(usuarios.find(any(Bson.class)))
            .thenReturn(buscaUsuarios);

        when(buscaUsuarios.first())
            .thenReturn(usuario);


        // Preparar data dentro do período
        LocalDate hoje = LocalDate.now(fuso);

        LocalDate vencimento = hoje.plusDays(2);

        Date dataValidade = Date.from(
            vencimento
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
        );


        // Preparar lote do usuário
        Document lote = new Document("_id", loteId)
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
            .append("arquivado", false);


        // Preparar produto do mesmo usuário
        Document produto = new Document("_id", itemId)
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


        // Criar as classes reais do servidor Socket
        RepositorioVencimentos repositorio =
            new RepositorioVencimentos(
                lotes,
                produtos
            );

        SessaoService sessaoService =
            new SessaoService(
                usuarios,
                sessoes
            );

        ServidorSocket servidorSocket =
            new ServidorSocket(
                repositorio,
                sessaoService,
                0
            );


        // Criar conexão Socket temporária
        try (
            ServerSocket servidorTeste = new ServerSocket(
                0,
                1,
                java.net.InetAddress.getByName("127.0.0.1")
            );

            Socket cliente = new Socket(
                "127.0.0.1",
                servidorTeste.getLocalPort()
            );

            Socket conexao = servidorTeste.accept()
        ) {

            cliente.setSoTimeout(5000);


            // Montar requisição HTTP autenticada
            String requisicao =
                "GET /api/lotes/proximos-vencimento?dias=7 HTTP/1.1\r\n" +
                "Host: 127.0.0.1\r\n" +
                "Authorization: Bearer " + token + "\r\n" +
                "Connection: close\r\n" +
                "\r\n";


            // Enviar a requisição pelo Socket
            cliente.getOutputStream().write(
                requisicao.getBytes(
                    StandardCharsets.UTF_8
                )
            );

            cliente.getOutputStream().flush();


            // Executar o atendimento real do ServidorSocket
            Method atenderCliente = ServidorSocket.class
                .getDeclaredMethod(
                    "atenderCliente",
                    Socket.class
                );

            atenderCliente.setAccessible(true);

            atenderCliente.invoke(
                servidorSocket,
                conexao
            );


            // Receber resposta HTTP do servidor
            String respostaHttp = new String(
                cliente.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8
            );


            // Separar cabeçalho e corpo da resposta
            String[] partesResposta =
                respostaHttp.split("\r\n\r\n", 2);

            assertEquals(
                2,
                partesResposta.length
            );


            // Verificar retorno HTTP 200
            assertTrue(
                partesResposta[0].startsWith(
                    "HTTP/1.1 200 OK"
                )
            );


            // Converter o JSON retornado
            Document json = Document.parse(
                partesResposta[1]
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


            // Verificar consulta da sessão pelo token
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


            // Verificar usuário associado à sessão
            ArgumentCaptor<Bson> filtroUsuario =
                ArgumentCaptor.forClass(Bson.class);

            verify(usuarios).find(
                filtroUsuario.capture()
            );

            assertEquals(
                new BsonObjectId(usuarioId),
                obterCondicao(
                    converterFiltro(
                        filtroUsuario.getValue()
                    ),
                    "_id"
                )
            );


            // Verificar filtro dos lotes por usuário
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


            // Verificar intervalo de vencimento
            Date inicioEsperado = Date.from(
                hoje.atStartOfDay(
                    ZoneOffset.UTC
                ).toInstant()
            );

            Date fimEsperado = Date.from(
                hoje.plusDays(7)
                    .atStartOfDay(ZoneOffset.UTC)
                    .toInstant()
            );

            BsonArray condicoes =
                consultaLotes.getArray("$and");

            assertNotNull(condicoes);

            boolean encontrouInicio = false;
            boolean encontrouFim = false;

            for (BsonValue valor : condicoes) {

                BsonDocument condicao =
                    valor.asDocument();

                if (!condicao.containsKey(
                    "dataValidade"
                )) {
                    continue;
                }

                BsonDocument data =
                    condicao.getDocument(
                        "dataValidade"
                    );

                if (data.containsKey("$gte")) {

                    assertEquals(
                        new BsonDateTime(
                            inicioEsperado.getTime()
                        ),
                        data.get("$gte")
                    );

                    encontrouInicio = true;
                }

                if (data.containsKey("$lt")) {

                    assertEquals(
                        new BsonDateTime(
                            fimEsperado.getTime()
                        ),
                        data.get("$lt")
                    );

                    encontrouFim = true;
                }
            }

            assertTrue(encontrouInicio);
            assertTrue(encontrouFim);


            // Verificar produto do mesmo usuário
            ArgumentCaptor<Bson> filtroProduto =
                ArgumentCaptor.forClass(Bson.class);

            verify(produtos).find(
                filtroProduto.capture()
            );

            BsonDocument consultaProduto =
                converterFiltro(
                    filtroProduto.getValue()
                );

            assertEquals(
                new BsonObjectId(usuarioId),
                obterCondicao(
                    consultaProduto,
                    "usuarioId"
                )
            );

            assertEquals(
                new BsonObjectId(itemId),
                obterCondicao(
                    consultaProduto,
                    "_id"
                )
            );
        }
    }


    // Converter filtro do MongoDB para BSON
    private BsonDocument converterFiltro(
        Bson filtro
    ) {

        return filtro.toBsonDocument(
            Document.class,
            MongoClientSettings.getDefaultCodecRegistry()
        );
    }


    // Recuperar uma condição do filtro
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