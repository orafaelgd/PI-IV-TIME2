// Código feito por Livia Carvalho Lucizano RA: 25017514

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

    // COLEÇÕES DO MONGODB

    private final MongoCollection<Document> lotes;
    private final MongoCollection<Document> itens;
    private final MongoCollection<Document> movimentacoes;

    // TRATAMENTO DE ERROS

    public static class LoteNaoEncontradoException
        extends RuntimeException {

        public LoteNaoEncontradoException() {
            super("Lote nao encontrado.");
        }
    }

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

    public LotesService(
        MongoCollection<Document> lotes,
        MongoCollection<Document> itens,
        MongoCollection<Document> movimentacoes
    ) {
        this.lotes = lotes;
        this.itens = itens;
        this.movimentacoes = movimentacoes;
    }

    // CONVERTER TEXTO

    private static String obterTexto(
        Document dados,
        String campo
    ) {

        Object valor = dados.get(campo);

        return valor instanceof String
            ? ((String) valor).trim()
            : "";
    }

    // CONVERTER NÚMEROS

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

    // CONVERTER DATA

    private static String dataTexto(Date data) {

        if (data == null) {
            return null;
        }

        return data.toInstant().toString();
    }

    // BUSCAR LOTE

    // Somente o proprietário pode consultar o lote
    private Document encontrarLote(
        ObjectId id,
        ObjectId usuarioId
    ) {

        Document lote = lotes.find(
            and(
                eq("_id", id),
                eq("usuarioId", usuarioId)
            )
        ).first();

        if (lote == null) {
            throw new LoteNaoEncontradoException();
        }

        return lote;
    }

    // VALIDAR LOTE

    private Document validarLote(
        Document dados,
        ObjectId loteExistenteId,
        ObjectId usuarioId
    ) {

        String itemTexto = obterTexto(dados, "itemId");
        String codigo = obterTexto(dados, "codigo");
        String quantidadeTexto = obterTexto(
            dados, "quantidadeRecebida"
        );
        String valorTexto = obterTexto(
            dados, "valorUnitario"
        );
        String entradaTexto = obterTexto(
            dados, "dataEntrada"
        );
        String validadeTexto = obterTexto(
            dados, "dataValidade"
        );
        String observacao = obterTexto(
            dados, "observacao"
        );

        // VALIDAR PRODUTO

        if (!ObjectId.isValid(itemTexto)) {
            throw new IllegalArgumentException(
                "Selecione um produto valido."
            );
        }

        ObjectId itemId = new ObjectId(itemTexto);

        // O produto deve pertencer ao usuário autenticado
        Document produto = itens.find(
            and(
                eq("_id", itemId),
                eq("usuarioId", usuarioId)
            )
        ).first();

        if (produto == null) {
            throw new IllegalArgumentException(
                "Produto nao encontrado."
            );
        }

        // VALIDAR PRODUTO INATIVO

        // Permite editar o lote já vinculado ao produto
        // Não permite criar um novo vínculo com produto inativo
        if ("inativo".equals(produto.getString("status"))) {

            Document loteExistente =
                loteExistenteId == null
                    ? null
                    : lotes.find(
                        and(
                            eq("_id", loteExistenteId),
                            eq("usuarioId", usuarioId)
                        )
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

        if (quantidade.signum() <= 0) {
            throw new IllegalArgumentException(
                "A quantidade recebida deve ser maior que zero."
            );
        }

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

            if (dataValidade.isBefore(dataEntrada)) {

                throw new IllegalArgumentException(
                    "A validade nao pode ser anterior " +
                    "a data de entrada."
                );
            }

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

        if (
            observacao.isEmpty() ||
            observacao.length() > 1000
        ) {
            throw new IllegalArgumentException(
                "Observacao invalida."
            );
        }

        // CALCULAR CUSTO TOTAL

        BigDecimal custoTotal =
            quantidade.multiply(valorUnitario);

        // Organiza os dados do lote
        return new Document()
            .append("itemId", itemId)
            .append("codigo", codigo)
            .append(
                "quantidadeInicial",
                new Decimal128(quantidade)
            )
            .append("dataEntrada", entrada)
            .append("dataValidade", validade)
            .append(
                "custoTotalAquisicao",
                new Decimal128(custoTotal)
            )
            .append("observacao", observacao);
    }

    // VERIFICAR CÓDIGO DUPLICADO

    // Cada usuário pode ter seus próprios códigos de lote
    private boolean codigoDuplicado(
        String codigo,
        ObjectId usuarioId,
        ObjectId ignorarId
    ) {

        if (ignorarId == null) {

            return lotes.find(
                and(
                    eq("usuarioId", usuarioId),
                    eq("codigo", codigo)
                )
            ).first() != null;
        }

        return lotes.find(
            and(
                eq("usuarioId", usuarioId),
                eq("codigo", codigo),
                ne("_id", ignorarId)
            )
        ).first() != null;
    }

    // CADASTRAR LOTE

    public Document cadastrarLote(
        Document dados,
        ObjectId usuarioId
    ) {

        Document lote = validarLote(
            dados, null, usuarioId
        );

        if (
            codigoDuplicado(
                lote.getString("codigo"),
                usuarioId,
                null
            )
        ) {
            throw new ConflitoLoteException(
                "Ja existe um lote com este codigo."
            );
        }

        // Salva o proprietário do lote
        lote.append("usuarioId", usuarioId);

        // O saldo inicial é igual à quantidade recebida
        lote.append(
            "quantidadeDisponivel",
            lote.get("quantidadeInicial")
        );

        // Preserva os campos utilizados pelo sistema
        lote.append("status", false);
        lote.append("inativo", false);
        lote.append("criadoEm", new Date());

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

    public Document buscarLote(
        ObjectId id,
        ObjectId usuarioId
    ) {

        Document lote = encontrarLote(id, usuarioId);

        ObjectId itemId = lote.getObjectId("itemId");

        Document resposta = new Document()
            .append("id", id.toHexString())
            .append(
                "itemId",
                itemId == null ? null : itemId.toHexString()
            )
            .append("codigo", lote.getString("codigo"))
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

    // Verifica movimentações vinculadas ao lote
    private boolean possuiMovimentacoes(ObjectId loteId) {

        return movimentacoes.countDocuments(
            eq("loteId", loteId)
        ) > 0;
    }

    // ATUALIZAR LOTE

    public void atualizarLote(
        ObjectId id,
        Document dados,
        ObjectId usuarioId
    ) {

        // Confere se o lote pertence ao usuário
        Document existente = encontrarLote(
            id, usuarioId
        );

        Document alteracoes = validarLote(
            dados, id, usuarioId
        );

        String codigo = alteracoes.getString("codigo");

        // VERIFICAR CÓDIGO DUPLICADO

        if (codigoDuplicado(codigo, usuarioId, id)) {

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

        // Não permite mudar produto ou quantidade
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

        // PROTEGER SALDO DO ESTOQUE

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

        if (mudouQuantidade) {

            alteracoes.append(
                "quantidadeDisponivel",
                alteracoes.get("quantidadeInicial")
            );
        }

        alteracoes.append("atualizadoEm", new Date());

        // SALVAR ALTERAÇÕES

        // Compara os valores anteriores antes de atualizar
        UpdateResult resultado = lotes.updateOne(
            and(
                eq("_id", id),
                eq("usuarioId", usuarioId),
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

        if (resultado.getMatchedCount() == 0) {

            throw new ConflitoLoteException(
                "Os dados do lote foram modificados. " +
                "Atualize a pagina e tente novamente."
            );
        }
    }

    // VERIFICAR EXCLUSÃO

    public Document verificarExclusao(
        ObjectId id,
        ObjectId usuarioId
    ) {

        encontrarLote(id, usuarioId);

        boolean temMovimentacoes =
            possuiMovimentacoes(id);

        String mensagem;

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
            .append("podeExcluir", !temMovimentacoes)
            .append(
                "possuiMovimentacoes",
                temMovimentacoes
            )
            .append("mensagem", mensagem);
    }

    // EXCLUIR LOTE

    public void excluirLote(
        ObjectId id,
        ObjectId usuarioId
    ) {

        encontrarLote(id, usuarioId);

        // Não permite excluir lotes com movimentações
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

        DeleteResult resultado = lotes.deleteOne(
            and(
                eq("_id", id),
                eq("usuarioId", usuarioId)
            )
        );

        if (resultado.getDeletedCount() == 0) {
            throw new LoteNaoEncontradoException();
        }
    }

    // ALTERAR STATUS

    public String alterarStatus(
        ObjectId id,
        Document dados,
        ObjectId usuarioId
    ) {

        encontrarLote(id, usuarioId);

        Object valor = dados.get("inativo");

        if (!(valor instanceof Boolean)) {
            throw new IllegalArgumentException(
                "Status invalido."
            );
        }

        boolean inativo = (Boolean) valor;

        UpdateResult resultado = lotes.updateOne(
            and(
                eq("_id", id),
                eq("usuarioId", usuarioId)
            ),
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

    // Retorna somente lotes da conta autenticada
    public Document listarLotes(ObjectId usuarioId) {

        List<Document> lista = new ArrayList<>();

        for (
            Document lote : lotes.find(
                eq("usuarioId", usuarioId)
            ).sort(new Document("dataEntrada", -1))
        ) {

            ObjectId itemId =
                lote.getObjectId("itemId");

            String nomeProduto =
                "Produto nao encontrado";

            // BUSCAR NOME DO PRODUTO

            if (itemId != null) {

                Document produto = itens.find(
                    and(
                        eq("_id", itemId),
                        eq("usuarioId", usuarioId)
                    )
                ).first();

                if (produto != null) {

                    String nome = produto.getString("nome");

                    if (nome != null) {
                        nomeProduto = nome;
                    }
                }
            }

            // ORGANIZAR DADOS PARA O FRONTEND

            Document resultado = new Document()
                .append(
                    "id",
                    lote.getObjectId("_id").toHexString()
                )
                .append(
                    "codigo",
                    lote.getString("codigo")
                )
                .append("produto", nomeProduto)
                .append(
                    "itemId",
                    itemId == null
                        ? null
                        : itemId.toHexString()
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
