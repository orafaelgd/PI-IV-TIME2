
// Código feito por Miriã Nascimento dos Anjos RA: 25892704

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

    // Coleções utilizadas para consultar usuários e armazenar as sessões
    private final MongoCollection<Document> usuarios;
    private final MongoCollection<Document> sessoes;
    private final SecureRandom random = new SecureRandom();

    // Recebe as coleções do banco necessárias para o funcionamento do login
    public LoginService(MongoCollection<Document> usuarios, MongoCollection<Document> sessoes) {
        this.usuarios = usuarios;
        this.sessoes = sessoes;
    }

    // Verifica as credenciais e cria uma sessão quando o login é válido
    public Document entrar(String email, String senha) {

        // Padroniza o e-mail e procura a conta cadastrada no banco
        String emailNormalizado = email.strip().toLowerCase(Locale.ROOT);
        Document usuario = usuarios.find(eq("email", emailNormalizado)).first();
        if (usuario == null || Boolean.FALSE.equals(usuario.get("ativo"))) {
            return null;
        }

        // Compara a senha informada com o hash armazenado no banco
        String hash = usuario.getString("senhaHash");
        if (hash == null || !senhaCorreta(senha, hash)) {
            return null;
        }

         // Gera um token aleatório para identificar a sessão do usuário
        byte[] segredo = new byte[32];
        random.nextBytes(segredo);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(segredo);
        
        // Define o prazo de validade da sessão em 8 horas
        Date expiracao = Date.from(Instant.now().plusSeconds(8 * 3600));

        // Armazena a sessão no MongoDB junto ao usuário e à data de expiração
        sessoes.insertOne(new Document("token", token)
                .append("usuarioId", usuario.get("_id"))
                .append("expiraEm", expiracao)
                .append("criadoEm", new Date()));

        // Retorna o token e os dados necessários para o frontend        
        return new Document("token", token)
                .append("usuario", dadosPublicos(usuario))
                .append("expiraEm", expiracao.toInstant().toString());
    }

    // Confere a senha utilizando o BCrypt
    private boolean senhaCorreta(String senha, String hash) {
        try {
            return BCrypt.checkpw(senha, hash);
        } catch (IllegalArgumentException erro) {
            return false;
        }
    }

    // Encerra a sessão removendo o token armazenado no banco
    public void sair(String token) {
        if (token != null && !token.isBlank()) {
            sessoes.deleteOne(eq("token", token));
        }
    }

    // Seleciona os dados do usuário que podem ser enviados ao frontend
    public static Document dadosPublicos(Document usuario) {
        Object identificador = usuario.get("_id");
        return new Document("id", identificador == null ? null : identificador.toString())
                .append("nome", usuario.getString("nome"))
                .append("email", usuario.getString("email"))
                .append("celular", usuario.getString("celular"));
    }
}
