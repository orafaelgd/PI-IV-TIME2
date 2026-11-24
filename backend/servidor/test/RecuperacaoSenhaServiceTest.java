package test;

import com.mongodb.MongoClientSettings;
import com.mongodb.client.ClientSession;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.TransactionBody;
import com.mongodb.client.result.UpdateResult;
import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mindrot.jbcrypt.BCrypt;
import org.mockito.ArgumentCaptor;
import service.GmailEmailService;
import service.RecuperacaoSenhaService;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Exercita a recuperação com coleções, sessão e remetente simulados.
 * A execução do callback é real; commit, rollback e conflitos do MongoDB
 * continuam sendo responsabilidade do driver, sem acesso a um banco neste teste.
 */
public class RecuperacaoSenhaServiceTest {
    private static final String TOKEN = "A".repeat(43);
    private static final String SENHA = "NovaSenha123!";
    private static final String URL = "http://localhost:5500/views/pages/redefinir-senha.html";

    private MongoClient cliente;
    private ClientSession sessao;
    private MongoCollection<Document> usuarios;
    private MongoCollection<Document> sessoes;
    private MongoCollection<Document> recuperacoes;
    private FindIterable<Document> buscaUsuarios;
    private FindIterable<Document> buscaPedidos;
    private FindIterable<Document> buscaUsuariosTransacao;
    private FindIterable<Document> buscaPedidosTransacao;
    private GmailEmailService gmail;
    private RecuperacaoSenhaService service;
    private Document usuario;
    private Document pedido;
    private ObjectId usuarioId;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void prepararTeste() {
        cliente = mock(MongoClient.class);
        sessao = mock(ClientSession.class);
        usuarios = mock(MongoCollection.class);
        sessoes = mock(MongoCollection.class);
        recuperacoes = mock(MongoCollection.class);
        buscaUsuarios = mock(FindIterable.class);
        buscaPedidos = mock(FindIterable.class);
        buscaUsuariosTransacao = mock(FindIterable.class);
        buscaPedidosTransacao = mock(FindIterable.class);
        gmail = mock(GmailEmailService.class);
        usuarioId = new ObjectId();
        usuario = new Document("_id", usuarioId).append("email", "ana!teste@example.com");
        pedido = new Document("_id", new ObjectId()).append("usuarioId", usuarioId);

        when(gmail.configurado()).thenReturn(true);
        when(gmail.urlRedefinicao()).thenReturn(URL);
        when(cliente.startSession()).thenReturn(sessao);
        when(sessao.withTransaction(any())).thenAnswer(invocacao -> {
            TransactionBody<Boolean> corpo = invocacao.getArgument(0);
            return corpo.execute();
        });
        when(usuarios.find(any(Bson.class))).thenReturn(buscaUsuarios);
        when(buscaUsuarios.first()).thenReturn(usuario);
        when(recuperacoes.find(any(Bson.class))).thenReturn(buscaPedidos);
        when(buscaPedidos.sort(any(Bson.class))).thenReturn(buscaPedidos);
        when(buscaPedidos.first()).thenReturn(pedido);
        when(usuarios.find(eq(sessao), any(Bson.class))).thenReturn(buscaUsuariosTransacao);
        when(buscaUsuariosTransacao.first()).thenReturn(usuario);
        when(recuperacoes.find(eq(sessao), any(Bson.class))).thenReturn(buscaPedidosTransacao);
        when(buscaPedidosTransacao.first()).thenReturn(pedido);
        when(recuperacoes.updateOne(eq(sessao), any(Bson.class), any(Bson.class)))
            .thenReturn(UpdateResult.acknowledged(1, 1L, null));
        when(usuarios.updateOne(eq(sessao), any(Bson.class), any(Bson.class)))
            .thenReturn(UpdateResult.acknowledged(1, 1L, null));
        service = new RecuperacaoSenhaService(cliente, usuarios, sessoes, recuperacoes, gmail);
    }

    static Stream<String> senhasInvalidas() {
        return Stream.of(null, "curta", "x".repeat(51), "á".repeat(36) + "A", "é".repeat(40));
    }

    // Caracteres e bytes são limites diferentes: as duas últimas senhas
    // têm menos de 50 caracteres, mas ultrapassam os 72 bytes do BCrypt.
    @ParameterizedTest
    @MethodSource("senhasInvalidas")
    void deveRejeitarSenhaInvalidaAntesDeConsultarBanco(String senha) {
        assertThrows(IllegalArgumentException.class, () -> service.redefinir(TOKEN, senha));
        verifyNoInteractions(cliente, usuarios, sessoes, recuperacoes);
    }

