
package controller;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import com.mongodb.MongoException;
import com.mongodb.MongoWriteException;
import com.mongodb.ErrorCategory;
import com.mongodb.client.MongoCollection;

import org.bson.Document;
import org.bson.types.ObjectId;

import service.LotesService;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

public class LotesHandler implements HttpHandler {

    // Service responsável pelas operações dos lotes
    private final LotesService service;

    // Origens autorizadas a acessar a API
    private static final Set<String> ORIGENS = Set.of(
        "http://localhost:5500",
        "http://127.0.0.1:5500"
    );

    // CONSTRUTOR

    // Recebe as coleções do MongoDB e cria o Service
    public LotesHandler(
        MongoCollection<Document> lotes,
        MongoCollection<Document> itens,
        MongoCollection<Document> movimentacoes
    ) {
        this.service = new LotesService(
            lotes, itens, movimentacoes
        );
    }

    // ROTAS HTTP

    // Recebe as requisições enviadas pelo frontend
    @Override
    public void handle(HttpExchange req) throws IOException {

        String origem = req.getRequestHeaders()
            .getFirst("Origin");

        // Verifica se a origem tem permissão de acesso
        if (origem != null && !ORIGENS.contains(origem)) {
            responder(req, 403, "Origem nao permitida.");
            return;
        }

        // Configura as permissões de acesso da API
        if (origem != null) {

            req.getResponseHeaders().set(
                "Access-Control-Allow-Origin", origem
            );

            req.getResponseHeaders().set(
                "Access-Control-Allow-Headers", "Content-Type"
            );

            req.getResponseHeaders().set(
                "Access-Control-Allow-Methods",
                "GET, POST, PUT, PATCH, DELETE, OPTIONS"
            );

            req.getResponseHeaders().set("Vary", "Origin");
        }

        req.getResponseHeaders().set(
            "Cache-Control", "no-store"
        );

        String metodo = req.getRequestMethod();
        String caminho = req.getRequestURI().getPath();

        // Responde à verificação de CORS
        if (metodo.equals("OPTIONS")) {
            req.sendResponseHeaders(204, -1);
            req.close();
            return;
        }

        try {

            // LISTAR E CADASTRAR LOTES

            if (caminho.equals("/api/lotes")) {

                switch (metodo) {

                    case "GET":
                        responderJSON(
                            req, 200, service.listarLotes()
                        );
                        break;

                    case "POST":
                        Document cadastro = lerDados(req);

                        responderJSON(
                            req, 201,
                            service.cadastrarLote(cadastro)
                        );
                        break;

                    default:
                        responder(
                            req, 405, "Metodo nao permitido."
                        );
                }

                return;
            }

            // ROTAS COM ID

            if (caminho.startsWith("/api/lotes/")) {

                String restante = caminho.substring(
                    "/api/lotes/".length()
                );

                String[] partes = restante.split("/", -1);

                // Valida o ID informado na URL
                if (
                    partes.length < 1 ||
                    partes.length > 2 ||
                    !ObjectId.isValid(partes[0])
                ) {
                    responder(
                        req, 400, "ID do lote invalido."
                    );
                    return;
                }

                ObjectId id = new ObjectId(partes[0]);

                // BUSCAR, ATUALIZAR E EXCLUIR LOTE

                if (partes.length == 1) {

                    switch (metodo) {

                        case "GET":
                            responderJSON(
                                req, 200, service.buscarLote(id)
                            );
                            break;

                        case "PUT":
                            Document alteracoes = lerDados(req);

                            service.atualizarLote(id, alteracoes);

                            responder(
                                req, 200,
                                "Lote atualizado com sucesso!"
                            );
                            break;

                        case "DELETE":
                            service.excluirLote(id);

                            responder(
                                req, 200,
                                "Lote excluido com sucesso!"
                            );
                            break;

                        default:
                            responder(
                                req, 405,
                                "Metodo nao permitido."
                            );
                    }

                    return;
                }

                // VERIFICAR EXCLUSÃO

                if (
                    partes[1].equals("exclusao") &&
                    metodo.equals("GET")
                ) {
                    responderJSON(
                        req, 200,
                        service.verificarExclusao(id)
                    );
                    return;
                }

                // INATIVAR OU REATIVAR LOTE

                if (
                    partes[1].equals("status") &&
                    metodo.equals("PATCH")
                ) {
                    Document dados = lerDados(req);

                    String mensagem = service.alterarStatus(
                        id, dados
                    );

                    responder(req, 200, mensagem);
                    return;
                }
            }

            // Retorna erro quando a rota não existe
            responder(req, 404, "Rota nao encontrada.");

        } catch (LotesService.LoteNaoEncontradoException erro) {

            responder(req, 404, erro.getMessage());

        } catch (LotesService.ConflitoLoteException erro) {

            responderJSON(
                req, 409, erro.getResposta()
            );

        } catch (IllegalArgumentException erro) {

            responder(req, 400, erro.getMessage());

        } catch (MongoWriteException erro) {

            // Trata códigos duplicados no banco
            if (
                erro.getError().getCategory() ==
                ErrorCategory.DUPLICATE_KEY
            ) {
                responder(
                    req, 409, "Codigo de lote ja cadastrado."
                );
            } else {
                System.err.println(
                    "Erro ao salvar lote: " + erro.getMessage()
                );

                responder(
                    req, 500, "Erro ao salvar no MongoDB."
                );
            }

        } catch (MongoException erro) {

            System.err.println(
                "Erro MongoDB: " + erro.getMessage()
            );

            responder(
                req, 500, "Erro ao acessar o banco de dados."
            );

        } catch (RuntimeException erro) {

            System.err.println(
                "Erro interno: " + erro.getMessage()
            );

            responder(
                req, 500, "Erro interno do servidor."
            );
        }
    }

    // LEITURA DOS DADOS

    // Lê o JSON enviado pelo frontend
    private Document lerDados(HttpExchange req)
        throws IOException {

        byte[] conteudo = req.getRequestBody()
            .readNBytes(8193);

        // Limita o tamanho dos dados recebidos
        if (conteudo.length > 8192) {
            throw new IllegalArgumentException(
                "Dados enviados sao muito grandes."
            );
        }

        try {
            return Document.parse(
                new String(
                    conteudo, StandardCharsets.UTF_8
                )
            );
        } catch (RuntimeException erro) {
            throw new IllegalArgumentException(
                "JSON invalido."
            );
        }
    }

    // RESPOSTAS HTTP

    // Retorna uma mensagem em formato JSON
    private static void responder(
        HttpExchange req,
        int status,
        String mensagem
    ) throws IOException {

        responderJSON(
            req, status, new Document("mensagem", mensagem)
        );
    }

    // Envia os dados e o código HTTP ao frontend
    private static void responderJSON(
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
            status, resposta.length
        );

        try (OutputStream saida = req.getResponseBody()) {
            saida.write(resposta);
        }
    }
}
