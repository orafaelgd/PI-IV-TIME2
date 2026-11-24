package test;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import service.GmailEmailService;
import service.PerfilUsuarioService;
import service.RecuperacaoSenhaService;
import service.SessaoService;
import service.UsuarioService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class EmailCadastroRecuperacaoTest {

    // Um endereço incompatível com o SMTP é recusado em todas as entradas,
    // antes de criar conta, alterar perfil ou consultar pedidos de recuperação.
    @ParameterizedTest
    @ValueSource(strings = {
        "aná@example.com", "ana\"teste@example.com", "ana<teste@example.com",
        "ana@example.com\r\nBcc: outra@example.com"
    })
    @SuppressWarnings("unchecked")
    void deveUsarMesmaValidacaoNoCadastroPerfilERecuperacao(String email) {
        MongoClient cliente = mock(MongoClient.class);
        MongoCollection<Document> usuarios = mock(MongoCollection.class);
        MongoCollection<Document> sessoes = mock(MongoCollection.class);
        MongoCollection<Document> recuperacoes = mock(MongoCollection.class);
        SessaoService sessaoService = mock(SessaoService.class);
        GmailEmailService gmail = mock(GmailEmailService.class);

        assertThrows(IllegalArgumentException.class,
            () -> new UsuarioService(usuarios).cadastrarUsuario(cadastro(email)));
        assertThrows(IllegalArgumentException.class,
            () -> new PerfilUsuarioService(usuarios, sessaoService).atualizarDados(
                new Document("_id", new ObjectId()), new Document("email", email)));
        assertThrows(IllegalArgumentException.class,
            () -> new RecuperacaoSenhaService(cliente, usuarios, sessoes, recuperacoes, gmail).solicitar(email));

        verifyNoInteractions(cliente, usuarios, sessoes, recuperacoes, sessaoService, gmail);
    }

    @Test
    @SuppressWarnings("unchecked")
    void deveAceitarPontuacaoEEmailNormalizadoNoCadastro() {
        MongoCollection<Document> usuarios = mock(MongoCollection.class);
        doAnswer(invocacao -> {
            Document usuario = invocacao.getArgument(0);
            usuario.put("_id", new ObjectId());
            return null;
        }).when(usuarios).insertOne(any(Document.class));

        new UsuarioService(usuarios).cadastrarUsuario(cadastro(" ANA!TESTE@EXAMPLE.COM "));

        ArgumentCaptor<Document> registrado = ArgumentCaptor.forClass(Document.class);
        verify(usuarios).insertOne(registrado.capture());
        assertEquals("ana!teste@example.com", registrado.getValue().getString("email"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void deveAceitarPontuacaoEEmailNormalizadoNoPerfil() {
        MongoCollection<Document> usuarios = mock(MongoCollection.class);
        SessaoService sessaoService = mock(SessaoService.class);
        ObjectId id = new ObjectId();
        Document atualizado = new Document("_id", id).append("email", "ana!teste@example.com");
        FindIterable<Document> consulta = mock(FindIterable.class);
        when(usuarios.updateOne(any(Bson.class), any(Bson.class)))
            .thenReturn(UpdateResult.acknowledged(1, 1L, null));
        when(usuarios.find(any(Bson.class))).thenReturn(consulta);
        when(consulta.first()).thenReturn(atualizado);

        Document resposta = new PerfilUsuarioService(usuarios, sessaoService).atualizarDados(
            new Document("_id", id), new Document("email", " ANA!TESTE@EXAMPLE.COM "));

        ArgumentCaptor<Bson> alteracao = ArgumentCaptor.forClass(Bson.class);
        verify(usuarios).updateOne(any(Bson.class), alteracao.capture());
        Document campos = Document.parse(alteracao.getValue().toBsonDocument(
            Document.class, com.mongodb.MongoClientSettings.getDefaultCodecRegistry()).toJson());
        assertEquals("ana!teste@example.com", campos.get("$set", Document.class).getString("email"));
        assertEquals("ana!teste@example.com", resposta.get("usuario", Document.class).getString("email"));
    }

    private Document cadastro(String email) {
        return new Document("nome", "Pessoa de Teste")
            .append("email", email)
            .append("celular", "11999999999")
            .append("senha", "SenhaDeTeste123!");
    }
}
