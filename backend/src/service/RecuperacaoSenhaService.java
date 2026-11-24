// Código feito por Miriã Nascimento dos Anjos RA: 25892704

package service;

import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.mindrot.jbcrypt.BCrypt;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Locale;

public class RecuperacaoSenhaService {

    // Define o gerador dos tokens e o prazo de validade do link de recuperação
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long PRAZO_MINUTOS = 20;

    // Coleções utilizadas no processo de recuperação de senha
    private final MongoClient cliente;
    private final MongoCollection<Document> usuarios;
    private final MongoCollection<Document> sessoes;
    private final MongoCollection<Document> recuperacoes;
    private final GmailEmailService gmail;

    // Inicializa as coleções necessárias para consultar e atualizar os dados
    public RecuperacaoSenhaService(MongoClient cliente,
                                    MongoCollection<Document> usuarios,
                                    MongoCollection<Document> sessoes,
                                    MongoCollection<Document> recuperacoes) {
        this(cliente, usuarios, sessoes, recuperacoes, new GmailEmailService());
    }

    // Permite testar o fluxo com um remetente simulado, sem enviar e-mails reais.
    public RecuperacaoSenhaService(MongoClient cliente,
                                    MongoCollection<Document> usuarios,
                                    MongoCollection<Document> sessoes,
                                    MongoCollection<Document> recuperacoes,
                                    GmailEmailService gmail) {
        this.cliente = cliente;
        this.usuarios = usuarios;
        this.sessoes = sessoes;
        this.recuperacoes = recuperacoes;
        this.gmail = gmail;
    }

    // Verifica se o serviço de envio de e-mails está configurado
    public boolean configurado() {
        return gmail.configurado();
    }

    // Recebe o e-mail informado e prepara o envio do link de recuperação
    public void solicitar(String emailInformado) throws Exception {

        // Valida o formato do e-mail antes de consultar o banco
        if (emailInformado == null) {
            throw new IllegalArgumentException("Informe um e-mail válido.");
        }
        String email = emailInformado.strip().toLowerCase(Locale.ROOT);
        if (!ConfiguracaoEmail.emailValido(email)) {
            throw new IllegalArgumentException("Informe um e-mail válido.");
        }
        if (!configurado()) {
            throw new IllegalStateException("Envio de e-mail não configurado.");
        }

        // Procura a conta cadastrada sem revelar se o e-mail existe no sistema
        Document usuario = usuarios.find(Filters.eq("email", email)).first();
        if (usuario == null || Boolean.FALSE.equals(usuario.getBoolean("ativo"))) {
            return; // A API nunca revela se a conta existe.
        }

        // Impede novas solicitações para a mesma conta em um intervalo de 60 segundos
        ObjectId usuarioId = usuario.getObjectId("_id");
        if (usuarioId == null) return;
        Date limiteFrequencia = Date.from(Instant.now().minusSeconds(60));
        Document recente = recuperacoes.find(Filters.and(
                Filters.eq("usuarioId", usuarioId),
                Filters.gt("criadoEm", limiteFrequencia)
        )).sort(Sorts.descending("criadoEm")).first();
        if (recente != null) return;

        // Gera um token aleatório e armazena apenas seu hash no banco
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String hash = hashToken(token);
        Date agora = new Date();
        Date expiraEm = Date.from(Instant.now().plusSeconds(PRAZO_MINUTOS * 60));

        // Registra a solicitação com a data de expiração e o status de utilização
        Document pedido = new Document("usuarioId", usuarioId)
                .append("tokenHash", hash)
                .append("criadoEm", agora)
                .append("expiraEm", expiraEm)
                .append("utilizado", false);
        recuperacoes.insertOne(pedido);

        // Monta o link de redefinição com o token gerado
        String urlRedefinicao = gmail.urlRedefinicao();
        String separador = urlRedefinicao.contains("?") ? "&" : "?";
        String link = urlRedefinicao + separador
                + "token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);

        // Envia o e-mail e remove a solicitação caso ocorra alguma falha
        try {
            gmail.enviarRecuperacao(email, link);
        } catch (Exception erro) {
            recuperacoes.deleteOne(Filters.eq("tokenHash", hash));
            throw erro;
        }
    }

