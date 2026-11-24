package test;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;
import controller.RecuperacaoSenhaHandler;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import service.RecuperacaoSenhaService;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Confere as respostas HTTP com serviço simulado. Nenhuma conexão de rede,
 * credencial local, conta real ou envio de e-mail participa destes testes.
 */
public class RecuperacaoSenhaHandlerTest {
    private RecuperacaoSenhaService service;
    private RecuperacaoSenhaHandler handler;

    @BeforeEach
    void prepararTeste() {
        service = mock(RecuperacaoSenhaService.class);
        handler = new RecuperacaoSenhaHandler(service);
    }

    @Test
    void deveConfirmarSolicitacaoSemRevelarSeContaExiste() throws Exception {
        when(service.configurado()).thenReturn(true);
        Troca troca = post("solicitar", new Document("email", "ana@example.com"));
        handler.handle(troca);

        assertEquals(200, troca.status);
        assertTrue(troca.mensagem().startsWith("Se o e-mail estiver cadastrado"));
        verify(service).solicitar("ana@example.com");
        assertEquals("no-store", troca.saida.getFirst("Cache-Control"));
    }

    @Test
    void deveInformarIndisponibilidadeQuandoEmailNaoEstaConfigurado() throws Exception {
        when(service.configurado()).thenReturn(false);
        Troca troca = post("solicitar", new Document("email", "ana@example.com"));
        handler.handle(troca);

        assertEquals(503, troca.status);
        assertEquals("Serviço de e-mail indisponível.", troca.mensagem());
        verify(service, never()).solicitar(anyString());
    }

    @Test
    void deveConfirmarSenhaRedefinidaSemExigirConfiguracaoSmtp() throws Exception {
        when(service.redefinir("token-de-teste", "NovaSenha123!")).thenReturn(true);
        Troca troca = post("redefinir", new Document("token", "token-de-teste").append("novaSenha", "NovaSenha123!"));
        handler.handle(troca);

        assertEquals(200, troca.status);
        assertEquals("Senha redefinida com sucesso.", troca.mensagem());
        verify(service, never()).configurado();
    }

    @Test
    void deveRejeitarLinkInvalidoOuExpirado() throws Exception {
        when(service.redefinir(anyString(), anyString())).thenReturn(false);
        Troca troca = post("redefinir", new Document("token", "invalido").append("novaSenha", "NovaSenha123!"));
        handler.handle(troca);

        assertEquals(400, troca.status);
        assertEquals("Link inválido ou expirado. Solicite outro.", troca.mensagem());
    }

    @Test
    void deveRetornarErroDeValidacaoDaSenha() throws Exception {
        when(service.redefinir(anyString(), anyString()))
            .thenThrow(new IllegalArgumentException("A senha deve ter de 8 a 50 caracteres e no máximo 72 bytes."));
        Troca troca = post("redefinir", new Document("token", "token-de-teste").append("novaSenha", "curta"));
        handler.handle(troca);

        assertEquals(400, troca.status);
        assertTrue(troca.mensagem().contains("8 a 50 caracteres"));
    }

    @Test
    void deveRetornarIndisponibilidadeSemExporFalhaInterna() throws Exception {
        when(service.redefinir(anyString(), anyString()))
            .thenThrow(new IllegalStateException("Detalhe interno da escrita no banco"));
        Troca troca = post("redefinir", new Document("token", "token-de-teste").append("novaSenha", "NovaSenha123!"));
        handler.handle(troca);

        assertEquals(503, troca.status);
        assertEquals("Serviço temporariamente indisponível.", troca.mensagem());
        assertFalse(troca.textoResposta().contains("Detalhe interno"));
    }

    @Test
    void deveRejeitarOrigemNaoPermitida() throws Exception {
        Troca troca = post("solicitar", new Document("email", "ana@example.com"));
        troca.entrada.set("Origin", "http://outra-origem.invalid");
        handler.handle(troca);

        assertEquals(403, troca.status);
        verifyNoInteractions(service);
    }

    @Test
    void deveResponderPreflightDaOrigemLocal() throws Exception {
        Troca troca = new Troca("OPTIONS", "/api/senha/solicitar", "");
        troca.entrada.set("Origin", "http://localhost:5500");
        handler.handle(troca);

        assertEquals(204, troca.status);
        assertEquals("http://localhost:5500", troca.saida.getFirst("Access-Control-Allow-Origin"));
        verifyNoInteractions(service);
    }

    @Test
    void deveRejeitarJsonInvalidoOuRequisicaoMuitoGrande() throws Exception {
        Troca invalida = new Troca("POST", "/api/senha/redefinir", "{");
        handler.handle(invalida);
        assertEquals(400, invalida.status);

        Troca excessiva = new Troca("POST", "/api/senha/redefinir", "x".repeat(4097));
        handler.handle(excessiva);
        assertEquals(413, excessiva.status);
        verifyNoInteractions(service);
    }

    @Test
    void deveRejeitarMetodoOuRotaNaoDisponivel() throws Exception {
        Troca get = new Troca("GET", "/api/senha/redefinir", "");
        handler.handle(get);
        assertEquals(405, get.status);

        Troca desconhecida = new Troca("POST", "/api/senha/desconhecida", "{}");
        handler.handle(desconhecida);
        assertEquals(404, desconhecida.status);
        verifyNoInteractions(service);
    }

    private static Troca post(String operacao, Document corpo) {
        return new Troca("POST", "/api/senha/" + operacao, corpo.toJson());
    }

    private static class Troca extends HttpExchange {
        private final Headers entrada = new Headers();
        private final Headers saida = new Headers();
        private final String metodo;
        private final URI uri;
        private final ByteArrayInputStream corpo;
        private final ByteArrayOutputStream resposta = new ByteArrayOutputStream();
        private int status;

        Troca(String metodo, String uri, String corpo) {
            this.metodo = metodo;
            this.uri = URI.create(uri);
            this.corpo = new ByteArrayInputStream(corpo.getBytes(StandardCharsets.UTF_8));
        }

        String textoResposta() { return resposta.toString(StandardCharsets.UTF_8); }
        String mensagem() { return Document.parse(textoResposta()).getString("mensagem"); }
        public Headers getRequestHeaders() { return entrada; }
        public Headers getResponseHeaders() { return saida; }
        public URI getRequestURI() { return uri; }
        public String getRequestMethod() { return metodo; }
        public HttpContext getHttpContext() { return null; }
        public void close() {}
        public InputStream getRequestBody() { return corpo; }
        public OutputStream getResponseBody() { return resposta; }
        public void sendResponseHeaders(int status, long tamanho) { this.status = status; }
        public InetSocketAddress getRemoteAddress() { return new InetSocketAddress("127.0.0.1", 0); }
        public InetSocketAddress getLocalAddress() { return new InetSocketAddress("127.0.0.1", 0); }
        public String getProtocol() { return "HTTP/1.1"; }
        public int getResponseCode() { return status; }
        public Object getAttribute(String nome) { return null; }
        public void setAttribute(String nome, Object valor) {}
        public void setStreams(InputStream entrada, OutputStream saida) {}
        public HttpPrincipal getPrincipal() { return null; }
    }
}
