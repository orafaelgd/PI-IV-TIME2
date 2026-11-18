
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

    // MOTIVOS PERMITIDOS

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

    // CONSULTAR HISTÓRICO DE SAÍDA

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

        // FILTRAR SOMENTE MOVIMENTAÇÕES DE SAÍDA
        // NÃO INCLUIR ENTRADAS DE ESTOQUE

        if (somenteDesperdicio) {

            // COMPATIBILIDADE COM CONSULTAS ANTIGAS
            // APENAS MOVIMENTAÇÕES DE DESPERDÍCIO

            filtros.add(
                Filters.in(
                    "tipo",
                    "VENCIMENTO",
                    "AJUSTE_SAIDA"
                )
            );

        } else {

            // HISTÓRICO COMPLETO DE SAÍDAS
            // CONSUMO, VENCIMENTO E AJUSTES DE SAÍDA

            filtros.add(
                Filters.in(
                    "tipo",
                    "CONSUMO",
                    "VENCIMENTO",
                    "AJUSTE_SAIDA"
                )
            );
        }

        // FILTRO POR DATA INICIAL

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

        // FILTRO POR DATA FINAL

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

        // FILTRO POR MOTIVO

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

        // APLICAR FILTROS DE SAÍDA, DATA E MOTIVO

        consulta.add(
            Aggregates.match(
                Filters.and(filtros)
            )
        );

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

        // DESCARTAR MOVIMENTAÇÕES SEM LOTE

        consulta.add(
            Aggregates.unwind("$lote")
        );

        // VERIFICAR PROPRIEDADE DO LOTE

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

        // DESCARTAR MOVIMENTAÇÕES SEM PRODUTO

        consulta.add(
            Aggregates.unwind("$produto")
        );

        // VERIFICAR PROPRIEDADE DO PRODUTO

        consulta.add(
            Aggregates.match(
                Filters.eq(
                    "produto.usuarioId",
                    usuarioId
                )
            )
        );

        // CONTAR REGISTROS DA CONTA AUTENTICADA

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

        // ORDENAR DO MAIS RECENTE PARA O MAIS ANTIGO

        consultaPagina.add(
            Aggregates.sort(
                Sorts.descending(
                    "dataMovimentacao",
                    "criadoEm",
                    "_id"
                )
            )
        );

        // PULAR REGISTROS DAS PÁGINAS ANTERIORES

        consultaPagina.add(
            Aggregates.skip(
                (int) deslocamento
            )
        );

        // LIMITAR RESULTADOS DA PÁGINA

        consultaPagina.add(
            Aggregates.limit(limite)
        );

        // EXECUTAR CONSULTA

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

        if (
            nomeConta == null ||
            nomeConta.isBlank()
        ) {

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

            // VERIFICAÇÃO ADICIONAL DE SEGURANÇA

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

            // IDENTIFICAR SE É DESPERDÍCIO

            boolean desperdicio =
                "VENCIMENTO".equals(tipo) ||
                "AJUSTE_SAIDA".equals(tipo);

            // IDENTIFICAR MOTIVO

            String motivoRegistro =
                movimentacao.getString("motivo");

            // COMPATIBILIDADE COM REGISTROS
            // QUE NÃO POSSUEM MOTIVO PREENCHIDO

            if (
                motivoRegistro == null ||
                motivoRegistro.isBlank()
            ) {

                if ("CONSUMO".equals(tipo)) {

                    motivoRegistro = "Consumido";

                } else if ("VENCIMENTO".equals(tipo)) {

                    motivoRegistro = "Vencimento";

                } else {

                    motivoRegistro = "Não informado";
                }
            }

            // RESPONSÁVEL PELO REGISTRO

            String origem =
                movimentacao.getString("origem");

            String responsavel = "Não informado";

            if ("AUTOMATICA".equals(origem)) {

                // REGISTRO GERADO PELO SISTEMA

                responsavel = "Sistema";

            } else {

                // BUSCAR RESPONSÁVEL DA MOVIMENTAÇÃO

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

                    // COMPATIBILIDADE COM REGISTROS ANTIGOS

                    responsavel = nomeConta;
                }
            }

            // IDENTIFICADORES

            Object loteId =
                movimentacao.get("loteId");

            Object produtoId =
                lote.get("itemId");

            // DATA DA MOVIMENTAÇÃO

            Date dataOcorrencia =
                movimentacao.getDate(
                    "dataMovimentacao"
                );

            // CRIAR REGISTRO PARA O FRONTEND

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
                    motivoRegistro
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

        // LOG DE CONFERÊNCIA

        System.out.println(
            "HISTÓRICO DE SAÍDA - Usuário: " +
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

        // IDENTIFICADOR OBJECTID

        if (identificador instanceof ObjectId) {

            return usuarios.find(
                Filters.eq(
                    "_id",
                    identificador
                )
            ).first();
        }

        // IDENTIFICADOR STRING

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
