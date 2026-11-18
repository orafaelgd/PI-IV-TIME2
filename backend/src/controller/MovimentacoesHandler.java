
package controller;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import com.mongodb.MongoException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;

import org.bson.Document;
import org.bson.types.ObjectId;

import service.MovimentacoesService;
import service.SessaoService;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

public class MovimentacoesHandler implements HttpHandler {

    // ORIGENS PERMITIDAS

    private static final Set<String> ORIGENS = Set.of(
        "http://localhost:5500",
        "http://127.0.0.1:5500"
    );

    // SERVIÇOS

    private final MovimentacoesService movimentacoesService;
    private final SessaoService sessaoService;

    // CONSTRUTOR

    public MovimentacoesHandler(
        MongoClient cliente,
        MongoCollection<Document> lotes,
        MongoCollection<Document> movimentacoes,
        MongoCollection<Document> produtos,
        MongoCollection<Document> usuarios,
        MongoCollection<Document> sessoes
    ) {

        this.movimentacoesService =
            new MovimentacoesService(
                cliente,
                lotes,
                movimentacoes,
                produtos
            );

        this.sessaoService =
            new SessaoService(
                usuarios,
                sessoes
            );
    }

    // ATENDER REQUISIÇÕES

    @Override
    public void handle(HttpExchange requisicao)
            throws IOException {

        // VERIFICAR ORIGEM

        String origem = requisicao
            .getRequestHeaders()
            .getFirst("Origin");

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
                "Content-Type, Authorization"
            );

            requisicao.getResponseHeaders().set(
                "Access-Control-Allow-Methods",
                "POST, OPTIONS"
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

        String metodo =
            requisicao.getRequestMethod();

        String rota =
            requisicao.getRequestURI().getPath();

        // VALIDAR ROTA

        if (!"/api/movimentacoes".equals(rota)) {

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

        // PERMITIR SOMENTE POST

        if (!"POST".equals(metodo)) {

            requisicao.getResponseHeaders().set(
                "Allow",
                "POST, OPTIONS"
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

            // Verifica o token enviado pelo frontend
            Document usuario =
                sessaoService.autenticar(
                    requisicao
                );

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

            // RECUPERAR ID DO USUÁRIO LOGADO

            ObjectId usuarioId =
                usuario.getObjectId("_id");

            // LER CORPO DA REQUISIÇÃO

            byte[] conteudo =
                requisicao.getRequestBody()
                    .readNBytes(8193);

            // LIMITE DE 8 KB

            if (conteudo.length > 8192) {

                responder(
                    requisicao,
                    413,
                    "Requisição muito grande."
                );

                return;
            }

            // CONVERTER JSON

            Document dados;

            try {

                dados = Document.parse(
                    new String(
                        conteudo,
                        StandardCharsets.UTF_8
                    )
                );

            } catch (RuntimeException erro) {

                responder(
                    requisicao,
                    400,
                    "JSON inválido."
                );

                return;
            }

            // REGISTRAR MOVIMENTAÇÃO

            // O Service executa as validações
            // e as operações no MongoDB
            MovimentacoesService.Resultado resultado =
                movimentacoesService.registrarSaida(
                    dados,
                    usuarioId
                );

            // RETORNAR RESULTADO

            responder(
                requisicao,
                resultado.getStatus(),
                resultado.getMensagem()
            );

        } catch (IllegalArgumentException erro) {

            // DADOS INVÁLIDOS

            responder(
                requisicao,
                400,
                "Dados inválidos: " +
                erro.getMessage()
            );

        } catch (MongoException erro) {

            // ERRO DO MONGODB

            System.err.println(
                "Erro MongoDB em movimentações: " +
                erro.getMessage()
            );

            responder(
                requisicao,
                503,
                "Não foi possível confirmar a operação."
            );

        } catch (RuntimeException erro) {

            // ERRO INESPERADO

            System.err.println(
                "Erro ao registrar movimentação: " +
                erro.getMessage()
            );

            responder(
                requisicao,
                500,
                "Erro interno ao processar a movimentação."
            );
        }
    }

    // ENVIAR RESPOSTA JSON

    private static void responder(
        HttpExchange requisicao,
        int status,
        String mensagem
    ) throws IOException {

        Document resposta = new Document(
            "mensagem",
            mensagem
        );

        byte[] conteudo = resposta.toJson()
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
            conteudo.length
        );

        try (
            OutputStream saida =
                requisicao.getResponseBody()
        ) {

            saida.write(conteudo);
        }
    }
}
