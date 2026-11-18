
package service;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.DateTimeException;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

public class HistoricoDespService {

    // CONFIGURAÇÕES

    private static final ZoneId FUSO =
        ZoneId.of("America/Sao_Paulo");

    private static final Set<String> MOTIVOS = Set.of(
        "Consumido",
        "Vencimento",
        "Contaminado",
        "Danificado",
        "Outro"
    );

    // COLEÇÕES DO MONGODB

    private final MongoCollection<Document> movimentacoes;
    private final MongoCollection<Document> lotes;
    private final MongoCollection<Document> itens;
    private final MongoCollection<Document> usuarios;

    // CONSTRUTOR

    public HistoricoDespService(MongoDatabase banco) {

        movimentacoes =
            banco.getCollection("movimentacoes");

        lotes =
            banco.getCollection("lotes");

        itens =
            banco.getCollection("itens");

        usuarios =
            banco.getCollection("users");
    }

    // CONSULTAR HISTÓRICO

    public Document consultarHistorico(
        LocalDate inicio,
        LocalDate fim,
        String motivo,
        int pagina,
        int limite,
        boolean somenteDesperdicio,
        ObjectId usuarioId
    ) {

        // VALIDAR USUÁRIO

        if (usuarioId == null) {

            throw new IllegalArgumentException(
                "Usuário não identificado."
            );
        }

        // VALIDAR PAGINAÇÃO

        if (
            pagina < 1 ||
            limite < 1 ||
            limite > 100
        ) {

            throw new IllegalArgumentException(
                "Paginação inválida."
            );
        }

        // VALIDAR PERÍODO

        if (
            inicio != null &&
            fim != null &&
            inicio.isAfter(fim)
        ) {

            throw new IllegalArgumentException(
                "Data inicial maior que a final."
            );
        }

        // VALIDAR MOTIVO

        if (
            motivo != null &&
            !MOTIVOS.contains(motivo)
        ) {

            throw new IllegalArgumentException(
                "Motivo inválido."
            );
        }

        // CALCULAR PAGINAÇÃO

        long deslocamento =
            ((long) pagina - 1L) * limite;

        if (deslocamento > Integer.MAX_VALUE) {

            throw new IllegalArgumentException(
                "Página inválida."
            );
        }

        // FILTROS INICIAIS

        List<Bson> filtros = new ArrayList<>();

        // SOMENTE MOVIMENTAÇÕES DE DESPERDÍCIO

        if (somenteDesperdicio) {

            filtros.add(
                Filters.in(
                    "tipo",
                    "VENCIMENTO",
                    "AJUSTE_SAIDA"
                )
            );
        }

        // DATA INICIAL

        if (inicio != null) {

            filtros.add(
                Filters.gte(
                    "dataMovimentacao",
                    Date.from(
                        inicio.atStartOfDay(FUSO)
                            .toInstant()
                    )
                )
            );
        }

        // DATA FINAL

        if (fim != null) {

            try {

                filtros.add(
                    Filters.lt(
                        "dataMovimentacao",
                        Date.from(
                            fim.plusDays(1)
                                .atStartOfDay(FUSO)
                                .toInstant()
                        )
                    )
                );

            } catch (
                DateTimeException |
                ArithmeticException erro
            ) {

                throw new IllegalArgumentException(
                    "Data final inválida."
                );
            }
        }

        // MOTIVO

        if (motivo != null) {

            filtros.add(
                Filters.eq(
                    "motivo",
                    motivo
                )
            );
        }

        // PIPELINE DO MONGODB

        List<Bson> consulta = new ArrayList<>();

        // APLICAR FILTROS

        if (!filtros.isEmpty()) {

            consulta.add(
                Aggregates.match(
                    Filters.and(filtros)
                )
            );
        }

        // VINCULAR MOVIMENTAÇÃO AO LOTE

        // movimentacoes.loteId -> lotes._id
        consulta.add(
            Aggregates.lookup(
                lotes.getNamespace().getCollectionName(),
                "loteId",
                "_id",
                "lote"
            )
        );

        // Descarta movimentações sem lote correspondente
        consulta.add(
            Aggregates.unwind("$lote")
        );

        // FILTRAR LOTE PELO USUÁRIO

        // O lote obrigatoriamente deve pertencer
        // ao usuário identificado pela sessão.
        consulta.add(
            Aggregates.match(
                Filters.eq(
                    "lote.usuarioId",
                    usuarioId
                )
            )
        );

        // VINCULAR LOTE AO PRODUTO

        // lotes.itemId -> itens._id
        consulta.add(
            Aggregates.lookup(
                itens.getNamespace().getCollectionName(),
                "lote.itemId",
                "_id",
                "produto"
            )
        );

        // Descarta movimentações sem produto
        consulta.add(
            Aggregates.unwind("$produto")
        );

        // FILTRAR PRODUTO PELO USUÁRIO

        // O produto também deve pertencer
        // à mesma conta autenticada.
        consulta.add(
            Aggregates.match(
                Filters.eq(
                    "produto.usuarioId",
                    usuarioId
                )
            )
        );

        // CONTAR APENAS OS REGISTROS DA CONTA

        List<Bson> consultaTotal =
            new ArrayList<>(consulta);

        consultaTotal.add(
            Aggregates.count("total")
        );

        Document contagem =
            movimentacoes.aggregate(consultaTotal)
                .first();

        long total = contagem == null
            ? 0L
            : contagem.get(
                "total",
                Number.class
            ).longValue();

        // CONSULTAR REGISTROS PAGINADOS

        List<Bson> consultaPagina =
            new ArrayList<>(consulta);

        // Registros mais recentes primeiro
        consultaPagina.add(
            Aggregates.sort(
                Sorts.descending(
                    "dataMovimentacao",
                    "criadoEm",
                    "_id"
                )
            )
        );

        consultaPagina.add(
            Aggregates.skip(
                (int) deslocamento
            )
        );

        consultaPagina.add(
            Aggregates.limit(limite)
        );

        List<Document> resultados =
            movimentacoes.aggregate(consultaPagina)
                .into(new ArrayList<>());

        // BUSCAR NOME DA CONTA

        Document conta = usuarios.find(
            Filters.eq("_id", usuarioId)
        ).first();

        String nomeConta = conta == null
            ? "Não informado"
            : conta.getString("nome");

        if (nomeConta == null || nomeConta.isBlank()) {

            nomeConta = "Não informado";
        }

        // MONTAR RESULTADOS

        List<Document> registros = new ArrayList<>();

        for (Document movimentacao : resultados) {

            Document lote = movimentacao.get(
                "lote",
                Document.class
            );

            Document produto = movimentacao.get(
                "produto",
                Document.class
            );

            // VERIFICAÇÃO ADICIONAL

            // A consulta já verifica a propriedade
            // no MongoDB. Esta verificação evita
            // montar registros inconsistentes.
            if (
                lote == null ||
                produto == null ||
                !usuarioId.equals(
                    lote.get("usuarioId")
                ) ||
                !usuarioId.equals(
                    produto.get("usuarioId")
                )
            ) {

                throw new IllegalStateException(
                    "Registro com vínculo de usuário inconsistente."
                );
            }

            // TIPO DA MOVIMENTAÇÃO

            String tipo =
                movimentacao.getString("tipo");

            boolean desperdicio =
                "VENCIMENTO".equals(tipo) ||
                "AJUSTE_SAIDA".equals(tipo);

            // RESPONSÁVEL PELO REGISTRO

            String origem =
                movimentacao.getString("origem");

            String responsavel =
                "Não informado";

            if ("AUTOMATICA".equals(origem)) {

                responsavel = "Sistema";

            } else {

                // Identifica quem realizou o registro.
                // Esse campo não define a propriedade
                // do lote ou do produto.
                Object idResponsavel =
                    movimentacao.get("usuarioId");

                if (idResponsavel != null) {

                    Document responsavelEncontrado =
                        buscarUsuarioPorId(
                            idResponsavel
                        );

                    if (
                        responsavelEncontrado != null &&
                        responsavelEncontrado.getString("nome") != null
                    ) {

                        responsavel =
                            responsavelEncontrado.getString("nome");
                    }

                } else {

                    // Compatibilidade com movimentações
                    // antigas sem responsável informado
                    responsavel = nomeConta;
                }
            }

            // DADOS DA MOVIMENTAÇÃO

            Object loteId =
                movimentacao.get("loteId");

            Object produtoId =
                lote.get("itemId");

            Date dataOcorrencia =
                movimentacao.getDate(
                    "dataMovimentacao"
                );

            // CRIAR REGISTRO

            Document registro = new Document()
                .append(
                    "id",
                    converterId(
                        movimentacao.get("_id")
                    )
                )
                .append(
                    "loteId",
                    converterId(loteId)
                )
                .append(
                    "codigoLote",
                    lote.getString("codigo")
                )
                .append(
                    "produtoId",
                    converterId(produtoId)
                )
                .append(
                    "produtoNome",
                    produto.getString("nome")
                )
                .append(
                    "unidade",
                    produto.getString("unidadeMedida")
                )
                .append(
                    "quantidade",
                    decimalTexto(
                        movimentacao.get("quantidade")
                    )
                )
                .append(
                    "tipo",
                    tipo
                )
                .append(
                    "motivo",
                    movimentacao.getString("motivo")
                )
                .append(
                    "desperdicio",
                    desperdicio
                )
                .append(
                    "dataOcorrencia",
                    converterData(dataOcorrencia)
                )
                .append(
                    "criadoEm",
                    converterData(
                        movimentacao.getDate("criadoEm")
                    )
                )
                .append(
                    "origem",
                    origem
                )
                .append(
                    "responsavelNome",
                    responsavel
                )
                .append(
                    "observacao",
                    movimentacao.getString("observacao")
                )
                .append(
                    "valorPerdaCentavos",
                    desperdicio
                        ? converterNumero(
                            movimentacao.get(
                                "valorPerdaCentavos"
                            )
                        )
                        : 0
                );

            registros.add(registro);
        }

        // LOG PARA CONFERÊNCIA

        System.out.println(
            "HISTÓRICO - Usuário: " +
            usuarioId.toHexString() +
            " | Registros encontrados: " +
            total
        );

        // RESPOSTA PARA O FRONTEND

        return new Document()
            .append(
                "itens",
                registros
            )
            .append(
                "total",
                total
            )
            .append(
                "pagina",
                pagina
            )
            .append(
                "limite",
                limite
            );
    }

