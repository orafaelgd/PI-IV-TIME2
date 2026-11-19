package service;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
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
import java.util.regex.Pattern;

public class RecuperacaoSenhaService {
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long PRAZO_MINUTOS = 20;

    private final MongoCollection<Document> usuarios;
    private final MongoCollection<Document> sessoes;
    private final MongoCollection<Document> recuperacoes;
    private final GmailEmailService gmail = new GmailEmailService();

    public RecuperacaoSenhaService(MongoCollection<Document> usuarios,
                                    MongoCollection<Document> sessoes,
                                    MongoCollection<Document> recuperacoes) {
        this.usuarios = usuarios;
        this.sessoes = sessoes;
        this.recuperacoes = recuperacoes;
    }

    public boolean configurado() {
        return gmail.configurado();
    }

    public void solicitar(String emailInformado) throws Exception {
        if (emailInformado == null) {
            throw new IllegalArgumentException("Informe um e-mail válido.");
        }
        String email = emailInformado.strip().toLowerCase(Locale.ROOT);
        if (email.length() > 150 || !EMAIL.matcher(email).matches()) {
            throw new IllegalArgumentException("Informe um e-mail válido.");
        }
        if (!configurado()) {
            throw new IllegalStateException("Envio de e-mail não configurado.");
        }

        Document usuario = usuarios.find(Filters.eq("email", email)).first();
        if (usuario == null || Boolean.FALSE.equals(usuario.getBoolean("ativo"))) {
            return; // A API nunca revela se a conta existe.
        }

        ObjectId usuarioId = usuario.getObjectId("_id");
        Date limiteFrequencia = Date.from(Instant.now().minusSeconds(60));
        Document recente = recuperacoes.find(Filters.and(
                Filters.eq("usuarioId", usuarioId),
                Filters.gt("criadoEm", limiteFrequencia)
        )).sort(Sorts.descending("criadoEm")).first();
        if (recente != null) return;

        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String hash = hashToken(token);
        Date agora = new Date();
        Date expiraEm = Date.from(Instant.now().plusSeconds(PRAZO_MINUTOS * 60));
        Document pedido = new Document("usuarioId", usuarioId)
                .append("tokenHash", hash)
                .append("criadoEm", agora)
                .append("expiraEm", expiraEm)
                .append("utilizado", false);
        recuperacoes.insertOne(pedido);

        String separador = variavel("APP_RESET_URL").contains("?") ? "&" : "?";
        String link = variavel("APP_RESET_URL") + separador
                + "token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
        try {
            gmail.enviarRecuperacao(email, link);
        } catch (Exception erro) {
            recuperacoes.deleteOne(Filters.eq("_id", pedido.getObjectId("_id")));
            throw erro;
        }
    }

    public boolean redefinir(String token, String novaSenha) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{40,100}")) return false;
        if (novaSenha == null || novaSenha.length() < 8 || novaSenha.length() > 72) {
            throw new IllegalArgumentException("A senha deve conter entre 8 e 72 caracteres.");
        }
        String hash = hashToken(token);
        Document pedido = recuperacoes.find(Filters.and(
                Filters.eq("tokenHash", hash),
                Filters.eq("utilizado", false),
                Filters.gt("expiraEm", new Date())
        )).first();
        if (pedido == null) return false;
        ObjectId id = pedido.getObjectId("_id");
        ObjectId usuarioId = pedido.getObjectId("usuarioId");
        Document usuario = usuarios.find(Filters.eq("_id", usuarioId)).first();
        if (usuario == null || Boolean.FALSE.equals(usuario.getBoolean("ativo"))) return false;

        String senhaHash = BCrypt.hashpw(novaSenha, BCrypt.gensalt(12));
        UpdateResult reservado = recuperacoes.updateOne(Filters.and(
                Filters.eq("_id", id),
                Filters.eq("utilizado", false),
                Filters.gt("expiraEm", new Date())
        ), new Document("$set", new Document("utilizado", true)));
        if (reservado.getModifiedCount() != 1) return false;

        usuarios.updateOne(Filters.eq("_id", usuarioId),
                new Document("$set", new Document("senhaHash", senhaHash)));
        sessoes.deleteMany(Filters.eq("usuarioId", usuarioId));
        recuperacoes.updateMany(Filters.and(
                Filters.eq("usuarioId", usuarioId),
                Filters.eq("utilizado", false)
        ), new Document("$set", new Document("utilizado", true)));
        return true;
    }

    private static String hashToken(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (Exception erro) {
            throw new IllegalStateException("Falha ao processar token.", erro);
        }
    }

    private static String variavel(String nome) {
        String valor = System.getenv(nome);
        return valor == null ? "" : valor.trim();
    }
}
