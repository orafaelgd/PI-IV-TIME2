// Código feito por Livia Carvalho Lucizano RA: 25017514

package service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;

import org.bson.Document;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;

import static com.mongodb.client.model.Filters.*;

public class ProdutosService {

    // COLEÇÕES DO MONGODB

    private final MongoCollection<Document> itens;
    private final MongoCollection<Document> categorias;
    private final MongoCollection<Document> lotes;
    private final MongoCollection<Document> movimentacoes;

    // TRATAMENTO DE ERROS

    public static class ProdutoNaoEncontradoException
        extends RuntimeException {

        public ProdutoNaoEncontradoException() {
            super("Produto nao encontrado.");
        }
    }

    public static class ConflitoExclusaoException
        extends RuntimeException {

        public ConflitoExclusaoException(String mensagem) {
            super(mensagem);
        }
    }

    // CONSTRUTOR

    public ProdutosService(
        MongoCollection<Document> itens,
        MongoCollection<Document> categorias,
        MongoCollection<Document> lotes,
        MongoCollection<Document> movimentacoes
    ) {
        this.itens = itens;
        this.categorias = categorias;
        this.lotes = lotes;
        this.movimentacoes = movimentacoes;
    }

    // CATEGORIAS

    // Lista somente as categorias do usuário autenticado
    public Document listarCategorias(ObjectId usuarioId) {

        List<Document> lista = new ArrayList<>();

        for (Document categoria : categorias.find(
            eq("usuarioId", usuarioId)
        )) {

            String nome = categoria.getString("nome");
            ObjectId id = categoria.getObjectId("_id");

            if (nome == null || nome.isBlank() || id == null) {
                continue;
            }

            lista.add(
                new Document()
                    .append("id", id.toHexString())
                    .append("nome", nome)
            );
        }

        return new Document("categorias", lista);
    }

    // Gera um código padronizado para a categoria
    private static String gerarCodigo(String nome) {

        String texto = Normalizer.normalize(
            nome.trim().toLowerCase(Locale.ROOT),
            Normalizer.Form.NFD
        );

        return texto
            .replaceAll("\\p{M}", "")
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-|-$", "");
    }

    // BUSCAR OU CRIAR CATEGORIA

    // Cada usuário possui suas próprias categorias
    private ObjectId buscarOuCriarCategoria(
        String nome,
        ObjectId usuarioId
    ) {

        String codigoBase = gerarCodigo(nome);

        if (codigoBase.isEmpty()) {
            throw new IllegalArgumentException(
                "Categoria invalida."
            );
        }

        // Acrescenta o ID do usuário ao código
        // Evita conflitos com categorias de outras contas
        String codigo = usuarioId.toHexString()
            + "-" + codigoBase;

        // Busca a categoria somente na conta atual
        Document categoria = categorias.find(
            and(
                eq("usuarioId", usuarioId),
                eq("codigo", codigo)
            )
        ).first();

        // Verifica também o nome da categoria
        if (categoria == null) {

            categoria = categorias.find(
                and(
                    eq("usuarioId", usuarioId),
                    regex(
                        "nome",
                        "^" + Pattern.quote(nome) + "$",
                        "i"
                    )
                )
            ).first();
        }

        // Cadastra a categoria quando ela não existe
        if (categoria == null) {

            categoria = new Document()
                .append("nome", nome)
                .append("codigo", codigo)
                .append("usuarioId", usuarioId)
                .append("criadoEm", new Date());

            categorias.insertOne(categoria);
        }

        return categoria.getObjectId("_id");
    }

    // BUSCAR NOME DA CATEGORIA

    // Impede consultar categorias de outra conta
    private String buscarNomeCategoria(
        Document produto,
        ObjectId usuarioId
    ) {

        ObjectId categoriaId =
            produto.getObjectId("categoriaId");

        if (categoriaId == null) {
            return "Sem categoria";
        }

        Document categoria = categorias.find(
            and(
                eq("_id", categoriaId),
                eq("usuarioId", usuarioId)
            )
        ).first();

        if (categoria == null) {
            return "Sem categoria";
        }

        return categoria.getString("nome");
    }

    // CONVERSÃO DOS DADOS

    // Recupera os campos de texto do formulário
    private static String obterTexto(
        Document documento,
        String campo
    ) {

        Object valor = documento.get(campo);

        return valor instanceof String
            ? ((String) valor).trim()
            : "";
    }

    // VALIDAÇÃO DOS PRODUTOS

