
package service;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;

import org.bson.Document;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static com.mongodb.client.model.Filters.*;

public class ProximosVencimentoService {

    // CONFIGURAÇÕES

    private static final ZoneId FUSO =
        ZoneId.of("America/Sao_Paulo");

    // PERÍODOS PERMITIDOS

    private static final Set<Integer> PERIODOS_VALIDOS =
        Set.of(1, 3, 7, 15, 30);

    // COLEÇÕES DO MONGODB

    private final MongoCollection<Document> lotes;
    private final MongoCollection<Document> produtos;

    // CONSTRUTOR

    public ProximosVencimentoService(
        MongoCollection<Document> lotes,
        MongoCollection<Document> produtos
    ) {
        this.lotes = lotes;
        this.produtos = produtos;
    }

    // CONSULTAR PRÓXIMOS VENCIMENTOS

    public Document consultarProximosVencimentos(
        int dias,
        ObjectId usuarioId
    ) {

        // VALIDAR USUÁRIO

        if (usuarioId == null) {
            throw new IllegalArgumentException(
                "Usuário não identificado."
            );
        }

        // VALIDAR PERÍODO

        if (!PERIODOS_VALIDOS.contains(dias)) {
            throw new IllegalArgumentException(
                "O período deve ser 1, 3, 7, 15 ou 30 dias."
            );
        }

        // CALCULAR PERÍODO
        // HOJE É CONSIDERADO O PRIMEIRO DIA

        LocalDate hoje = LocalDate.now(FUSO);

        LocalDate fimExclusivo =
            hoje.plusDays(dias);

        Date inicio = Date.from(
            hoje.atStartOfDay(FUSO).toInstant()
        );

        Date fim = Date.from(
            fimExclusivo.atStartOfDay(FUSO).toInstant()
        );

        // LISTA DE RESULTADOS

        List<Document> resultado = new ArrayList<>();

        // CONSULTAR LOTES DA CONTA AUTENTICADA
        // SOMENTE LOTES COM SALDO E NÃO INATIVOS

        try (
            MongoCursor<Document> cursor =
                lotes.find(
                    and(
                        eq("usuarioId", usuarioId),
                        gte("dataValidade", inicio),
                        lt("dataValidade", fim),
                        gt(
                            "quantidadeDisponivel",
                            new Decimal128(BigDecimal.ZERO)
                        ),
                        ne("arquivado", true),
                        ne("inativo", true)
                    )
                )
                .sort(new Document("dataValidade", 1))
                .iterator()
        ) {

            // PERCORRER LOTES

            while (cursor.hasNext()) {

                Document lote = cursor.next();

                ObjectId itemId =
                    lote.getObjectId("itemId");

                if (itemId == null) {
                    continue;
                }

                // VERIFICAR PRODUTO DA MESMA CONTA

                Document produto = produtos.find(
                    and(
                        eq("_id", itemId),
                        eq("usuarioId", usuarioId)
                    )
                ).first();

                if (produto == null) {
                    continue;
                }

                // IGNORAR PRODUTOS INATIVOS

                String status =
                    produto.getString("status");

                if (!"ativo".equalsIgnoreCase(status)) {
                    continue;
                }

                // RECUPERAR DADOS

                ObjectId loteId =
                    lote.getObjectId("_id");

                Date validade =
                    lote.getDate("dataValidade");

                Decimal128 saldo =
                    lote.get(
                        "quantidadeDisponivel",
                        Decimal128.class
                    );

                if (
                    loteId == null ||
                    validade == null ||
                    saldo == null
                ) {
                    continue;
                }

                // FORMATAR DATA DE VALIDADE

                String dataValidade =
                    validade.toInstant()
                        .atZone(FUSO)
                        .toLocalDate()
                        .toString();

                // FORMATAR QUANTIDADE

                String quantidadeDisponivel =
                    saldo.bigDecimalValue()
                        .toPlainString();

                // MONTAR DADOS PARA A TABELA

                Document registro = new Document()
                    .append(
                        "loteId",
                        loteId.toHexString()
                    )
                    .append(
                        "codigo",
                        lote.getString("codigo")
                    )
                    .append(
                        "itemId",
                        itemId.toHexString()
                    )
                    .append(
                        "produtoNome",
                        produto.getString("nome")
                    )
                    .append(
                        "quantidadeDisponivel",
                        quantidadeDisponivel
                    )
                    .append(
                        "unidadeMedida",
                        produto.getString("unidadeMedida")
                    )
                    .append(
                        "dataValidade",
                        dataValidade
                    );

                resultado.add(registro);
            }
        }

        // RESPOSTA PARA O FRONTEND

        return new Document()
            .append("dataInicio", hoje.toString())
            .append(
                "dataFim",
                fimExclusivo.minusDays(1).toString()
            )
            .append("total", resultado.size())
            .append("lotes", resultado);
    }
}
