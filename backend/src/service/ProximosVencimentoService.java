
package service;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;

import org.bson.Document;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static com.mongodb.client.model.Filters.*;

public class ProximosVencimentoService {

    // =====================================================
    // CONFIGURAÇÕES DE DATA E PERÍODOS PERMITIDOS
    // Define o fuso horário utilizado pelo sistema
    // e os intervalos disponíveis para consulta.
    // =====================================================

    private static final ZoneId FUSO =
        ZoneId.of("America/Sao_Paulo");

    private static final Set<Integer> PERIODOS_VALIDOS =
        Set.of(1, 3, 7, 15, 30);

    // =====================================================
    // COLEÇÕES DO MONGODB
    // Armazena as referências às coleções de lotes
    // e produtos utilizadas nas consultas.
    // =====================================================

    private final MongoCollection<Document> lotes;
    private final MongoCollection<Document> produtos;

    // =====================================================
    // CONSTRUTOR
    // Recebe as coleções do MongoDB disponibilizadas
    // pelo Controller para realizar as consultas.
    // =====================================================

    public ProximosVencimentoService(
        MongoCollection<Document> lotes,
        MongoCollection<Document> produtos
    ) {
        this.lotes = lotes;
        this.produtos = produtos;
    }

    // =====================================================
    // CONSULTA DOS PRÓXIMOS VENCIMENTOS
    // Executa as regras de negócio para identificar
    // lotes próximos do vencimento pertencentes ao
    // usuário autenticado.
    // =====================================================

    public Document consultarProximosVencimentos(
        int dias,
        ObjectId usuarioId
    ) {

        // VALIDAÇÃO DO USUÁRIO
        // Impede consultas quando não existe
        // um identificador de usuário válido.

        if (usuarioId == null) {
            throw new IllegalArgumentException(
                "Usuário não identificado."
            );
        }

        // VALIDAÇÃO DO PERÍODO
        // Permite apenas os intervalos de dias
        // definidos pela funcionalidade.

        if (!PERIODOS_VALIDOS.contains(dias)) {
            throw new IllegalArgumentException(
                "O período deve ser 1, 3, 7, 15 ou 30 dias."
            );
        }

        // =================================================
        // DEFINIÇÃO DO INTERVALO DE DATAS
        // Inclui hoje e o vencimento exatamente daqui a N dias.
        // O filtro Hoje (1) consulta somente o dia atual.
        // O limite exclusivo é a meia-noite do dia seguinte.
        // =================================================

        LocalDate hoje = LocalDate.now(FUSO);

        LocalDate fimExclusivo =
            hoje.plusDays(dias == 1 ? 1 : dias + 1);

        // As datas dos lotes são gravadas à meia-noite UTC.
        // São Paulo define apenas qual é o dia atual da consulta.
        Date inicio = Date.from(
            hoje.atStartOfDay(ZoneOffset.UTC).toInstant()
        );

        Date fim = Date.from(
            fimExclusivo.atStartOfDay(ZoneOffset.UTC).toInstant()
        );

        // LISTA DE RESULTADOS
        // Armazena os lotes encontrados após
        // a aplicação de todos os critérios.

        List<Document> resultado = new ArrayList<>();

        // =================================================
        // CONSULTA DOS LOTES NO MONGODB
        // Busca somente os lotes da conta autenticada,
        // com validade dentro do período selecionado,
        // saldo positivo e que não estejam arquivados
        // ou marcados como inativos.
        // =================================================

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

            // =================================================
            // PROCESSAMENTO DOS LOTES ENCONTRADOS
            // Percorre os registros e verifica a existência
            // dos produtos relacionados antes de incluí-los
            // no resultado da consulta.
            // =================================================

            while (cursor.hasNext()) {

                Document lote = cursor.next();

                ObjectId itemId =
                    lote.getObjectId("itemId");

                if (itemId == null) {
                    continue;
                }

                // =================================================
                // VERIFICAÇÃO DO PRODUTO E DO USUÁRIO
                // Confirma que o produto relacionado ao lote
                // pertence à mesma conta autenticada.
                // =================================================

                Document produto = produtos.find(
                    and(
                        eq("_id", itemId),
                        eq("usuarioId", usuarioId)
                    )
                ).first();

                if (produto == null) {
                    continue;
                }

                // FILTRO DE PRODUTOS ATIVOS
                // Ignora os produtos que não possuem
                // o status ativo no cadastro.

                String status =
                    produto.getString("status");

                if (!"ativo".equalsIgnoreCase(status)) {
                    continue;
                }

                // =================================================
                // RECUPERAÇÃO DOS DADOS DO LOTE
                // Obtém o identificador, a data de validade
                // e o saldo disponível para apresentação.
                // =================================================

                ObjectId loteId =
                    lote.getObjectId("_id");

                Date validade =
                    lote.getDate("dataValidade");

                Decimal128 saldo =
                    lote.get(
                        "quantidadeDisponivel",
                        Decimal128.class
                    );

                // Ignora registros sem as informações
                // necessárias para a tabela.

                if (
                    loteId == null ||
                    validade == null ||
                    saldo == null
                ) {
                    continue;
                }

                // =================================================
                // FORMATAÇÃO DA DATA E DA QUANTIDADE
                // Preserva o dia da validade ao ler a data em UTC.
                // Converte o saldo Decimal128 para texto, preservando
                // a representação decimal do valor.
                // =================================================

                String dataValidade =
                    validade.toInstant()
                        .atZone(ZoneOffset.UTC)
                        .toLocalDate()
                        .toString();

                String quantidadeDisponivel =
                    saldo.bigDecimalValue()
                        .toPlainString();

                // =================================================
                // ORGANIZAÇÃO DOS DADOS PARA O FRONTEND
                // Monta um documento com as informações
                // utilizadas na tabela de próximos vencimentos.
                // =================================================

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

        // =====================================================
        // MONTAGEM DA RESPOSTA FINAL
        // Retorna o intervalo consultado, a quantidade
        // de lotes encontrados e a lista de registros
        // para apresentação no frontend.
        // =====================================================

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
