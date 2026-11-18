
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
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class HistoricoDespHandler implements HttpHandler {

    // ORIGENS PERMITIDAS

    private static final Set<String> ORIGENS = Set.of(
        "http://localhost:5500",
        "http://127.0.0.1:5500"
    );

    // SERVIÇOS

    private final HistoricoDespService historicoService;
    private final SessaoService sessaoService;

    // CONSTRUTOR

    public HistoricoDespHandler(MongoDatabase banco) {

        historicoService = new HistoricoDespService(banco);

        sessaoService = new SessaoService(
            banco.getCollection("users"),
            banco.getCollection("sessoes")
        );
    }

    // ATENDER REQUISIÇÕES

    @Override
    public void handle(HttpExchange requisicao)
            throws IOException {

        String origem = requisicao
            .getRequestHeaders()
            .getFirst("Origin");

        // VALIDAR ORIGEM

        if (
            origem != null &&
            !ORIGENS.contains(origem)
        ) {

            responder(
                requisicao,
                403,
                "Origem não permitida."
            );

            return;
        }

        // CONFIGURAR CORS

        if (origem != null) {

            requisicao.getResponseHeaders().set(
                "Access-Control-Allow-Origin",
                origem
            );

            requisicao.getResponseHeaders().set(
                "Access-Control-Allow-Headers",
                "Authorization, Content-Type"
            );

            requisicao.getResponseHeaders().set(
                "Access-Control-Allow-Methods",
                "GET, OPTIONS"
            );

            requisicao.getResponseHeaders().set(
                "Vary",
                "Origin"
            );
        }

        requisicao.getResponseHeaders().set(
            "Cache-Control",
            "no-store"
        );

        String metodo = requisicao.getRequestMethod();

        String caminho = requisicao
            .getRequestURI()
            .getPath();

        // VALIDAR ROTA

        if (!"/api/saidas".equals(caminho)) {

            responder(
                requisicao,
                404,
                "Rota não encontrada."
            );

            return;
        }

        // RESPONDER OPTIONS

        if ("OPTIONS".equals(metodo)) {

            requisicao.sendResponseHeaders(204, -1);
            requisicao.close();

            return;
        }

        // PERMITIR SOMENTE GET

        if (!"GET".equals(metodo)) {

            requisicao.getResponseHeaders().set(
                "Allow",
                "GET, OPTIONS"
            );

            responder(
                requisicao,
                405,
                "Método não permitido."
            );

            return;
        }

        try {

            // AUTENTICAR USUÁRIO

            // O SESSAOSERVICE VALIDA O TOKEN
            // E RECUPERA A CONTA NA COLEÇÃO USERS

            Document usuario =
                sessaoService.autenticar(requisicao);

            if (
                usuario == null ||
                usuario.getObjectId("_id") == null
            ) {

                responder(
                    requisicao,
                    401,
                    "Sessão inválida ou expirada. Faça login novamente."
                );

                return;
            }

            // RECUPERAR ID DA CONTA LOGADA

            ObjectId usuarioId =
                usuario.getObjectId("_id");

            // LOG DE CONFERÊNCIA

            System.out.println(
                "HISTÓRICO DE SAÍDA - Usuário: " +
                usuarioId.toHexString()
            );

            // LER PARÂMETROS DA URL

            Map<String, String> parametros =
                lerParametros(
                    requisicao.getRequestURI().getRawQuery()
                );

            // FILTROS DE DATA

            LocalDate inicio = lerData(
                parametros.get("dataInicio")
            );

            LocalDate fim = lerData(
                parametros.get("dataFim")
            );

            // PAGINAÇÃO

            int pagina = Integer.parseInt(
                parametros.getOrDefault("pagina", "1")
            );

            int limite = Integer.parseInt(
                parametros.getOrDefault("limite", "20")
            );

            // PADRÃO: MOSTRAR TODAS AS SAÍDAS

            String somenteTexto = parametros.getOrDefault(
                "somenteDesperdicio",
                "false"
            );

            // VALIDAR FILTRO DE DESPERDÍCIO

            if (
                !"true".equals(somenteTexto) &&
                !"false".equals(somenteTexto)
            ) {

                throw new IllegalArgumentException(
                    "Filtro somenteDesperdicio inválido."
                );
            }

            // CONSULTAR SERVICE

            Document resultado =
                historicoService.consultarHistorico(
                    inicio,
                    fim,
                    parametros.get("motivo"),
                    pagina,
                    limite,
                    Boolean.parseBoolean(somenteTexto),
                    usuarioId
                );

            // RETORNAR JSON

            responderJSON(
                requisicao,
                200,
                resultado
            );

        } catch (IllegalArgumentException erro) {

            responder(
                requisicao,
                400,
                erro.getMessage()
            );

        } catch (MongoException erro) {

            System.err.println(
                "Erro MongoDB no histórico: " +
                erro.getMessage()
            );

            responder(
                requisicao,
                500,
                "Erro ao acessar o banco de dados."
            );

        } catch (RuntimeException erro) {

            System.err.println(
                "Erro no histórico: " +
                erro.getMessage()
            );

            responder(
                requisicao,
                500,
                "Erro interno ao consultar o histórico."
            );
        }
    }

    // LER PARÂMETROS DA URL

    private static Map<String, String> lerParametros(
        String query
    ) {

        Map<String, String> parametros =
            new HashMap<>();

        if (query == null || query.isBlank()) {
            return parametros;
        }

        for (String parte : query.split("&")) {

            String[] par = parte.split("=", 2);

            if (par.length != 2) {

                throw new IllegalArgumentException(
                    "Parâmetro inválido."
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

            // NÃO PERMITIR PARÂMETROS DUPLICADOS

            if (
                parametros.putIfAbsent(nome, valor) != null
            ) {

                throw new IllegalArgumentException(
                    "Parâmetro duplicado."
                );
            }
        }

        return parametros;
    }

    // LER DATA

    private static LocalDate lerData(String valor) {

        if (valor == null) {
            return null;
        }

        try {

            return LocalDate.parse(valor);

        } catch (DateTimeParseException erro) {

            throw new IllegalArgumentException(
                "Data inválida. Utilize AAAA-MM-DD."
            );
        }
    }

    // RESPONDER COM MENSAGEM

    private static void responder(
        HttpExchange requisicao,
        int status,
        String mensagem
    ) throws IOException {

        responderJSON(
            requisicao,
            status,
            new Document("mensagem", mensagem)
        );
    }

    // RESPONDER JSON

    private static void responderJSON(
        HttpExchange requisicao,
        int status,
        Document dados
    ) throws IOException {

        byte[] resposta = dados.toJson()
            .getBytes(StandardCharsets.UTF_8);

        requisicao.getResponseHeaders().set(
            "Content-Type",
            "application/json; charset=UTF-8"
        );

        requisicao.getResponseHeaders().set(
            "Cache-Control",
            "no-store"
        );

        requisicao.sendResponseHeaders(
            status,
            resposta.length
        );

        try (
            OutputStream saida =
                requisicao.getResponseBody()
        ) {

            saida.write(resposta);
        }
    }
}
