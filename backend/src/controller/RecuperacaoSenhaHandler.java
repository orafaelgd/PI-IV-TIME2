package controller;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import service.RecuperacaoSenhaService;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

public class RecuperacaoSenhaHandler implements HttpHandler {
    private static final Set<String> ORIGENS = Set.of(
            "http://localhost:5500", "http://127.0.0.1:5500"
    );
    private final RecuperacaoSenhaService service;

    public RecuperacaoSenhaHandler(MongoCollection<Document> usuarios,
                                   MongoCollection<Document> sessoes,
                                   MongoCollection<Document> recuperacoes) {
        this.service = new RecuperacaoSenhaService(usuarios, sessoes, recuperacoes);
    }

    @Override
    public void handle(HttpExchange req) throws IOException {
        String origem = req.getRequestHeaders().getFirst("Origin");
        if (origem != null && !ORIGENS.contains(origem)) {
            responder(req, 403, "Origem não permitida.");
            return;
        }
        if (origem != null) {
            req.getResponseHeaders().set("Access-Control-Allow-Origin", origem);
            req.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
            req.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, OPTIONS");
            req.getResponseHeaders().set("Vary", "Origin");
        }
        if ("OPTIONS".equals(req.getRequestMethod())) {
            req.sendResponseHeaders(204, -1);
            req.close();
            return;
        }
        if (!"POST".equals(req.getRequestMethod())) {
            responder(req, 405, "Método não permitido.");
            return;
        }

        String rota = req.getRequestURI().getPath();
        if (!rota.equals("/api/senha/solicitar") && !rota.equals("/api/senha/redefinir")) {
            responder(req, 404, "Rota não encontrada.");
            return;
        }
        try {
            byte[] bytes = req.getRequestBody().readNBytes(4097);
            if (bytes.length > 4096) {
                responder(req, 413, "Requisição muito grande.");
                return;
            }
            Document corpo;
            try {
                corpo = Document.parse(new String(bytes, StandardCharsets.UTF_8));
            } catch (RuntimeException erro) {
                responder(req, 400, "JSON inválido.");
                return;
            }
            if (rota.equals("/api/senha/solicitar")) {
                if (!service.configurado()) {
                    responder(req, 503, "Serviço de e-mail indisponível.");
                    return;
                }
                service.solicitar(texto(corpo, "email"));
                responder(req, 200, "Se o e-mail estiver cadastrado, enviaremos um link de recuperação.");
            } else {
                boolean atualizado = service.redefinir(
                        texto(corpo, "token"), texto(corpo, "novaSenha"));
                if (!atualizado) {
                    responder(req, 400, "Link inválido ou expirado. Solicite outro.");
                    return;
                }
                responder(req, 200, "Senha redefinida com sucesso.");
            }
        } catch (IllegalArgumentException erro) {
            responder(req, 400, erro.getMessage());
        } catch (Exception erro) {
            System.err.println("Erro no serviço de recuperação de senha: " + erro.getClass().getSimpleName());
            responder(req, 503, "Serviço temporariamente indisponível.");
        }
    }

    private static String texto(Document d, String chave) {
        Object v = d.get(chave);
        return v instanceof String ? (String) v : null;
    }

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
