
package controller;

import service.RelatoriosService;
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
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class RelatoriosHandler implements HttpHandler {

    // SERVIÇOS

    private final RelatoriosService relatoriosService;
    private final SessaoService sessaoService;

    // ORIGENS PERMITIDAS

    private static final Set<String> ORIGENS = Set.of(
        "http://localhost:5500",
        "http://127.0.0.1:5500"
    );

    // CONSTRUTOR

    public RelatoriosHandler(
        MongoCollection<Document> itens,
        MongoCollection<Document> lotes,
        MongoCollection<Document> movimentacoes,
        MongoCollection<Document> usuarios,
        MongoCollection<Document> sessoes
    ) {

        this.relatoriosService = new RelatoriosService(
            itens,
            lotes,
            movimentacoes
        );

        this.sessaoService = new SessaoService(
            usuarios,
            sessoes
        );
    }

    // REQUISIÇÕES HTTP

    @Override
    public void handle(HttpExchange requisicao)
        throws IOException {

        String origem = requisicao
            .getRequestHeaders()
            .getFirst("Origin");

        // VERIFICAR ORIGEM

        if (
            origem != null &&
            !ORIGENS.contains(origem)
        ) {

            responder(
                requisicao,
                403,
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

            // Permite enviar o token de autenticação
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

        String metodo =
            requisicao.getRequestMethod();

        String caminho =
            requisicao.getRequestURI().getPath();

        // ROTAS DISPONÍVEIS

        boolean rotaValida = Set.of(
            "/api/relatorios/estoque-por-produto",
            "/api/relatorios/financeiro-por-produto",
            "/api/relatorios/desperdicios/por-produto",
            "/api/relatorios/desperdicios/por-motivo",
            "/api/relatorios/desperdicios/por-periodo",
            "/api/relatorios/indicadores",
            "/api/relatorios/desperdicios/evolucao-mensal"
        ).contains(caminho);

        if (!rotaValida) {

            responder(
                requisicao,
                404,
                "Rota nao encontrada."
            );

            return;
        }

        // REQUISIÇÃO OPTIONS

        if ("OPTIONS".equals(metodo)) {

            requisicao.sendResponseHeaders(
                204, -1
            );

            requisicao.close();
            return;
        }

        // VALIDAR MÉTODO

        if (!"GET".equals(metodo)) {

            requisicao.getResponseHeaders().set(
                "Allow", "GET, OPTIONS"
            );

            responder(
                requisicao,
                405,
                "Metodo nao permitido."
            );

            return;
        }

        // AUTENTICAÇÃO E CONSULTA

        try {

            // Valida a sessão pelo token
            Document usuario =
                sessaoService.autenticar(requisicao);

            if (
                usuario == null ||
                usuario.getObjectId("_id") == null
            ) {

                responder(
                    requisicao,
                    401,
                    "Sessao invalida ou expirada. " +
                    "Faca login novamente."
                );

                return;
            }

            // Identifica o proprietário dos dados
            ObjectId usuarioId =
                usuario.getObjectId("_id");

            Document relatorio;

            // SELECIONAR RELATÓRIO

            switch (caminho) {

                case "/api/relatorios/estoque-por-produto":

                    relatorio =
                        relatoriosService.consultarEstoquePorProduto(
                            usuarioId
                        );

                    break;

                case "/api/relatorios/financeiro-por-produto":

                    relatorio =
                        relatoriosService.consultarFinanceiroPorProduto(
                            usuarioId
                        );

                    break;

                case "/api/relatorios/desperdicios/por-produto":

                    relatorio =
                        relatoriosService.consultarDesperdicioPorProduto(
                            usuarioId
                        );

                    break;

                case "/api/relatorios/desperdicios/por-motivo":

                    relatorio =
                        relatoriosService.consultaDesperdicioPorMotivo(
                            usuarioId
                        );

                    break;

                case "/api/relatorios/desperdicios/por-periodo":

                    relatorio = consultarDesperdicioPorPeriodo(
                        requisicao,
                        usuarioId
                    );

                    break;

                case "/api/relatorios/indicadores":

                    relatorio =
                        relatoriosService.consultaIndicadoresGerais(
                            usuarioId
                        );

                    break;

                case "/api/relatorios/desperdicios/evolucao-mensal":

                    relatorio =
                        relatoriosService.consultarEvolucaoMensal(
                            usuarioId
                        );

                    break;

                default:

                    responder(
                        requisicao,
                        404,
                        "Rota nao encontrada."
                    );

                    return;
            }

            responderJSON(
                requisicao,
                200,
                relatorio
            );

        } catch (IllegalArgumentException erro) {

            responder(
                requisicao,
                400,
                erro.getMessage()
            );

        } catch (MongoException erro) {

            System.err.println(
                "Erro MongoDB em Relatorios: " +
                erro.getMessage()
            );

            responder(
                requisicao,
                500,
                "Erro ao acessar o banco de dados."
            );

        } catch (RuntimeException erro) {

            System.err.println(
                "Erro interno em Relatorios: " +
                erro.getMessage()
            );

            responder(
                requisicao,
                500,
                "Erro interno do servidor."
            );
        }
    }

    // CONSULTAR DESPERDÍCIO POR PERÍODO

    private Document consultarDesperdicioPorPeriodo(
        HttpExchange requisicao,
        ObjectId usuarioId
    ) {

        String query = requisicao
            .getRequestURI()
            .getRawQuery();

        if (
            query == null ||
            query.isBlank()
        ) {

            throw new IllegalArgumentException(
                "Informe as datas de inicio e fim."
            );
        }

        Map<String, String> parametros =
            new HashMap<>();

        // Lê os parâmetros da URL
        for (String parte : query.split("&")) {

            String[] par = parte.split("=", 2);

            if (
                par.length != 2 ||
                !Set.of("inicio", "fim").contains(par[0]) ||
                parametros.putIfAbsent(
                    par[0], par[1]
                ) != null
            ) {

                throw new IllegalArgumentException(
                    "Parametros invalidos."
                );
            }
        }

        // VALIDAR DATAS

        LocalDate inicio;
        LocalDate fim;

        try {

            inicio = LocalDate.parse(
                parametros.getOrDefault(
                    "inicio", ""
                )
            );

            fim = LocalDate.parse(
                parametros.getOrDefault(
                    "fim", ""
                )
            );

        } catch (DateTimeParseException erro) {

            throw new IllegalArgumentException(
                "As datas devem estar no formato AAAA-MM-DD."
            );
        }

        if (inicio.isAfter(fim)) {

            throw new IllegalArgumentException(
                "A data inicial nao pode ser posterior a final."
            );
        }

        // Consulta somente registros da conta
        return relatoriosService.consultaDesperdicioPorPeriodo(
            inicio,
            fim,
            usuarioId
        );
    }

    // RESPOSTAS HTTP

    private static void responder(
        HttpExchange requisicao,
        int status,
        String mensagem
    ) throws IOException {

        responderJSON(
            requisicao,
            status,
            new Document(
                "mensagem", mensagem
            )
        );
    }

    // ENVIAR RESPOSTA JSON

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