    // Valida as informações antes de salvar no banco
    private Document validarProduto(
        Document dados,
        ObjectId usuarioId
    ) {

        String nome = obterTexto(
            dados, "nomeProduto"
        );

        String categoria = obterTexto(
            dados, "categoria"
        ).replaceAll("\\s+", " ");

        String quantidadeTexto = obterTexto(
            dados, "quantidadeUnidade"
        );

        String unidade = obterTexto(
            dados, "unidadeMedida"
        ).toLowerCase(Locale.ROOT);

        String status = obterTexto(
            dados, "status"
        ).toLowerCase(Locale.ROOT);

        String observacao = obterTexto(
            dados, "observacao"
        );

        // VALIDAR NOME

        if (nome.isEmpty() || nome.length() > 150) {
            throw new IllegalArgumentException(
                "Nome do produto invalido."
            );
        }

        // VALIDAR CATEGORIA

        if (categoria.isEmpty() || categoria.length() > 100) {
            throw new IllegalArgumentException(
                "Categoria invalida."
            );
        }

        // VALIDAR QUANTIDADE

        BigDecimal quantidade;

        try {
            quantidade = new BigDecimal(
                quantidadeTexto.replace(",", ".")
            );

        } catch (NumberFormatException erro) {
            throw new IllegalArgumentException(
                "Quantidade da unidade invalida."
            );
        }

        if (
            quantidade.signum() <= 0 ||
            quantidade.compareTo(
                BigDecimal.valueOf(Double.MAX_VALUE)
            ) > 0
        ) {
            throw new IllegalArgumentException(
                "Quantidade da unidade invalida."
            );
        }

        // VALIDAR UNIDADE DE MEDIDA

        if (!Set.of("kg", "g", "l", "ml", "un")
            .contains(unidade)) {

            throw new IllegalArgumentException(
                "Unidade de medida invalida."
            );
        }

        // VALIDAR STATUS

        if (!Set.of("ativo", "inativo").contains(status)) {
            throw new IllegalArgumentException(
                "Status invalido."
            );
        }

        // VALIDAR OBSERVAÇÃO

        if (
            observacao.isEmpty() ||
            observacao.length() > 1000
        ) {
            throw new IllegalArgumentException(
                "Observacao invalida."
            );
        }

        // VINCULAR CATEGORIA AO USUÁRIO

        ObjectId categoriaId =
            buscarOuCriarCategoria(categoria, usuarioId);

        // Organiza os dados antes de salvar
        return new Document()
            .append("nome", nome)
            .append("categoriaId", categoriaId)
            .append(
                "quantidadeUnidade",
                quantidade.doubleValue()
            )
            .append("unidadeMedida", unidade)
            .append("status", status)
            .append("observacao", observacao);
    }

    // CADASTRAR PRODUTO

    public Document cadastrarProduto(
        Document dados,
        ObjectId usuarioId
    ) {

        // Valida as informações recebidas
        Document produto = validarProduto(dados, usuarioId);

        // Identifica o dono do produto
        produto.append("usuarioId", usuarioId);

        produto.append("criadoEm", new Date());

        // Salva o produto na conta autenticada
        itens.insertOne(produto);

        return new Document()
            .append(
                "mensagem",
                "Produto cadastrado com sucesso!"
            )
            .append(
                "id",
                produto.getObjectId("_id").toHexString()
            );
    }

    // BUSCAR PRODUTO DO USUÁRIO

    // Verifica se o produto pertence à conta autenticada
    private Document encontrarProduto(
        ObjectId id,
        ObjectId usuarioId
    ) {

        Document produto = itens.find(
            and(
                eq("_id", id),
                eq("usuarioId", usuarioId)
            )
        ).first();

        if (produto == null) {
            throw new ProdutoNaoEncontradoException();
        }

        return produto;
    }

    // BUSCAR PRODUTO PARA EDIÇÃO

    public Document buscarProduto(
        ObjectId id,
        ObjectId usuarioId
    ) {

        Document produto = encontrarProduto(id, usuarioId);

        Document resposta = new Document()
            .append("id", id.toHexString())
            .append(
                "nome",
                produto.getString("nome")
            )
            .append(
                "categoria",
                buscarNomeCategoria(produto, usuarioId)
            )
            .append(
                "quantidadeUnidade",
                obterDecimal(
                    produto, "quantidadeUnidade"
                ).toPlainString()
            )
            .append(
                "unidadeMedida",
                produto.getString("unidadeMedida")
            )
            .append(
                "status",
                produto.getString("status")
            )
            .append(
                "observacao",
                produto.getString("observacao")
            );

        return new Document("produto", resposta);
    }

    // ATUALIZAR PRODUTO

