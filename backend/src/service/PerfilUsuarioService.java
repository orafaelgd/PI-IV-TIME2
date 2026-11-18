// Código feito por Lucas David de Sousa RA: 25895152

package service;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.UpdateResult;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.mindrot.jbcrypt.BCrypt;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.NoSuchElementException;

import static com.mongodb.client.model.Filters.eq;

public class PerfilUsuarioService {

    // COLEÇÕES E SERVIÇOS

    private final MongoCollection<Document> usuarios;
    private final SessaoService sessaoService;

    // CONSTRUTOR

    public PerfilUsuarioService(
        MongoCollection<Document> usuarios,
        SessaoService sessaoService
    ) {
        this.usuarios = usuarios;
        this.sessaoService = sessaoService;
    }

    // IDENTIFICAR USUÁRIO

    private static ObjectId obterIdUsuario(Document usuario) {

        if (
            usuario == null ||
            usuario.getObjectId("_id") == null
        ) {
            throw new NoSuchElementException(
                "Usuário não encontrado."
            );
        }

        return usuario.getObjectId("_id");
    }

    // ATUALIZAR E-MAIL OU CELULAR

    public Document atualizarDados(
        Document usuario,
        Document dados
    ) {

        // VALIDAR CAMPOS RECEBIDOS

        if (
            dados == null ||
            dados.size() != 1 ||
            !(
                dados.containsKey("email") ||
                dados.containsKey("celular")
            )
        ) {
            throw new IllegalArgumentException(
                "Envie somente email ou celular."
            );
        }

        String campo = dados.containsKey("email")
            ? "email"
            : "celular";

        Object valorRecebido = dados.get(campo);

        if (!(valorRecebido instanceof String)) {
            throw new IllegalArgumentException(
                "O campo informado deve ser texto."
            );
        }

        String valor;

        // VALIDAR E-MAIL

        if ("email".equals(campo)) {

            valor = ((String) valorRecebido)
                .strip()
                .toLowerCase(Locale.ROOT);

            if (
                valor.length() > 150 ||
                !valor.matches(
                    "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"
                )
            ) {
                throw new IllegalArgumentException(
                    "Informe um e-mail válido."
                );
            }

        } else {

            // VALIDAR CELULAR

            valor = ((String) valorRecebido)
                .replaceAll("\\D", "");

            // Remove o código do Brasil
            if (
                valor.length() == 13 &&
                valor.startsWith("55")
            ) {
                valor = valor.substring(2);
            }

            if (!valor.matches("\\d{11}")) {
                throw new IllegalArgumentException(
                    "Informe um celular válido com DDD."
                );
            }
        }

        // USUÁRIO AUTENTICADO

        ObjectId usuarioId =
            obterIdUsuario(usuario);

        // ATUALIZAR NO MONGODB

        UpdateResult resultado = usuarios.updateOne(
            eq("_id", usuarioId),
            Updates.set(campo, valor)
        );

        if (resultado.getMatchedCount() == 0) {
            throw new NoSuchElementException(
                "Usuário não encontrado."
            );
        }

        // CONSULTAR DADOS ATUALIZADOS

        Document atualizado = usuarios.find(
            eq("_id", usuarioId)
        ).first();

        if (atualizado == null) {
            throw new NoSuchElementException(
                "Usuário não encontrado."
            );
        }

        // RETORNAR DADOS PÚBLICOS

        return new Document(
            "mensagem",
            "Dados atualizados com sucesso!"
        ).append(
            "usuario",
            dadosPublicos(atualizado)
        );
    }

    // ALTERAR SENHA

    public Document alterarSenha(
        Document usuario,
        Document dados
    ) {

        // VALIDAR CAMPOS

        if (
            dados == null ||
            dados.size() != 2 ||
            !(dados.get("senhaAtual") instanceof String) ||
            !(dados.get("novaSenha") instanceof String)
        ) {
            throw new IllegalArgumentException(
                "Informe a senha atual e a nova senha."
            );
        }

        String senhaAtual =
            dados.getString("senhaAtual");

        String novaSenha =
            dados.getString("novaSenha");

        ObjectId usuarioId =
            obterIdUsuario(usuario);

        // CONSULTAR SENHA ATUAL

        // Busca novamente o hash para evitar usar
        // informações desatualizadas da sessão
        Document atual = usuarios.find(
            eq("_id", usuarioId)
        ).first();

        if (atual == null) {
            throw new NoSuchElementException(
                "Usuário não encontrado."
            );
        }

        String senhaHash =
            atual.getString("senhaHash");

        // VALIDAR SENHA ATUAL

        if (
            senhaAtual.isEmpty() ||
            senhaHash == null ||
            !BCrypt.checkpw(senhaAtual, senhaHash)
        ) {
            throw new IllegalArgumentException(
                "Senha atual incorreta."
            );
        }

        // VALIDAR NOVA SENHA

        int tamanhoBytes = novaSenha
            .getBytes(StandardCharsets.UTF_8)
            .length;

        if (
            novaSenha.length() < 8 ||
            novaSenha.length() > 50 ||
            tamanhoBytes > 72
        ) {
            throw new IllegalArgumentException(
                "A nova senha deve ter de 8 a 50 caracteres e no máximo 72 bytes."
            );
        }

        // IMPEDIR REUTILIZAÇÃO

        if (BCrypt.checkpw(novaSenha, senhaHash)) {
            throw new IllegalArgumentException(
                "A nova senha deve ser diferente da atual."
            );
        }

        // GERAR HASH

        String novoHash = BCrypt.hashpw(
            novaSenha,
            BCrypt.gensalt(12)
        );

        // SALVAR NOVA SENHA

        // Garante que a senha anterior não mudou
        // durante a operação
        UpdateResult resultado = usuarios.updateOne(
            com.mongodb.client.model.Filters.and(
                eq("_id", usuarioId),
                eq("senhaHash", senhaHash)
            ),
            Updates.set("senhaHash", novoHash)
        );

        if (resultado.getMatchedCount() == 0) {
            throw new IllegalArgumentException(
                "A senha foi modificada. Tente novamente."
            );
        }

        // ENCERRAR SESSÕES

        sessaoService.encerrarSessoesDoUsuario(
            usuarioId
        );

        // RETORNAR CONFIRMAÇÃO

        return new Document(
            "mensagem",
            "Senha alterada com sucesso! Faça login novamente."
        );
    }

    // DADOS PÚBLICOS DO USUÁRIO

    private static Document dadosPublicos(
        Document usuario
    ) {

        return new Document(
            "id",
            usuario.getObjectId("_id").toHexString()
        )
            .append(
                "nome",
                usuario.getString("nome")
            )
            .append(
                "email",
                usuario.getString("email")
            )
            .append(
                "celular",
                usuario.getString("celular")
            )
            .append(
                "cargo",
                usuario.getString("cargo")
            );
    }
}
