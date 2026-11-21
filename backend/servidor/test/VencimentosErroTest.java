// Código feito por Lucas David de Sousa RA: 25895152

package test;

import controller.ProximosVencimentoHandler;

import com.sun.net.httpserver.HttpServer;

import com.mongodb.client.MongoCollection;

import org.bson.Document;

import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class VencimentosErroTest {

    @Test
    @SuppressWarnings("unchecked")
    void deveRejeitarConsultaSemToken()
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


        // Iniciar servidor HTTP temporário
        HttpServer servidor = HttpServer.create(
            new InetSocketAddress("127.0.0.1", 0),
            0
        );


        // Registrar o endpoint de vencimentos
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
            int porta =
                servidor.getAddress().getPort();

            String endereco =
                "http://127.0.0.1:" + porta +
                "/api/lotes/proximos-vencimento?dias=7";


            // Criar requisição sem token
            HttpRequest requisicao = HttpRequest
                .newBuilder(URI.create(endereco))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();


            // Enviar requisição HTTP real
            HttpClient cliente =
                HttpClient.newHttpClient();

            HttpResponse<String> resposta =
                cliente.send(
                    requisicao,
                    HttpResponse.BodyHandlers.ofString(
                        StandardCharsets.UTF_8
                    )
                );


            // Verificar retorno HTTP 401
            assertEquals(
                401,
                resposta.statusCode()
            );


            // Converter a resposta JSON
            Document json = Document.parse(
                resposta.body()
            );


            // Verificar mensagem de erro
            assertEquals(
                "Sessão inválida ou expirada. Faça login novamente.",
                json.getString("erro")
            );


            // Verificar que nenhum lote foi retornado
            assertFalse(
                json.containsKey("lotes")
            );

            assertFalse(
                json.containsKey("total")
            );


            // Verificar que não houve consultas ao MongoDB
            verifyNoInteractions(
                sessoes,
                usuarios,
                lotes,
                produtos
            );

        } finally {

            // Encerrar servidor após o teste
            servidor.stop(0);

        }
    }
}