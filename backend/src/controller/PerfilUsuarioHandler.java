// Código feito por Lucas David de Sousa RA: 25895152

package controller;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoException;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;

import org.bson.Document;
import org.bson.types.ObjectId;

import service.PerfilUsuarioService;
import service.SessaoService;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.NoSuchElementException;
import java.util.Set;

public class PerfilUsuarioHandler implements HttpHandler {

    // ORIGENS PERMITIDAS

    private static final Set<String> ORIGENS = Set.of(
        "http://localhost:5500",
        "http://127.0.0.1:5500"
    );

    // SERVIÇOS

    private final SessaoService sessaoService;
    private final PerfilUsuarioService perfilUsuarioService;

    // CONSTRUTOR

    public PerfilUsuarioHandler(
        MongoCollection<Document> usuarios,
        MongoCollection<Document> sessoes
    ) {

        this.sessaoService = new SessaoService(
            usuarios,
            sessoes
        );

        this.perfilUsuarioService = new PerfilUsuarioService(
            usuarios,
            sessaoService
        );
    }

    // REQUISIÇÕES HTTP

    @Override
    public void handle(HttpExchange requisicao)
            throws IOException {

        String origem = requisicao.getRequestHeaders()
            .getFirst("Origin");

        // VERIFICAR ORIGEM

        if (origem != null && !ORIGENS.contains(origem)) {
            responder(
                requisicao,
                403,
                "Origem não permitida."
            );
            return;
        }

        // CONFIGURAÇÃO DE CORS

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
                "GET, PATCH, OPTIONS"
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
        String rota = requisicao.getRequestURI().getPath();

        // VALIDAR ROTA

        if (
            !"/api/perfil".equals(rota) &&
            !"/api/perfil/senha".equals(rota)
        ) {
            responder(
                requisicao,
                404,
                "Rota não encontrada."
            );
            return;
        }

        // REQUISIÇÃO OPTIONS

        if ("OPTIONS".equals(metodo)) {
            requisicao.sendResponseHeaders(204, -1);
            requisicao.close();
            return;
        }

        // VALIDAR MÉTODO

        boolean buscarPerfil =
            "/api/perfil".equals(rota) &&
            "GET".equals(metodo);

        boolean alterarDados =
            "PATCH".equals(metodo);

        if (!buscarPerfil && !alterarDados) {

            requisicao.getResponseHeaders().set(
                "Allow",
                "/api/perfil".equals(rota)
                    ? "GET, PATCH, OPTIONS"
                    : "PATCH, OPTIONS"
            );

            responder(
                requisicao,
                405,
                "Método não permitido."
            );
            return;
        }

        try {

            // AUTENTICAÇÃO

            // Identifica o usuário pelo token da sessão
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

            ObjectId usuarioId =
                usuario.getObjectId("_id");

            // CONSULTAR PERFIL

            // Não recebe o ID do frontend
            // O ID é obtido diretamente da sessão
            if (buscarPerfil) {

                Document perfil = new Document()
                    .append(
                        "id",
                        usuarioId.toHexString()
                    )
                    .append(
                        "nome",
                        usuario.getString("nome")
                    )
                    .append(
                        "email",
                        usuario.getString("email")
                    )
                    .append(
                        "celular",
                        usuario.getString("celular")
                    )
                    .append(
                        "cargo",
                        usuario.getString("cargo")
                    );

                responderJSON(
                    requisicao,
                    200,
                    perfil
                );

                return;
            }

            // LER DADOS ENVIADOS

            Document dados = lerCorpo(requisicao);

            if (dados == null) {
                responder(
                    requisicao,
                    400,
                    "JSON inválido ou maior que 8 KB."
                );
                return;
            }

            Document resposta;

            // ALTERAR SENHA

            if ("/api/perfil/senha".equals(rota)) {

                resposta = perfilUsuarioService.alterarSenha(
                    usuario,
                    dados
                );

            } else {

                // ALTERAR E-MAIL OU CELULAR

                resposta = perfilUsuarioService.atualizarDados(
                    usuario,
                    dados
                );
            }

            responderJSON(
                requisicao,
                200,
                resposta
            );

        } catch (IllegalArgumentException erro) {

            responder(
                requisicao,
                400,
                erro.getMessage()
            );

        } catch (NoSuchElementException erro) {

            responder(
                requisicao,
                404,
                erro.getMessage()
            );

        } catch (MongoWriteException erro) {

            // E-MAIL DUPLICADO

            if (
                erro.getError().getCategory()
                    == ErrorCategory.DUPLICATE_KEY
            ) {
                responder(
                    requisicao,
                    409,
                    "Este e-mail já está cadastrado."
                );
            } else {
                responder(
                    requisicao,
                    500,
                    "Não foi possível salvar a alteração."
                );
            }

        } catch (MongoException erro) {

            System.err.println(
                "Erro MongoDB no perfil: " +
                erro.getMessage()
            );

            responder(
                requisicao,
                500,
                "Erro ao acessar o banco de dados."
            );

        } catch (RuntimeException erro) {

            System.err.println(
                "Erro no perfil: " +
                erro.getMessage()
            );

            responder(
                requisicao,
                500,
                "Erro ao processar a solicitação."
            );
        }
    }

    // LER CORPO DA REQUISIÇÃO

    private static Document lerCorpo(
        HttpExchange requisicao
    ) throws IOException {

        byte[] conteudo = requisicao.getRequestBody()
            .readNBytes(8193);

        if (conteudo.length > 8192) {
            return null;
        }

        try {
            return Document.parse(
                new String(
                    conteudo,
                    StandardCharsets.UTF_8
                )
            );
        } catch (RuntimeException erro) {
            return null;
        }
    }

    // RESPOSTA COM MENSAGEM

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

        try (OutputStream saida =
                requisicao.getResponseBody()) {

            saida.write(resposta);
        }
    }
}
