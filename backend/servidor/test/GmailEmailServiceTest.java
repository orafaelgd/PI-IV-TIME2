package test;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import service.ConfiguracaoEmail;
import service.GmailEmailService;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class GmailEmailServiceTest {
    private static final String URL = "http://localhost:5500/views/pages/redefinir-senha.html";
    private static final String RESPOSTAS_ATE_DATA =
        "220 smtp.example ESMTP\r\n250-smtp.example\r\n250 AUTH LOGIN\r\n"
        + "334 usuario\r\n334 senha\r\n235 autenticado\r\n250 remetente\r\n250 destinatario\r\n354 dados\r\n";

    // A conexão injetada usa somente streams em memória, sem DNS ou servidor SMTP.
    @Test
    void deveEnviarMensagemEcoStockEmUtf8EConfigurarVerificacaoTls() throws Exception {
        Conversa conversa = conversa(RESPOSTAS_ATE_DATA + "250 aceita\r\n221 encerrado\r\n");

        conversa.service.enviarRecuperacao("ana!teste@example.com", URL + "?token=teste");

        String enviado = conversa.saida.toString(StandardCharsets.US_ASCII);
        assertTrue(enviado.contains("MAIL FROM:<avaliacao@example.com>\r\n"));
        assertTrue(enviado.contains("RCPT TO:<ana!teste@example.com>\r\n"));
        assertTrue(enviado.contains("From: EcoStock <avaliacao@example.com>\r\n"));
        assertTrue(enviado.contains("Subject: Redefinicao de senha - EcoStock\r\n"));
        assertTrue(enviado.contains(Base64.getEncoder().encodeToString(
            "abcdefghijklmnop".getBytes(StandardCharsets.UTF_8))));
        String corpo = enviado.substring(enviado.indexOf("\r\n\r\n") + 4, enviado.indexOf("\r\n.\r\n"));
        String mensagem = new String(Base64.getMimeDecoder().decode(corpo), StandardCharsets.UTF_8);
        assertTrue(mensagem.contains("sua senha do EcoStock"));
        assertTrue(mensagem.contains(URL + "?token=teste"));
        assertTrue(mensagem.contains("20 minutos"));
        assertFalse(mensagem.contains("PsicomManager"));

        ArgumentCaptor<SSLParameters> parametros = ArgumentCaptor.forClass(SSLParameters.class);
        verify(conversa.socket).setSSLParameters(parametros.capture());
        assertEquals("HTTPS", parametros.getValue().getEndpointIdentificationAlgorithm());
        assertFalse(parametros.getValue().getServerNames().isEmpty());
        verify(conversa.socket).setSoTimeout(15000);
        verify(conversa.socket).startHandshake();
        verify(conversa.socket).close();
    }

    @Test
    void falhaNoQuitNaoDeveInvalidarMensagemAceita() throws Exception {
        Conversa conversa = conversa(RESPOSTAS_ATE_DATA + "250 aceita\r\n");

        assertDoesNotThrow(() -> conversa.service.enviarRecuperacao("destino@example.com", URL + "?token=teste"));
        assertTrue(conversa.saida.toString(StandardCharsets.US_ASCII).endsWith("QUIT\r\n"));
        verify(conversa.socket).close();
    }

    @Test
    void falhaAoFecharSocketNaoDeveInvalidarMensagemAceita() throws Exception {
        Conversa conversa = conversa(RESPOSTAS_ATE_DATA + "250 aceita\r\n221 encerrado\r\n");
        doThrow(new IOException("Falha de fechamento simulada")).when(conversa.socket).close();

        assertDoesNotThrow(() -> conversa.service.enviarRecuperacao("destino@example.com", URL + "?token=teste"));
    }

    @Test
    void rejeicaoAntesDaAceitacaoDeveSerInformadaSemExporCredenciais() throws Exception {
        Conversa conversa = conversa("220 smtp.example\r\n250 AUTH LOGIN\r\n334 usuario\r\n334 senha\r\n535 recusado\r\n");
        doThrow(new IOException("Falha de fechamento simulada")).when(conversa.socket).close();

        IllegalStateException erro = assertThrows(IllegalStateException.class,
            () -> conversa.service.enviarRecuperacao("destino@example.com", URL + "?token=teste"));

        assertTrue(erro.getMessage().contains("535"));
        assertFalse(erro.getMessage().contains("abcdefghijklmnop"));
        assertEquals(1, erro.getSuppressed().length);
        assertFalse(conversa.saida.toString(StandardCharsets.US_ASCII).contains("From: EcoStock"));
    }

    @Test
    void enderecoInvalidoNaoDeveAbrirConexao() {
        AtomicBoolean abriu = new AtomicBoolean();
        GmailEmailService service = new GmailEmailService(configuracao(), () -> {
            abriu.set(true);
            throw new IOException("Uma conexão real não deve ser tentada");
        });

        assertThrows(IllegalArgumentException.class,
            () -> service.enviarRecuperacao("destino@example.com\r\nBcc: outro@example.com", URL));
        assertFalse(abriu.get());
    }

    @Test
    void configuracaoAusenteNaoDeveAbrirConexao() {
        AtomicBoolean abriu = new AtomicBoolean();
        GmailEmailService service = new GmailEmailService(new ConfiguracaoEmail("", "", ""), () -> {
            abriu.set(true);
            throw new IOException("Uma conexão real não deve ser tentada");
        });

        assertFalse(service.configurado());
        assertThrows(IllegalStateException.class,
            () -> service.enviarRecuperacao("destino@example.com", URL));
        assertFalse(abriu.get());
    }

    private ConfiguracaoEmail configuracao() {
        return new ConfiguracaoEmail("avaliacao@example.com", "abcd efgh ijkl mnop", URL);
    }

    private Conversa conversa(String respostas) throws Exception {
        SSLSocket socket = mock(SSLSocket.class);
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        when(socket.getSSLParameters()).thenReturn(new SSLParameters());
        when(socket.getInputStream()).thenReturn(new ByteArrayInputStream(respostas.getBytes(StandardCharsets.US_ASCII)));
        when(socket.getOutputStream()).thenReturn(saida);
        return new Conversa(socket, saida, new GmailEmailService(configuracao(), () -> socket));
    }

    private record Conversa(SSLSocket socket, ByteArrayOutputStream saida, GmailEmailService service) {}
}
