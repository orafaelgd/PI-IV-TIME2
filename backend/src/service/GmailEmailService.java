// Código feito por Miriã Nascimento dos Anjos RA: 25892704

package service;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Pattern;

public final class GmailEmailService {

    // Configura o servidor do Gmail e o formato aceito para os endereços de e-mail
    private static final Pattern EMAIL = Pattern.compile(
            "^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$");
    private static final String HOST = "smtp.gmail.com";
    private static final int PORT = 465;

    // Verifica se as variáveis necessárias para o envio de e-mails estão configuradas
    public boolean configurado() {
        return !ambiente("GMAIL_USER").isBlank()
                && !ambiente("GMAIL_APP_PASSWORD").isBlank()
                && !ambiente("APP_RESET_URL").isBlank();
    }

    // Prepara e envia o e-mail com o link de recuperação de senha
    public void enviarRecuperacao(String destinatario, String link) throws Exception {
        String remetente = ambiente("GMAIL_USER");
        String senhaDeApp = ambiente("GMAIL_APP_PASSWORD").replace(" ", "");

        // Confere as configurações e os endereços antes de iniciar o envio
        if (!configurado()) {
            throw new IllegalStateException("Gmail SMTP nao configurado.");
        }
        if (!EMAIL.matcher(destinatario).matches() || !EMAIL.matcher(remetente).matches()) {
            throw new IllegalArgumentException("Endereco de e-mail invalido.");
        }

        // Monta o conteúdo da mensagem e informa o prazo de validade do link
        String assunto = "Redefinicao de senha - PsicomManager";
        String mensagem = "Ola!\n\nPara redefinir sua senha do PsicomManager, acesse:\n"
                + link + "\n\nEste link expira em 20 minutos e so pode ser usado uma vez."
                + "\nSe voce nao solicitou esta alteracao, ignore esta mensagem.\n";

        String conteudoBase64 = Base64.getMimeEncoder(76, "\r\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(mensagem.getBytes(StandardCharsets.UTF_8));

        // Estabelece uma conexão segura com o servidor SMTP do Gmail
        SSLSocketFactory fabrica = (SSLSocketFactory) SSLSocketFactory.getDefault();
        try (SSLSocket socket = (SSLSocket) fabrica.createSocket(HOST, PORT)) {
            socket.setSoTimeout(15000);
            SSLParameters parametros = socket.getSSLParameters();
            parametros.setEndpointIdentificationAlgorithm("HTTPS");
            socket.setSSLParameters(parametros);
            socket.startHandshake();

            BufferedReader entrada = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            BufferedWriter saida = new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII));

            // Realiza a autenticação no Gmail utilizando o e-mail e a senha de aplicativo
            verificar(entrada, 220);
            comando(saida, entrada, "EHLO localhost", 250);
            comando(saida, entrada, "AUTH LOGIN", 334);
            comando(saida, entrada, codificar(remetente), 334);
            comando(saida, entrada, codificar(senhaDeApp), 235);

            // Informa ao servidor quem está enviando e quem receberá a mensagem
            comando(saida, entrada, "MAIL FROM:<" + remetente + ">", 250);
            comando(saida, entrada, "RCPT TO:<" + destinatario + ">", 250, 251);
            comando(saida, entrada, "DATA", 354);

            // Envia os cabeçalhos e o conteúdo do e-mail
            saida.write("From: PsicomManager <" + remetente + ">\r\n");
            saida.write("To: <" + destinatario + ">\r\n");
            saida.write("Subject: " + assunto + "\r\n");
            saida.write("MIME-Version: 1.0\r\n");
            saida.write("Content-Type: text/plain; charset=UTF-8\r\n");
            saida.write("Content-Transfer-Encoding: base64\r\n");
            saida.write("\r\n");
            saida.write(conteudoBase64 + "\r\n.\r\n");
            saida.flush();

            // Confirma o envio e encerra a comunicação com o servidor
            verificar(entrada, 250);
            comando(saida, entrada, "QUIT", 221);
        }
    }

    // Converte os dados para Base64, formato utilizado na autenticação SMTP
    private static String codificar(String valor) {
        return Base64.getEncoder().encodeToString(valor.getBytes(StandardCharsets.UTF_8));
    }

    // Envia um comando ao servidor SMTP e verifica se a resposta foi aceita
    private static void comando(BufferedWriter saida, BufferedReader entrada,
                                String texto, int... esperados) throws Exception {
        saida.write(texto + "\r\n");
        saida.flush();
        verificar(entrada, esperados);
    }

    // Confere os códigos de resposta retornados pelo servidor Gmail
    private static void verificar(BufferedReader entrada, int... esperados) throws Exception {
        String linha;
        int codigo;

        do {
            linha = entrada.readLine();
            if (linha == null || linha.length() < 4 || !linha.substring(0, 3).matches("\\d{3}")) {
                throw new IllegalStateException("Resposta SMTP invalida.");
            }
            codigo = Integer.parseInt(linha.substring(0, 3));
        } while (linha.charAt(3) == '-');

        for (int esperado : esperados) if (codigo == esperado) return;

        // Evita mostrar informações sensíveis que possam aparecer na resposta do servidor
        throw new IllegalStateException("Gmail SMTP recusou a operacao (codigo " + codigo + ").");
    }

    // Recupera o valor de uma variável de ambiente utilizada na configuração
    private static String ambiente(String nome) {
        String valor = System.getenv(nome);
        return valor == null ? "" : valor.trim();
    }
}
