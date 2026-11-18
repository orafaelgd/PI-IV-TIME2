package controller;

import service.ProdutosService;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import com.mongodb.MongoException;
import com.mongodb.client.MongoCollection;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import org.bson.Document;
import org.bson.types.ObjectId;

public class ProdutosHandler implements HttpHandler {

    // Service responsável pelas operações dos produtos.
    private final ProdutosService service;

    // Origens autorizadas a acessar a API.
    private static final Set<String> ORIGENS = Set.of(
        "http://localhost:5500",
        "http://127.0.0.1:5500"
    );

    // Construtor: recebe as coleções do MongoDB.
    // Mantém os mesmos parâmetros do Handler original.
    public ProdutosHandler(
        MongoCollection<Document> itens,
        MongoCollection<Document> categorias,
        MongoCollection<Document> lotes,
        MongoCollection<Document> movimentacoes
    ) {
        this.service = new ProdutosService(
            itens, categorias, lotes, movimentacoes
        );
    }

    // Recebe as requisições HTTP do frontend.
    @Override
    public void handle(HttpExchange req) throws IOException {

        String origem = req.getRequestHeaders()
            .getFirst("Origin");

        // Impede requisições de origens não autorizadas.
        if (origem != null && !ORIGENS.contains(origem)) {
            responder(req, 403, "Origem nao permitida.");
            return;
        }

        // Configuração de CORS.
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

        // Responde às requisições de pré-verificação CORS.
        if ("OPTIONS".equals(metodo)) {
            req.sendResponseHeaders(204, -1);
            req.close();
            return;
        }

        try {

            // ROTA: listar categorias.
            if (caminho.equals("/api/categorias")) {

                if (metodo.equals("GET")) {
                    responderJSON(
                        req, 200, service.listarCategorias()
                    );
                } else {
                    responder(req, 405, "Metodo nao permitido.");
                }
                return;
            }

            // ROTAS: listar e cadastrar produtos.
            if (caminho.equals("/api/produtos")) {

                switch (metodo) {

                    case "GET":
                        responderJSON(
                            req, 200, service.listarProdutos()
                        );
                        break;

                    case "POST":
                        Document cadastro = lerDados(req);

                        responderJSON(
                            req, 201,
                            service.cadastrarProduto(cadastro)
                        );
                        break;

                    default:
                        responder(req, 405, "Metodo nao permitido.");
                }
                return;
            }

            // ROTAS: operações com um produto específico.
            if (caminho.startsWith("/api/produtos/")) {

                String restante = caminho.substring(
                    "/api/produtos/".length()
                );

                String[] partes = restante.split("/", -1);

                // Valida o ID recebido na URL.
                if (partes.length < 1 ||
                    partes.length > 2 ||
                    !ObjectId.isValid(partes[0])) {

                    responder(req, 400, "ID do produto invalido.");
                    return;
                }

                ObjectId id = new ObjectId(partes[0]);

                // /api/produtos/{id}
                if (partes.length == 1) {

                    switch (metodo) {

                        case "GET":
                            responderJSON(
                                req, 200,
                                service.buscarProduto(id)
                            );
                            break;

                        case "PUT":
                            Document alteracoes = lerDados(req);

                            service.atualizarProduto(
                                id, alteracoes
                            );

                            responder(
                                req, 200,
                                "Produto atualizado com sucesso!"
                            );
                            break;

                        case "DELETE":
                            service.excluirProduto(id);

                            responder(
                                req, 200,
                                "Produto excluido com sucesso!"
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

                // Verifica se o produto pode ser excluído.
                if (partes[1].equals("exclusao") &&
                    metodo.equals("GET")) {

                    responderJSON(
                        req, 200,
                        service.verificarExclusao(id)
                    );
                    return;
                }

                // Altera o status do produto.
                if (partes[1].equals("status") &&
                    metodo.equals("PATCH")) {

                    Document dados = lerDados(req);

                    String mensagem = service.alterarStatus(
                        id, dados
                    );

                    responder(req, 200, mensagem);
                    return;
                }
            }

            // Rota não encontrada.
            responder(req, 404, "Rota nao encontrada.");

        } catch (ProdutosService.ProdutoNaoEncontradoException erro) {

            responder(req, 404, erro.getMessage());

        } catch (ProdutosService.ConflitoExclusaoException erro) {

            responderJSON(
                req, 409,
                new Document()
                    .append("podeExcluir", false)
                    .append("mensagem", erro.getMessage())
            );

        } catch (IllegalArgumentException erro) {

            responder(req, 400, erro.getMessage());

        } catch (MongoException erro) {

            System.err.println(
                "Erro MongoDB: " + erro.getMessage()
            );

            responder(req, 500, "Erro ao acessar o banco.");

        } catch (RuntimeException erro) {

            System.err.println(
                "Erro interno: " + erro.getMessage()
            );

            responder(
                req, 500, "Erro interno do servidor."
            );
        }
    }

    // Lê o JSON recebido do frontend.
    private Document lerDados(HttpExchange req)
        throws IOException {

        byte[] conteudo = req.getRequestBody()
            .readNBytes(8193);

        // Limita o tamanho do JSON recebido.
        if (conteudo.length > 8192) {
            throw new IllegalArgumentException(
                "Dados muito grandes."
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

    // Envia uma mensagem ao frontend em formato JSON.
    private static void responder(
        HttpExchange req,
        int status,
        String mensagem
    ) throws IOException {

        responderJSON(
            req, status,
            new Document("mensagem", mensagem)
        );
    }

    // Envia documentos JSON ao frontend.
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
