
// Código feito por Miriã Nascimento dos Anjos RA: 25892704

const API_URL = "http://127.0.0.1:8080";

document.addEventListener("DOMContentLoaded", () => {

  // Recupera o token enviado pelo link de redefinição de senha
  const token = new URLSearchParams(window.location.search).get("token");

  // Remove o token da URL para que ele não continue aparecendo no navegador
  window.history.replaceState({}, "", window.location.pathname);

  // Recupera os campos do formulário e os elementos de confirmação
  const form = document.getElementById("formRedefinirSenha");
  const senha = document.getElementById("novaSenha");
  const confirmar = document.getElementById("confirmarSenha");
  const erro = document.getElementById("erroSenha");
  const botao = document.getElementById("btnRedefinir");
  const etapaFormulario = document.getElementById("etapaFormulario");
  const etapaSucesso = document.getElementById("etapaSucesso");

  // Exibe mensagens de erro quando os dados informados são inválidos
  function informarErro(texto) {
    erro.textContent = texto;
    erro.hidden = false;
    senha.setAttribute("aria-invalid", "true");
    confirmar.setAttribute("aria-invalid", "true");
  }

  // Limpa as mensagens de erro quando o usuário altera os campos
  [senha, confirmar].forEach(campo => campo.addEventListener("input", () => {
    erro.hidden = true;
    senha.removeAttribute("aria-invalid");
    confirmar.removeAttribute("aria-invalid");
  }));

  // Redireciona para a tela de login após a confirmação
  document.getElementById("btnOk").addEventListener("click", () => {
    window.location.replace("login-usuario.html");
  });

  // Impede a redefinição caso o link não possua um token válido para consulta
  if (!token || !/^[A-Za-z0-9_-]{40,100}$/.test(token)) {
    informarErro("Link inválido ou incompleto. Solicite uma nova recuperação.");
    botao.disabled = true;
    return;
  }

  // Valida a nova senha e envia a solicitação de redefinição
  form.addEventListener("submit", async (evento) => {
    evento.preventDefault();
    if (botao.disabled) return;
    erro.hidden = true;

    // Usa os mesmos limites do cadastro e do backend. Acentos e emojis
    // ocupam mais bytes, por isso a contagem de caracteres sozinha não basta.
    if (senha.value.length < 8 || senha.value.length > 50) {
      informarErro("A senha deve conter entre 8 e 50 caracteres.");
      return;
    }

    if (new TextEncoder().encode(senha.value).length > 72) {
      informarErro("A senha ultrapassa o limite de tamanho permitido. Reduza os caracteres acentuados ou emojis.");
      return;
    }

    // Confere se a nova senha e a confirmação são iguais
    if (senha.value !== confirmar.value) {
      informarErro("As senhas não coincidem.");
      return;
    }

    // Desabilita o botão enquanto a nova senha está sendo salva
    botao.disabled = true;
    botao.textContent = "Salvando...";

    try {

      // Envia o token e a nova senha para a API de redefinição
      const resposta = await fetch(`${API_URL}/api/senha/redefinir`, {
        method: "POST",
        headers: { "Content-Type": "application/json", "Accept": "application/json" },
        cache: "no-store",
        body: JSON.stringify({ token, novaSenha: senha.value })
      });

      // Informa quando o link está inválido, expirado ou ocorre algum erro
      if (!resposta.ok) {
        const dados = await resposta.json().catch(() => ({}));
        informarErro(dados.mensagem || "Link inválido ou expirado. Solicite outro.");
        return;
      }

      // Limpa o formulário e remove os dados da sessão anterior
      form.reset();
      sessionStorage.removeItem("token");
      sessionStorage.removeItem("usuario");

      // Oculta o formulário e apresenta a confirmação da senha redefinida
      etapaFormulario.hidden = true;
      etapaSucesso.hidden = false;

    } catch (falha) {

      // Exibe uma mensagem caso ocorra uma falha de comunicação com o servidor
      console.error("Falha ao redefinir:", falha);
      informarErro("Não foi possível redefinir sua senha. Tente novamente.");

    } finally {

      // Restaura o botão após finalizar a solicitação
      botao.disabled = false;
      botao.textContent = "Redefinir senha";
    }
  });
});