    public void atualizarProduto(
        ObjectId id,
        Document dados,
        ObjectId usuarioId
    ) {

        // Confere se o produto pertence ao usuário
        encontrarProduto(id, usuarioId);

        // Valida os novos dados
        Document alteracoes = validarProduto(
            dados, usuarioId
        );

        alteracoes.append("atualizadoEm", new Date());

        // Altera somente o produto da conta autenticada
        UpdateResult resultado = itens.updateOne(
            and(
                eq("_id", id),
                eq("usuarioId", usuarioId)
            ),
            new Document("$set", alteracoes)
        );

        if (resultado.getMatchedCount() == 0) {
            throw new ProdutoNaoEncontradoException();
        }
    }

    // CONSULTAR MOVIMENTAÇÕES

    // Verifica movimentações diretamente no produto
    // ou nos lotes vinculados a ele
    private boolean possuiMovimentacoes(ObjectId produtoId) {

        if (movimentacoes.countDocuments(
            or(
                eq("itemId", produtoId),
                eq("produtoId", produtoId)
            )
        ) > 0) {
            return true;
        }

        List<ObjectId> idsLotes = new ArrayList<>();

        // Busca os lotes pertencentes ao produto
        for (Document lote : lotes.find(
            eq("itemId", produtoId)
        )) {

            ObjectId loteId = lote.getObjectId("_id");

            if (loteId != null) {
                idsLotes.add(loteId);
            }
        }

        if (idsLotes.isEmpty()) {
            return false;
        }

        // Verifica movimentações vinculadas aos lotes
        return movimentacoes.countDocuments(
            in("loteId", idsLotes)
        ) > 0;
    }

    // CONSULTAR LOTES

    // Verifica se existem lotes vinculados ao produto
    private boolean possuiLotes(ObjectId produtoId) {

        return lotes.countDocuments(
            eq("itemId", produtoId)
        ) > 0;
    }

    // VERIFICAR EXCLUSÃO

    public Document verificarExclusao(
        ObjectId id,
        ObjectId usuarioId
    ) {

        // Confere se o produto pertence ao usuário
        encontrarProduto(id, usuarioId);

        boolean temMovimentacoes =
            possuiMovimentacoes(id);

        boolean temLotes =
            possuiLotes(id);

        boolean podeExcluir =
            !temMovimentacoes && !temLotes;

        String mensagem;

        // Impede excluir produtos com movimentações
        if (temMovimentacoes) {

            mensagem =
                "Este produto nao pode ser excluido, " +
                "pois possui movimentacoes registradas. " +
                "Deseja torna-lo inativo?";

        } else if (temLotes) {

            mensagem =
                "Este produto nao pode ser excluido, " +
                "pois possui lotes vinculados. " +
                "Deseja torna-lo inativo?";

        } else {

            mensagem =
                "Tem certeza de que deseja excluir este produto?";
        }

        return new Document()
            .append("podeExcluir", podeExcluir)
            .append(
                "possuiMovimentacoes",
                temMovimentacoes
            )
            .append("possuiLotes", temLotes)
            .append("mensagem", mensagem);
    }

    // EXCLUIR PRODUTO

    public void excluirProduto(
        ObjectId id,
        ObjectId usuarioId
    ) {

        // Verifica o proprietário do produto
        encontrarProduto(id, usuarioId);

        // Não permite excluir produtos com histórico
        if (possuiMovimentacoes(id) || possuiLotes(id)) {

            throw new ConflitoExclusaoException(
                "Este produto nao pode ser excluido, " +
                "pois possui movimentacoes ou lotes vinculados. " +
                "Deseja torna-lo inativo?"
            );
        }

        // Exclui somente registros da conta atual
        DeleteResult resultado = itens.deleteOne(
            and(
                eq("_id", id),
                eq("usuarioId", usuarioId)
            )
        );

        if (resultado.getDeletedCount() == 0) {
            throw new ProdutoNaoEncontradoException();
        }
    }

    // ALTERAR STATUS

    public String alterarStatus(
        ObjectId id,
        Document dados,
        ObjectId usuarioId
    ) {

        // Confere se o produto pertence ao usuário
        encontrarProduto(id, usuarioId);

        String status = obterTexto(
            dados, "status"
        ).toLowerCase(Locale.ROOT);

        // Valida o status informado
        if (!Set.of("ativo", "inativo").contains(status)) {
            throw new IllegalArgumentException(
                "Status invalido."
            );
        }

        // Atualiza somente o produto do usuário
        UpdateResult resultado = itens.updateOne(
            and(
                eq("_id", id),
                eq("usuarioId", usuarioId)
            ),
            new Document(
                "$set",
                new Document()
                    .append("status", status)
                    .append("atualizadoEm", new Date())
            )
        );

        if (resultado.getMatchedCount() == 0) {
            throw new ProdutoNaoEncontradoException();
        }

        return status.equals("inativo")
            ? "Produto inativado com sucesso!"
            : "Status atualizado com sucesso!";
    }

    // LISTAR PRODUTOS

