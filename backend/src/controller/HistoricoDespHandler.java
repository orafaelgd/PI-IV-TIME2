// Código feito por Miriã Nascimento dos Anjos RA: 25892704

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

    // Define quais endereços podem realizar requisições para esta rota
    private static final Set<String> ORIGENS = Set.of(
        "http://localhost:5500",
        "http://127.0.0.1:5500"
    );

    // Serviços utilizados para consultar o histórico e validar a sessão
    private final HistoricoDespService historicoService;
    private final SessaoService sessaoService;

    // Inicializa os serviços e as coleções necessárias do banco
    public HistoricoDespHandler(MongoDatabase banco) {

        historicoService = new HistoricoDespService(banco);

        sessaoService = new SessaoService(
            banco.getCollection("users"),
            banco.getCollection("sessoes")
        );
    }

    // Recebe as requisições e verifica se podem acessar o histórico de saídas
    @Override
    public void handle(HttpExchange requisicao)
            throws IOException {

        String origem = requisicao
            .getRequestHeaders()
            .getFirst("Origin");

        // Bloqueia requisições que vierem de origens não autorizadas
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

        // Configura os cabeçalhos CORS para permitir a comunicação com o frontend
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

        // Confere se a requisição está sendo feita para a rota correta
        if (!"/api/saidas".equals(caminho)) {

            responder(
                requisicao,
                404,
                "Rota não encontrada."
            );

            return;
        }

        // Responde às requisições OPTIONS utilizadas na verificação do CORS
        if ("OPTIONS".equals(metodo)) {

            requisicao.sendResponseHeaders(204, -1);
            requisicao.close();

            return;
        }

        // A consulta ao histórico permite apenas o método GET
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

            // Verifica o token da sessão e recupera os dados do usuário logado
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

            // Utiliza o ID da conta para consultar somente seus registros
            ObjectId usuarioId =
                usuario.getObjectId("_id");

            System.out.println(
                "HISTÓRICO DE SAÍDA - Usuário: " +
                usuarioId.toHexString()
            );

            // Recupera os filtros enviados pela URL
            Map<String, String> parametros =
                lerParametros(
                    requisicao.getRequestURI().getRawQuery()
                );

            // Converte as datas informadas para aplicar o filtro por período
            LocalDate inicio = lerData(
                parametros.get("dataInicio")
            );

            LocalDate fim = lerData(
                parametros.get("dataFim")
            );

            // Define a página e a quantidade de registros exibidos por consulta
            int pagina = Integer.parseInt(
                parametros.getOrDefault("pagina", "1")
            );

            int limite = Integer.parseInt(
                parametros.getOrDefault("limite", "20")
            );

            // Por padrão, o histórico apresenta todas as saídas registradas
            String somenteTexto = parametros.getOrDefault(
                "somenteDesperdicio",
                "false"
            );

            // Confere se o filtro de desperdício recebeu um valor válido
            if (
                !"true".equals(somenteTexto) &&
                !"false".equals(somenteTexto)
            ) {

                throw new IllegalArgumentException(
                    "Filtro somenteDesperdicio inválido."
                );
            }

            // Envia os filtros ao serviço responsável pela consulta no banco
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

            // Retorna os registros encontrados em formato JSON
            responderJSON(
                requisicao,
                200,
                resultado
            );

        } catch (IllegalArgumentException erro) {

            // Retorna erro quando algum parâmetro informado é inválido
            responder(
                requisicao,
                400,
                erro.getMessage()
            );

        } catch (MongoException erro) {

            // Trata possíveis falhas de comunicação com o MongoDB
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

            // Trata erros inesperados durante a consulta
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

    // Separa os parâmetros da URL e impede que um mesmo parâmetro seja enviado duas vezes
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

    // Converte a data recebida e verifica se está no formato esperado
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

    // Monta uma resposta de erro ou aviso com a mensagem informada
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

    // Envia a resposta em JSON com o status HTTP correspondente
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
