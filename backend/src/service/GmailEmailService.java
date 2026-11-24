// Código feito por Miriã Nascimento dos Anjos RA: 25892704

package service;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

public final class GmailEmailService {
    private static final String HOST = "smtp.gmail.com";
    private static final int PORT = 465;
    private static final int TIMEOUT_MS = 15000;

    @FunctionalInterface
    public interface ConexaoSmtp {
        SSLSocket abrir() throws IOException;
    }

    private final ConfiguracaoEmail configuracao;
    private final ConexaoSmtp conexao;

    public GmailEmailService() {
        this(ConfiguracaoEmail.carregar());
    }

    public GmailEmailService(ConfiguracaoEmail configuracao) {
        this(configuracao, GmailEmailService::abrirConexao);
    }

    // A conexão pode ser simulada nos testes, sem acessar a rede nem enviar mensagens.
    public GmailEmailService(ConfiguracaoEmail configuracao, ConexaoSmtp conexao) {
        this.configuracao = configuracao;
        this.conexao = conexao;
    }

    public boolean configurado() {
        return configuracao.configurado();
    }

    public String urlRedefinicao() {
        return configuracao.urlRedefinicao();
    }

    public void enviarRecuperacao(String destinatario, String link) throws Exception {
        if (!configurado()) {
            throw new IllegalStateException("Gmail SMTP não configurado. Confira o arquivo email.properties ou as variáveis de ambiente.");
        }
        if (!ConfiguracaoEmail.emailValido(destinatario)) {
            throw new IllegalArgumentException("Endereço de e-mail inválido.");
        }

        String remetente = configuracao.remetente();
        String assunto = "Redefinicao de senha - EcoStock";
        String mensagem = "Olá!\n\nPara redefinir sua senha do EcoStock, acesse:\n"
            + link + "\n\nEste link expira em 20 minutos e só pode ser usado uma vez."
            + "\nSe você não solicitou esta alteração, ignore esta mensagem.\n";
        String conteudoBase64 = Base64.getMimeEncoder(76, "\r\n".getBytes(StandardCharsets.US_ASCII))
            .encodeToString(mensagem.getBytes(StandardCharsets.UTF_8));

        SSLSocket socket = conexao.abrir();
        boolean mensagemAceita = false;
        Exception falha = null;
        try {
            socket.setSoTimeout(TIMEOUT_MS);
            SSLParameters parametros = socket.getSSLParameters();
            parametros.setEndpointIdentificationAlgorithm("HTTPS");
            parametros.setServerNames(List.of(new SNIHostName(HOST)));
            socket.setSSLParameters(parametros);
            socket.startHandshake();

            BufferedReader entrada = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            BufferedWriter saida = new BufferedWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII));

            verificar(entrada, 220);
            comando(saida, entrada, "EHLO localhost", 250);
            comando(saida, entrada, "AUTH LOGIN", 334);
            comando(saida, entrada, codificar(remetente), 334);
            comando(saida, entrada, codificar(configuracao.senhaAplicativo()), 235);
            comando(saida, entrada, "MAIL FROM:<" + remetente + ">", 250);
            comando(saida, entrada, "RCPT TO:<" + destinatario + ">", 250, 251);
            comando(saida, entrada, "DATA", 354);

            saida.write("From: EcoStock <" + remetente + ">\r\n");
            saida.write("To: <" + destinatario + ">\r\n");
            saida.write("Subject: " + assunto + "\r\n");
            saida.write("MIME-Version: 1.0\r\n");
            saida.write("Content-Type: text/plain; charset=UTF-8\r\n");
            saida.write("Content-Transfer-Encoding: base64\r\n\r\n");
            saida.write(conteudoBase64 + "\r\n.\r\n");
            saida.flush();

            // Após o 250, o servidor já aceitou a mensagem. Uma falha ao encerrar
            // a conexão não pode apagar o pedido e invalidar um link já enviado.
            verificar(entrada, 250);
            mensagemAceita = true;
            try {
                comando(saida, entrada, "QUIT", 221);
            } catch (Exception encerramento) {
                // O envio já foi aceito; resta fechar a conexão local.
            }
        } catch (Exception erro) {
            falha = erro;
            throw erro;
        } finally {
            try {
                socket.close();
            } catch (Exception encerramento) {
                if (!mensagemAceita) {
                    if (falha != null) falha.addSuppressed(encerramento);
                    else throw encerramento;
                }
            }
        }
    }

    private static SSLSocket abrirConexao() throws IOException {
        Socket transporte = new Socket();
        try {
            transporte.connect(new InetSocketAddress(HOST, PORT), TIMEOUT_MS);
            SSLSocketFactory fabrica = (SSLSocketFactory) SSLSocketFactory.getDefault();
            return (SSLSocket) fabrica.createSocket(transporte, HOST, PORT, true);
        } catch (IOException | RuntimeException erro) {
            try { transporte.close(); } catch (IOException encerramento) { erro.addSuppressed(encerramento); }
            throw erro;
        }
    }

    private static String codificar(String valor) {
        return Base64.getEncoder().encodeToString(valor.getBytes(StandardCharsets.UTF_8));
    }

    private static void comando(BufferedWriter saida, BufferedReader entrada,
                                String texto, int... esperados) throws Exception {
        saida.write(texto + "\r\n");
        saida.flush();
        verificar(entrada, esperados);
    }

    private static void verificar(BufferedReader entrada, int... esperados) throws Exception {
        String linha;
        int codigo = -1;
        int codigoInicial = -1;
        do {
            linha = entrada.readLine();
            if (linha == null || linha.length() < 4 || !linha.substring(0, 3).matches("\\d{3}")
                || (linha.charAt(3) != ' ' && linha.charAt(3) != '-')) {
                throw new IllegalStateException("Resposta SMTP inválida.");
            }
            codigo = Integer.parseInt(linha.substring(0, 3));
            if (codigoInicial == -1) codigoInicial = codigo;
            if (codigo != codigoInicial) throw new IllegalStateException("Resposta SMTP inválida.");
        } while (linha.charAt(3) == '-');

        for (int esperado : esperados) if (codigo == esperado) return;
        throw new IllegalStateException("Gmail SMTP recusou a operação (código " + codigo + ").");
    }
}
