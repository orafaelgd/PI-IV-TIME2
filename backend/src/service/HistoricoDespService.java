// Código feito por Miriã Nascimento dos Anjos RA: 25892704

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


    // Define o fuso usado nos filtros de data
    private static final ZoneId FUSO =
        ZoneId.of("America/Sao_Paulo");


    // Motivos aceitos ao filtrar o histórico
    private static final Set<String> MOTIVOS = Set.of(
        "Consumido",
        "Vencimento",
        "Contaminado",
        "Danificado",
        "Outro"
    );


    // Coleções utilizadas para reunir movimentações, lotes, produtos e usuários
    private final MongoCollection<Document> movimentacoes;
    private final MongoCollection<Document> lotes;
    private final MongoCollection<Document> itens;
    private final MongoCollection<Document> usuarios;


    // Inicializa as coleções necessárias para consultar o histórico
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


    // Consulta as saídas da conta com os filtros e a paginação informados
    public Document consultarHistorico(
        LocalDate inicio,
        LocalDate fim,
        String motivo,
        int pagina,
        int limite,
        boolean somenteDesperdicio,
        ObjectId usuarioId
    ) {


        // Impede consultas sem um usuário identificado
        if (usuarioId == null) {

            throw new IllegalArgumentException(
                "Usuário não identificado."
            );
        }


        if (
            pagina < 1 ||
            limite < 1 ||
            limite > 100
        ) {

            throw new IllegalArgumentException(
                "Paginação inválida."
            );
        }


        if (
            inicio != null &&
            fim != null &&
            inicio.isAfter(fim)
        ) {

            throw new IllegalArgumentException(
                "Data inicial maior que a final."
            );
        }


        if (
            motivo != null &&
            !MOTIVOS.contains(motivo)
        ) {

            throw new IllegalArgumentException(
                "Motivo inválido."
            );
        }


        // Calcula quantos registros devem ser ignorados nas páginas anteriores
        long deslocamento =
            ((long) pagina - 1L) * limite;

        if (deslocamento > Integer.MAX_VALUE) {

            throw new IllegalArgumentException(
                "Página inválida."
            );
        }


        // Monta os filtros para incluir apenas saídas, nunca entradas de estoque
        List<Bson> filtros = new ArrayList<>();


        // Mantém a opção de mostrar somente vencimentos e ajustes de saída
        if (somenteDesperdicio) {


            filtros.add(
                Filters.in(
                    "tipo",
                    "VENCIMENTO",
                    "AJUSTE_SAIDA"
                )
            );

        } else {


            filtros.add(
                Filters.in(
                    "tipo",
                    "CONSUMO",
                    "VENCIMENTO",
                    "AJUSTE_SAIDA"
                )
            );
        }


        // Aplica o início do período, considerando o horário de São Paulo
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


        // Inclui todos os registros até o final do dia informado
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


        // Filtra pelo motivo quando ele é informado
        if (motivo != null) {

            filtros.add(
                Filters.eq(
                    "motivo",
                    motivo
                )
            );
        }


        // Monta a consulta do MongoDB juntando movimentações, lotes e produtos
        List<Bson> consulta = new ArrayList<>();


        consulta.add(
            Aggregates.match(
                Filters.and(filtros)
            )
        );



        consulta.add(
            Aggregates.lookup(
                lotes.getNamespace().getCollectionName(),
                "loteId",
                "_id",
                "lote"
            )
        );


        consulta.add(
            Aggregates.unwind("$lote")
        );


        consulta.add(
            Aggregates.match(
                Filters.eq(
                    "lote.usuarioId",
                    usuarioId
                )
            )
        );



        consulta.add(
            Aggregates.lookup(
                itens.getNamespace().getCollectionName(),
                "lote.itemId",
                "_id",
                "produto"
            )
        );


        consulta.add(
            Aggregates.unwind("$produto")
        );


        consulta.add(
            Aggregates.match(
                Filters.eq(
                    "produto.usuarioId",
                    usuarioId
                )
            )
        );


        // Conta os resultados antes de aplicar a paginação
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


        // Ordena os registros mais recentes e seleciona apenas a página solicitada
        List<Bson> consultaPagina =
            new ArrayList<>(consulta);


        consultaPagina.add(
            Aggregates.sort(
                Sorts.descending(
                    "dataMovimentacao",
                    "criadoEm",
                    "_id"
                )
            )
        );


        consultaPagina.add(
            Aggregates.skip(
                (int) deslocamento
            )
        );


        consultaPagina.add(
            Aggregates.limit(limite)
        );


        List<Document> resultados =
            movimentacoes.aggregate(consultaPagina)
                .into(new ArrayList<>());


        // Recupera o nome da conta para registros antigos sem responsável identificado
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


        // Prepara os dados de cada movimentação para exibição no frontend
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


            // Confere os vínculos com a conta antes de montar o registro
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


            String tipo =
                movimentacao.getString("tipo");


            // Diferencia desperdícios das saídas por consumo
            boolean desperdicio =
                "VENCIMENTO".equals(tipo) ||
                "AJUSTE_SAIDA".equals(tipo);


            // Preenche motivos ausentes em registros mais antigos
            String motivoRegistro =
                movimentacao.getString("motivo");


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


            // Identifica o responsável, incluindo saídas geradas automaticamente pelo sistema
            String origem =
                movimentacao.getString("origem");

            String responsavel = "Não informado";

            if ("AUTOMATICA".equals(origem)) {


                responsavel = "Sistema";

            } else {


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


                    responsavel = nomeConta;
                }
            }


            Object loteId =
                movimentacao.get("loteId");

            Object produtoId =
                lote.get("itemId");


            Date dataOcorrencia =
                movimentacao.getDate(
                    "dataMovimentacao"
                );


            // Reúne as informações que serão enviadas à tela de histórico
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


        // Registra a quantidade encontrada para conferência no servidor
        System.out.println(
            "HISTÓRICO DE SAÍDA - Usuário: " +
            usuarioId.toHexString() +
            " | Registros encontrados: " +
            total
        );


        // Retorna os registros e os dados da paginação
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


    // Localiza o responsável tanto por ObjectId quanto por um ID salvo como texto
    private Document buscarUsuarioPorId(
        Object identificador
    ) {


        if (identificador instanceof ObjectId) {

            return usuarios.find(
                Filters.eq(
                    "_id",
                    identificador
                )
            ).first();
        }


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


    // Converte o identificador para texto quando necessário
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


    // Converte quantidades decimais sem perder sua representação original
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


    // Formata as datas para o retorno em JSON
    private static String converterData(Date data) {

        return data == null
            ? null
            : data.toInstant().toString();
    }


    // Prepara os valores numéricos do desperdício para a resposta
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
