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

    // Coleções utilizadas no MongoDB.
    private final MongoCollection<Document> itens;
    private final MongoCollection<Document> categorias;
    private final MongoCollection<Document> lotes;
    private final MongoCollection<Document> movimentacoes;

    // Exceção utilizada quando o produto não existe.
    public static class ProdutoNaoEncontradoException
        extends RuntimeException {

        public ProdutoNaoEncontradoException() {
            super("Produto nao encontrado.");
        }
    }

    // Exceção utilizada para impedir exclusões indevidas.
    public static class ConflitoExclusaoException
        extends RuntimeException {

        public ConflitoExclusaoException(String mensagem) {
            super(mensagem);
        }
    }

    // Construtor que recebe as coleções do banco.
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

    // Retorna todas as categorias cadastradas.
    public Document listarCategorias() {

        List<Document> lista = new ArrayList<>();

        for (Document categoria : categorias.find()) {

            String nome = categoria.getString("nome");

            if (nome == null || nome.isBlank()) {
                continue;
            }

            ObjectId id = categoria.getObjectId("_id");

            lista.add(
                new Document()
                    .append("id", id.toHexString())
                    .append("nome", nome)
            );
        }

        return new Document("categorias", lista);
    }

    // Gera um código padronizado para a categoria.
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

    // Busca a categoria ou cadastra uma nova.
    private ObjectId buscarOuCriarCategoria(String nome) {

        String codigo = gerarCodigo(nome);

        if (codigo.isEmpty()) {
            throw new IllegalArgumentException(
                "Categoria invalida."
            );
        }

        Document categoria = categorias.find(
            eq("codigo", codigo)
        ).first();

        // Busca também pelo nome, ignorando maiúsculas.
        if (categoria == null) {
            categoria = categorias.find(
                regex(
                    "nome",
                    "^" + Pattern.quote(nome) + "$",
                    "i"
                )
            ).first();
        }

        // Cadastra a categoria se ela ainda não existir.
        if (categoria == null) {

            categoria = new Document()
                .append("nome", nome)
                .append("codigo", codigo)
                .append("criadoEm", new Date());

            categorias.insertOne(categoria);
        }

        return categoria.getObjectId("_id");
    }

    // Obtém o nome da categoria vinculada ao produto.
    private String buscarNomeCategoria(Document produto) {

        ObjectId categoriaId =
            produto.getObjectId("categoriaId");

        if (categoriaId == null) {
            return "Sem categoria";
        }

        Document categoria = categorias.find(
            eq("_id", categoriaId)
        ).first();

        if (categoria == null) {
            return "Sem categoria";
        }

        return categoria.getString("nome");
    }

    // VALIDAÇÃO DOS PRODUTOS

    // Obtém um campo de texto e remove espaços nas pontas.
    private static String obterTexto(
        Document documento,
        String campo
    ) {

        Object valor = documento.get(campo);

        return valor instanceof String
            ? ((String) valor).trim()
            : "";
    }

    // Valida os dados antes de cadastrar ou atualizar.
    private Document validarProduto(Document dados) {

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

        // Valida o nome.
        if (nome.isEmpty() || nome.length() > 150) {
            throw new IllegalArgumentException(
                "Nome do produto invalido."
            );
        }

        // Valida a categoria.
        if (categoria.isEmpty() ||
            categoria.length() > 100) {

            throw new IllegalArgumentException(
                "Categoria invalida."
            );
        }

        // Converte a quantidade para número.
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

        // A quantidade precisa ser maior que zero.
        if (quantidade.signum() <= 0 ||
            quantidade.compareTo(
                BigDecimal.valueOf(Double.MAX_VALUE)
            ) > 0) {

            throw new IllegalArgumentException(
                "Quantidade da unidade invalida."
            );
        }

        // Unidades de medida aceitas.
        if (!Set.of("kg", "g", "l", "ml", "un")
            .contains(unidade)) {

            throw new IllegalArgumentException(
                "Unidade de medida invalida."
            );
        }

        // Status permitido.
        if (!Set.of("ativo", "inativo")
            .contains(status)) {

            throw new IllegalArgumentException(
                "Status invalido."
            );
        }

        // Validação da observação.
        if (observacao.isEmpty() ||
            observacao.length() > 1000) {

            throw new IllegalArgumentException(
                "Observacao invalida."
            );
        }

        // Busca ou cadastra a categoria informada.
        ObjectId categoriaId =
            buscarOuCriarCategoria(categoria);

        // Retorna os dados padronizados para o banco.
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

    // Valida e cadastra um produto no MongoDB.
    public Document cadastrarProduto(Document dados) {

        Document produto = validarProduto(dados);

        produto.append("criadoEm", new Date());

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

    // BUSCAR PRODUTO PELO ID

    // Busca o produto ou informa que ele não existe.
    private Document encontrarProduto(ObjectId id) {

        Document produto = itens.find(
            eq("_id", id)
        ).first();

        if (produto == null) {
            throw new ProdutoNaoEncontradoException();
        }

        return produto;
    }

    // Retorna as informações de um produto específico.
    public Document buscarProduto(ObjectId id) {

        Document produto = encontrarProduto(id);

        Document resposta = new Document()
            .append("id", id.toHexString())
            .append(
                "nome",
                produto.getString("nome")
            )
            .append(
                "categoria",
                buscarNomeCategoria(produto)
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

    // Atualiza as informações de um produto existente.
    public void atualizarProduto(
        ObjectId id,
        Document dados
    ) {

        // Verifica se o produto existe.
        encontrarProduto(id);

        // Valida os novos dados.
        Document alteracoes = validarProduto(dados);

        alteracoes.append("atualizadoEm", new Date());

        UpdateResult resultado = itens.updateOne(
            eq("_id", id),
            new Document("$set", alteracoes)
        );

        if (resultado.getMatchedCount() == 0) {
            throw new ProdutoNaoEncontradoException();
        }
    }

    // VERIFICAÇÃO DE LOTES E MOVIMENTAÇÕES

    // Verifica se o produto possui movimentações registradas.
    private boolean possuiMovimentacoes(ObjectId produtoId) {

        // Busca movimentações vinculadas diretamente ao produto.
        if (movimentacoes.countDocuments(
            or(
                eq("itemId", produtoId),
                eq("produtoId", produtoId)
            )
        ) > 0) {
            return true;
        }

        // Reúne os IDs dos lotes pertencentes ao produto.
        List<ObjectId> idsLotes = new ArrayList<>();

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

        // Verifica movimentações vinculadas aos lotes.
        return movimentacoes.countDocuments(
            in("loteId", idsLotes)
        ) > 0;
    }

    // Verifica se existem lotes vinculados ao produto.
    private boolean possuiLotes(ObjectId produtoId) {

        return lotes.countDocuments(
            eq("itemId", produtoId)
        ) > 0;
    }

    // VERIFICAR EXCLUSÃO

    // Consulta se o produto pode ser excluído.
    public Document verificarExclusao(ObjectId id) {

        encontrarProduto(id);

        boolean temMovimentacoes =
            possuiMovimentacoes(id);

        boolean temLotes =
            possuiLotes(id);

        boolean podeExcluir =
            !temMovimentacoes && !temLotes;

        String mensagem;

        // Produtos com histórico não podem ser excluídos.
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

    // Exclui apenas produtos sem lotes ou movimentações.
    public void excluirProduto(ObjectId id) {

        encontrarProduto(id);

        // Confere novamente os vínculos antes de excluir.
        if (possuiMovimentacoes(id) || possuiLotes(id)) {

            throw new ConflitoExclusaoException(
                "Este produto nao pode ser excluido, " +
                "pois possui movimentacoes ou lotes vinculados. " +
                "Deseja torna-lo inativo?"
            );
        }

        DeleteResult resultado = itens.deleteOne(
            eq("_id", id)
        );

        if (resultado.getDeletedCount() == 0) {
            throw new ProdutoNaoEncontradoException();
        }
    }

    // ALTERAR STATUS

    // Permite ativar ou inativar um produto.
    public String alterarStatus(
        ObjectId id,
        Document dados
    ) {

        encontrarProduto(id);

        String status = obterTexto(
            dados, "status"
        ).toLowerCase(Locale.ROOT);

        // Aceita somente ativo ou inativo.
        if (!Set.of("ativo", "inativo")
            .contains(status)) {

            throw new IllegalArgumentException(
                "Status invalido."
            );
        }

        UpdateResult resultado = itens.updateOne(
            eq("_id", id),
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

    // Lista produtos e calcula as informações do estoque.
    public Document listarProdutos() {

        List<Document> listaProdutos = new ArrayList<>();

        for (Document produto : itens.find()) {

            ObjectId produtoId =
                produto.getObjectId("_id");

            String nomeCategoria =
                buscarNomeCategoria(produto);

            BigDecimal quantidadeUnidade =
                obterDecimal(produto, "quantidadeUnidade");

            BigDecimal quantidadeTotal =
                BigDecimal.ZERO;

            BigDecimal custoEstoque =
                BigDecimal.ZERO;

            BigDecimal quantidadeComPreco =
                BigDecimal.ZERO;

            Date validadeMaisProxima = null;

            // Consulta os lotes pertencentes ao produto.
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

                // Ignora lotes sem estoque disponível.
                if (quantidadeDisponivel.signum() <= 0) {
                    continue;
                }

                // Soma a quantidade disponível dos lotes.
                quantidadeTotal = quantidadeTotal.add(
                    quantidadeDisponivel
                );

                // Calcula o custo do estoque pelo preço
                // de aquisição proporcional de cada lote.
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

                // Identifica a validade mais próxima.
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

            // Calcula o preço médio ponderado do estoque.
            String precoUnitario = null;

            if (quantidadeComPreco.signum() > 0) {

                precoUnitario = custoEstoque.divide(
                    quantidadeComPreco,
                    2,
                    RoundingMode.HALF_UP
                ).toPlainString();
            }

            // Converte a data de validade para texto.
            String dataValidade = null;

            if (validadeMaisProxima != null) {

                dataValidade = validadeMaisProxima
                    .toInstant()
                    .toString();
            }

            // Monta os dados enviados para a tela.
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

    // Converte diferentes formatos numéricos em BigDecimal.
    private static BigDecimal obterDecimal(
        Document documento,
        String campo
    ) {

        Object valor = documento.get(campo);

        // Valores armazenados como Decimal128.
        if (valor instanceof Decimal128) {
            return ((Decimal128) valor).bigDecimalValue();
        }

        // Valores numéricos convencionais.
        if (valor instanceof Number) {
            return new BigDecimal(valor.toString());
        }

        // Valores armazenados como texto.
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
