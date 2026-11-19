
package controller;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import com.mongodb.MongoException;
import com.mongodb.client.MongoCollection;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.bson.json.JsonMode;
import org.bson.json.JsonWriterSettings;

import service.ProximosVencimentoService;
import service.SessaoService;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;

public class ProximosVencimentoHandler implements HttpHandler {

    // =====================================================
    // CONFIGURAÇÕES DE ACESSO E FILTROS
    // Define as origens autorizadas a acessar a API
    // e os períodos disponíveis para consulta.
    // =====================================================

    private static final Set<String> ORIGENS = Set.of(
        "http://localhost:5500",
        "http://127.0.0.1:5500"
    );

    private static final Set<Integer> PERIODOS_VALIDOS =
        Set.of(1, 3, 7, 15, 30);

    // =====================================================
    // SERVIÇOS UTILIZADOS PELO CONTROLLER
    // O serviço de vencimentos executa as consultas.
    // O serviço de sessão autentica o usuário.
    // =====================================================

    private final ProximosVencimentoService vencimentoService;
    private final SessaoService sessaoService;

    // =====================================================
    // CONSTRUTOR
    // Recebe as coleções do MongoDB e inicializa
    // os serviços necessários para a funcionalidade.
    // =====================================================

    public ProximosVencimentoHandler(
        MongoCollection<Document> lotes,
        MongoCollection<Document> produtos,
        MongoCollection<Document> usuarios,
        MongoCollection<Document> sessoes
    ) {

        this.vencimentoService =
            new ProximosVencimentoService(
                lotes,
                produtos
            );

        this.sessaoService =
            new SessaoService(
                usuarios,
                sessoes
            );
    }

    // =====================================================
    // RECEBIMENTO DAS REQUISIÇÕES HTTP
    // Controla o acesso à API, verifica a origem,
    // valida a rota e permite apenas os métodos
    // utilizados pela funcionalidade.
    // =====================================================

    @Override
    public void handle(HttpExchange requisicao)
            throws IOException {

        String origem = requisicao
            .getRequestHeaders()
            .getFirst("Origin");

        // VERIFICAÇÃO DA ORIGEM DA REQUISIÇÃO

        if (
            origem != null &&
            !ORIGENS.contains(origem)
        ) {

            responder(
                requisicao,
                403,
                new Document(
                    "erro",
                    "Origem não permitida."
                )
            );

            return;
        }

        // CONFIGURAÇÃO DE CORS
        // Permite que o frontend local realize requisições
        // e envie o token de autenticação.

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

        // VALIDAÇÃO DA ROTA
        // Garante que a requisição seja destinada
        // ao endpoint de próximos vencimentos.

        String rota = requisicao
            .getRequestURI()
            .getPath();

        if (
            !"/api/lotes/proximos-vencimento".equals(rota)
        ) {

            responder(
                requisicao,
                404,
                new Document(
                    "erro",
                    "Rota não encontrada."
                )
            );

            return;
        }

        // TRATAMENTO DO MÉTODO OPTIONS
        // Responde às verificações de CORS realizadas
        // pelo navegador antes de determinadas requisições.

        String metodo =
            requisicao.getRequestMethod();

        if ("OPTIONS".equals(metodo)) {

            requisicao.sendResponseHeaders(204, -1);
            requisicao.close();

            return;
        }

        // VALIDAÇÃO DO MÉTODO HTTP
        // A consulta de vencimentos aceita apenas GET.

        if (!"GET".equals(metodo)) {

            requisicao.getResponseHeaders().set(
                "Allow",
                "GET, OPTIONS"
            );

            responder(
                requisicao,
                405,
                new Document(
                    "erro",
                    "Método não permitido."
                )
            );

            return;
        }

        try {

            // =================================================
            // AUTENTICAÇÃO DO USUÁRIO
            // Verifica a sessão pelo token enviado
            // e impede consultas sem autenticação válida.
            // =================================================

            Document usuario =
                sessaoService.autenticar(requisicao);

            if (
                usuario == null ||
                usuario.getObjectId("_id") == null
            ) {

                responder(
                    requisicao,
                    401,
                    new Document(
                        "erro",
                        "Sessão inválida ou expirada. Faça login novamente."
                    )
                );

                return;
            }

            // IDENTIFICAÇÃO DO USUÁRIO AUTENTICADO
            // O identificador será utilizado para consultar
            // os dados vinculados à conta correspondente.

            ObjectId usuarioId =
                usuario.getObjectId("_id");

            // =================================================
            // LEITURA DOS FILTROS DE VENCIMENTO
            // Recupera o período solicitado pela interface.
            // =================================================

            int dias = lerDias(
                requisicao.getRequestURI()
                    .getRawQuery()
            );

            // =================================================
            // CONSULTA DOS PRÓXIMOS VENCIMENTOS
            // Encaminha o período e o usuário ao Service,
            // responsável pelas regras de negócio.
            // =================================================

            Document resultado =
                vencimentoService.consultarProximosVencimentos(
                    dias,
                    usuarioId
                );

            // RETORNO DA CONSULTA
            // Envia os dados obtidos pelo Service ao frontend.

            responder(
                requisicao,
                200,
                resultado
            );

        } catch (IllegalArgumentException erro) {

            // =================================================
            // TRATAMENTO DE PARÂMETROS INVÁLIDOS
            // Retorna HTTP 400 quando os filtros
            // informados não são aceitos.
            // =================================================

            responder(
                requisicao,
                400,
                new Document(
                    "erro",
                    erro.getMessage()
                )
            );

        } catch (MongoException erro) {

            // =================================================
            // TRATAMENTO DE ERROS DO MONGODB
            // Registra a falha no servidor e retorna
            // uma mensagem genérica para o frontend.
            // =================================================

            System.err.println(
                "Erro MongoDB nos vencimentos: " +
                erro.getMessage()
            );

            responder(
                requisicao,
                500,
                new Document(
                    "erro",
                    "Erro ao consultar o banco de dados."
                )
            );

        } catch (RuntimeException erro) {

            // =================================================
            // TRATAMENTO DE ERROS INTERNOS
            // Evita que falhas inesperadas interrompam
            // o fluxo de resposta da API.
            // =================================================

            System.err.println(
                "Erro nos vencimentos: " +
                erro.getMessage()
            );

            responder(
                requisicao,
                500,
                new Document(
                    "erro",
                    "Erro interno ao consultar vencimentos."
                )
            );
        }
    }

