package service;

import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

/** Configuração local do envio, com prioridade para as variáveis de ambiente. */
public final class ConfiguracaoEmail {
    private static final Pattern EMAIL = Pattern.compile(
        "^[A-Za-z0-9!#$%&'*+/=?^_`{|}~.\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z0-9\\-]+$"
    );

    private final String remetente;
    private final String senhaAplicativo;
    private final String urlRedefinicao;

    public ConfiguracaoEmail(String remetente, String senhaAplicativo, String urlRedefinicao) {
        this.remetente = texto(remetente);
        this.senhaAplicativo = texto(senhaAplicativo).replace(" ", "");
        this.urlRedefinicao = texto(urlRedefinicao);
    }

    public ConfiguracaoEmail(Properties propriedades, Map<String, String> ambiente) {
        this(valor("GMAIL_USER", propriedades, ambiente),
            valor("GMAIL_APP_PASSWORD", propriedades, ambiente),
            valor("APP_RESET_URL", propriedades, ambiente));
    }

    public static ConfiguracaoEmail carregar() {
        return carregar(Path.of("").toAbsolutePath(), System.getenv());
    }

    // O arquivo é opcional: a aplicação pode iniciar sem habilitar a recuperação.
    public static ConfiguracaoEmail carregar(Path diretorio, Map<String, String> ambiente) {
        Path arquivo = diretorio.resolve("email.properties");
        if (!Files.isRegularFile(arquivo)) {
            arquivo = diretorio.resolve("backend").resolve("email.properties");
        }

        Properties propriedades = new Properties();
        if (Files.isRegularFile(arquivo)) {
            try (Reader leitor = Files.newBufferedReader(arquivo, StandardCharsets.UTF_8)) {
                propriedades.load(leitor);
            } catch (IOException | IllegalArgumentException erro) {
                // Não registra conteúdo do arquivo nem impede o uso das demais telas.
                propriedades.clear();
            }
        }
        return new ConfiguracaoEmail(propriedades, ambiente);
    }

    public boolean configurado() {
        return emailValido(remetente)
            && !senhaAplicativo.isBlank()
            && senhaAplicativo.chars().noneMatch(Character::isISOControl)
            && urlValida(urlRedefinicao);
    }

    public String remetente() { return remetente; }
    public String senhaAplicativo() { return senhaAplicativo; }
    public String urlRedefinicao() { return urlRedefinicao; }

    // A pontuação usual do endereço, como '!', é aceita sem permitir injeção SMTP.
    public static boolean emailValido(String email) {
        return email != null && email.length() <= 150 && EMAIL.matcher(email).matches();
    }

    private static boolean urlValida(String valor) {
        try {
            URI uri = URI.create(valor);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getRawUserInfo() != null
                || uri.getRawFragment() != null || uri.getPort() > 65535 || uri.getPort() == 0) {
                return false;
            }
            if (uri.getRawQuery() != null) {
                for (String parametro : uri.getRawQuery().split("&")) {
                    String nome = URLDecoder.decode(parametro.split("=", 2)[0], StandardCharsets.UTF_8);
                    if (nome.equalsIgnoreCase("token")) return false;
                }
            }
            return true;
        } catch (IllegalArgumentException erro) {
            return false;
        }
    }

    private static String valor(String chave, Properties propriedades, Map<String, String> ambiente) {
        String valorAmbiente = ambiente.get(chave);
        return valorAmbiente != null && !valorAmbiente.isBlank()
            ? valorAmbiente : propriedades.getProperty(chave, "");
    }

    private static String texto(String valor) {
        return valor == null ? "" : valor.strip();
    }
}