    @Test
    void deveAtualizarSenhaEInvalidarAcessosNaMesmaTransacao() {
        assertTrue(service.redefinir(TOKEN, SENHA));

        ArgumentCaptor<Bson> alteracao = ArgumentCaptor.forClass(Bson.class);
        verify(usuarios).updateOne(eq(sessao), any(Bson.class), alteracao.capture());
        String hash = bson(alteracao.getValue()).getDocument("$set").getString("senhaHash").getValue();
        assertTrue(BCrypt.checkpw(SENHA, hash));
        assertFalse(BCrypt.checkpw("SenhaAntiga123!", hash));

        verify(sessao).withTransaction(any());
        verify(recuperacoes).updateOne(eq(sessao), any(Bson.class), any(Bson.class));
        verify(sessoes).deleteMany(eq(sessao), any(Bson.class));
        verify(recuperacoes).updateMany(eq(sessao), any(Bson.class), any(Bson.class));
        verify(sessao).close();
        verify(usuarios, never()).updateOne(any(Bson.class), any(Bson.class));
        verify(recuperacoes, never()).updateOne(any(Bson.class), any(Bson.class));
        verify(sessoes, never()).deleteMany(any(Bson.class));
    }

    @Test
    void deveRejeitarTokenAusenteOuMalformadoSemConsultarBanco() {
        assertFalse(service.redefinir(null, SENHA));
        assertFalse(service.redefinir("curto", SENHA));
        verifyNoInteractions(cliente, usuarios, sessoes, recuperacoes);
    }

    @Test
    void deveExigirTokenNaoUtilizadoENaoExpirado() {
        when(buscaPedidos.first()).thenReturn(null);
        assertFalse(service.redefinir(TOKEN, SENHA));

        ArgumentCaptor<Bson> filtro = ArgumentCaptor.forClass(Bson.class);
        verify(recuperacoes).find(filtro.capture());
        BsonDocument consulta = bson(filtro.getValue());
        assertEquals(hash(TOKEN), condicao(consulta, "tokenHash").asString().getValue());
        assertFalse(condicao(consulta, "utilizado").asBoolean().getValue());
        long limite = condicao(consulta, "expiraEm").asDocument().getDateTime("$gt").getValue();
        assertTrue(Math.abs(System.currentTimeMillis() - limite) < 5000);
        verifyNoInteractions(cliente);
    }

    @Test
    void deveReconsultarTokenDentroDaTransacaoAntesDeGravar() {
        when(buscaPedidosTransacao.first()).thenReturn(null);
        assertFalse(service.redefinir(TOKEN, SENHA));
        verify(sessao).withTransaction(any());
        verify(recuperacoes, never()).updateOne(eq(sessao), any(Bson.class), any(Bson.class));
        verify(usuarios, never()).updateOne(eq(sessao), any(Bson.class), any(Bson.class));
    }

    @Test
    void deveImpedirRedefinicaoDeUsuarioDesativadoDuranteAOperacao() {
        when(buscaUsuariosTransacao.first()).thenReturn(new Document("_id", usuarioId).append("ativo", false));
        assertFalse(service.redefinir(TOKEN, SENHA));
        verify(recuperacoes, never()).updateOne(eq(sessao), any(Bson.class), any(Bson.class));
    }

    @Test
    void deveRejeitarLinkReservadoPorOutraOperacao() {
        when(recuperacoes.updateOne(eq(sessao), any(Bson.class), any(Bson.class)))
            .thenReturn(UpdateResult.acknowledged(0, 0L, null));
        assertFalse(service.redefinir(TOKEN, SENHA));
        verify(usuarios, never()).updateOne(eq(sessao), any(Bson.class), any(Bson.class));
        verifyNoInteractions(sessoes);
    }

    @Test
    void deveInterromperTransacaoSeUsuarioNaoForAtualizado() {
        when(usuarios.updateOne(eq(sessao), any(Bson.class), any(Bson.class)))
            .thenReturn(UpdateResult.acknowledged(0, 0L, null));
        assertThrows(IllegalStateException.class, () -> service.redefinir(TOKEN, SENHA));
        verify(sessao).withTransaction(any());
        verify(sessao).close();
        verifyNoInteractions(sessoes);
        verify(recuperacoes, never()).updateMany(eq(sessao), any(Bson.class), any(Bson.class));
    }

    @Test
    void devePropagarFalhaDeGravacaoParaAbortarTransacao() {
        when(usuarios.updateOne(eq(sessao), any(Bson.class), any(Bson.class)))
            .thenThrow(new IllegalStateException("Falha de escrita simulada"));
        assertThrows(IllegalStateException.class, () -> service.redefinir(TOKEN, SENHA));
        verify(sessao).withTransaction(any());
        verify(sessao).close();
        verifyNoInteractions(sessoes);
    }

    @Test
    void devePropagarFalhaAoEncerrarSessoesParaAbortarTransacao() {
        when(sessoes.deleteMany(eq(sessao), any(Bson.class)))
            .thenThrow(new IllegalStateException("Falha de escrita simulada"));
        assertThrows(IllegalStateException.class, () -> service.redefinir(TOKEN, SENHA));
        verify(sessao).withTransaction(any());
        verify(recuperacoes, never()).updateMany(eq(sessao), any(Bson.class), any(Bson.class));
    }

