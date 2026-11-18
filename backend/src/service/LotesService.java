
package service;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;

import org.bson.Document;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static com.mongodb.client.model.Filters.*;

public class LotesService {

    // Coleções utilizadas no MongoDB
    private final MongoCollection<Document> lotes;
    private final MongoCollection<Document> itens;
    private final MongoCollection<Document> movimentacoes;

    // TRATAMENTO DE ERROS

    // Erro utilizado quando um lote não é encontrado
    public static class LoteNaoEncontradoException
        extends RuntimeException {

        public LoteNaoEncontradoException() {
            super("Lote nao encontrado.");
        }
    }

    // Erro utilizado quando uma operação não é permitida
    public static class ConflitoLoteException
        extends RuntimeException {

        private final Document resposta;

        public ConflitoLoteException(String mensagem) {
            this(new Document("mensagem", mensagem));
        }

        public ConflitoLoteException(Document resposta) {
            super(resposta.getString("mensagem"));
            this.resposta = resposta;
        }

        public Document getResposta() {
            return resposta;
        }
    }

    // CONSTRUTOR

    // Recebe as coleções necessárias para gerenciar os lotes
    public LotesService(
        MongoCollection<Document> lotes,
        MongoCollection<Document> itens,
        MongoCollection<Document> movimentacoes
    ) {
        this.lotes = lotes;
        this.itens = itens;
        this.movimentacoes = movimentacoes;
    }

    // CONVERSÃO DOS DADOS

    // Obtém o texto de um campo e remove espaços extras
    private static String obterTexto(
        Document dados,
        String campo
    ) {
        Object valor = dados.get(campo);

        return valor instanceof String
            ? ((String) valor).trim()
            : "";
    }

