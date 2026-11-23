
package service;

import com.mongodb.client.MongoCollection;

import org.bson.Document;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.mongodb.client.model.Filters.*;

public class DashboardService {

    // COLEÇÕES DO MONGODB

    private final MongoCollection<Document> itens;
    private final MongoCollection<Document> lotes;

    // FUSO HORÁRIO

    private static final ZoneId FUSO =
        ZoneId.of("America/Sao_Paulo");

    // CONSTRUTOR

    public DashboardService(
        MongoCollection<Document> itens,
        MongoCollection<Document> lotes
    ) {

        this.itens = itens;
        this.lotes = lotes;
    }

    // CONVERTER VALORES

    // Converte valores numéricos do MongoDB
    private static BigDecimal obterDecimal(
        Document documento,
        String campo
    ) {

        Object valor = documento.get(campo);

        if (valor instanceof Decimal128) {

            return ((Decimal128) valor)
                .bigDecimalValue();
        }

        if (valor instanceof Number) {

            return new BigDecimal(
                valor.toString()
            );
        }

        if (valor instanceof String) {

            try {

                return new BigDecimal(
                    ((String) valor).replace(",", ".")
                );

            } catch (NumberFormatException erro) {

                return BigDecimal.ZERO;
            }
        }

        return BigDecimal.ZERO;
    }

    // CONVERTER DATA

    // Os lotes armazenam datas à meia-noite UTC
    private static LocalDate obterData(
        Document documento,
        String campo
    ) {

        Date data = documento.getDate(campo);

        if (data == null) {
            return null;
        }

        return data.toInstant()
            .atZone(ZoneOffset.UTC)
            .toLocalDate();
    }

    // VERIFICAR PRODUTO ATIVO

    private static boolean produtoAtivo(
        Document produto
    ) {

        return produto != null &&
            "ativo".equalsIgnoreCase(
                produto.getString("status")
            );
    }

    // VERIFICAR LOTE DISPONÍVEL

    private static boolean loteDisponivel(
        Document lote
    ) {

        if (
            Boolean.TRUE.equals(
                lote.getBoolean("inativo")
            )
        ) {
            return false;
        }

        if (
            Boolean.TRUE.equals(
                lote.getBoolean("arquivado")
            )
        ) {
            return false;
        }

        return obterDecimal(
            lote, "quantidadeDisponivel"
        ).compareTo(BigDecimal.ZERO) > 0;
    }

    // CONSULTAR RESUMO DO DASHBOARD

