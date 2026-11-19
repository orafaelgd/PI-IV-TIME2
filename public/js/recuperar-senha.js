const API_URL = "http://127.0.0.1:8080";

document.addEventListener("DOMContentLoaded", () => {
  const form = document.getElementById("formRecuperarSenha");
  const email = document.getElementById("email");
  const erro = document.getElementById("erroEmail");
  const mensagem = document.getElementById("mensagem");
  const botao = document.getElementById("btnEnviar");

  email.addEventListener("input", () => {
    erro.hidden = true;
    email.removeAttribute("aria-invalid");
  });

  form.addEventListener("submit", async (evento) => {
    evento.preventDefault();
    const endereco = email.value.trim().toLowerCase();
    erro.hidden = true;
    mensagem.hidden = true;
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(endereco) || endereco.length > 150) {
      erro.textContent = "Informe um e-mail válido.";
      erro.hidden = false;
      email.setAttribute("aria-invalid", "true");
      return;
    }

    botao.disabled = true;
    botao.textContent = "Enviando...";
    try {
      const resposta = await fetch(`${API_URL}/api/senha/solicitar`, {
        method: "POST",
        headers: { "Content-Type": "application/json", "Accept": "application/json" },
        cache: "no-store",
        body: JSON.stringify({ email: endereco })
      });
      if (!resposta.ok) throw new Error(`HTTP ${resposta.status}`);
      mensagem.textContent = "Se o e-mail estiver cadastrado, você receberá um link para redefinir a senha. Verifique também a caixa de spam.";
      mensagem.hidden = false;
      form.reset();
    } catch (falha) {
      console.error("Falha ao solicitar recuperação:", falha);
      erro.textContent = "Não foi possível enviar a solicitação. Tente novamente.";
      erro.hidden = false;
    } finally {
      botao.disabled = false;
      botao.textContent = "Enviar link";
    }
  });
});
