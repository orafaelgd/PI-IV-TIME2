// Código feito por Miriã Nascimento dos Anjos RA: 25892704

package controller;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import service.RecuperacaoSenhaService;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

public class RecuperacaoSenhaHandler implements HttpHandler {

    // Define as origens autorizadas a acessar as rotas de recuperação de senha
    private static final Set<String> ORIGENS = Set.of(
            "http://localhost:5500", "http://127.0.0.1:5500"
    );

    private final RecuperacaoSenhaService service;

    // Inicializa o serviço com as coleções necessárias do banco de dados
    public RecuperacaoSenhaHandler(MongoClient cliente,
                                   MongoCollection<Document> usuarios,
                                   MongoCollection<Document> sessoes,
                                   MongoCollection<Document> recuperacoes) {
        this(new RecuperacaoSenhaService(cliente, usuarios, sessoes, recuperacoes));
    }

    public RecuperacaoSenhaHandler(RecuperacaoSenhaService service) {
        this.service = service;
    }

    // Recebe as requisições de solicitação e redefinição de senha
    @Override
    public void handle(HttpExchange req) throws IOException {
        String origem = req.getRequestHeaders().getFirst("Origin");

        // Verifica se a requisição veio de uma origem permitida
        if (origem != null && !ORIGENS.contains(origem)) {
            responder(req, 403, "Origem não permitida.");
            return;
        }

        // Configura os cabeçalhos para permitir a comunicação com o frontend
        if (origem != null) {
            req.getResponseHeaders().set("Access-Control-Allow-Origin", origem);
            req.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
            req.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, OPTIONS");
            req.getResponseHeaders().set("Vary", "Origin");
        }

        // Responde às requisições OPTIONS utilizadas na verificação do CORS
        if ("OPTIONS".equals(req.getRequestMethod())) {
            req.sendResponseHeaders(204, -1);
            req.close();
            return;
        }

        // As operações de recuperação de senha aceitam apenas requisições POST
        if (!"POST".equals(req.getRequestMethod())) {
            responder(req, 405, "Método não permitido.");
            return;
        }

        // Verifica se a rota solicitada corresponde a uma das funcionalidades disponíveis
        String rota = req.getRequestURI().getPath();
        if (!rota.equals("/api/senha/solicitar") && !rota.equals("/api/senha/redefinir")) {
            responder(req, 404, "Rota não encontrada.");
            return;
        }

        try {
            // Limita o tamanho dos dados recebidos na requisição
            byte[] bytes = req.getRequestBody().readNBytes(4097);
            if (bytes.length > 4096) {
                responder(req, 413, "Requisição muito grande.");
                return;
            }

            // Converte o conteúdo recebido para um documento JSON
            Document corpo;
            try {
                corpo = Document.parse(new String(bytes, StandardCharsets.UTF_8));
            } catch (RuntimeException erro) {
                responder(req, 400, "JSON inválido.");
                return;
            }

            // Solicita o envio do link de recuperação para o e-mail informado
            if (rota.equals("/api/senha/solicitar")) {

                // Confere se o serviço de envio de e-mails está configurado
                if (!service.configurado()) {
                    responder(req, 503, "Serviço de e-mail indisponível.");
                    return;
                }

                service.solicitar(texto(corpo, "email"));
                responder(req, 200, "Se o e-mail estiver cadastrado, enviaremos um link de recuperação.");

            } else {

                // Verifica o token de recuperação e tenta atualizar a senha
                boolean atualizado = service.redefinir(
                        texto(corpo, "token"), texto(corpo, "novaSenha"));

                if (!atualizado) {
                    responder(req, 400, "Link inválido ou expirado. Solicite outro.");
                    return;
                }

                responder(req, 200, "Senha redefinida com sucesso.");
            }

        } catch (IllegalArgumentException erro) {

            // Retorna uma mensagem quando os dados informados são inválidos
            responder(req, 400, erro.getMessage());

        } catch (Exception erro) {

            // Trata falhas inesperadas durante a recuperação de senha
            System.err.println("Erro no serviço de recuperação de senha: " + erro.getClass().getSimpleName());
            responder(req, 503, "Serviço temporariamente indisponível.");
        }
    }

    // Recupera um campo do JSON e retorna seu valor caso seja texto
    private static String texto(Document d, String chave) {
        Object v = d.get(chave);
        return v instanceof String ? (String) v : null;
    }

    // Envia a resposta em JSON com a mensagem e o status HTTP correspondente
    private static void responder(HttpExchange req, int status, String mensagem) throws IOException {
        byte[] resposta = new Document("mensagem", mensagem).toJson()
                .getBytes(StandardCharsets.UTF_8);

        req.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        req.getResponseHeaders().set("Cache-Control", "no-store");
        req.sendResponseHeaders(status, resposta.length);

        try (OutputStream out = req.getResponseBody()) {
            out.write(resposta);
        }
    }
}
