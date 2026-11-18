package service;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
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
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final Set<String> MOTIVOS = Set.of(
            "Consumido", "Vencimento", "Contaminado", "Danificado", "Outro"
    );

    private final MongoCollection<Document> movimentacoes;
    private final MongoCollection<Document> lotes;
    private final MongoCollection<Document> itens;
    private final MongoCollection<Document> sessoes;
    private final MongoCollection<Document> usuarios;

    public HistoricoDespService(MongoDatabase banco) {
        this.movimentacoes = banco.getCollection("movimentacoes");
        this.lotes = banco.getCollection("lotes");
        this.itens = banco.getCollection("itens");
        this.sessoes = banco.getCollection("sessoes");
        this.usuarios = banco.getCollection("users");
    }

    public boolean autenticado(String token) {
        if (token == null || token.isBlank() || token.length() > 200) {
            return false;
        }

        Document sessao = sessoes.find(Filters.and(
                Filters.eq("token", token),
                Filters.gt("expiraEm", new Date())
        )).first();

        if (sessao == null) return false;

        Document usuario = buscarPorId(usuarios, sessao.get("usuarioId"));
        return usuario != null && !Boolean.FALSE.equals(usuario.getBoolean("ativo"));
    }

    public Document consultarHistorico(LocalDate inicio, LocalDate fim, String motivo,
                                      int pagina, int limite, boolean somenteDesperdicio) {
        if (pagina < 1 || limite < 1 || limite > 100) {
            throw new IllegalArgumentException("Paginação inválida.");
        }
        if (inicio != null && fim != null && inicio.isAfter(fim)) {
            throw new IllegalArgumentException("Data inicial maior que a final.");
        }
        if (motivo != null && !MOTIVOS.contains(motivo)) {
            throw new IllegalArgumentException("Motivo inválido.");
        }

        List<Bson> filtros = new ArrayList<>();
        if (somenteDesperdicio) {
            filtros.add(Filters.in("tipo", "VENCIMENTO", "AJUSTE_SAIDA"));
        }
        if (inicio != null) {
            filtros.add(Filters.gte("dataMovimentacao",
                    Date.from(inicio.atStartOfDay(FUSO).toInstant())));
        }
        if (fim != null) {
            filtros.add(Filters.lt("dataMovimentacao",
                    Date.from(fim.plusDays(1).atStartOfDay(FUSO).toInstant())));
        }
        if (motivo != null) filtros.add(Filters.eq("motivo", motivo));

        Bson filtro = filtros.isEmpty() ? new Document() : Filters.and(filtros);
        long total = movimentacoes.countDocuments(filtro);
        long deslocamento = ((long) pagina - 1) * limite;
        if (deslocamento > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Página inválida.");
        }

        List<Document> registros = new ArrayList<>();
        for (Document movimentacao : movimentacoes.find(filtro)
                .sort(Sorts.descending("dataMovimentacao", "criadoEm"))
                .skip((int) deslocamento)
                .limit(limite)) {

            Object loteId = movimentacao.get("loteId");
            Document lote = buscarPorId(lotes, loteId);
            Object itemId = lote == null ? null : lote.get("itemId");
            Document produto = buscarPorId(itens, itemId);

            String origem = movimentacao.getString("origem");
            String responsavel = "Não informado";
            if ("AUTOMATICA".equals(origem)) {
                responsavel = "Sistema";
            } else {
                Document usuario = buscarPorId(usuarios, movimentacao.get("usuarioId"));
                if (usuario != null && usuario.getString("nome") != null) {
                    responsavel = usuario.getString("nome");
                }
            }

            String tipo = movimentacao.getString("tipo");
            boolean desperdicio = "VENCIMENTO".equals(tipo) || "AJUSTE_SAIDA".equals(tipo);
            Date data = movimentacao.getDate("dataMovimentacao");

            Document registro = new Document("id", id(movimentacao.get("_id")))
                    .append("loteId", id(loteId))
                    .append("codigoLote", lote == null ? null : lote.getString("codigo"))
                    .append("produtoId", id(itemId))
                    .append("produtoNome", produto == null ? null : produto.getString("nome"))
                    .append("unidade", produto == null ? null : produto.getString("unidadeMedida"))
                    .append("quantidade", decimalTexto(movimentacao.get("quantidade")))
                    .append("tipo", tipo)
                    .append("motivo", movimentacao.getString("motivo"))
                    .append("desperdicio", desperdicio)
                    .append("dataOcorrencia", data == null ? null : data.toInstant().toString())
                    .append("criadoEm", dataIso(movimentacao.getDate("criadoEm")))
                    .append("origem", origem)
                    .append("responsavelNome", responsavel)
                    .append("observacao", movimentacao.getString("observacao"))
                    .append("valorPerdaCentavos", desperdicio
                            ? numeroSimples(movimentacao.get("valorPerdaCentavos")) : 0);

            registros.add(registro);
        }

        return new Document("itens", registros)
                .append("total", total)
                .append("pagina", pagina)
                .append("limite", limite);
    }

    private Document buscarPorId(MongoCollection<Document> colecao, Object identificador) {
        if (identificador == null) return null;
        if (identificador instanceof String && ObjectId.isValid((String) identificador)) {
            Document resultado = colecao.find(
                    Filters.eq("_id", new ObjectId((String) identificador))).first();
            if (resultado != null) return resultado;
        }
        return colecao.find(Filters.eq("_id", identificador)).first();
    }

    private static String id(Object valor) {
        if (valor == null) return null;
        return valor instanceof ObjectId ? ((ObjectId) valor).toHexString() : valor.toString();
    }

    private static String decimalTexto(Object valor) {
        if (valor == null) return null;
        if (valor instanceof Decimal128) return ((Decimal128) valor).bigDecimalValue().toPlainString();
        if (valor instanceof BigDecimal) return ((BigDecimal) valor).toPlainString();
        return valor.toString();
    }

    private static String dataIso(Date data) {
        return data == null ? null : data.toInstant().toString();
    }

    private static Object numeroSimples(Object valor) {
        if (valor instanceof Decimal128) return ((Decimal128) valor).bigDecimalValue().toPlainString();
        if (valor instanceof BigDecimal) return ((BigDecimal) valor).toPlainString();
        return valor;
    }
}

