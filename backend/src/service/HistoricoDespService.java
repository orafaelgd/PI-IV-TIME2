
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

        this.movimentacoes =
            banco.getCollection("movimentacoes");

        this.lotes =
            banco.getCollection("lotes");

        this.itens =
            banco.getCollection("itens");

        this.usuarios =
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
                "Usuario nao identificado."
            );
        }

        // VALIDAR PAGINAÇÃO

        if (
            pagina < 1 ||
            limite < 1 ||
            limite > 100
        ) {
            throw new IllegalArgumentException(
                "Paginacao invalida."
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
                "Motivo invalido."
            );
        }

        // VALIDAR DESLOCAMENTO

        long deslocamento =
            ((long) pagina - 1) * limite;

        if (deslocamento > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                "Pagina invalida."
            );
        }

        // FILTROS DAS MOVIMENTAÇÕES

        List<Bson> filtros = new ArrayList<>();

        // Consulta apenas desperdícios quando solicitado
        if (somenteDesperdicio) {

            filtros.add(
                Filters.in(
                    "tipo",
                    "VENCIMENTO",
                    "AJUSTE_SAIDA"
                )
            );
        }

        // FILTRO DE DATA INICIAL

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

        // FILTRO DE DATA FINAL

        if (fim != null) {

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
        }

        // FILTRO DE MOTIVO

        if (motivo != null) {

            filtros.add(
                Filters.eq(
                    "motivo",
                    motivo
                )
            );
        }

        // ETAPAS DA CONSULTA

        List<Bson> consulta = new ArrayList<>();

        // Aplica os filtros da movimentação
        if (!filtros.isEmpty()) {

            consulta.add(
                Aggregates.match(
                    Filters.and(filtros)
                )
            );
        }

        // BUSCAR LOTE DA MOVIMENTAÇÃO

        consulta.add(
            Aggregates.lookup(
                lotes.getNamespace().getCollectionName(),
                "loteId",
                "_id",
                "lote"
            )
        );

        // Exige que o lote exista
        consulta.add(
            Aggregates.unwind("$lote")
        );

        // FILTRAR PELA CONTA

        // O lote precisa pertencer ao usuário autenticado
        consulta.add(
            Aggregates.match(
                Filters.eq(
                    "lote.usuarioId",
                    usuarioId
                )
            )
        );

        // VERIFICAR PRODUTO DA CONTA

        // Busca o produto vinculado ao lote
        consulta.add(
            Aggregates.lookup(
                itens.getNamespace().getCollectionName(),
                "lote.itemId",
                "_id",
                "produto"
            )
        );

        consulta.add(
            Aggregates.unwind("$produto")
        );

        // Não mistura produtos de outros usuários
        consulta.add(
            Aggregates.match(
                Filters.eq(
                    "produto.usuarioId",
                    usuarioId
                )
            )
        );

        // CONTAR REGISTROS DA CONTA

        List<Bson> consultaTotal =
            new ArrayList<>(consulta);

        consultaTotal.add(
            Aggregates.count("total")
        );

        Document contagem = movimentacoes
            .aggregate(consultaTotal)
            .first();

        long total = contagem == null
            ? 0L
            : contagem.get(
                "total",
                Number.class
            ).longValue();

        // PAGINAR REGISTROS DA CONTA

        List<Bson> consultaPagina =
            new ArrayList<>(consulta);

        // Mostra primeiro as movimentações recentes
        consultaPagina.add(
            Aggregates.sort(
                Sorts.orderBy(
                    Sorts.descending(
                        "dataMovimentacao",
                        "criadoEm"
                    ),
                    Sorts.descending("_id")
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

        // BUSCAR RESULTADOS

        List<Document> movimentacoesDaConta =
            movimentacoes.aggregate(consultaPagina)
                .into(new ArrayList<>());

        List<Document> registros =
            new ArrayList<>();

        // MONTAR HISTÓRICO

        for (
            Document movimentacao :
                movimentacoesDaConta
        ) {

            Document lote =
                movimentacao.get(
                    "lote",
                    Document.class
                );

            Document produto =
                movimentacao.get(
                    "produto",
                    Document.class
                );

            String origem =
                movimentacao.getString("origem");

            String responsavel =
                "Não informado";

            // IDENTIFICAR RESPONSÁVEL

            if ("AUTOMATICA".equals(origem)) {

                responsavel = "Sistema";

            } else {

                // Consulta o responsável somente quando
                // o registro pertence ao usuário autenticado
                Object idResponsavel =
                    movimentacao.get("usuarioId");

                if (
                    usuarioId.equals(idResponsavel) ||
                    usuarioId.toHexString().equals(
                        idResponsavel
                    )
                ) {

                    Document usuario = usuarios.find(
                        Filters.eq(
                            "_id",
                            usuarioId
                        )
                    ).first();

                    if (
                        usuario != null &&
                        usuario.getString("nome") != null
                    ) {
                        responsavel =
                            usuario.getString("nome");
                    }
                }
            }

            // VERIFICAR TIPO

            String tipo =
                movimentacao.getString("tipo");

            boolean desperdicio =
                "VENCIMENTO".equals(tipo) ||
                "AJUSTE_SAIDA".equals(tipo);

            Date data =
                movimentacao.getDate(
                    "dataMovimentacao"
                );

            // RECUPERAR IDENTIFICADORES

            Object loteId =
                movimentacao.get("loteId");

            Object itemId =
                lote.get("itemId");

            // MONTAR REGISTRO

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
                    converterId(itemId)
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
                    dataIso(data)
                )
                .append(
                    "criadoEm",
                    dataIso(
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
                        ? numeroSimples(
                            movimentacao.get(
                                "valorPerdaCentavos"
                            )
                        )
                        : 0
                );

            registros.add(registro);
        }

        // RETORNAR RESULTADO

        return new Document()
            .append("itens", registros)
            .append("total", total)
            .append("pagina", pagina)
            .append("limite", limite);
    }

    // CONVERTER ID

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

            return ((Decimal128) valor)
                .bigDecimalValue()
                .toPlainString();
        }

        if (valor instanceof BigDecimal) {

            return ((BigDecimal) valor)
                .toPlainString();
        }

        return valor.toString();
    }

    // CONVERTER DATA

    private static String dataIso(Date data) {

        return data == null
            ? null
            : data.toInstant().toString();
    }

    // CONVERTER VALOR NUMÉRICO

    private static Object numeroSimples(
        Object valor
    ) {

        if (valor instanceof Decimal128) {

            return ((Decimal128) valor)
                .bigDecimalValue()
                .toPlainString();
        }

        if (valor instanceof BigDecimal) {

            return ((BigDecimal) valor)
                .toPlainString();
        }

        return valor;
    }
}
