package controller;

import com.mongodb.client.MongoDatabase;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.bson.Document;
import service.HistoricoDespService;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class HistoricoDespHandler implements HttpHandler {
    private static final Set<String> ORIGENS = Set.of(
            "http://localhost:5500", "http://127.0.0.1:5500"
    );

    private final HistoricoDespService service;

    public HistoricoDespHandler(MongoDatabase banco) {
        this.service = new HistoricoDespService(banco);
    }

    @Override
    public void handle(HttpExchange req) throws IOException {
        String origem = req.getRequestHeaders().getFirst("Origin");
        if (origem != null && !ORIGENS.contains(origem)) {
            responder(req, 403, new Document("mensagem", "Origem não permitida."));
            return;
        }

        if (origem != null) {
            req.getResponseHeaders().set("Access-Control-Allow-Origin", origem);
            req.getResponseHeaders().set("Access-Control-Allow-Headers", "Authorization, Content-Type");
            req.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
            req.getResponseHeaders().set("Vary", "Origin");
        }

        req.getResponseHeaders().set("Cache-Control", "no-store");
        if ("OPTIONS".equals(req.getRequestMethod())) {
            req.sendResponseHeaders(204, -1);
            req.close();
            return;
        }

        if (!"/api/saidas".equals(req.getRequestURI().getPath())) {
            responder(req, 404, new Document("mensagem", "Rota não encontrada."));
            return;
        }
        if (!"GET".equals(req.getRequestMethod())) {
            req.getResponseHeaders().set("Allow", "GET, OPTIONS");
            responder(req, 405, new Document("mensagem", "Método não permitido."));
            return;
        }

        try {
            String cabecalho = req.getRequestHeaders().getFirst("Authorization");
            String token = cabecalho != null && cabecalho.startsWith("Bearer ")
                    ? cabecalho.substring(7).trim() : null;
            if (!service.autenticado(token)) {
                responder(req, 401, new Document("mensagem", "Faça login para consultar o histórico."));
                return;
            }

            Map<String, String> parametros = lerParametros(req.getRequestURI().getRawQuery());
            LocalDate inicio = lerData(parametros.get("dataInicio"));
            LocalDate fim = lerData(parametros.get("dataFim"));
            int pagina = Integer.parseInt(parametros.getOrDefault("pagina", "1"));
            int limite = Integer.parseInt(parametros.getOrDefault("limite", "20"));
            String somenteTexto = parametros.getOrDefault("somenteDesperdicio", "false");
            if (!somenteTexto.equals("true") && !somenteTexto.equals("false")) {
                throw new IllegalArgumentException("Filtro somenteDesperdicio inválido.");
            }

            Document resultado = service.consultarHistorico(
                    inicio, fim, parametros.get("motivo"), pagina, limite,
                    Boolean.parseBoolean(somenteTexto)
            );
            responder(req, 200, resultado);
        } catch (IllegalArgumentException | DateTimeParseException erro) {
            responder(req, 400, new Document("mensagem", "Filtros inválidos."));
        } catch (RuntimeException erro) {
            System.err.println("Erro ao consultar histórico: " + erro.getMessage());
            responder(req, 500, new Document("mensagem", "Erro ao consultar histórico."));
        }
    }

    private static Map<String, String> lerParametros(String query) {
        Map<String, String> parametros = new HashMap<>();
        if (query == null || query.isEmpty()) return parametros;
        for (String parte : query.split("&")) {
            String[] par = parte.split("=", 2);
            if (par.length != 2) throw new IllegalArgumentException("Parâmetro inválido.");
            parametros.put(
                    URLDecoder.decode(par[0], StandardCharsets.UTF_8),
                    URLDecoder.decode(par[1], StandardCharsets.UTF_8)
            );
        }
        return parametros;
    }

    private static LocalDate lerData(String valor) {
        return valor == null ? null : LocalDate.parse(valor);
    }

    private static void responder(HttpExchange req, int status, Document dados)
            throws IOException {
        byte[] resposta = dados.toJson().getBytes(StandardCharsets.UTF_8);
        req.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        req.sendResponseHeaders(status, resposta.length);
        try (OutputStream out = req.getResponseBody()) {
            out.write(resposta);
        }
    }
}
