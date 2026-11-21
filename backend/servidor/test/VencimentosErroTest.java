// Código feito por Lucas David de Sousa RA: 25895152

package test;

import server.ServidorSocket;

import model.RepositorioVencimentos;
import service.SessaoService;

import com.mongodb.client.MongoCollection;

import org.bson.Document;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import java.nio.charset.StandardCharsets;

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


        // Criar as classes reais do servidor
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
                InetAddress.getByName("127.0.0.1")
            );

            Socket cliente = new Socket(
                "127.0.0.1",
                servidorTeste.getLocalPort()
            );

            Socket conexao = servidorTeste.accept()
        ) {

            cliente.setSoTimeout(5000);


            // Preparar requisição sem autenticação
            String requisicao =
                "GET /api/lotes/proximos-vencimento?dias=7 HTTP/1.1\r\n" +
                "Host: 127.0.0.1\r\n" +
                "Connection: close\r\n" +
                "\r\n";


            // Enviar requisição pelo Socket
            cliente.getOutputStream().write(
                requisicao.getBytes(
                    StandardCharsets.UTF_8
                )
            );

            cliente.getOutputStream().flush();


            // Executar o atendimento real do servidor
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


            // Receber resposta HTTP
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


            // Verificar retorno HTTP 401
            assertTrue(
                partesResposta[0].startsWith(
                    "HTTP/1.1 401 Unauthorized"
                )
            );


            // Verificar o formato JSON
            assertTrue(
                partesResposta[0].contains(
                    "Content-Type: application/json"
                )
            );


            // Converter o JSON recebido
            Document json = Document.parse(
                partesResposta[1]
            );


            // Verificar mensagem de autenticação
            assertEquals(
                "Sessao invalida ou expirada",
                json.getString("erro")
            );


            // Verificar que nenhum lote foi retornado
            assertFalse(
                json.containsKey("lotes")
            );

            assertFalse(
                json.containsKey("total")
            );


            // Verificar que o banco não foi consultado
            verifyNoInteractions(
                sessoes,
                usuarios,
                lotes,
                produtos
            );
        }
    }
}