    // Valida o link de recuperação e atualiza a senha do usuário
    public boolean redefinir(String token, String novaSenha) {

        // Confere o formato do token e o tamanho permitido para a nova senha
        if (token == null || !token.matches("[A-Za-z0-9_-]{40,100}")) return false;
        if (novaSenha == null || novaSenha.length() < 8 || novaSenha.length() > 50
                || novaSenha.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("A senha deve ter de 8 a 50 caracteres e no máximo 72 bytes.");
        }

        // Procura uma solicitação que ainda não foi utilizada e não está expirada
        String hash = hashToken(token);
        Document pedido = recuperacoes.find(filtroPedidoValido(hash)).first();
        if (pedido == null) return false;

        ObjectId usuarioId = pedido.getObjectId("usuarioId");
        if (usuarioId == null) return false;
        Document usuario = usuarios.find(Filters.eq("_id", usuarioId)).first();
        if (usuario == null || Boolean.FALSE.equals(usuario.getBoolean("ativo"))) return false;

        // Calcula o BCrypt uma única vez, depois da consulta inicial e fora
        // da transação, que pode ser repetida pelo driver em caso de conflito.
        String senhaHash = BCrypt.hashpw(novaSenha, BCrypt.gensalt(12));

        // A reserva do link, a senha e as sessões são gravadas juntas. Se uma
        // etapa falhar, withTransaction desfaz as alterações, inclusive o uso do link.
        try (ClientSession sessao = cliente.startSession()) {
            return sessao.withTransaction(() -> redefinirNaTransacao(sessao, hash, senhaHash));
        }
    }

    private boolean redefinirNaTransacao(ClientSession sessao, String hash, String senhaHash) {
        // Reconsulta dentro da transação: outro pedido pode ter usado ou
        // invalidado o link enquanto o hash da nova senha era calculado.
        Document pedido = recuperacoes.find(sessao, filtroPedidoValido(hash)).first();
        if (pedido == null) return false;

        ObjectId id = pedido.getObjectId("_id");
        ObjectId usuarioId = pedido.getObjectId("usuarioId");
        if (id == null || usuarioId == null) return false;

        Document usuario = usuarios.find(sessao, Filters.eq("_id", usuarioId)).first();
        if (usuario == null || Boolean.FALSE.equals(usuario.getBoolean("ativo"))) return false;

        UpdateResult reservado = recuperacoes.updateOne(sessao, Filters.and(
                Filters.eq("_id", id),
                Filters.eq("utilizado", false),
                Filters.gt("expiraEm", new Date())
        ), new Document("$set", new Document("utilizado", true)));
        if (reservado.getModifiedCount() != 1) return false;

        UpdateResult atualizado = usuarios.updateOne(sessao, Filters.and(
                Filters.eq("_id", usuarioId), Filters.ne("ativo", false)),
                new Document("$set", new Document("senhaHash", senhaHash)));
        if (atualizado.getMatchedCount() != 1 || atualizado.getModifiedCount() != 1) {
            throw new IllegalStateException("Não foi possível atualizar a senha do usuário.");
        }

        sessoes.deleteMany(sessao, Filters.eq("usuarioId", usuarioId));

        // Invalida outras solicitações de recuperação ainda pendentes
        recuperacoes.updateMany(sessao, Filters.and(
                Filters.eq("usuarioId", usuarioId),
                Filters.eq("utilizado", false)
        ), new Document("$set", new Document("utilizado", true)));

        return true;
    }

    private static Bson filtroPedidoValido(String hash) {
        return Filters.and(
                Filters.eq("tokenHash", hash),
                Filters.eq("utilizado", false),
                Filters.gt("expiraEm", new Date()));
    }

    // Gera o hash SHA-256 do token para armazená-lo de forma segura
    private static String hashToken(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (Exception erro) {
            throw new IllegalStateException("Falha ao processar token.", erro);
        }
    }

}