    // Retorna somente produtos do usuário autenticado
    public Document listarProdutos(ObjectId usuarioId) {

        List<Document> listaProdutos = new ArrayList<>();

        // Filtra os produtos pelo ID do usuário
        for (Document produto : itens.find(
            eq("usuarioId", usuarioId)
        )) {

            ObjectId produtoId =
                produto.getObjectId("_id");

            String nomeCategoria =
                buscarNomeCategoria(produto, usuarioId);

            BigDecimal quantidadeUnidade =
                obterDecimal(produto, "quantidadeUnidade");

            BigDecimal quantidadeTotal =
                BigDecimal.ZERO;

            BigDecimal custoEstoque =
                BigDecimal.ZERO;

            BigDecimal quantidadeComPreco =
                BigDecimal.ZERO;

            Date validadeMaisProxima = null;

            // CALCULAR ESTOQUE DOS LOTES

            // Consulta os lotes do produto selecionado
            for (Document lote : lotes.find(
                eq("itemId", produtoId)
            )) {

                BigDecimal quantidadeDisponivel =
                    obterDecimal(
                        lote, "quantidadeDisponivel"
                    );

                BigDecimal quantidadeInicial =
                    obterDecimal(
                        lote, "quantidadeInicial"
                    );

                BigDecimal custoTotal =
                    obterDecimal(
                        lote, "custoTotalAquisicao"
                    );

                // Ignora lotes sem quantidade disponível
                if (quantidadeDisponivel.signum() <= 0) {
                    continue;
                }

                quantidadeTotal = quantidadeTotal.add(
                    quantidadeDisponivel
                );

                // CALCULAR PREÇO MÉDIO PONDERADO

                if (
                    quantidadeInicial.signum() > 0 &&
                    lote.get("custoTotalAquisicao") != null
                ) {

                    BigDecimal precoDoLote =
                        custoTotal.divide(
                            quantidadeInicial,
                            10,
                            RoundingMode.HALF_UP
                        );

                    custoEstoque = custoEstoque.add(
                        precoDoLote.multiply(
                            quantidadeDisponivel
                        )
                    );

                    quantidadeComPreco =
                        quantidadeComPreco.add(
                            quantidadeDisponivel
                        );
                }

                // IDENTIFICAR VALIDADE MAIS PRÓXIMA

                Date validade = lote.getDate(
                    "dataValidade"
                );

                if (validade != null) {

                    if (
                        validadeMaisProxima == null ||
                        validade.before(validadeMaisProxima)
                    ) {
                        validadeMaisProxima = validade;
                    }
                }
            }

            // CALCULAR PREÇO UNITÁRIO

            String precoUnitario = null;

            if (quantidadeComPreco.signum() > 0) {

                precoUnitario = custoEstoque.divide(
                    quantidadeComPreco,
                    2,
                    RoundingMode.HALF_UP
                ).toPlainString();
            }

            // CONVERTER DATA DE VALIDADE

            String dataValidade = null;

            if (validadeMaisProxima != null) {

                dataValidade = validadeMaisProxima
                    .toInstant()
                    .toString();
            }

            // ORGANIZAR RESPOSTA PARA O FRONTEND

            Document resultado = new Document()
                .append(
                    "id",
                    produtoId.toHexString()
                )
                .append(
                    "nome",
                    produto.getString("nome")
                )
                .append(
                    "categoria",
                    nomeCategoria
                )
                .append(
                    "quantidadeUnidade",
                    quantidadeUnidade.toPlainString()
                )
                .append(
                    "unidadeMedida",
                    produto.getString("unidadeMedida")
                )
                .append(
                    "quantidadeTotal",
                    quantidadeTotal.toPlainString()
                )
                .append(
                    "precoUnitario",
                    precoUnitario
                )
                .append(
                    "dataValidade",
                    dataValidade
                )
                .append(
                    "status",
                    produto.getString("status")
                )
                .append(
                    "observacao",
                    produto.getString("observacao")
                );

            listaProdutos.add(resultado);
        }

        return new Document("produtos", listaProdutos);
    }

    // CONVERSÃO DOS NÚMEROS DO MONGODB

    // Converte os valores armazenados para BigDecimal
    private static BigDecimal obterDecimal(
        Document documento,
        String campo
    ) {

        Object valor = documento.get(campo);

        if (valor instanceof Decimal128) {
            return ((Decimal128) valor).bigDecimalValue();
        }

        if (valor instanceof Number) {
            return new BigDecimal(valor.toString());
        }

        if (valor instanceof String) {

            try {
                return new BigDecimal((String) valor);
            } catch (NumberFormatException erro) {
                return BigDecimal.ZERO;
            }
        }

        return BigDecimal.ZERO;
    }
}
