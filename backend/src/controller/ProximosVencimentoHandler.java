
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

    // ORIGENS PERMITIDAS

    private static final Set<String> ORIGENS = Set.of(
        "http://localhost:5500",
        "http://127.0.0.1:5500"
    );

    // PERÍODOS PERMITIDOS

    private static final Set<Integer> PERIODOS_VALIDOS =
        Set.of(1, 3, 7, 15, 30);

    // SERVIÇOS

    private final ProximosVencimentoService vencimentoService;
    private final SessaoService sessaoService;

    // CONSTRUTOR

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

    // RECEBER REQUISIÇÕES

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
                new Document(
                    "erro",
                    "Origem não permitida."
                )
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

        // VALIDAR ROTA

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

        // RESPONDER OPTIONS

        String metodo =
            requisicao.getRequestMethod();

        if ("OPTIONS".equals(metodo)) {

            requisicao.sendResponseHeaders(204, -1);
            requisicao.close();

            return;
        }

        // PERMITIR SOMENTE GET

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

            // AUTENTICAR USUÁRIO PELO TOKEN

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

            // RECUPERAR USUÁRIO LOGADO

            ObjectId usuarioId =
                usuario.getObjectId("_id");

            // LER PERÍODO

            int dias = lerDias(
                requisicao.getRequestURI()
                    .getRawQuery()
            );

            // CONSULTAR O SERVICE

            Document resultado =
                vencimentoService.consultarProximosVencimentos(
                    dias,
                    usuarioId
                );

            // ENVIAR RESPOSTA

            responder(
                requisicao,
                200,
                resultado
            );

        } catch (IllegalArgumentException erro) {

            responder(
                requisicao,
                400,
                new Document(
                    "erro",
                    erro.getMessage()
                )
            );

        } catch (MongoException erro) {

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

    // LER PARÂMETRO DIAS

    private static int lerDias(String query) {

        // VALOR PADRÃO: 7 DIAS

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

        // VALIDAR OS CINCO FILTROS

        if (!PERIODOS_VALIDOS.contains(dias)) {

            throw new IllegalArgumentException(
                "O parâmetro dias deve ser 1, 3, 7, 15 ou 30."
            );
        }

        return dias;
    }

    // RESPOSTA JSON

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