    // Converte números do MongoDB para BigDecimal
    private static BigDecimal obterDecimal(
        Document dados,
        String campo
    ) {
        Object valor = dados.get(campo);

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

    // Converte a data para texto usado pelo frontend
    private static String dataTexto(Date data) {

        if (data == null) {
            return null;
        }

        return data.toInstant().toString();
    }

    // BUSCAR LOTE NO BANCO

    // Consulta o lote e verifica se ele existe
    private Document encontrarLote(ObjectId id) {

        Document lote = lotes.find(
            eq("_id", id)
        ).first();

        if (lote == null) {
            throw new LoteNaoEncontradoException();
        }

        return lote;
    }

    // VALIDAR DADOS DO LOTE

    // Valida os dados para cadastro ou edição
    // O ID é nulo quando estamos cadastrando um lote
    private Document validarLote(
        Document dados,
        ObjectId loteExistenteId
    ) {

        String itemTexto =
            obterTexto(dados, "itemId");

        String codigo =
            obterTexto(dados, "codigo");

        String quantidadeTexto =
            obterTexto(dados, "quantidadeRecebida");

        String valorTexto =
            obterTexto(dados, "valorUnitario");

        String entradaTexto =
            obterTexto(dados, "dataEntrada");

        String validadeTexto =
            obterTexto(dados, "dataValidade");

        String observacao =
            obterTexto(dados, "observacao");

        // VALIDAR PRODUTO

        // Verifica se o ID do produto é válido
        if (!ObjectId.isValid(itemTexto)) {
            throw new IllegalArgumentException(
                "Selecione um produto valido."
            );
        }

        ObjectId itemId = new ObjectId(itemTexto);

        // Busca o produto vinculado ao lote
        Document produto = itens.find(
            eq("_id", itemId)
        ).first();

        if (produto == null) {
            throw new IllegalArgumentException(
                "Produto nao encontrado."
            );
        }

        // VALIDAR PRODUTO INATIVO

        // Não permite cadastrar lotes de produtos inativos
        // Permite editar lotes já vinculados ao mesmo produto
        if (
            "inativo".equals(
                produto.getString("status")
            )
        ) {

            Document loteExistente =
                loteExistenteId == null
                    ? null
                    : lotes.find(
                        eq("_id", loteExistenteId)
                    ).first();

            if (
                loteExistente == null ||
                !itemId.equals(
                    loteExistente.getObjectId("itemId")
                )
            ) {
                throw new IllegalArgumentException(
                    "Nao e permitido vincular lotes " +
                    "a produto inativo."
                );
            }
        }

        // VALIDAR CÓDIGO

        // O código é obrigatório e deve ter até 50 caracteres
        if (
            codigo.isEmpty() ||
            codigo.length() > 50
        ) {
            throw new IllegalArgumentException(
                "Codigo de lote invalido."
            );
        }

        // VALIDAR QUANTIDADE E VALOR

        BigDecimal quantidade;
        BigDecimal valorUnitario;

        // Converte a quantidade e o valor para números
        try {
            quantidade = new BigDecimal(
                quantidadeTexto.replace(",", ".")
            );

            valorUnitario = new BigDecimal(
                valorTexto.replace(",", ".")
            );

        } catch (NumberFormatException erro) {
            throw new IllegalArgumentException(
                "Quantidade ou valor unitario invalido."
            );
        }

        // A quantidade recebida deve ser positiva
        if (quantidade.signum() <= 0) {
            throw new IllegalArgumentException(
                "A quantidade recebida deve ser maior que zero."
            );
        }

        // O valor unitário deve ser positivo
        if (valorUnitario.signum() <= 0) {
            throw new IllegalArgumentException(
                "O valor unitario deve ser maior que zero."
            );
        }

        // VALIDAR DATAS

        Date entrada;
        Date validade;

        try {
            LocalDate dataEntrada =
                LocalDate.parse(entradaTexto);

            LocalDate dataValidade =
                LocalDate.parse(validadeTexto);

            // A validade não pode ser anterior à entrada
            if (dataValidade.isBefore(dataEntrada)) {
                throw new IllegalArgumentException(
                    "A validade nao pode ser anterior " +
                    "a data de entrada."
                );
            }

            // Converte as datas para o formato do MongoDB
            entrada = Date.from(
                dataEntrada.atStartOfDay()
                    .toInstant(ZoneOffset.UTC)
            );

            validade = Date.from(
                dataValidade.atStartOfDay()
                    .toInstant(ZoneOffset.UTC)
            );

        } catch (DateTimeParseException erro) {
            throw new IllegalArgumentException(
                "Informe datas validas."
            );
        }

        // VALIDAR OBSERVAÇÃO

        // A observação é obrigatória
        if (
            observacao.isEmpty() ||
            observacao.length() > 1000
        ) {
            throw new IllegalArgumentException(
                "Observacao invalida."
            );
        }

        // CALCULAR CUSTO TOTAL

        // Multiplica a quantidade pelo valor unitário
        BigDecimal custoTotal =
            quantidade.multiply(valorUnitario);

        // Converte os valores para Decimal128
        Decimal128 quantidadeDecimal =
            new Decimal128(quantidade);

        Decimal128 custoDecimal =
            new Decimal128(custoTotal);

        // Retorna os dados validados para salvar no banco
        return new Document()
            .append("itemId", itemId)
            .append("codigo", codigo)
            .append(
                "quantidadeInicial",
                quantidadeDecimal
            )
            .append("dataEntrada", entrada)
            .append("dataValidade", validade)
            .append(
                "custoTotalAquisicao",
                custoDecimal
            )
            .append("observacao", observacao);
    }

    // CADASTRAR LOTE

    // Valida e cadastra um novo lote no MongoDB
    public Document cadastrarLote(Document dados) {

        Document lote = validarLote(dados, null);

        String codigo = lote.getString("codigo");

        // Verifica se já existe um lote com esse código
        if (
            lotes.find(
                eq("codigo", codigo)
            ).first() != null
        ) {
            throw new ConflitoLoteException(
                "Ja existe um lote com este codigo."
            );
        }

        // A quantidade disponível começa igual à inicial
        lote.append(
            "quantidadeDisponivel",
            lote.get("quantidadeInicial")
        );

        // Mantém o campo original utilizado pelo banco
        lote.append("status", false);

        // Define o lote como ativo inicialmente
        lote.append("inativo", false);

        // Salva o lote no MongoDB
        lotes.insertOne(lote);

        return new Document()
            .append(
                "mensagem",
                "Lote cadastrado com sucesso!"
            )
            .append(
                "id",
                lote.getObjectId("_id").toHexString()
            );
    }

    // BUSCAR LOTE PARA EDIÇÃO

    // Retorna os dados de um lote específico
    public Document buscarLote(ObjectId id) {

        Document lote = encontrarLote(id);

        ObjectId itemId =
            lote.getObjectId("itemId");

        // Organiza os dados para enviar ao frontend
        Document resposta = new Document()
            .append("id", id.toHexString())
            .append(
                "itemId",
                itemId != null
                    ? itemId.toHexString()
                    : null
            )
            .append(
                "codigo",
                lote.getString("codigo")
            )
            .append(
                "quantidadeInicial",
                obterDecimal(
                    lote, "quantidadeInicial"
                ).toPlainString()
            )
            .append(
                "quantidadeDisponivel",
                obterDecimal(
                    lote, "quantidadeDisponivel"
                ).toPlainString()
            )
            .append(
                "custoTotalAquisicao",
                obterDecimal(
                    lote, "custoTotalAquisicao"
                ).toPlainString()
            )
            .append(
                "dataEntrada",
                dataTexto(lote.getDate("dataEntrada"))
            )
            .append(
                "dataValidade",
                dataTexto(lote.getDate("dataValidade"))
            )
            .append(
                "observacao",
                lote.getString("observacao")
            )
            .append(
                "inativo",
                lote.getBoolean("inativo", false)
            )
            .append(
                "possuiMovimentacoes",
                possuiMovimentacoes(id)
            );

        return new Document("lote", resposta);
    }

    // CONSULTAR MOVIMENTAÇÕES

    // Verifica se existem movimentações vinculadas ao lote
    private boolean possuiMovimentacoes(ObjectId loteId) {

        return movimentacoes.countDocuments(
            eq("loteId", loteId)
        ) > 0;
    }

    // ATUALIZAR LOTE

    // Atualiza um lote respeitando as regras de estoque
    public void atualizarLote(
        ObjectId id,
        Document dados
    ) {

        // Busca o lote antes de realizar alterações
        Document existente = encontrarLote(id);

        // Valida os novos dados recebidos
        Document alteracoes = validarLote(dados, id);

        String codigo = alteracoes.getString("codigo");

        // VERIFICAR CÓDIGO DUPLICADO

        // Impede utilizar o código de outro lote
        Document duplicado = lotes.find(
            and(
                eq("codigo", codigo),
                ne("_id", id)
            )
        ).first();

        if (duplicado != null) {
            throw new ConflitoLoteException(
                "Ja existe um lote com este codigo."
            );
        }

        // CONSULTAR DADOS ATUAIS

        ObjectId itemAtual =
            existente.getObjectId("itemId");

        ObjectId itemNovo =
            alteracoes.getObjectId("itemId");

        BigDecimal quantidadeAtual =
            obterDecimal(existente, "quantidadeInicial");

        BigDecimal quantidadeNova =
            obterDecimal(alteracoes, "quantidadeInicial");

        BigDecimal saldoAtual =
            obterDecimal(existente, "quantidadeDisponivel");

        boolean temMovimentacoes =
            possuiMovimentacoes(id);

        boolean mudouProduto =
            !itemAtual.equals(itemNovo);

        boolean mudouQuantidade =
            quantidadeAtual.compareTo(quantidadeNova) != 0;

        // PROTEGER LOTES COM MOVIMENTAÇÕES

        // Não permite alterar produto ou quantidade
        // quando o lote já possui movimentações
        if (
            temMovimentacoes &&
            (mudouProduto || mudouQuantidade)
        ) {
            throw new ConflitoLoteException(
                "Este lote possui movimentacoes. " +
                "Nao e permitido alterar o produto " +
                "ou a quantidade recebida."
            );
        }

        // PROTEGER SALDO DISPONÍVEL

        // Impede alterar a quantidade inicial quando
        // o saldo disponível já foi modificado
        if (
            mudouQuantidade &&
            saldoAtual.compareTo(quantidadeAtual) != 0
        ) {
            throw new ConflitoLoteException(
                "A quantidade nao pode ser alterada " +
                "porque o saldo disponivel ja foi modificado."
            );
        }

        // ATUALIZAR QUANTIDADE DISPONÍVEL

        // Atualiza o saldo quando a quantidade recebida muda
        if (mudouQuantidade) {
            alteracoes.append(
                "quantidadeDisponivel",
                alteracoes.get("quantidadeInicial")
            );
        }

        // Registra a data da atualização
        alteracoes.append("atualizadoEm", new Date());

        // SALVAR ALTERAÇÕES

        // Confere os valores anteriores para evitar
        // sobrescrever alterações feitas por outra operação
        UpdateResult resultado = lotes.updateOne(
            and(
                eq("_id", id),
                eq(
                    "quantidadeDisponivel",
                    existente.get("quantidadeDisponivel")
                ),
                eq(
                    "quantidadeInicial",
                    existente.get("quantidadeInicial")
                ),
                eq("itemId", itemAtual)
            ),
            new Document("$set", alteracoes)
        );

        // Informa quando os dados mudaram durante a edição
        if (resultado.getMatchedCount() == 0) {
            throw new ConflitoLoteException(
                "Os dados do lote foram modificados. " +
                "Atualize a pagina e tente novamente."
            );
        }
    }

    // VERIFICAR EXCLUSÃO

    // Consulta se o lote pode ser excluído
    public Document verificarExclusao(ObjectId id) {

        encontrarLote(id);

        boolean temMovimentacoes =
            possuiMovimentacoes(id);

        String mensagem;

        // Lotes com movimentações devem ser inativados
        if (temMovimentacoes) {
            mensagem =
                "Este lote nao pode ser excluido, " +
                "pois possui movimentacoes registradas. " +
                "Deseja torna-lo inativo?";
        } else {
            mensagem =
                "Tem certeza de que deseja excluir este lote? " +
                "Esta acao nao podera ser desfeita.";
        }

        return new Document()
            .append(
                "podeExcluir",
                !temMovimentacoes
            )
            .append(
                "possuiMovimentacoes",
                temMovimentacoes
            )
            .append("mensagem", mensagem);
    }

    // EXCLUIR LOTE

    // Exclui apenas lotes que não possuem movimentações
    public void excluirLote(ObjectId id) {

        encontrarLote(id);

        // Verifica novamente antes de excluir
        if (possuiMovimentacoes(id)) {
            throw new ConflitoLoteException(
                new Document()
                    .append("podeExcluir", false)
                    .append(
                        "mensagem",
                        "Este lote nao pode ser excluido, " +
                        "pois possui movimentacoes registradas. " +
                        "Deseja torna-lo inativo?"
                    )
            );
        }

        // Remove o lote do MongoDB
        DeleteResult resultado = lotes.deleteOne(
            eq("_id", id)
        );

        if (resultado.getDeletedCount() == 0) {
            throw new LoteNaoEncontradoException();
        }
    }

    // INATIVAR OU REATIVAR LOTE

    // Altera a situação do lote no sistema
    public String alterarStatus(
        ObjectId id,
        Document dados
    ) {

        // Verifica se o lote existe
        encontrarLote(id);

        Object valor = dados.get("inativo");

        // O status deve ser verdadeiro ou falso
        if (!(valor instanceof Boolean)) {
            throw new IllegalArgumentException(
                "Status invalido."
            );
        }

        boolean inativo = (Boolean) valor;

        // Atualiza a situação do lote no banco
        UpdateResult resultado = lotes.updateOne(
            eq("_id", id),
            new Document(
                "$set",
                new Document()
                    .append("inativo", inativo)
                    .append("atualizadoEm", new Date())
            )
        );

        if (resultado.getMatchedCount() == 0) {
            throw new LoteNaoEncontradoException();
        }

        return inativo
            ? "Lote inativado com sucesso!"
            : "Lote reativado com sucesso!";
    }

    // LISTAR LOTES

    // Lista os lotes cadastrados com os dados dos produtos
    public Document listarLotes() {

        List<Document> lista = new ArrayList<>();

        // Busca os lotes pela data de entrada mais recente
        for (
            Document lote : lotes.find()
                .sort(new Document("dataEntrada", -1))
        ) {

            ObjectId itemId =
                lote.getObjectId("itemId");

            String nomeProduto =
                "Produto nao encontrado";

            // BUSCAR NOME DO PRODUTO

            // Consulta o produto vinculado ao lote
            if (itemId != null) {

                Document produto = itens.find(
                    eq("_id", itemId)
                ).first();

                if (produto != null) {

                    String nome =
                        produto.getString("nome");

                    if (nome != null) {
                        nomeProduto = nome;
                    }
                }
            }

            // ORGANIZAR DADOS PARA O FRONTEND

            // Monta os dados que serão exibidos na tela
            Document resultado = new Document()
                .append(
                    "id",
                    lote.getObjectId("_id").toHexString()
                )
                .append(
                    "codigo",
                    lote.getString("codigo")
                )
                .append(
                    "produto",
                    nomeProduto
                )
                .append(
                    "itemId",
                    itemId != null
                        ? itemId.toHexString()
                        : null
                )
                .append(
                    "quantidadeInicial",
                    obterDecimal(
                        lote, "quantidadeInicial"
                    ).toPlainString()
                )
                .append(
                    "quantidadeDisponivel",
                    obterDecimal(
                        lote, "quantidadeDisponivel"
                    ).toPlainString()
                )
                .append(
                    "custoTotalAquisicao",
                    obterDecimal(
                        lote, "custoTotalAquisicao"
                    ).toPlainString()
                )
                .append(
                    "dataEntrada",
                    dataTexto(lote.getDate("dataEntrada"))
                )
                .append(
                    "dataValidade",
                    dataTexto(lote.getDate("dataValidade"))
                )
                .append(
                    "observacao",
                    lote.getString("observacao")
                )
                .append(
                    "inativo",
                    lote.getBoolean("inativo", false)
                );

            lista.add(resultado);
        }

        return new Document("lotes", lista);
    }
}
