
package service;

import com.mongodb.MongoException;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.UpdateResult;

import org.bson.Document;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import java.time.LocalDate;
import java.time.ZoneId;

import java.util.Date;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class MovimentacoesService {

    // =====================================================
    // CONFIGURAÇÕES DAS MOVIMENTAÇÕES
    // Define o fuso horário utilizado nas datas e os
    // motivos permitidos para o registro de saídas.
    // =====================================================

    private static final ZoneId FUSO =
        ZoneId.of("America/Sao_Paulo");

    private static final Set<String> MOTIVOS = Set.of(
        "consumido",
        "vencimento",
        "contaminado",
        "danificado",
        "outro"
    );

    // =====================================================
    // CONEXÃO E COLEÇÕES DO MONGODB
    // Mantém as referências necessárias para consultar
    // produtos e lotes, atualizar o estoque e registrar
    // o histórico das movimentações.
    // =====================================================

    private final MongoClient cliente;

    private final MongoCollection<Document> lotes;

    private final MongoCollection<Document> movimentacoes;

    private final MongoCollection<Document> produtos;

    // =====================================================
    // CONSTRUTOR
    // Recebe a conexão e as coleções disponibilizadas
    // pelo Controller, permitindo que o Service execute
    // as operações relacionadas ao estoque.
    // =====================================================

    public MovimentacoesService(
        MongoClient cliente,
        MongoCollection<Document> lotes,
        MongoCollection<Document> movimentacoes,
        MongoCollection<Document> produtos
    ) {

        this.cliente = cliente;
        this.lotes = lotes;
        this.movimentacoes = movimentacoes;
        this.produtos = produtos;
    }

    // =====================================================
    // CLASSE DE RESULTADO
    // Padroniza o retorno das operações, informando
    // ao Controller o código HTTP e a mensagem que
    // deverão ser apresentados ao frontend.
    // =====================================================

    public static class Resultado {

        private final int status;
        private final String mensagem;

        public Resultado(
            int status,
            String mensagem
        ) {

            this.status = status;
            this.mensagem = mensagem;
        }

        public int getStatus() {
            return status;
        }

        public String getMensagem() {
            return mensagem;
        }
    }

    // =====================================================
    // REGISTRO DE SAÍDA DE PRODUTOS
    // Recebe os dados enviados pelo Controller, valida
    // as informações da movimentação e realiza a baixa
    // no estoque associada ao usuário autenticado.
    // =====================================================

    public Resultado registrarSaida(
        Document dados,
        ObjectId usuarioId
    ) {

        // =================================================
        // VALIDAÇÃO DO USUÁRIO E DOS DADOS RECEBIDOS
        // Impede operações sem identificação da conta
        // ou sem as informações necessárias para a saída.
        // =================================================

        if (usuarioId == null) {

            throw new IllegalArgumentException(
                "Usuário não identificado."
            );
        }

        if (dados == null) {

            throw new IllegalArgumentException(
                "Dados não informados."
            );
        }

        // =================================================
        // IDENTIFICAÇÃO DO PRODUTO E DO LOTE
        // Recupera os identificadores recebidos no JSON
        // e verifica se possuem o formato ObjectId
        // utilizado pelo MongoDB.
        // =================================================

        String itemTexto =
            texto(dados, "itemId");

        String loteTexto =
            texto(dados, "loteId");

        if (
            !ObjectId.isValid(itemTexto) ||
            !ObjectId.isValid(loteTexto)
        ) {

            throw new IllegalArgumentException(
                "Produto ou lote inválido."
            );
        }

        ObjectId itemId =
            new ObjectId(itemTexto);

        ObjectId loteId =
            new ObjectId(loteTexto);

        // =================================================
        // VALIDAÇÃO DA QUANTIDADE DE SAÍDA
        // Converte a quantidade informada para BigDecimal,
        // exige um valor positivo e verifica se pode ser
        // representado no formato Decimal128 do MongoDB.
        // =================================================

        BigDecimal quantidade;

        try {

            quantidade = new BigDecimal(
                texto(dados, "quantidade")
                    .replace(",", ".")
            );

        } catch (NumberFormatException erro) {

            throw new IllegalArgumentException(
                "Quantidade inválida."
            );
        }

        if (quantidade.signum() <= 0) {

            throw new IllegalArgumentException(
                "A quantidade deve ser positiva."
            );
        }

        try {

            new Decimal128(quantidade);

        } catch (IllegalArgumentException erro) {

            throw new IllegalArgumentException(
                "Quantidade fora do limite permitido."
            );
        }

        // =================================================
        // VALIDAÇÃO DA DATA DA MOVIMENTAÇÃO
        // Converte a data informada e impede o registro
        // de saídas com datas posteriores ao dia atual,
        // considerando o fuso horário do sistema.
        // =================================================

        LocalDate data;

        try {

            data = LocalDate.parse(
                texto(dados, "dataSaida")
            );

        } catch (RuntimeException erro) {

            throw new IllegalArgumentException(
                "Data de saída inválida."
            );
        }

        if (data.isAfter(LocalDate.now(FUSO))) {

            throw new IllegalArgumentException(
                "Data futura não permitida."
            );
        }

        // =================================================
        // VALIDAÇÃO DO MOTIVO E DA OBSERVAÇÃO
        // Verifica se o motivo selecionado pertence às
        // opções permitidas e se a observação foi preenchida
        // respeitando o limite de caracteres.
        // =================================================

        String motivo = texto(
            dados,
            "motivo"
        ).toLowerCase(Locale.ROOT);

        if (!MOTIVOS.contains(motivo)) {

            throw new IllegalArgumentException(
                "Motivo inválido."
            );
        }

        String observacao = texto(
            dados,
            "observacao"
        );

        if (
            observacao.isEmpty() ||
            observacao.length() > 1000
        ) {

            throw new IllegalArgumentException(
                "Observação inválida."
            );
        }

        // =================================================
        // VALIDAÇÃO DA CHAVE DE IDEMPOTÊNCIA
        // Identifica de forma única cada operação.
        // Caso uma requisição precise ser reenviada,
        // a mesma chave deverá ser reutilizada para
        // evitar uma segunda baixa no estoque.
        // =================================================

        String chave;

        try {

            chave = UUID.fromString(
                texto(
                    dados,
                    "idempotencyKey"
                )
            ).toString();

        } catch (IllegalArgumentException erro) {

            throw new IllegalArgumentException(
                "Chave da operação inválida."
            );
        }

        // =================================================
        // CLASSIFICAÇÃO DA MOVIMENTAÇÃO
        // Relaciona o motivo selecionado no formulário
        // ao tipo de movimentação armazenado no banco,
        // diferenciando consumo, vencimento e ajustes.
        // =================================================

        String tipo;
        String motivoFormatado;

        switch (motivo) {

            case "consumido":

                tipo = "CONSUMO";
                motivoFormatado = "Consumido";
                break;

            case "vencimento":

                tipo = "VENCIMENTO";
                motivoFormatado = "Vencimento";
                break;

            case "contaminado":

                tipo = "AJUSTE_SAIDA";
                motivoFormatado = "Contaminado";
                break;

            case "danificado":

                tipo = "AJUSTE_SAIDA";
                motivoFormatado = "Danificado";
                break;

            default:

                tipo = "AJUSTE_SAIDA";
                motivoFormatado = "Outro";
                break;
        }

        // =================================================
        // CONVERSÃO DOS VALORES PARA O MONGODB
        // Prepara a data no fuso de São Paulo e converte
        // a quantidade para Decimal128.
        // A quantidade negativa será utilizada para
        // diminuir o saldo do lote selecionado.
        // =================================================

        Date dataMovimentacao = Date.from(
            data.atStartOfDay(FUSO)
                .toInstant()
        );

        Decimal128 quantidadeDecimal =
            new Decimal128(quantidade);

        Decimal128 quantidadeNegativa =
            new Decimal128(
                quantidade.negate()
            );

        // =================================================
        // GERAÇÃO DA ASSINATURA DA OPERAÇÃO
        // Cria uma identificação baseada nos dados
        // da movimentação e no usuário autenticado.
        // Essa assinatura permite verificar se uma
        // chave foi reutilizada com os mesmos dados.
        // =================================================

        String assinatura = gerarAssinatura(
            itemId,
            loteId,
            quantidade,
            data,
            motivo,
            observacao,
            usuarioId
        );

        // =================================================
        // VERIFICAÇÃO DE OPERAÇÕES JÁ REGISTRADAS
        // Consulta a chave de idempotência antes de
        // iniciar uma nova baixa.
        // Caso a operação já exista, retorna o resultado
        // correspondente sem descontar o estoque novamente.
        // =================================================

        try {

            Document existente =
                movimentacoes.find(
                    Filters.eq(
                        "idempotencyKey",
                        chave
                    )
                ).first();

            if (existente != null) {

                return verificarRepeticao(
                    existente,
                    assinatura,
                    usuarioId
                );
            }

        } catch (MongoException erro) {

            System.err.println(
                "Erro ao verificar operação: " +
                erro.getMessage()
            );

            return new Resultado(
                503,
                "Não foi possível verificar a operação."
            );
        }

        // =====================================================
        // TRANSAÇÃO DO MONGODB
        // Executa a atualização do estoque e o registro
        // do histórico como uma única operação.
        // Se alguma etapa falhar, a transação poderá ser
        // cancelada para evitar inconsistências nos dados.
        // =====================================================

        try (
            ClientSession sessao =
                cliente.startSession()
        ) {

            sessao.startTransaction();

            try {

                // =============================================
                // VERIFICAÇÃO DO PRODUTO
                // Confirma que o produto existe, está ativo
                // e pertence à conta do usuário autenticado.
                // =============================================

                Document produto = produtos.find(
                    sessao,
                    Filters.and(
                        Filters.eq(
                            "_id",
                            itemId
                        ),
                        Filters.eq(
                            "usuarioId",
                            usuarioId
                        ),
                        Filters.eq(
                            "status",
                            "ativo"
                        )
                    )
                ).first();

                if (produto == null) {

                    sessao.abortTransaction();

                    return new Resultado(
                        409,
                        "Produto inexistente, inativo ou não pertence à sua conta."
                    );
                }

                // =============================================
                // VERIFICAÇÃO DO LOTE E DO SALDO
                // Garante que o lote pertence ao produto
                // e ao usuário autenticado, não está
                // arquivado ou inativo e possui saldo
                // suficiente para a quantidade solicitada.
                // =============================================

                UpdateResult atualizacao =
                    lotes.updateOne(
                        sessao,
                        Filters.and(
                            Filters.eq(
                                "_id",
                                loteId
                            ),
                            Filters.eq(
                                "itemId",
                                itemId
                            ),
                            Filters.eq(
                                "usuarioId",
                                usuarioId
                            ),
                            Filters.ne(
                                "inativo",
                                true
                            ),
                            Filters.ne(
                                "arquivado",
                                true
                            ),
                            Filters.gte(
                                "quantidadeDisponivel",
                                quantidadeDecimal
                            )
                        ),
                        Updates.inc(
                            "quantidadeDisponivel",
                            quantidadeNegativa
                        )
                    );

                // =============================================
                // CONFIRMAÇÃO DA BAIXA NO ESTOQUE
                // Verifica se exatamente um lote foi alterado.
                // Caso contrário, cancela a transação e
                // informa que a saída não foi registrada.
                // =============================================

                if (
                    atualizacao.getModifiedCount() != 1
                ) {

                    sessao.abortTransaction();

                    return new Resultado(
                        409,
                        "Lote inexistente, inativo, não autorizado ou com saldo insuficiente."
                    );
                }

                // =============================================
                // PREPARAÇÃO DO HISTÓRICO DA MOVIMENTAÇÃO
                // Organiza os dados que serão armazenados,
                // incluindo produto, lote, quantidade, motivo,
                // usuário e identificadores da operação.
                // =============================================

                Document movimentacao =
                    new Document()
                        .append(
                            "loteId",
                            loteId
                        )
                        .append(
                            "itemId",
                            itemId
                        )
                        .append(
                            "usuarioId",
                            usuarioId
                        )
                        .append(
                            "tipo",
                            tipo
                        )
                        .append(
                            "quantidade",
                            quantidadeDecimal
                        )
                        .append(
                            "dataMovimentacao",
                            dataMovimentacao
                        )
                        .append(
                            "motivo",
                            motivoFormatado
                        )
                        .append(
                            "origem",
                            "MANUAL"
                        )
                        .append(
                            "observacao",
                            observacao
                        )
                        .append(
                            "criadoEm",
                            new Date()
                        )
                        .append(
                            "idempotencyKey",
                            chave
                        )
                        .append(
                            "assinaturaOperacao",
                            assinatura
                        );

                // =============================================
                // REGISTRO DO HISTÓRICO
                // Insere a movimentação no MongoDB utilizando
                // a mesma sessão da atualização do estoque.
                // =============================================

                movimentacoes.insertOne(
                    sessao,
                    movimentacao
                );

                // =============================================
                // CONFIRMAÇÃO DA TRANSAÇÃO
                // Finaliza a operação somente depois da
                // atualização do lote e do registro
                // da movimentação.
                // =============================================

                sessao.commitTransaction();

                return new Resultado(
                    201,
                    "Saída registrada com sucesso!"
                );

            } catch (MongoException erro) {

                // =============================================
                // TRATAMENTO DE FALHAS NA TRANSAÇÃO
                // Tenta cancelar a transação, caso permaneça
                // ativa, e verifica se a operação chegou
                // a ser registrada no banco de dados.
                // =============================================

                if (sessao.hasActiveTransaction()) {

                    try {

                        sessao.abortTransaction();

                    } catch (Exception ignorado) {

                        // O resultado da operação será
                        // verificado pela chave de idempotência.
                    }
                }

                // =============================================
                // VERIFICAÇÃO APÓS FALHA
                // Consulta a movimentação pela chave para
                // identificar operações que possam ter sido
                // confirmadas antes da perda de comunicação.
                // =============================================

                try {

                    Document existente =
                        movimentacoes.find(
                            Filters.eq(
                                "idempotencyKey",
                                chave
                            )
                        ).first();

                    if (existente != null) {

                        return verificarRepeticao(
                            existente,
                            assinatura,
                            usuarioId
                        );
                    }

                } catch (MongoException erroConsulta) {

                    System.err.println(
                        "Erro ao consultar operação: " +
                        erroConsulta.getMessage()
                    );
                }

                System.err.println(
                    "Falha na transação: " +
                    erro.getMessage()
                );

                return new Resultado(
                    503,
                    "Resultado não confirmado. Tente novamente com a mesma operação."
                );
            }

        } catch (MongoException erro) {

            // =================================================
            // TRATAMENTO DE FALHAS DE CONEXÃO
            // Informa que não foi possível confirmar
            // a operação quando ocorre um erro no acesso
            // ao MongoDB ou na criação da sessão.
            // =================================================

            System.err.println(
                "Erro de conexão na movimentação: " +
                erro.getMessage()
            );

            return new Resultado(
                503,
                "Não foi possível confirmar a operação."
            );
        }
    }

    // =====================================================
    // VERIFICAÇÃO DE OPERAÇÕES REPETIDAS
    // Confirma que a chave de idempotência pertence
    // ao usuário autenticado e compara a assinatura
    // para distinguir um reenvio legítimo de uma
    // tentativa de utilizar a chave em outra operação.
    // =====================================================

    private Resultado verificarRepeticao(
        Document existente,
        String assinatura,
        ObjectId usuarioId
    ) {

        // Verifica se a operação pertence à mesma conta.

        if (
            !usuarioId.equals(
                existente.get("usuarioId")
            )
        ) {

            return new Resultado(
                409,
                "Esta chave de operação não está disponível."
            );
        }

        // Se os dados são idênticos, confirma a operação
        // anterior sem realizar uma segunda baixa.

        if (
            assinatura.equals(
                existente.getString(
                    "assinaturaOperacao"
                )
            )
        ) {

            return new Resultado(
                200,
                "Saída já registrada anteriormente. Nenhuma nova baixa foi realizada."
            );
        }

        // Impede o uso da mesma chave para dados diferentes.

        return new Resultado(
            409,
            "Esta chave pertence a uma operação diferente."
        );
    }

    // =====================================================
    // GERAÇÃO DA ASSINATURA SHA-256
    // Organiza os dados da movimentação em um documento
    // padronizado e gera um hash para identificar
    // o conteúdo associado à chave de idempotência.
    // =====================================================

    private static String gerarAssinatura(
        ObjectId itemId,
        ObjectId loteId,
        BigDecimal quantidade,
        LocalDate data,
        String motivo,
        String observacao,
        ObjectId usuarioId
    ) {

        // Normaliza os dados para que operações equivalentes
        // produzam a mesma assinatura.

        Document dadosCanonicos =
            new Document()
                .append(
                    "usuarioId",
                    usuarioId.toHexString()
                )
                .append(
                    "itemId",
                    itemId.toHexString()
                )
                .append(
                    "loteId",
                    loteId.toHexString()
                )
                .append(
                    "quantidade",
                    quantidade.stripTrailingZeros()
                        .toPlainString()
                )
                .append(
                    "dataSaida",
                    data.toString()
                )
                .append(
                    "motivo",
                    motivo
                )
                .append(
                    "observacao",
                    observacao
                );

        try {

            MessageDigest digest =
                MessageDigest.getInstance(
                    "SHA-256"
                );

            byte[] hash = digest.digest(
                dadosCanonicos.toJson()
                    .getBytes(
                        StandardCharsets.UTF_8
                    )
            );

            return HexFormat.of()
                .formatHex(hash);

        } catch (NoSuchAlgorithmException erro) {

            throw new IllegalStateException(
                "Falha ao preparar identificação da saída.",
                erro
            );
        }
    }

    // =====================================================
    // RECUPERAÇÃO DE CAMPOS DE TEXTO
    // Obtém os valores recebidos no documento JSON,
    // remove espaços nas extremidades e retorna
    // uma string vazia quando o campo não é texto.
    // =====================================================

    private static String texto(
        Document dados,
        String campo
    ) {

        Object valor = dados.get(campo);

        return valor instanceof String
            ? ((String) valor).trim()
            : "";
    }
}
