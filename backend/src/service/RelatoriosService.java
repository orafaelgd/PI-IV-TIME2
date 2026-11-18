
package service;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RelatoriosService {

    // COLEÇÕES DO MONGODB

    private final MongoCollection<Document> itens;
    private final MongoCollection<Document> lotes;
    private final MongoCollection<Document> movimentacoes;

    // FUSO HORÁRIO

    private static final ZoneId FUSO =
        ZoneId.of("America/Sao_Paulo");

    // CONSTRUTOR

    public RelatoriosService(
        MongoCollection<Document> itens,
        MongoCollection<Document> lotes,
        MongoCollection<Document> movimentacoes
    ) {

        this.itens = itens;
        this.lotes = lotes;
        this.movimentacoes = movimentacoes;
    }

    // VALIDAR USUÁRIO

    // Impede consultar relatórios sem uma conta identificada
    private static void validarUsuario(ObjectId usuarioId) {

        if (usuarioId == null) {

            throw new IllegalArgumentException(
                "Usuario nao identificado."
            );
        }
    }

    // FILTRAR MOVIMENTAÇÕES DA CONTA

    // Relaciona a movimentação ao lote que a originou
    // O proprietário é identificado pelo usuarioId do lote
    private List<Bson> movimentacoesDaConta(
        ObjectId usuarioId
    ) {

        validarUsuario(usuarioId);

        List<Bson> etapas = new ArrayList<>();

        // Considera somente operações de desperdício
        etapas.add(
            Aggregates.match(
                Filters.in(
                    "tipo",
                    "VENCIMENTO",
                    "AJUSTE_SAIDA"
                )
            )
        );

        // Busca o lote da movimentação
        etapas.add(
            Aggregates.lookup(
                lotes.getNamespace().getCollectionName(),
                "loteId",
                "_id",
                "lote"
            )
        );

        // Exige que o lote exista
        etapas.add(
            Aggregates.unwind("$lote")
        );

        // Verifica o proprietário do lote
        etapas.add(
            Aggregates.match(
                Filters.eq(
                    "lote.usuarioId",
                    usuarioId
                )
            )
        );

        return etapas;
    }

    // FILTRAR PRODUTOS DA CONTA

    // Busca o produto do lote e valida sua propriedade
    private void adicionarProdutoDaConta(
        List<Bson> etapas,
        ObjectId usuarioId
    ) {

        etapas.add(
            Aggregates.lookup(
                itens.getNamespace().getCollectionName(),
                "lote.itemId",
                "_id",
                "produto"
            )
        );

        etapas.add(
            Aggregates.unwind("$produto")
        );

        etapas.add(
            Aggregates.match(
                Filters.eq(
                    "produto.usuarioId",
                    usuarioId
                )
            )
        );
    }

    // CONSULTAR ESTOQUE POR PRODUTO

    public Document consultarEstoquePorProduto(
        ObjectId usuarioId
    ) {

        validarUsuario(usuarioId);

        LocalDate hoje = LocalDate.now(FUSO);

        // Data de validade armazenada à meia-noite UTC
        Date inicioHoje = Date.from(
            hoje.atStartOfDay(ZoneOffset.UTC)
                .toInstant()
        );

        List<Bson> etapas = new ArrayList<>();

        // Consulta apenas lotes do usuário
        etapas.add(
            Aggregates.match(
                Filters.and(
                    Filters.eq(
                        "usuarioId",
                        usuarioId
                    ),
                    Filters.gte(
                        "dataValidade",
                        inicioHoje
                    ),
                    Filters.gt(
                        "quantidadeDisponivel",
                        new Decimal128(
                            BigDecimal.ZERO
                        )
                    )
                )
            )
        );

        // Agrupa as quantidades pelo produto
        etapas.add(
            Aggregates.group(
                "$itemId",
                Accumulators.sum(
                    "quantidadeTotal",
                    "$quantidadeDisponivel"
                )
            )
        );

        // Busca o produto cadastrado
        etapas.add(
            Aggregates.lookup(
                itens.getNamespace().getCollectionName(),
                "_id",
                "_id",
                "produto"
            )
        );

        etapas.add(
            Aggregates.unwind("$produto")
        );

        // Garante que o produto seja da mesma conta
        etapas.add(
            Aggregates.match(
                Filters.eq(
                    "produto.usuarioId",
                    usuarioId
                )
            )
        );

        // Organiza a resposta ao frontend
        etapas.add(
            Aggregates.project(
                Projections.fields(
                    Projections.excludeId(),

                    Projections.computed(
                        "itemId",
                        new Document(
                            "$toString", "$_id"
                        )
                    ),

                    Projections.computed(
                        "nome",
                        "$produto.nome"
                    ),

                    Projections.computed(
                        "unidadeMedida",
                        "$produto.unidadeMedida"
                    ),

                    Projections.include(
                        "quantidadeTotal"
                    )
                )
            )
        );

        List<Document> estoquePorProduto =
            lotes.aggregate(etapas)
                .into(new ArrayList<>());

        return new Document(
            "estoquePorProduto",
            estoquePorProduto
        );
    }

    // CONVERTER VALORES FINANCEIROS

    // Preserva a precisão dos números do MongoDB
    private static BigDecimal decimalFinanceiro(
        Object valor
    ) {

        if (valor instanceof Decimal128) {

            Decimal128 decimal =
                (Decimal128) valor;

            return decimal.isFinite()
                ? decimal.bigDecimalValue()
                : null;
        }

        if (valor instanceof Number) {

            try {

                return new BigDecimal(
                    valor.toString()
                );

            } catch (NumberFormatException erro) {

                return null;
            }
        }

        return null;
    }

    // CALCULAR PERDA DE UM LOTE

    private static BigDecimal calcularPerdaLote(
        BigDecimal quantidade,
        BigDecimal inicial,
        BigDecimal custo
    ) {

        // Multiplica antes de dividir para preservar precisão
        return quantidade.multiply(custo)
            .divide(
                inicial,
                MathContext.DECIMAL128
            );
    }

    // CONSULTAR FINANCEIRO POR PRODUTO

    public Document consultarFinanceiroPorProduto(
        ObjectId usuarioId
    ) {

        validarUsuario(usuarioId);

        List<Bson> etapas = new ArrayList<>();

        // Consulta as movimentações de desperdício
        etapas.add(
            Aggregates.match(
                Filters.in(
                    "tipo",
                    "VENCIMENTO",
                    "AJUSTE_SAIDA"
                )
            )
        );

        // Agrupa as perdas por lote
        etapas.add(
            Aggregates.group(
                "$loteId",
                Accumulators.sum(
                    "quantidadeDesperdicada",
                    "$quantidade"
                )
            )
        );

        // Busca o lote relacionado
        etapas.add(
            Aggregates.lookup(
                lotes.getNamespace().getCollectionName(),
                "_id",
                "_id",
                "lote"
            )
        );

        etapas.add(
            Aggregates.unwind("$lote")
        );

        // Filtra somente lotes da conta
        etapas.add(
            Aggregates.match(
                Filters.eq(
                    "lote.usuarioId",
                    usuarioId
                )
            )
        );

        // Valida o produto vinculado ao lote
        adicionarProdutoDaConta(
            etapas,
            usuarioId
        );

        // Organiza os dados para o cálculo
        etapas.add(
            Aggregates.project(
                Projections.fields(
                    Projections.excludeId(),

                    Projections.computed(
                        "itemId",
                        new Document(
                            "$toString",
                            "$produto._id"
                        )
                    ),

                    Projections.computed(
                        "nome",
                        "$produto.nome"
                    ),

                    Projections.computed(
                        "unidadeMedida",
                        "$produto.unidadeMedida"
                    ),

                    Projections.computed(
                        "quantidadeInicial",
                        "$lote.quantidadeInicial"
                    ),

                    Projections.computed(
                        "custoTotalAquisicao",
                        "$lote.custoTotalAquisicao"
                    ),

                    Projections.include(
                        "quantidadeDesperdicada"
                    )
                )
            )
        );

        etapas.add(
            Aggregates.sort(
                Sorts.ascending(
                    "nome",
                    "itemId"
                )
            )
        );

        List<Document> desperdiciosPorLote =
            movimentacoes.aggregate(etapas)
                .into(new ArrayList<>());

        // AGRUPAR LOTES POR PRODUTO

        Map<String, List<Document>> lotesPorProduto =
            new LinkedHashMap<>();

        for (Document lote : desperdiciosPorLote) {

            String itemId =
                lote.getString("itemId");

            lotesPorProduto
                .computeIfAbsent(
                    itemId,
                    chave -> new ArrayList<>()
                )
                .add(lote);
        }

        // CALCULAR VALORES FINANCEIROS

        List<Document> financeiroPorProduto =
            new ArrayList<>();

        for (
            List<Document> lotesDoProduto :
                lotesPorProduto.values()
        ) {

            BigDecimal quantidade =
                BigDecimal.ZERO;

            BigDecimal valorTotalEstoque =
                BigDecimal.ZERO;

            BigDecimal valorPerdido =
                BigDecimal.ZERO;

            for (Document lote : lotesDoProduto) {

                BigDecimal desperdicio =
                    decimalFinanceiro(
                        lote.get(
                            "quantidadeDesperdicada"
                        )
                    );

                BigDecimal inicial =
                    decimalFinanceiro(
                        lote.get(
                            "quantidadeInicial"
                        )
                    );

                BigDecimal custo =
                    decimalFinanceiro(
                        lote.get(
                            "custoTotalAquisicao"
                        )
                    );

                if (desperdicio == null) {

                    throw new IllegalArgumentException(
                        "Quantidade de desperdicio invalida no lote."
                    );
                }

                quantidade =
                    quantidade.add(desperdicio);

                boolean custoValido =
                    custo != null &&
                    custo.signum() >= 0;

                // Soma o custo original de cada lote
                valorTotalEstoque =
                    custoValido &&
                    valorTotalEstoque != null
                        ? valorTotalEstoque.add(custo)
                        : null;

                // Não inventa perdas quando faltam valores
                if (
                    !custoValido ||
                    inicial == null ||
                    inicial.signum() <= 0
                ) {

                    valorPerdido = null;

                } else if (
                    valorPerdido != null
                ) {

                    valorPerdido =
                        valorPerdido.add(
                            calcularPerdaLote(
                                desperdicio,
                                inicial,
                                custo
                            )
                        );
                }
            }

            // CALCULAR VALOR UNITÁRIO

            BigDecimal valorUnitario =
                valorPerdido != null &&
                quantidade.signum() > 0
                    ? valorPerdido.divide(
                        quantidade,
                        MathContext.DECIMAL128
                    )
                    : null;

            Document produto =
                lotesDoProduto.get(0);

            // Valores em texto preservam precisão no CSV
            financeiroPorProduto.add(
                new Document(
                    "itemId",
                    produto.getString("itemId")
                )
                .append(
                    "nome",
                    produto.getString("nome")
                )
                .append(
                    "unidadeMedida",
                    produto.getString("unidadeMedida")
                )
                .append(
                    "quantidadeDesperdicada",
                    quantidade.toPlainString()
                )
                .append(
                    "valorUnitario",
                    valorUnitario == null
                        ? null
                        : valorUnitario.toPlainString()
                )
                .append(
                    "valorTotalEstoque",
                    valorTotalEstoque == null
                        ? null
                        : valorTotalEstoque.toPlainString()
                )
                .append(
                    "valorPerdido",
                    valorPerdido == null
                        ? null
                        : valorPerdido.toPlainString()
                )
            );
        }

        return new Document(
            "financeiroPorProduto",
            financeiroPorProduto
        );
    }

    // CONSULTAR DESPERDÍCIO POR PRODUTO

    public Document consultarDesperdicioPorProduto(
        ObjectId usuarioId
    ) {

        // Consulta somente as movimentações da conta
        List<Bson> etapas =
            movimentacoesDaConta(usuarioId);

        // Verifica o produto vinculado
        adicionarProdutoDaConta(
            etapas,
            usuarioId
        );

        // Soma os desperdícios por produto
        etapas.add(
            Aggregates.group(
                "$produto._id",

                Accumulators.first(
                    "nome",
                    "$produto.nome"
                ),

                Accumulators.first(
                    "unidadeMedida",
                    "$produto.unidadeMedida"
                ),

                Accumulators.sum(
                    "quantidadeTotal",
                    "$quantidade"
                )
            )
        );

        // Organiza os dados do gráfico
        etapas.add(
            Aggregates.project(
                Projections.fields(
                    Projections.excludeId(),

                    Projections.computed(
                        "itemId",
                        new Document(
                            "$toString",
                            "$_id"
                        )
                    ),

                    Projections.include(
                        "nome",
                        "unidadeMedida",
                        "quantidadeTotal"
                    )
                )
            )
        );

        List<Document> desperdiciosPorProduto =
            movimentacoes.aggregate(etapas)
                .into(new ArrayList<>());

        return new Document(
            "desperdiciosPorProduto",
            desperdiciosPorProduto
        );
    }

    // CONSULTAR DESPERDÍCIO POR MOTIVO

    public Document consultaDesperdicioPorMotivo(
        ObjectId usuarioId
    ) {

        List<Bson> etapas =
            movimentacoesDaConta(usuarioId);

        // Agrupa os desperdícios por motivo
        etapas.add(
            Aggregates.group(
                "$motivo",
                Accumulators.sum(
                    "totalOcorrencias",
                    1
                )
            )
        );

        // Ordena os motivos mais frequentes
        etapas.add(
            Aggregates.sort(
                Sorts.descending(
                    "totalOcorrencias"
                )
            )
        );

        // Organiza os dados do gráfico
        etapas.add(
            Aggregates.project(
                Projections.fields(
                    Projections.excludeId(),

                    Projections.computed(
                        "motivo",
                        "$_id"
                    ),

                    Projections.include(
                        "totalOcorrencias"
                    )
                )
            )
        );

        List<Document> desperdiciosPorMotivo =
            movimentacoes.aggregate(etapas)
                .into(new ArrayList<>());

        return new Document(
            "desperdiciosPorMotivo",
            desperdiciosPorMotivo
        );
    }

    // CONSULTAR DESPERDÍCIO POR PERÍODO

    public Document consultaDesperdicioPorPeriodo(
        LocalDate inicio,
        LocalDate fim,
        ObjectId usuarioId
    ) {

        validarUsuario(usuarioId);

        Date dataInicio;
        Date dataFim;

        try {

            dataInicio = Date.from(
                inicio.atStartOfDay(FUSO)
                    .toInstant()
            );

            dataFim = Date.from(
                fim.plusDays(1)
                    .atStartOfDay(FUSO)
                    .toInstant()
            );

        } catch (
            DateTimeException |
            ArithmeticException erro
        ) {

            throw new IllegalArgumentException(
                "Intervalo de datas fora do limite permitido.",
                erro
            );
        }

        // AGRUPAR POR DIA

        Document agrupamentoData = new Document(
            "$dateToString",
            new Document(
                "format",
                "%Y-%m-%d"
            )
            .append(
                "date",
                "$dataMovimentacao"
            )
            .append(
                "timezone",
                "America/Sao_Paulo"
            )
        );

        List<Bson> etapas =
            movimentacoesDaConta(usuarioId);

        // Filtra as datas solicitadas
        etapas.add(
            Aggregates.match(
                Filters.and(
                    Filters.gte(
                        "dataMovimentacao",
                        dataInicio
                    ),
                    Filters.lt(
                        "dataMovimentacao",
                        dataFim
                    )
                )
            )
        );

        // Conta as ocorrências por dia
        etapas.add(
            Aggregates.group(
                agrupamentoData,
                Accumulators.sum(
                    "totalOcorrencias",
                    1
                )
            )
        );

        etapas.add(
            Aggregates.sort(
                Sorts.ascending("_id")
            )
        );

        etapas.add(
            Aggregates.project(
                Projections.fields(
                    Projections.excludeId(),

                    Projections.computed(
                        "data",
                        "$_id"
                    ),

                    Projections.include(
                        "totalOcorrencias"
                    )
                )
            )
        );

        List<Document> desperdicioPorPeriodo =
            movimentacoes.aggregate(etapas)
                .into(new ArrayList<>());

        return new Document(
            "desperdicioPorPeriodo",
            desperdicioPorPeriodo
        );
    }

    // CONSULTAR INDICADORES GERAIS

    public Document consultaIndicadoresGerais(
        ObjectId usuarioId
    ) {

        validarUsuario(usuarioId);

        // PRODUTOS ATIVOS DA CONTA

        long totalRegistrados = itens.countDocuments(
            Filters.and(
                Filters.eq(
                    "usuarioId",
                    usuarioId
                ),
                Filters.eq(
                    "status",
                    "ativo"
                )
            )
        );

        // TOTAL DE DESPERDÍCIOS DA CONTA

        List<Bson> etapas =
            movimentacoesDaConta(usuarioId);

        etapas.add(
            Aggregates.count("total")
        );

        Document contagem =
            movimentacoes.aggregate(etapas)
                .first();

        long totalDesperdicios =
            contagem == null
                ? 0L
                : contagem.get(
                    "total",
                    Number.class
                ).longValue();

        // MOTIVO MAIS FREQUENTE

        Document resultadoMotivos =
            consultaDesperdicioPorMotivo(
                usuarioId
            );

        List<Document> motivos =
            resultadoMotivos.getList(
                "desperdiciosPorMotivo",
                Document.class
            );

        String motivoFrequente =
            motivos.isEmpty()
                ? null
                : motivos.get(0)
                    .getString("motivo");

        // PERDAS FINANCEIRAS

        Decimal128 perdasFinanceiras =
            calcularPerdasFinanceiras(
                usuarioId
            );

        // RETORNAR INDICADORES

        return new Document()
            .append(
                "registrados",
                totalRegistrados
            )
            .append(
                "desperdicados",
                totalDesperdicios
            )
            .append(
                "motivoMaisFrequente",
                motivoFrequente
            )
            .append(
                "perdasFinanceiras",
                perdasFinanceiras
            );
    }

    // CALCULAR PERDAS FINANCEIRAS

    private Decimal128 calcularPerdasFinanceiras(
        ObjectId usuarioId
    ) {

        validarUsuario(usuarioId);

        List<Bson> etapas = new ArrayList<>();

        // Considera somente os desperdícios
        etapas.add(
            Aggregates.match(
                Filters.in(
                    "tipo",
                    "VENCIMENTO",
                    "AJUSTE_SAIDA"
                )
            )
        );

        // Soma o desperdício por lote
        etapas.add(
            Aggregates.group(
                "$loteId",
                Accumulators.sum(
                    "quantidadeDesperdicada",
                    "$quantidade"
                )
            )
        );

        // Busca o lote
        etapas.add(
            Aggregates.lookup(
                lotes.getNamespace().getCollectionName(),
                "_id",
                "_id",
                "lote"
            )
        );

        etapas.add(
            Aggregates.unwind("$lote")
        );

        // Filtra os lotes do usuário
        etapas.add(
            Aggregates.match(
                Filters.eq(
                    "lote.usuarioId",
                    usuarioId
                )
            )
        );

        // Organiza os valores para os cálculos
        etapas.add(
            Aggregates.project(
                Projections.fields(
                    Projections.excludeId(),

                    Projections.computed(
                        "quantidadeInicial",
                        "$lote.quantidadeInicial"
                    ),

                    Projections.computed(
                        "custoTotalAquisicao",
                        "$lote.custoTotalAquisicao"
                    ),

                    Projections.include(
                        "quantidadeDesperdicada"
                    )
                )
            )
        );

        List<Document> desperdiciosPorLote =
            movimentacoes.aggregate(etapas)
                .into(new ArrayList<>());

        BigDecimal total =
            BigDecimal.ZERO;

        // SOMAR PERDAS

        for (
            Document lote :
                desperdiciosPorLote
        ) {

            BigDecimal quantidade =
                decimalFinanceiro(
                    lote.get(
                        "quantidadeDesperdicada"
                    )
                );

            BigDecimal inicial =
                decimalFinanceiro(
                    lote.get(
                        "quantidadeInicial"
                    )
                );

            BigDecimal custo =
                decimalFinanceiro(
                    lote.get(
                        "custoTotalAquisicao"
                    )
                );

            if (
                quantidade != null &&
                inicial != null &&
                inicial.signum() > 0 &&
                custo != null &&
                custo.signum() >= 0
            ) {

                total = total.add(
                    calcularPerdaLote(
                        quantidade,
                        inicial,
                        custo
                    )
                );
            }
        }

        // Mantém o formato Decimal128 do MongoDB
        return new Decimal128(
            total.round(
                MathContext.DECIMAL128
            )
        );
    }

    // CONSULTAR EVOLUÇÃO MENSAL

    public Document consultarEvolucaoMensal(
        ObjectId usuarioId
    ) {

        validarUsuario(usuarioId);

        YearMonth mesAtual =
            YearMonth.now(FUSO);

        // Considera os últimos seis meses
        YearMonth primeiroMes =
            mesAtual.minusMonths(5);

        Date inicio = Date.from(
            primeiroMes.atDay(1)
                .atStartOfDay(FUSO)
                .toInstant()
        );

        Date fim = Date.from(
            mesAtual.plusMonths(1)
                .atDay(1)
                .atStartOfDay(FUSO)
                .toInstant()
        );

        // AGRUPAR POR MÊS

        Document agrupamentoMes = new Document(
            "$dateToString",
            new Document(
                "format",
                "%Y-%m"
            )
            .append(
                "date",
                "$dataMovimentacao"
            )
            .append(
                "timezone",
                "America/Sao_Paulo"
            )
        );

        // Busca somente movimentações da conta
        List<Bson> etapas =
            movimentacoesDaConta(usuarioId);

        // Filtra os últimos seis meses
        etapas.add(
            Aggregates.match(
                Filters.and(
                    Filters.gte(
                        "dataMovimentacao",
                        inicio
                    ),
                    Filters.lt(
                        "dataMovimentacao",
                        fim
                    )
                )
            )
        );

        // Conta as ocorrências por mês
        etapas.add(
            Aggregates.group(
                agrupamentoMes,
                Accumulators.sum(
                    "totalOcorrencias",
                    1
                )
            )
        );

        etapas.add(
            Aggregates.sort(
                Sorts.ascending("_id")
            )
        );

        List<Document> resultados =
            movimentacoes.aggregate(etapas)
                .into(new ArrayList<>());

        // MAPEAR OCORRÊNCIAS

        Map<String, Long> ocorrenciasPorMes =
            new HashMap<>();

        for (Document resultado : resultados) {

            ocorrenciasPorMes.put(
                resultado.getString("_id"),
                resultado.get(
                    "totalOcorrencias",
                    Number.class
                ).longValue()
            );
        }

        // ORGANIZAR SEIS MESES

        List<Document> evolucaoMensal =
            new ArrayList<>();

        for (int i = 0; i < 6; i++) {

            String mes = primeiroMes
                .plusMonths(i)
                .toString();

            evolucaoMensal.add(
                new Document(
                    "mes",
                    mes
                )
                .append(
                    "totalOcorrencias",
                    ocorrenciasPorMes
                        .getOrDefault(
                            mes,
                            0L
                        )
                )
            );
        }

        return new Document(
            "evolucaoMensal",
            evolucaoMensal
        );
    }
}
