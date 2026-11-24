
// Código feito por Miriã Nascimento dos Anjos RA: 25892704

const API_URL = "http://127.0.0.1:8080";

document.addEventListener("DOMContentLoaded", () => {

  // Recupera os elementos do formulário de recuperação de senha
  const form = document.getElementById("formRecuperarSenha");
  const email = document.getElementById("email");
  const erro = document.getElementById("erroEmail");
  const mensagem = document.getElementById("mensagem");
  const botao = document.getElementById("btnEnviar");

  // Remove a mensagem de erro quando o usuário começa a corrigir o e-mail
  email.addEventListener("input", () => {
    erro.hidden = true;
    email.removeAttribute("aria-invalid");
  });

  // Valida o e-mail e envia a solicitação de recuperação de senha
  form.addEventListener("submit", async (evento) => {
    evento.preventDefault();
    if (botao.disabled) return;

    const endereco = email.value.trim().toLowerCase();

    erro.hidden = true;
    mensagem.hidden = true;

    // Confere se o e-mail informado possui um formato válido
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(endereco) || endereco.length > 150) {
      erro.textContent = "Informe um e-mail válido.";
      erro.hidden = false;
      email.setAttribute("aria-invalid", "true");
      return;
    }

    // Desabilita o botão enquanto a solicitação está sendo enviada
    botao.disabled = true;
    botao.textContent = "Enviando...";

    try {

      // Envia o e-mail informado para a API de recuperação de senha
      const resposta = await fetch(`${API_URL}/api/senha/solicitar`, {
        method: "POST",
        headers: { "Content-Type": "application/json", "Accept": "application/json" },
        cache: "no-store",
        body: JSON.stringify({ email: endereco })
      });

      // Mantém a explicação do backend, como a indisponibilidade do envio de e-mail.
      if (!resposta.ok) {
        const dados = await resposta.json().catch(() => ({}));
        erro.textContent = typeof dados.mensagem === "string" && dados.mensagem.trim()
          ? dados.mensagem
          : "Não foi possível enviar a solicitação. Tente novamente.";
        erro.hidden = false;
        return;
      }

      // Informa sobre o envio do link sem revelar se o e-mail está cadastrado
      mensagem.textContent = "Se o e-mail estiver cadastrado, você receberá um link para redefinir a senha. Verifique também a caixa de spam.";
      mensagem.hidden = false;
      form.reset();

    } catch (falha) {

      // Exibe uma mensagem caso ocorra alguma falha na solicitação
      console.error("Falha ao solicitar recuperação:", falha);
      erro.textContent = "Não foi possível enviar a solicitação. Tente novamente.";
      erro.hidden = false;

    } finally {

      // Restaura o botão após finalizar a tentativa de envio
      botao.disabled = false;
      botao.textContent = "Enviar link";
    }
  });
});
