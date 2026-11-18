// Código feito por Livia Carvalho Lucizano RA: 25017514

package controller;

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

import service.ProdutosService;
import service.SessaoService;

public class ProdutosHandler implements HttpHandler {

    // SERVIÇOS

    // Gerencia as operações dos produtos
    private final ProdutosService service;

    // Verifica a sessão do usuário
    private final SessaoService sessaoService;

    // ORIGENS PERMITIDAS

    private static final Set<String> ORIGENS = Set.of(
        "http://localhost:5500",
        "http://127.0.0.1:5500"
    );

    // CONSTRUTOR

    public ProdutosHandler(
        MongoCollection<Document> itens,
        MongoCollection<Document> categorias,
        MongoCollection<Document> lotes,
        MongoCollection<Document> movimentacoes,
        MongoCollection<Document> usuarios,
        MongoCollection<Document> sessoes
    ) {

        this.service = new ProdutosService(
            itens, categorias, lotes, movimentacoes
        );

        this.sessaoService = new SessaoService(
            usuarios, sessoes
        );
    }

    // ROTAS HTTP

    @Override
    public void handle(HttpExchange req) throws IOException {

        String origem = req.getRequestHeaders()
            .getFirst("Origin");

        // Verifica se a origem é permitida
        if (origem != null && !ORIGENS.contains(origem)) {
            responder(req, 403, "Origem nao permitida.");
            return;
        }

        // CONFIGURAÇÃO DE CORS

        if (origem != null) {

            req.getResponseHeaders().set(
                "Access-Control-Allow-Origin", origem
            );

            // Permite enviar o token no cabeçalho
            req.getResponseHeaders().set(
                "Access-Control-Allow-Headers",
                "Content-Type, Authorization"
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

        // Responde à verificação CORS do navegador
        if ("OPTIONS".equals(metodo)) {
            req.sendResponseHeaders(204, -1);
            req.close();
            return;
        }

        try {

            // AUTENTICAÇÃO DO USUÁRIO

            // Confere o token no MongoDB
            Document usuario = sessaoService.autenticar(req);

            if (usuario == null) {

                responder(
                    req, 401,
                    "Sessao invalida ou expirada. Faca login novamente."
                );
                return;
            }

            // Identifica o usuário autenticado
            ObjectId usuarioId = usuario.getObjectId("_id");

            if (usuarioId == null) {
                responder(req, 401, "Usuario nao autenticado.");
                return;
            }

            // LISTAR CATEGORIAS

            if (caminho.equals("/api/categorias")) {

                if ("GET".equals(metodo)) {

                    responderJSON(
                        req, 200,
                        service.listarCategorias(usuarioId)
                    );

                } else {
                    responder(req, 405, "Metodo nao permitido.");
                }
                return;
            }

            // LISTAR E CADASTRAR PRODUTOS

            if (caminho.equals("/api/produtos")) {

                switch (metodo) {

                    case "GET":

                        responderJSON(
                            req, 200,
                            service.listarProdutos(usuarioId)
                        );
                        break;

                    case "POST":

                        Document cadastro = lerDados(req);

                        responderJSON(
                            req, 201,
                            service.cadastrarProduto(
                                cadastro, usuarioId
                            )
                        );
                        break;

                    default:
                        responder(req, 405, "Metodo nao permitido.");
                }
                return;
            }

            // ROTAS COM ID DO PRODUTO

            if (caminho.startsWith("/api/produtos/")) {

                String restante = caminho.substring(
                    "/api/produtos/".length()
                );

                String[] partes = restante.split("/", -1);

                // Verifica se o ID informado é válido
                if (
                    partes.length < 1 ||
                    partes.length > 2 ||
                    !ObjectId.isValid(partes[0])
                ) {
                    responder(req, 400, "ID do produto invalido.");
                    return;
                }

                ObjectId id = new ObjectId(partes[0]);

                // BUSCAR, EDITAR E EXCLUIR PRODUTO

                if (partes.length == 1) {

                    switch (metodo) {

                        case "GET":

                            responderJSON(
                                req, 200,
                                service.buscarProduto(id, usuarioId)
                            );
                            break;

                        case "PUT":

                            Document alteracoes = lerDados(req);

                            service.atualizarProduto(
                                id, alteracoes, usuarioId
                            );

                            responder(
                                req, 200,
                                "Produto atualizado com sucesso!"
                            );
                            break;

                        case "DELETE":

                            service.excluirProduto(id, usuarioId);

                            responder(
                                req, 200,
                                "Produto excluido com sucesso!"
                            );
                            break;

                        default:
                            responder(req, 405, "Metodo nao permitido.");
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
                        service.verificarExclusao(id, usuarioId)
                    );
                    return;
                }

                // ALTERAR STATUS

                if (
                    partes[1].equals("status") &&
                    metodo.equals("PATCH")
                ) {

                    Document dados = lerDados(req);

                    String mensagem = service.alterarStatus(
                        id, dados, usuarioId
                    );

                    responder(req, 200, mensagem);
                    return;
                }
            }

            // ROTA NÃO ENCONTRADA

            responder(req, 404, "Rota nao encontrada.");

        } catch (
            ProdutosService.ProdutoNaoEncontradoException erro
        ) {

            responder(req, 404, erro.getMessage());

        } catch (
            ProdutosService.ConflitoExclusaoException erro
        ) {

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

            responder(req, 500, "Erro interno do servidor.");
        }
    }

    // LEITURA DOS DADOS

    // Recebe e converte o JSON enviado pelo frontend
    private Document lerDados(HttpExchange req)
        throws IOException {

        byte[] conteudo = req.getRequestBody()
            .readNBytes(8193);

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
            throw new IllegalArgumentException("JSON invalido.");
        }
    }

    // RESPOSTAS HTTP

    // Retorna uma mensagem ao frontend
    private static void responder(
        HttpExchange req,
        int status,
        String mensagem
    ) throws IOException {

        responderJSON(
            req, status, new Document("mensagem", mensagem)
        );
    }

    // Envia a resposta em formato JSON
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

        req.sendResponseHeaders(status, resposta.length);

        try (OutputStream saida = req.getResponseBody()) {
            saida.write(resposta);
        }
    }
}