    // =====================================================
    // LEITURA E VALIDAÇÃO DO PARÂMETRO DIAS
    // Interpreta os parâmetros recebidos pela URL.
    // Caso nenhum período seja informado, utiliza 7 dias.
    // Aceita apenas os cinco períodos definidos pelo sistema.
    // =====================================================

    private static int lerDias(String query) {

        int dias = 7;
        boolean encontrado = false;

        if (query == null || query.isBlank()) {
            return dias;
        }

        for (String parametro : query.split("&")) {

            String[] partes =
                parametro.split("=", 2);

            String nome = URLDecoder.decode(
                partes[0],
                StandardCharsets.UTF_8
            );

            if ("dias".equals(nome)) {

                // Impede parâmetros repetidos ou incompletos.

                if (
                    encontrado ||
                    partes.length != 2
                ) {

                    throw new IllegalArgumentException(
                        "Parâmetro dias inválido."
                    );
                }

                encontrado = true;

                String valor = URLDecoder.decode(
                    partes[1],
                    StandardCharsets.UTF_8
                );

                try {

                    dias = Integer.parseInt(valor);

                } catch (NumberFormatException erro) {

                    throw new IllegalArgumentException(
                        "O parâmetro dias deve ser 1, 3, 7, 15 ou 30."
                    );
                }
            }
        }

        // VALIDAÇÃO FINAL DO PERÍODO
        // Impede consultas com valores não previstos.

        if (!PERIODOS_VALIDOS.contains(dias)) {

            throw new IllegalArgumentException(
                "O parâmetro dias deve ser 1, 3, 7, 15 ou 30."
            );
        }

        return dias;
    }

    // =====================================================
    // FORMATAÇÃO E ENVIO DA RESPOSTA JSON
    // Converte o Document para JSON, configura o tipo
    // de conteúdo e devolve a resposta HTTP ao navegador.
    // =====================================================

    private static void responder(
        HttpExchange requisicao,
        int status,
        Document corpo
    ) throws IOException {

        JsonWriterSettings configuracao =
            JsonWriterSettings.builder()
                .outputMode(JsonMode.RELAXED)
                .build();

        String json =
            corpo.toJson(configuracao);

        byte[] resposta = json.getBytes(
            StandardCharsets.UTF_8
        );

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
