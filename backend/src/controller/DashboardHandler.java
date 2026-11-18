
package controller;

import service.DashboardService;
import service.SessaoService;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import com.mongodb.MongoException;
import com.mongodb.client.MongoCollection;

import org.bson.Document;
import org.bson.types.ObjectId;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

public class DashboardHandler implements HttpHandler {

    // SERVIÇOS

    private final DashboardService dashboardService;
    private final SessaoService sessaoService;

    // ORIGENS PERMITIDAS

    private static final Set<String> ORIGENS = Set.of(
        "http://localhost:5500",
        "http://127.0.0.1:5500"
    );

    // CONSTRUTOR

    // Recebe as coleções do MongoDB
    public DashboardHandler(
        MongoCollection<Document> itens,
        MongoCollection<Document> lotes,
        MongoCollection<Document> usuarios,
        MongoCollection<Document> sessoes
    ) {

        this.dashboardService = new DashboardService(
            itens, lotes
        );

        this.sessaoService = new SessaoService(
            usuarios, sessoes
        );
    }

    // REQUISIÇÕES HTTP

    @Override
    public void handle(HttpExchange requisicao)
        throws IOException {

        String origem = requisicao.getRequestHeaders()
            .getFirst("Origin");

        // Verifica se a origem é permitida
        if (
            origem != null &&
            !ORIGENS.contains(origem)
        ) {

            responder(
                requisicao, 403,
                "Origem nao permitida."
            );

            return;
        }

        // CONFIGURAÇÃO DE CORS

        if (origem != null) {

            requisicao.getResponseHeaders().set(
                "Access-Control-Allow-Origin",
                origem
            );

            // Permite o token enviado pelo frontend
            requisicao.getResponseHeaders().set(
                "Access-Control-Allow-Headers",
                "Content-Type, Authorization"
            );

            requisicao.getResponseHeaders().set(
                "Access-Control-Allow-Methods",
                "GET, OPTIONS"
            );

            requisicao.getResponseHeaders().set(
                "Vary", "Origin"
            );
        }

        requisicao.getResponseHeaders().set(
            "Cache-Control", "no-store"
        );

        String metodo = requisicao.getRequestMethod();

        String caminho = requisicao.getRequestURI()
            .getPath();

        // VERIFICAR ROTA

        if (!caminho.equals("/api/dashboard/resumo")) {

            responder(
                requisicao, 404,
                "Rota nao encontrada."
            );

            return;
        }

        // RESPONDER OPTIONS

        if ("OPTIONS".equals(metodo)) {

            requisicao.sendResponseHeaders(204, -1);
            requisicao.close();

            return;
        }

        // VALIDAR MÉTODO

        if (!"GET".equals(metodo)) {

            requisicao.getResponseHeaders().set(
                "Allow", "GET, OPTIONS"
            );

            responder(
                requisicao, 405,
                "Metodo nao permitido."
            );

            return;
        }

        // CONSULTAR RESUMO

        consultarResumo(requisicao);
    }

    // AUTENTICAR E CONSULTAR DASHBOARD

    private void consultarResumo(
        HttpExchange requisicao
    ) throws IOException {

        try {

            // Valida o token usando o SessaoService
            Document usuario =
                sessaoService.autenticar(requisicao);

            if (
                usuario == null ||
                usuario.getObjectId("_id") == null
            ) {

                responder(
                    requisicao, 401,
                    "Sessao invalida ou expirada. " +
                    "Faca login novamente."
                );

                return;
            }

            // Identifica o usuário autenticado
            ObjectId usuarioId =
                usuario.getObjectId("_id");

            // Consulta somente os dados da conta
            Document resumo =
                dashboardService.consultarResumo(usuarioId);

            responderJSON(
                requisicao, 200, resumo
            );

        } catch (MongoException erro) {

            System.err.println(
                "Erro MongoDB no Dashboard: " +
                erro.getMessage()
            );

            responder(
                requisicao, 500,
                "Erro ao acessar o banco de dados."
            );

        } catch (RuntimeException erro) {

            System.err.println(
                "Erro no Dashboard: " +
                erro.getMessage()
            );

            responder(
                requisicao, 500,
                "Erro interno do servidor."
            );
        }
    }

    // RESPOSTAS HTTP

    // Retorna uma mensagem em JSON
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

    // ENVIAR JSON

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

        requisicao.sendResponseHeaders(
            status, resposta.length
        );

        try (
            OutputStream saida =
                requisicao.getResponseBody()
        ) {

            saida.write(resposta);
        }
    }
}
