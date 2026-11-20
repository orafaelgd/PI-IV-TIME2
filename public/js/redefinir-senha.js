const API_URL = "http://127.0.0.1:8080";

document.addEventListener("DOMContentLoaded", () => {
  const token = new URLSearchParams(window.location.search).get("token");
  window.history.replaceState({}, "", window.location.pathname);
  const form = document.getElementById("formRedefinirSenha");
  const senha = document.getElementById("novaSenha");
  const confirmar = document.getElementById("confirmarSenha");
  const erro = document.getElementById("erroSenha");
  const botao = document.getElementById("btnRedefinir");
  const etapaFormulario = document.getElementById("etapaFormulario");
  const etapaSucesso = document.getElementById("etapaSucesso");

  function informarErro(texto) {
    erro.textContent = texto;
    erro.hidden = false;
    senha.setAttribute("aria-invalid", "true");
    confirmar.setAttribute("aria-invalid", "true");
  }
  [senha, confirmar].forEach(campo => campo.addEventListener("input", () => {
    erro.hidden = true;
    senha.removeAttribute("aria-invalid");
    confirmar.removeAttribute("aria-invalid");
  }));
  document.getElementById("btnOk").addEventListener("click", () => {
    window.location.replace("login-usuario.html");
  });
  if (!token) {
    informarErro("Link inválido ou incompleto. Solicite uma nova recuperação.");
    botao.disabled = true;
    return;
  }

  form.addEventListener("submit", async (evento) => {
    evento.preventDefault();
    erro.hidden = true;
    if (senha.value.length < 8 || senha.value.length > 72) {
      informarErro("A senha deve conter entre 8 e 72 caracteres.");
      return;
    }
    if (senha.value !== confirmar.value) {
      informarErro("As senhas não coincidem.");
      return;
    }
    botao.disabled = true;
    botao.textContent = "Salvando...";
    try {
      const resposta = await fetch(`${API_URL}/api/senha/redefinir`, {
        method: "POST",
        headers: { "Content-Type": "application/json", "Accept": "application/json" },
        cache: "no-store",
        body: JSON.stringify({ token, novaSenha: senha.value })
      });
      if (!resposta.ok) {
        const dados = await resposta.json().catch(() => ({}));
        informarErro(dados.mensagem || "Link inválido ou expirado. Solicite outro.");
        return;
      }
      form.reset();
      sessionStorage.removeItem("token");
      sessionStorage.removeItem("usuario");
      etapaFormulario.hidden = true;
      etapaSucesso.hidden = false;
    } catch (falha) {
      console.error("Falha ao redefinir:", falha);
      informarErro("Não foi possível redefinir sua senha. Tente novamente.");
    } finally {
      botao.disabled = false;
      botao.textContent = "Redefinir senha";
    }
  });
});