    @Test
    void devePropagarFalhaAoInvalidarOutrosPedidosParaAbortarTransacao() {
        when(recuperacoes.updateMany(eq(sessao), any(Bson.class), any(Bson.class)))
            .thenThrow(new IllegalStateException("Falha de escrita simulada"));
        assertThrows(IllegalStateException.class, () -> service.redefinir(TOKEN, SENHA));
        verify(sessao).withTransaction(any());
        verify(sessao).close();
    }

    @Test
    void deveSolicitarLinkComTokenProtegidoEPrazoDeVinteMinutos() throws Exception {
        when(buscaPedidos.first()).thenReturn(null);
        service.solicitar(" ANA!TESTE@EXAMPLE.COM ");

        ArgumentCaptor<Document> novoPedido = ArgumentCaptor.forClass(Document.class);
        verify(recuperacoes).insertOne(novoPedido.capture());
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(gmail).enviarRecuperacao(eq("ana!teste@example.com"), link.capture());
        assertTrue(link.getValue().startsWith(URL + "?token="));
        String token = URLDecoder.decode(link.getValue().split("token=", 2)[1], StandardCharsets.UTF_8);
        assertTrue(token.matches("[A-Za-z0-9_-]{43}"));

        Document registrado = novoPedido.getValue();
        assertEquals(hash(token), registrado.getString("tokenHash"));
        assertFalse(registrado.containsKey("token"));
        assertFalse(registrado.getBoolean("utilizado"));
        assertEquals(usuarioId, registrado.getObjectId("usuarioId"));
        long prazo = registrado.getDate("expiraEm").getTime() - registrado.getDate("criadoEm").getTime();
        assertTrue(prazo >= 1_199_000 && prazo <= 1_201_000);
    }

    @Test
    void devePreservarQueryDaUrlAoAdicionarToken() throws Exception {
        when(buscaPedidos.first()).thenReturn(null);
        when(gmail.urlRedefinicao()).thenReturn(URL + "?origem=login");
        service.solicitar("ana!teste@example.com");
        verify(gmail).enviarRecuperacao(eq("ana!teste@example.com"), startsWith(URL + "?origem=login&token="));
    }

    @Test
    void deveRespeitarIntervaloDeSessentaSegundosEntreSolicitacoes() throws Exception {
        service.solicitar("ana!teste@example.com");
        verify(recuperacoes, never()).insertOne(any(Document.class));
        verify(gmail, never()).enviarRecuperacao(anyString(), anyString());

        ArgumentCaptor<Bson> filtro = ArgumentCaptor.forClass(Bson.class);
        verify(recuperacoes).find(filtro.capture());
        long limite = condicao(bson(filtro.getValue()), "criadoEm").asDocument().getDateTime("$gt").getValue();
        assertTrue(Math.abs(Instant.now().minusSeconds(60).toEpochMilli() - limite) < 5000);
    }

    @Test
    void deveIgnorarContaAusenteOuInativaSemEnviarEmail() throws Exception {
        when(buscaUsuarios.first()).thenReturn(null);
        service.solicitar("ausente@example.com");
        when(buscaUsuarios.first()).thenReturn(new Document("_id", usuarioId).append("ativo", false));
        service.solicitar("inativa@example.com");
        verifyNoInteractions(recuperacoes);
        verify(gmail, never()).enviarRecuperacao(anyString(), anyString());
    }

    @Test
    void deveRemoverPedidoQuandoEnvioDeEmailFalhar() throws Exception {
        when(buscaPedidos.first()).thenReturn(null);
        doThrow(new IOException("Falha SMTP simulada")).when(gmail).enviarRecuperacao(anyString(), anyString());
        assertThrows(IOException.class, () -> service.solicitar("ana!teste@example.com"));

        ArgumentCaptor<Document> criado = ArgumentCaptor.forClass(Document.class);
        verify(recuperacoes).insertOne(criado.capture());
        ArgumentCaptor<Bson> exclusao = ArgumentCaptor.forClass(Bson.class);
        verify(recuperacoes).deleteOne(exclusao.capture());
        assertEquals(criado.getValue().getString("tokenHash"), bson(exclusao.getValue()).getString("tokenHash").getValue());
    }

    @Test
    void deveExigirConfiguracaoAntesDeSolicitarEmail() {
        when(gmail.configurado()).thenReturn(false);
        assertThrows(IllegalStateException.class, () -> service.solicitar("ana@example.com"));
        verifyNoInteractions(usuarios, recuperacoes);
    }

    private static BsonDocument bson(Bson filtro) {
        return filtro.toBsonDocument(Document.class, MongoClientSettings.getDefaultCodecRegistry());
    }

    private static org.bson.BsonValue condicao(BsonDocument filtro, String campo) {
        for (var parte : filtro.getArray("$and")) {
            BsonDocument documento = parte.asDocument();
            if (documento.containsKey(campo)) return documento.get(campo);
        }
        throw new AssertionError("Condição não encontrada: " + campo);
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception erro) {
            throw new AssertionError(erro);
        }
    }
}