    // BUSCAR RESPONSÁVEL

    private Document buscarUsuarioPorId(
        Object identificador
    ) {

        if (identificador instanceof ObjectId) {

            return usuarios.find(
                Filters.eq(
                    "_id",
                    identificador
                )
            ).first();
        }

        if (
            identificador instanceof String &&
            ObjectId.isValid((String) identificador)
        ) {

            return usuarios.find(
                Filters.eq(
                    "_id",
                    new ObjectId(
                        (String) identificador
                    )
                )
            ).first();
        }

        return null;
    }

    // CONVERTER IDENTIFICADOR

    private static String converterId(Object valor) {

        if (valor == null) {
            return null;
        }

        if (valor instanceof ObjectId) {

            return ((ObjectId) valor)
                .toHexString();
        }

        return valor.toString();
    }

    // CONVERTER QUANTIDADE

    private static String decimalTexto(Object valor) {

        if (valor == null) {
            return null;
        }

        if (valor instanceof Decimal128) {

            Decimal128 decimal =
                (Decimal128) valor;

            return decimal.isFinite()
                ? decimal.bigDecimalValue()
                    .toPlainString()
                : null;
        }

        if (valor instanceof BigDecimal) {

            return ((BigDecimal) valor)
                .toPlainString();
        }

        return valor.toString();
    }

    // CONVERTER DATA

    private static String converterData(Date data) {

        return data == null
            ? null
            : data.toInstant().toString();
    }

    // CONVERTER NÚMERO

    private static Object converterNumero(Object valor) {

        if (valor instanceof Decimal128) {

            Decimal128 decimal =
                (Decimal128) valor;

            return decimal.isFinite()
                ? decimal.bigDecimalValue()
                    .toPlainString()
                : null;
        }

        if (valor instanceof BigDecimal) {

            return ((BigDecimal) valor)
                .toPlainString();
        }

        return valor;
    }
}
