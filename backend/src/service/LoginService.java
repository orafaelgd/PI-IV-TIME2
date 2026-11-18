package service;

import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.mindrot.jbcrypt.BCrypt;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Locale;

import static com.mongodb.client.model.Filters.eq;

public class LoginService {
    private final MongoCollection<Document> usuarios;
    private final MongoCollection<Document> sessoes;
    private final SecureRandom random = new SecureRandom();

    public LoginService(MongoCollection<Document> usuarios, MongoCollection<Document> sessoes) {
        this.usuarios = usuarios;
        this.sessoes = sessoes;
    }

    public Document entrar(String email, String senha) {
        String emailNormalizado = email.strip().toLowerCase(Locale.ROOT);
        Document usuario = usuarios.find(eq("email", emailNormalizado)).first();
        if (usuario == null || Boolean.FALSE.equals(usuario.get("ativo"))) {
            return null;
        }

        String hash = usuario.getString("senhaHash");
        if (hash == null || !senhaCorreta(senha, hash)) {
            return null;
        }

        byte[] segredo = new byte[32];
        random.nextBytes(segredo);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(segredo);
        Date expiracao = Date.from(Instant.now().plusSeconds(8 * 3600));

        sessoes.insertOne(new Document("token", token)
                .append("usuarioId", usuario.get("_id"))
                .append("expiraEm", expiracao)
                .append("criadoEm", new Date()));

        return new Document("token", token)
                .append("usuario", dadosPublicos(usuario))
                .append("expiraEm", expiracao.toInstant().toString());
    }

    private boolean senhaCorreta(String senha, String hash) {
        try {
            return BCrypt.checkpw(senha, hash);
        } catch (IllegalArgumentException erro) {
            return false;
        }
    }

    public void sair(String token) {
        if (token != null && !token.isBlank()) {
            sessoes.deleteOne(eq("token", token));
        }
    }

    public static Document dadosPublicos(Document usuario) {
        Object identificador = usuario.get("_id");
        return new Document("id", identificador == null ? null : identificador.toString())
                .append("nome", usuario.getString("nome"))
                .append("email", usuario.getString("email"))
                .append("celular", usuario.getString("celular"));
    }
}
