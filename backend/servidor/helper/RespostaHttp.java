// Author: Rafael Gaudencio Dias
// Ra: 25018953

package helper;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Escreve uma resposta HTTP diretamente na conexão do servidor Socket.
 * Como não há um HttpServer montando a resposta, os cabeçalhos e o corpo são enviados aqui.
 */
public class RespostaHttp {

    // Atalho para respostas que não precisam informar uma origem para o CORS.
    public static void enviar(
            OutputStream saida,
            int status,
            String descricao,
            String json) throws IOException {

        enviar(saida, status, descricao, json, null);
    }

    // Recebe o código HTTP, sua descrição e o JSON já pronto para envio.
    // A origem é usada somente para montar os cabeçalhos de acesso do navegador.
    public static void enviar(
            OutputStream saida,
            int status,
            String descricao,
            String json,
            String origem) throws IOException {

        // Content-Length conta bytes, não caracteres: letras acentuadas podem ocupar mais de um byte.
        // A mesma sequência UTF-8 usada no cálculo será enviada como corpo da resposta.
        byte[] corpo = json.getBytes(StandardCharsets.UTF_8);

        StringBuilder cabecalho = new StringBuilder();

        // Primeiro vem a linha de status, seguida pelos cabeçalhos separados por CRLF.
        cabecalho.append("HTTP/1.1 ")
                .append(status)
                .append(" ")
                .append(descricao)
                .append("\r\n");

        cabecalho.append(
                "Content-Type: application/json; charset=UTF-8\r\n");

        cabecalho.append("Content-Length: ")
                .append(corpo.length)
                .append("\r\n");

        cabecalho.append("Cache-Control: no-store\r\n");

        // O frontend usa outra porta, então o navegador precisa da autorização de CORS.
        // Só essas duas origens recebem os cabeçalhos; as demais não recebem essa permissão.
        if (origem != null &&
                (origem.equals("http://localhost:5500") ||
                        origem.equals("http://127.0.0.1:5500"))) {

            cabecalho.append("Access-Control-Allow-Origin: ")
                    .append(origem)
                    .append("\r\n");

            cabecalho.append("Vary: Origin\r\n");

            cabecalho.append(
                    "Access-Control-Allow-Methods: GET, OPTIONS\r\n");

            cabecalho.append(
                    "Access-Control-Allow-Headers: Authorization\r\n");
        }

        // Uma linha vazia marca o fim dos cabeçalhos e o início do corpo no protocolo HTTP.
        cabecalho.append("Connection: close\r\n");
        cabecalho.append("\r\n");

        // Envia os cabeçalhos antes do JSON e libera os dados pendentes com flush.
        // O fechamento da conexão fica a cargo de quem chamou este método.
        saida.write(
                cabecalho.toString().getBytes(StandardCharsets.UTF_8));

        saida.write(corpo);
        saida.flush();
    }
}
