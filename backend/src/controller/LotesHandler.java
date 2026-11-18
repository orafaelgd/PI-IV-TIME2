// Código feito por Livia Carvalho Lucizano RA: 25017514

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
import service.SessaoService;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

public class LotesHandler implements HttpHandler {

    // SERVIÇOS

    private final LotesService service;
    private final SessaoService sessaoService;

    // ORIGENS PERMITIDAS

    private static final Set<String> ORIGENS = Set.of(
        "http://localhost:5500",
        "http://127.0.0.1:5500"
    );

    // CONSTRUTOR

    // Recebe as coleções e cria os Services
    public LotesHandler(
        MongoCollection<Document> lotes,
        MongoCollection<Document> itens,
        MongoCollection<Document> movimentacoes,
        MongoCollection<Document> usuarios,
        MongoCollection<Document> sessoes
    ) {

        this.service = new LotesService(
            lotes, itens, movimentacoes
        );

        this.sessaoService = new SessaoService(
            usuarios, sessoes
        );
    }

    // REQUISIÇÕES HTTP

    @Override
    public void handle(HttpExchange req) throws IOException {

        String origem = req.getRequestHeaders()
            .getFirst("Origin");

        // Bloqueia origens não permitidas
        if (origem != null && !ORIGENS.contains(origem)) {
            responder(req, 403, "Origem nao permitida.");
            return;
        }

        // CONFIGURAÇÃO DE CORS

        if (origem != null) {

            req.getResponseHeaders().set(
                "Access-Control-Allow-Origin", origem
            );

            req.getResponseHeaders().set(
                "Access-Control-Allow-Headers",
                "Content-Type, Authorization"
            );

            req.getResponseHeaders().set(
                "Access-Control-Allow-Methods",
                "GET, POST, PUT, PATCH, DELETE, OPTIONS"
            );

            req.getResponseHeaders().set(
                "Vary", "Origin"
            );
        }

        req.getResponseHeaders().set(
            "Cache-Control", "no-store"
        );

        String metodo = req.getRequestMethod();
        String caminho = req.getRequestURI().getPath();

        // REQUISIÇÃO OPTIONS

        if ("OPTIONS".equals(metodo)) {
            req.sendResponseHeaders(204, -1);
            req.close();
            return;
        }

        try {

            // AUTENTICAÇÃO DO USUÁRIO

            // Consulta a sessão no MongoDB
            Document usuario = sessaoService.autenticar(req);

            if (
                usuario == null ||
                usuario.getObjectId("_id") == null
            ) {

                responder(
                    req, 401,
                    "Sessao invalida ou expirada. Faca login novamente."
                );

                return;
            }

            // Obtém o ID do usuário autenticado
            ObjectId usuarioId =
                usuario.getObjectId("_id");

            // LISTAR E CADASTRAR LOTES

            if (caminho.equals("/api/lotes")) {

                switch (metodo) {

                    case "GET":

                        responderJSON(
                            req, 200,
                            service.listarLotes(usuarioId)
                        );
                        break;

                    case "POST":

                        Document cadastro = lerDados(req);

                        responderJSON(
                            req, 201,
                            service.cadastrarLote(
                                cadastro, usuarioId
                            )
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

            // ROTAS COM ID

            if (caminho.startsWith("/api/lotes/")) {

                String restante = caminho.substring(
                    "/api/lotes/".length()
                );

                String[] partes = restante.split("/", -1);

                // Valida o ID recebido
                if (
                    partes.length < 1 ||
                    partes.length > 2 ||
                    !ObjectId.isValid(partes[0])
                ) {

                    responder(
                        req, 400,
                        "ID do lote invalido."
                    );

                    return;
                }

                ObjectId id = new ObjectId(partes[0]);

                // BUSCAR, EDITAR E EXCLUIR LOTE

                if (partes.length == 1) {

                    switch (metodo) {

                        case "GET":

                            responderJSON(
                                req, 200,
                                service.buscarLote(
                                    id, usuarioId
                                )
                            );
                            break;

                        case "PUT":

                            Document alteracoes = lerDados(req);

                            service.atualizarLote(
                                id, alteracoes, usuarioId
                            );

                            responder(
                                req, 200,
                                "Lote atualizado com sucesso!"
                            );
                            break;

                        case "DELETE":

                            service.excluirLote(
                                id, usuarioId
                            );

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
                        service.verificarExclusao(
                            id, usuarioId
                        )
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

            responder(
                req, 404,
                "Rota nao encontrada."
            );

        } catch (
            LotesService.LoteNaoEncontradoException erro
        ) {

            responder(req, 404, erro.getMessage());

        } catch (
            LotesService.ConflitoLoteException erro
        ) {

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
                    req, 409,
                    "Codigo de lote ja cadastrado."
                );

            } else {

                System.err.println(
                    "Erro ao salvar lote: " + erro.getMessage()
                );

                responder(
                    req, 500,
                    "Erro ao salvar no MongoDB."
                );
            }

        } catch (MongoException erro) {

            System.err.println(
                "Erro MongoDB: " + erro.getMessage()
            );

            responder(
                req, 500,
                "Erro ao acessar o banco de dados."
            );

        } catch (RuntimeException erro) {

            System.err.println(
                "Erro interno: " + erro.getMessage()
            );

            responder(
                req, 500,
                "Erro interno do servidor."
            );
        }
    }

    // LEITURA DOS DADOS

    // Converte o JSON recebido do frontend
    private Document lerDados(HttpExchange req)
        throws IOException {

        byte[] conteudo = req.getRequestBody()
            .readNBytes(8193);

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

    // Envia uma mensagem JSON ao frontend
    private static void responder(
        HttpExchange req,
        int status,
        String mensagem
    ) throws IOException {

        responderJSON(
            req,
            status,
            new Document("mensagem", mensagem)
        );
    }

    // Envia os dados da resposta
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
