
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

    // =====================================================
    // CONFIGURAÇÃO DAS ORIGENS PERMITIDAS
    // Define os endereços do frontend autorizados
    // a realizar requisições ao backend.
    // =====================================================

    private static final Set<String> ORIGENS = Set.of(
        "http://localhost:5500",
        "http://127.0.0.1:5500"
    );

    // =====================================================
    // SERVIÇOS UTILIZADOS PELO CONTROLLER
    // MovimentacoesService executa as regras de negócio.
    // SessaoService valida a autenticação do usuário.
    // =====================================================

    private final MovimentacoesService movimentacoesService;
    private final SessaoService sessaoService;

    // =====================================================
    // CONSTRUTOR
    // Recebe as coleções do MongoDB e inicializa
    // os serviços responsáveis pelas movimentações
    // e pelo gerenciamento de sessões.
    // =====================================================

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

    // =====================================================
    // RECEBIMENTO E VALIDAÇÃO DAS REQUISIÇÕES HTTP
    // Verifica a origem, configura as permissões de acesso,
    // valida a rota e permite apenas os métodos
    // necessários para registrar movimentações.
    // =====================================================

    @Override
    public void handle(HttpExchange requisicao)
            throws IOException {

        // VERIFICAÇÃO DA ORIGEM
        // Impede solicitações de origens diferentes
        // das autorizadas pelo sistema.

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

        // =================================================
        // CONFIGURAÇÃO DE CORS
        // Permite que o frontend envie requisições
        // com conteúdo JSON e token de autenticação.
        // =================================================

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

        // =================================================
        // VALIDAÇÃO DA ROTA E DO MÉTODO HTTP
        // Garante que a solicitação seja destinada
        // ao endpoint de movimentações e utiliza
        // somente os métodos POST e OPTIONS.
        // =================================================

        if (!"/api/movimentacoes".equals(rota)) {

            responder(
                requisicao,
                404,
                "Rota não encontrada."
            );

            return;
        }

        // Responde às verificações de CORS do navegador.

        if ("OPTIONS".equals(metodo)) {

            requisicao.sendResponseHeaders(204, -1);
            requisicao.close();

            return;
        }

        // O registro de saída deve utilizar POST.

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

            // =================================================
            // AUTENTICAÇÃO DO USUÁRIO
            // Utiliza o SessaoService para verificar o token
            // Bearer, a existência e validade da sessão
            // e a situação do usuário cadastrado.
            // =================================================

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

            // IDENTIFICAÇÃO DA CONTA AUTENTICADA
            // Obtém o ID diretamente da sessão validada,
            // evitando confiar em identificadores enviados
            // pelo frontend no corpo da requisição.

            ObjectId usuarioId =
                usuario.getObjectId("_id");

            // =================================================
            // LEITURA E LIMITAÇÃO DO CORPO DA REQUISIÇÃO
            // Lê os dados enviados pelo formulário e
            // impede o processamento de conteúdos
            // maiores que 8 KB.
            // =================================================

            byte[] conteudo =
                requisicao.getRequestBody()
                    .readNBytes(8193);

            if (conteudo.length > 8192) {

                responder(
                    requisicao,
                    413,
                    "Requisição muito grande."
                );

                return;
            }

            // =================================================
            // CONVERSÃO DOS DADOS JSON
            // Transforma o conteúdo recebido em Document
            // para que o Service possa validar e processar
            // as informações da movimentação.
            // =================================================

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

            // =================================================
            // REGISTRO DA MOVIMENTAÇÃO PELO SERVICE
            // Encaminha os dados da saída e o ID do usuário
            // autenticado para aplicação das regras
            // de negócio e atualização do estoque.
            // =================================================

            MovimentacoesService.Resultado resultado =
                movimentacoesService.registrarSaida(
                    dados,
                    usuarioId
                );

            // =================================================
            // RETORNO DO RESULTADO AO FRONTEND
            // Envia o código HTTP e a mensagem produzidos
            // pelo Service após o processamento da saída.
            // =================================================

            responder(
                requisicao,
                resultado.getStatus(),
                resultado.getMensagem()
            );

        } catch (IllegalArgumentException erro) {

            // =================================================
            // TRATAMENTO DE DADOS INVÁLIDOS
            // Retorna HTTP 400 quando os dados
            // recebidos não atendem às validações.
            // =================================================

            responder(
                requisicao,
                400,
                "Dados inválidos: " +
                erro.getMessage()
            );

        } catch (MongoException erro) {

            // =================================================
            // TRATAMENTO DE FALHAS NO MONGODB
            // Registra o erro no servidor e informa
            // que não foi possível confirmar a operação.
            // =================================================

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

            // =================================================
            // TRATAMENTO DE ERROS INTERNOS
            // Evita que falhas inesperadas deixem
            // a requisição sem uma resposta adequada.
            // =================================================

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

    // =====================================================
    // FORMATAÇÃO E ENVIO DAS RESPOSTAS JSON
    // Organiza a mensagem em formato JSON, configura
    // os cabeçalhos HTTP e envia a resposta ao frontend.
    // =====================================================

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
