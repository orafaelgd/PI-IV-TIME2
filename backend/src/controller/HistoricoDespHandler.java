
package controller;

import com.mongodb.MongoException;
import com.mongodb.client.MongoDatabase;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import org.bson.Document;
import org.bson.types.ObjectId;

import service.HistoricoDespService;
import service.SessaoService;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class HistoricoDespHandler implements HttpHandler {

    // SERVIÇOS

    private final HistoricoDespService service;
    private final SessaoService sessaoService;

    // ORIGENS PERMITIDAS

    private static final Set<String> ORIGENS = Set.of(
        "http://localhost:5500",
        "http://127.0.0.1:5500"
    );

    // CONSTRUTOR

    // Recebe o banco e as coleções de autenticação
    public HistoricoDespHandler(MongoDatabase banco) {

        this.service = new HistoricoDespService(banco);

        this.sessaoService = new SessaoService(
            banco.getCollection("users"),
            banco.getCollection("sessoes")
        );
    }

    // REQUISIÇÕES HTTP

    @Override
    public void handle(HttpExchange req) throws IOException {

        String origem = req.getRequestHeaders()
            .getFirst("Origin");

        // VALIDAR ORIGEM

        if (
            origem != null &&
            !ORIGENS.contains(origem)
        ) {
            responder(
                req, 403,
                new Document(
                    "mensagem",
                    "Origem nao permitida."
                )
            );
            return;
        }

        // CONFIGURAÇÃO DE CORS

        if (origem != null) {

            req.getResponseHeaders().set(
                "Access-Control-Allow-Origin",
                origem
            );

            req.getResponseHeaders().set(
                "Access-Control-Allow-Headers",
                "Authorization, Content-Type"
            );

            req.getResponseHeaders().set(
                "Access-Control-Allow-Methods",
                "GET, OPTIONS"
            );

            req.getResponseHeaders().set(
                "Vary", "Origin"
            );
        }

        req.getResponseHeaders().set(
            "Cache-Control", "no-store"
        );

        // VERIFICAR ROTA

        if (
            !"/api/saidas".equals(
                req.getRequestURI().getPath()
            )
        ) {
            responder(
                req, 404,
                new Document(
                    "mensagem",
                    "Rota nao encontrada."
                )
            );
            return;
        }

        // RESPONDER OPTIONS

        if ("OPTIONS".equals(req.getRequestMethod())) {
            req.sendResponseHeaders(204, -1);
            req.close();
            return;
        }

        // VALIDAR MÉTODO

        if (!"GET".equals(req.getRequestMethod())) {

            req.getResponseHeaders().set(
                "Allow",
                "GET, OPTIONS"
            );

            responder(
                req, 405,
                new Document(
                    "mensagem",
                    "Metodo nao permitido."
                )
            );
            return;
        }

        try {

            // AUTENTICAR USUÁRIO

            // Valida o token usando o SessaoService
            Document usuario = sessaoService.autenticar(req);

            if (
                usuario == null ||
                usuario.getObjectId("_id") == null
            ) {
                responder(
                    req, 401,
                    new Document(
                        "mensagem",
                        "Sessao invalida ou expirada. Faca login novamente."
                    )
                );
                return;
            }

            // Identifica a conta autenticada
            ObjectId usuarioId =
                usuario.getObjectId("_id");

            // LER FILTROS

            Map<String, String> parametros =
                lerParametros(
                    req.getRequestURI().getRawQuery()
                );

            LocalDate inicio =
                lerData(parametros.get("dataInicio"));

            LocalDate fim =
                lerData(parametros.get("dataFim"));

            int pagina = Integer.parseInt(
                parametros.getOrDefault(
                    "pagina", "1"
                )
            );

            int limite = Integer.parseInt(
                parametros.getOrDefault(
                    "limite", "20"
                )
            );

            String somenteTexto =
                parametros.getOrDefault(
                    "somenteDesperdicio",
                    "false"
                );

            if (
                !somenteTexto.equals("true") &&
                !somenteTexto.equals("false")
            ) {
                throw new IllegalArgumentException(
                    "Filtro somenteDesperdicio invalido."
                );
            }

            // CONSULTAR HISTÓRICO DA CONTA

            Document resultado = service.consultarHistorico(
                inicio,
                fim,
                parametros.get("motivo"),
                pagina,
                limite,
                Boolean.parseBoolean(somenteTexto),
                usuarioId
            );

            responder(req, 200, resultado);

        } catch (IllegalArgumentException erro) {

            responder(
                req, 400,
                new Document(
                    "mensagem",
                    "Filtros invalidos: " +
                    erro.getMessage()
                )
            );

        } catch (MongoException erro) {

            System.err.println(
                "Erro MongoDB no historico: " +
                erro.getMessage()
            );

            responder(
                req, 500,
                new Document(
                    "mensagem",
                    "Erro ao acessar o banco de dados."
                )
            );

        } catch (RuntimeException erro) {

            System.err.println(
                "Erro ao consultar historico: " +
                erro.getMessage()
            );

            responder(
                req, 500,
                new Document(
                    "mensagem",
                    "Erro ao consultar historico."
                )
            );
        }
    }

    // LER PARÂMETROS

    // Converte os filtros da URL em um mapa
    private static Map<String, String> lerParametros(
        String query
    ) {

        Map<String, String> parametros =
            new HashMap<>();

        if (
            query == null ||
            query.isEmpty()
        ) {
            return parametros;
        }

        for (String parte : query.split("&")) {

            String[] par = parte.split("=", 2);

            if (par.length != 2) {
                throw new IllegalArgumentException(
                    "Parametro invalido."
                );
            }

            String nome = URLDecoder.decode(
                par[0],
                StandardCharsets.UTF_8
            );

            String valor = URLDecoder.decode(
                par[1],
                StandardCharsets.UTF_8
            );

            // Impede parâmetros duplicados
            if (
                parametros.putIfAbsent(
                    nome, valor
                ) != null
            ) {
                throw new IllegalArgumentException(
                    "Parametro duplicado."
                );
            }
        }

        return parametros;
    }

    // LER DATA

    private static LocalDate lerData(String valor) {

        return valor == null
            ? null
            : LocalDate.parse(valor);
    }

    // RESPOSTAS HTTP

    private static void responder(
        HttpExchange req,
        int status,
        Document dados
    ) throws IOException {

        byte[] resposta = dados.toJson()
            .getBytes(StandardCharsets.UTF_8);

        req.getResponseHeaders().set(
            "Content-Type",
            "application/json; charset=UTF-8"
        );

        req.sendResponseHeaders(
            status,
            resposta.length
        );

        try (
            OutputStream saida =
                req.getResponseBody()
        ) {
            saida.write(resposta);
        }
    }
}
