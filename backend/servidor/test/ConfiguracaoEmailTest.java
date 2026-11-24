package test;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import service.ConfiguracaoEmail;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

public class ConfiguracaoEmailTest {
    private static final String URL = "http://localhost:5500/views/pages/redefinir-senha.html";

    @TempDir
    Path temporario;

    // Todos os arquivos são temporários; a configuração real do usuário não é lida.
    @Test
    void deveLerArquivoUtf8ExecutandoPelaPastaBackend() throws Exception {
        String url = URL + "?idioma=português";
        Files.writeString(temporario.resolve("email.properties"), propriedadesTexto(url), StandardCharsets.UTF_8);

        ConfiguracaoEmail configuracao = ConfiguracaoEmail.carregar(temporario, Map.of());

        assertTrue(configuracao.configurado());
        assertEquals("avaliacao@example.com", configuracao.remetente());
        assertEquals(url, configuracao.urlRedefinicao());
        assertEquals("abcdefghijklmnop", configuracao.senhaAplicativo());
    }

    @Test
    void deveLocalizarArquivoBackendExecutandoPelaRaiz() throws Exception {
        Path backend = Files.createDirectory(temporario.resolve("backend"));
        Files.writeString(backend.resolve("email.properties"), propriedadesTexto(URL), StandardCharsets.UTF_8);

        assertTrue(ConfiguracaoEmail.carregar(temporario, Map.of()).configurado());
    }

    @Test
    void variaveisDeAmbienteDevemTerPrioridadeSobreArquivo() {
        Properties arquivo = propriedades(URL);
        ConfiguracaoEmail configuracao = new ConfiguracaoEmail(arquivo, Map.of(
            "GMAIL_USER", "ambiente@example.com",
            "GMAIL_APP_PASSWORD", " senha de teste ",
            "APP_RESET_URL", "https://ecostock.example/redefinir"
        ));

        assertTrue(configuracao.configurado());
        assertEquals("ambiente@example.com", configuracao.remetente());
        assertEquals("senhadeteste", configuracao.senhaAplicativo());
        assertEquals("https://ecostock.example/redefinir", configuracao.urlRedefinicao());
    }

    @Test
    void ausenciaOuArquivoInvalidoNaoDevemImpedirInicializacao() throws Exception {
        assertFalse(ConfiguracaoEmail.carregar(temporario, Map.of()).configurado());
        Files.writeString(temporario.resolve("email.properties"), "GMAIL_USER=\\uINVALIDO", StandardCharsets.UTF_8);
        assertFalse(ConfiguracaoEmail.carregar(temporario, Map.of()).configurado());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "", "/views/pages/redefinir-senha.html", "javascript:alert(1)",
        "http://localhost:5500/redefinir#secao", "http://localhost:5500/redefinir?token=anterior",
        "https://ecostock.example/redefinir?%74oken=anterior", "http://usuario:senha@localhost/redefinir",
        "http://localhost:70000/redefinir"
    })
    void deveRejeitarUrlQueNaoPodeReceberTokenNaQuery(String url) {
        assertFalse(new ConfiguracaoEmail(propriedades(url), Map.of()).configurado());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ana!teste@example.com", "ana+teste@example.com", "o'brien@example.com"})
    void deveAceitarPontuacaoUsualDoEnderecoCadastrado(String email) {
        assertTrue(ConfiguracaoEmail.emailValido(email));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "ana@example.com\r\nBcc: outra@example.com", "Ana <ana@example.com>",
        "ana teste@example.com", "aná@example.com", "ana@example.com\t"
    })
    void deveRejeitarEnderecoInseguroParaSmtp(String email) {
        assertFalse(ConfiguracaoEmail.emailValido(email));
    }

    private Properties propriedades(String url) {
        Properties propriedades = new Properties();
        propriedades.setProperty("GMAIL_USER", "avaliacao@example.com");
        propriedades.setProperty("GMAIL_APP_PASSWORD", "abcd efgh ijkl mnop");
        propriedades.setProperty("APP_RESET_URL", url);
        return propriedades;
    }

    private String propriedadesTexto(String url) {
        return "GMAIL_USER=avaliacao@example.com\nGMAIL_APP_PASSWORD=abcd efgh ijkl mnop\nAPP_RESET_URL=" + url + "\n";
    }
}