    public Document consultarResumo(
        ObjectId usuarioId
    ) {

        // Impede consultas sem um usuário válido
        if (usuarioId == null) {

            throw new IllegalArgumentException(
                "Usuario nao identificado."
            );
        }

        // CONTAR PRODUTOS

        // Conta somente os produtos da conta
        long totalProdutos = itens.countDocuments(
            eq("usuarioId", usuarioId)
        );

        long produtosAtivos = itens.countDocuments(
            and(
                eq("usuarioId", usuarioId),
                eq("status", "ativo")
            )
        );

        long produtosInativos = itens.countDocuments(
            and(
                eq("usuarioId", usuarioId),
                eq("status", "inativo")
            )
        );

        // CONTAR LOTES

        // Conta somente os lotes da conta
        long totalLotes = lotes.countDocuments(
            eq("usuarioId", usuarioId)
        );

        // DEFINIR PERÍODO

        LocalDate hoje = LocalDate.now(FUSO);

        // Inclui hoje e os vencimentos até daqui a sete dias.
        LocalDate ultimoDia = hoje.plusDays(7);

        // BUSCAR PRODUTOS DO USUÁRIO

        // Guarda os produtos para relacionar com os lotes
        Map<ObjectId, Document> produtosPorId =
            new HashMap<>();

        for (
            Document produto : itens.find(
                eq("usuarioId", usuarioId)
            )
        ) {

            ObjectId id = produto.getObjectId("_id");

            if (id != null) {

                produtosPorId.put(
                    id, produto
                );
            }
        }

        // DADOS DOS INDICADORES

        long lotesProximosVencimento = 0;

        List<Document> proximosVencimentos =
            new ArrayList<>();

        Map<ObjectId, BigDecimal> saldos =
            new HashMap<>();

        // CONSULTAR LOTES DO USUÁRIO

        for (
            Document lote : lotes.find(
                eq("usuarioId", usuarioId)
            )
        ) {

            // Identifica o produto do lote
            ObjectId itemId =
                lote.getObjectId("itemId");

            if (itemId == null) {
                continue;
            }

            Document produto =
                produtosPorId.get(itemId);

            // Ignora referências que não pertencem à conta
            if (produto == null) {
                continue;
            }

            // Obtém a data de validade
            LocalDate validade =
                obterData(lote, "dataValidade");

            if (validade == null) {
                continue;
            }

            // Lotes disponíveis e não vencidos
            if (
                !validade.isBefore(hoje) &&
                loteDisponivel(lote)
            ) {

                // SOMAR ESTOQUE POR PRODUTO

                BigDecimal quantidade =
                    obterDecimal(
                        lote, "quantidadeDisponivel"
                    );

                saldos.merge(
                    itemId,
                    quantidade,
                    BigDecimal::add
                );
            }

            // VERIFICAR PRÓXIMOS VENCIMENTOS

            // Considera apenas produtos ativos
            if (!produtoAtivo(produto)) {
                continue;
            }

            if (!loteDisponivel(lote)) {
                continue;
            }

            // Verifica o período de sete dias
            if (
                validade.isBefore(hoje) ||
                validade.isAfter(ultimoDia)
            ) {
                continue;
            }

            lotesProximosVencimento++;

            // Recupera o nome e a unidade do produto
            String nomeProduto =
                produto.getString("nome");

            if (
                nomeProduto == null ||
                nomeProduto.isBlank()
            ) {

                nomeProduto = "Produto sem nome";
            }

            String unidade =
                produto.getString("unidadeMedida");

            if (
                unidade == null ||
                unidade.isBlank()
            ) {

                unidade = "";
            }

            // Calcula quantos dias faltam
            long diasRestantes =
                ChronoUnit.DAYS.between(
                    hoje, validade
                );

            // ORGANIZAR DADOS DA TABELA

            Document vencimento = new Document()
                .append(
                    "produtoNome",
                    nomeProduto
                )
                .append(
                    "codigo",
                    lote.getString("codigo")
                )
                .append(
                    "quantidadeDisponivel",
                    obterDecimal(
                        lote,
                        "quantidadeDisponivel"
                    ).toPlainString()
                )
                .append(
                    "unidadeMedida",
                    unidade
                )
                .append(
                    "dataValidade",
                    validade.toString()
                )
                .append(
                    "diasRestantes",
                    diasRestantes
                );

            proximosVencimentos.add(vencimento);
        }

        // ORDENAR PRÓXIMOS VENCIMENTOS

        // Mostra primeiro os lotes que vencem antes
        proximosVencimentos.sort(
            (primeiro, segundo) -> {

                String dataPrimeiro =
                    primeiro.getString("dataValidade");

                String dataSegundo =
                    segundo.getString("dataValidade");

                return dataPrimeiro.compareTo(
                    dataSegundo
                );
            }
        );

        // ORGANIZAR SALDOS POR PRODUTO

        List<Document> saldosPorProduto =
            new ArrayList<>();

        for (
            Map.Entry<ObjectId, BigDecimal> entrada
                : saldos.entrySet()
        ) {

            Document saldo = new Document()
                .append("_id", entrada.getKey())
                .append(
                    "quantidadeTotal",
                    new Decimal128(
                        entrada.getValue()
                    )
                );

            saldosPorProduto.add(saldo);
        }

        // RETORNAR RESUMO

        return new Document()
            .append(
                "totalProdutos",
                totalProdutos
            )
            .append(
                "produtosAtivos",
                produtosAtivos
            )
            .append(
                "produtosInativos",
                produtosInativos
            )
            .append(
                "totalLotes",
                totalLotes
            )
            .append(
                "lotesProximosVencimento",
                lotesProximosVencimento
            )
            .append(
                "saldoPorProduto",
                saldosPorProduto
            )
            .append(
                "proximosVencimentos",
                proximosVencimentos
            )
            .append(
                "dataInicio",
                hoje.toString()
            );
    }
}
