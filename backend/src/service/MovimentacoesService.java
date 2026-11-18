
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

    // CONFIGURAÇÕES

    private static final ZoneId FUSO =
        ZoneId.of("America/Sao_Paulo");

    private static final Set<String> MOTIVOS = Set.of(
        "consumido",
        "vencimento",
        "contaminado",
        "danificado",
        "outro"
    );

    // CONEXÃO E COLEÇÕES

    private final MongoClient cliente;

    private final MongoCollection<Document> lotes;

    private final MongoCollection<Document> movimentacoes;

    private final MongoCollection<Document> produtos;

    // CONSTRUTOR

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

    // CLASSE DE RESULTADO

    // Permite retornar ao Controller um status HTTP
    // e uma mensagem sem depender de HttpExchange
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

    // REGISTRAR SAÍDA

    public Resultado registrarSaida(
        Document dados,
        ObjectId usuarioId
    ) {

        // VALIDAR USUÁRIO

        if (usuarioId == null) {

            throw new IllegalArgumentException(
                "Usuário não identificado."
            );
        }

        // VALIDAR JSON

        if (dados == null) {

            throw new IllegalArgumentException(
                "Dados não informados."
            );
        }

        // IDENTIFICAR PRODUTO E LOTE

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

        // VALIDAR QUANTIDADE

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

        // VERIFICAR COMPATIBILIDADE COM DECIMAL128

        try {

            new Decimal128(quantidade);

        } catch (IllegalArgumentException erro) {

            throw new IllegalArgumentException(
                "Quantidade fora do limite permitido."
            );
        }

        // VALIDAR DATA

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

        // VALIDAR MOTIVO

        String motivo = texto(
            dados,
            "motivo"
        ).toLowerCase(Locale.ROOT);

        if (!MOTIVOS.contains(motivo)) {

            throw new IllegalArgumentException(
                "Motivo inválido."
            );
        }

        // VALIDAR OBSERVAÇÃO

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

        // VALIDAR CHAVE DE IDEMPOTÊNCIA

        // A mesma operação deve utilizar a mesma chave
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

        // DEFINIR TIPO DA MOVIMENTAÇÃO

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

        // CONVERTER DATA

        Date dataMovimentacao = Date.from(
            data.atStartOfDay(FUSO)
                .toInstant()
        );

        // CONVERTER QUANTIDADE

        Decimal128 quantidadeDecimal =
            new Decimal128(quantidade);

        Decimal128 quantidadeNegativa =
            new Decimal128(
                quantidade.negate()
            );

        // GERAR ASSINATURA DA OPERAÇÃO

        // A assinatura identifica os dados da saída,
        // inclusive a conta autenticada.
        String assinatura = gerarAssinatura(
            itemId,
            loteId,
            quantidade,
            data,
            motivo,
            observacao,
            usuarioId
        );

        // VERIFICAR OPERAÇÃO JÁ REGISTRADA

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

        // INICIAR TRANSAÇÃO

        // A baixa no estoque e o histórico precisam
        // ser gravados na mesma transação.
        try (
            ClientSession sessao =
                cliente.startSession()
        ) {

            sessao.startTransaction();

            try {

                // VERIFICAR PRODUTO DA CONTA

                // O produto precisa existir e pertencer
                // ao usuário identificado pelo token.
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
                        )
                    )
                ).first();

                if (produto == null) {

                    sessao.abortTransaction();

                    return new Resultado(
                        409,
                        "Produto inexistente ou não pertence à sua conta."
                    );
                }

                // ATUALIZAR ESTOQUE DO LOTE

                // Verifica:
                // 1. ID do lote
                // 2. ID do produto
                // 3. ID do usuário
                // 4. Saldo disponível
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

                // LOTE INVÁLIDO OU SEM ESTOQUE

                if (
                    atualizacao.getModifiedCount() != 1
                ) {

                    sessao.abortTransaction();

                    return new Resultado(
                        409,
                        "Lote inexistente, não autorizado ou com saldo insuficiente."
                    );
                }

                // CRIAR MOVIMENTAÇÃO

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

                // INSERIR HISTÓRICO

                movimentacoes.insertOne(
                    sessao,
                    movimentacao
                );

                // CONFIRMAR TRANSAÇÃO

                sessao.commitTransaction();

                return new Resultado(
                    201,
                    "Saída registrada com sucesso!"
                );

            } catch (MongoException erro) {

                // CANCELAR TRANSAÇÃO ATIVA

                if (sessao.hasActiveTransaction()) {

                    try {

                        sessao.abortTransaction();

                    } catch (Exception ignorado) {

                        // Verificar a operação pela chave
                    }
                }

                // CONFERIR RESULTADO DA OPERAÇÃO

                // Se o commit não foi confirmado, verifica
                // se a movimentação foi registrada.
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

    // VERIFICAR OPERAÇÃO REPETIDA

    private Resultado verificarRepeticao(
        Document existente,
        String assinatura,
        ObjectId usuarioId
    ) {

        // CONFIRMAR PROPRIETÁRIO

        // Não permite reutilizar uma chave pertencente
        // a outra conta.
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

        // MESMA OPERAÇÃO

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

        // CHAVE UTILIZADA PARA OUTRA OPERAÇÃO

        return new Resultado(
            409,
            "Esta chave pertence a uma operação diferente."
        );
    }

    // GERAR ASSINATURA SHA-256

    private static String gerarAssinatura(
        ObjectId itemId,
        ObjectId loteId,
        BigDecimal quantidade,
        LocalDate data,
        String motivo,
        String observacao,
        ObjectId usuarioId
    ) {

        // NORMALIZAR DADOS DA OPERAÇÃO

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

    // RECUPERAR CAMPO DE TEXTO

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
