// Código feito por Miriã Nascimento dos Anjos RA: 25892704

package controller;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import service.LoginService;
import service.SessaoService;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

public class LoginHandler implements HttpHandler {
    private final LoginService loginService;
    private final SessaoService sessaoService;

    // Define os endereços autorizados a realizar requisições
    private static final Set<String> ORIGENS = Set.of(
            "http://localhost:5500",
            "http://127.0.0.1:5500"
    );

    // Inicializa os serviços responsáveis pelo login e pelas sessões
    public LoginHandler(MongoCollection<Document> usuarios, MongoCollection<Document> sessoes) {
        this.loginService = new LoginService(usuarios, sessoes);
        this.sessaoService = new SessaoService(usuarios, sessoes);
    }

    // Recebe as requisições e direciona cada uma para sua funcionalidade
    @Override
    public void handle(HttpExchange req) throws IOException {
        req.getResponseHeaders().set("Cache-Control", "no-store");
        String origem = req.getRequestHeaders().getFirst("Origin");

        // Verifica a origem da requisição antes de permitir o acesso
        if (origem != null && !ORIGENS.contains(origem)) {
            responder(req, 403, "Origem não permitida.");
            return;
        }

        // Configura a comunicação entre o frontend e o backend
        if (origem != null) {
            req.getResponseHeaders().set("Access-Control-Allow-Origin", origem);
            req.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
            req.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            req.getResponseHeaders().set("Vary", "Origin");
        }

        String caminho = req.getRequestURI().getPath();
        String metodo = req.getRequestMethod();

        // Confere se a rota solicitada existe
        if (!Set.of("/api/login", "/api/logout", "/api/usuario").contains(caminho)) {
            responder(req, 404, "Rota não encontrada.");
            return;
        }

        // Responde às requisições OPTIONS utilizadas na verificação do CORS
        if ("OPTIONS".equals(metodo)) {
            req.sendResponseHeaders(204, -1);
            req.close();
            return;
        }

        // Verifica se o método HTTP é permitido para a rota solicitada
        if ((caminho.equals("/api/usuario") && !metodo.equals("GET"))
                || (!caminho.equals("/api/usuario") && !metodo.equals("POST"))) {
            req.getResponseHeaders().set("Allow", caminho.equals("/api/usuario") ? "GET, OPTIONS" : "POST, OPTIONS");
            responder(req, 405, "Método não permitido.");
            return;
        }

        // Encaminha a requisição para login, logout ou consulta do usuário
        try {
            switch (caminho) {
                case "/api/login" -> fazerLogin(req);
                case "/api/logout" -> fazerLogout(req);
                case "/api/usuario" -> consultarUsuario(req);
                default -> responder(req, 404, "Rota não encontrada.");
            }
        } catch (RuntimeException erro) {
            System.err.println("Falha no serviço de autenticação: " + erro.getClass().getSimpleName());
            responder(req, 500, "Erro ao acessar o serviço.");
        }
    }

    // Recebe os dados do formulário e realiza a autenticação do usuário
    private void fazerLogin(HttpExchange req) throws IOException {

        // Limita o tamanho dos dados recebidos na requisição
        byte[] conteudo = req.getRequestBody().readNBytes(8193);
        if (conteudo.length > 8192) {
            responder(req, 413, "Requisição muito grande.");
            return;
        }

        // Converte os dados recebidos para um documento JSON
        Document corpo;
        try {
            corpo = Document.parse(new String(conteudo, StandardCharsets.UTF_8));
        } catch (RuntimeException erro) {
            responder(req, 400, "JSON inválido.");
            return;
        }

        // Verifica se o e-mail e a senha foram enviados como texto
        Object valorEmail = corpo.get("email");
        Object valorSenha = corpo.get("senha");
        if (!(valorEmail instanceof String) || !(valorSenha instanceof String)) {
            responder(req, 400, "Informe e-mail e senha válidos.");
            return;
        }

        // Valida os dados antes de consultar o serviço de login
        String email = ((String) valorEmail).strip();
        String senha = (String) valorSenha;
        if (email.isBlank() || email.length() > 150 || !email.contains("@")
                || senha.isEmpty() || senha.length() > 1000) {
            responder(req, 400, "Informe e-mail e senha válidos.");
            return;
        }

        // Confere as credenciais e retorna os dados da sessão quando o login é válido
        Document resultado = loginService.entrar(email, senha);
        if (resultado == null) {
            responder(req, 401, "E-mail ou senha incorretos.");
            return;
        }
        responderJSON(req, 200, resultado);
    }

    // Encerra a sessão do usuário autenticado
    private void fazerLogout(HttpExchange req) throws IOException {
        Document usuario = sessaoService.autenticar(req);
        if (usuario == null) {
            responder(req, 401, "Não autenticado.");
            return;
        }

        loginService.sair(SessaoService.extrairToken(req));
        responder(req, 200, "Sessão encerrada.");
    }

    // Consulta os dados públicos do usuário que está logado
    private void consultarUsuario(HttpExchange req) throws IOException {
        Document usuario = sessaoService.autenticar(req);
        if (usuario == null) {
            responder(req, 401, "Não autenticado.");
            return;
        }

        responderJSON(req, 200, LoginService.dadosPublicos(usuario));
    }

    // Prepara uma mensagem de resposta no formato JSON
    private static void responder(HttpExchange req, int status, String mensagem) throws IOException {
        responderJSON(req, status, new Document("mensagem", mensagem));
    }

    // Envia os dados em JSON com o código de resposta HTTP correspondente
    private static void responderJSON(HttpExchange req, int status, Document dados) throws IOException {
        byte[] bytes = dados.toJson().getBytes(StandardCharsets.UTF_8);
        req.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        req.sendResponseHeaders(status, bytes.length);

        try (OutputStream saida = req.getResponseBody()) {
            saida.write(bytes);
        }
    }
}
