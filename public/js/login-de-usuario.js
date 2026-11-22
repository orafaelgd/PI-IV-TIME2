
// Código feito por Miriã Nascimento dos Anjos RA: 25892704

const API_URL = "http://127.0.0.1:8080";

document.addEventListener("DOMContentLoaded", () => {

    // Recupera os elementos do formulário de login
    const form = document.getElementById("formLogin");
    if (!form) return;

    const email = document.getElementById("email");
    const senha = document.getElementById("senha");
    const erroEmail = document.getElementById("erroEmail");
    const erroSenha = document.getElementById("erroSenha");
    const btnMostrarSenha = document.getElementById("btnMostrarSenha");
    const btnEntrar = form.querySelector('button[type="submit"]');

    // Exibe uma mensagem de erro no campo que não passou pela validação
    function mostrarErro(campo, elementoErro, mensagem) {
        elementoErro.textContent = mensagem;
        elementoErro.classList.remove("hidden");
        campo.setAttribute("aria-invalid", "true");
    }

    // Remove a mensagem de erro quando o usuário corrige o campo
    function limparErro(campo, elementoErro) {
        elementoErro.textContent = "";
        elementoErro.classList.add("hidden");
        campo.removeAttribute("aria-invalid");
    }

    // Limpa os erros conforme o usuário digita nos campos
    email.addEventListener("input", () => limparErro(email, erroEmail));
    senha.addEventListener("input", () => limparErro(senha, erroSenha));

    // Permite mostrar ou ocultar a senha digitada
    btnMostrarSenha.addEventListener("click", () => {
        const mostrando = senha.type === "text";
        senha.type = mostrando ? "password" : "text";
        btnMostrarSenha.setAttribute("aria-label", mostrando ? "Mostrar senha" : "Ocultar senha");
    });

    // Valida os dados do formulário e envia a solicitação de login
    form.addEventListener("submit", async (evento) => {
        evento.preventDefault();
        if (btnEntrar.disabled) return;

        limparErro(email, erroEmail);
        limparErro(senha, erroSenha);

        const emailDigitado = email.value.trim().toLowerCase();
        const senhaDigitada = senha.value;
        let valido = true;

        // Verifica se o e-mail foi preenchido e possui um formato válido
        if (!emailDigitado) {
            mostrarErro(email, erroEmail, "Informe seu e-mail.");
            valido = false;
        } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(emailDigitado) || emailDigitado.length > 150) {
            mostrarErro(email, erroEmail, "Informe um e-mail válido.");
            valido = false;
        }

        // Verifica se o campo de senha foi preenchido
        if (!senhaDigitada) {
            mostrarErro(senha, erroSenha, "Informe sua senha.");
            valido = false;
        }

        if (!valido) return;

        // Desabilita o botão para evitar várias tentativas ao mesmo tempo
        btnEntrar.disabled = true;
        btnEntrar.textContent = "Entrando...";

        try {

            // Envia o e-mail e a senha para a API de autenticação
            const resposta = await fetch(`${API_URL}/api/login`, {
                method: "POST",
                headers: { "Content-Type": "application/json", "Accept": "application/json" },
                cache: "no-store",
                body: JSON.stringify({ email: emailDigitado, senha: senhaDigitada })
            });

            const dados = await resposta.json();

            // Informa quando as credenciais estão incorretas ou ocorre uma falha no login
            if (!resposta.ok) {
                mostrarErro(senha, erroSenha, resposta.status === 401
                    ? "E-mail ou senha incorretos."
                    : (dados.mensagem || "Não foi possível realizar o login."));
                return;
            }

            // Confere se a resposta contém os dados necessários da sessão
            if (typeof dados.token !== "string" || !dados.token || !dados.usuario) {
                throw new Error("Resposta de login incompleta.");
            }

            // Salva o token e os dados do usuário e redireciona para o Dashboard
            sessionStorage.setItem("token", dados.token);
            sessionStorage.setItem("usuario", JSON.stringify(dados.usuario));
            window.location.replace("dashboard.html");

        } catch (erro) {

            // Exibe uma mensagem caso ocorra algum erro de comunicação com o servidor
            console.error("Erro no login:", erro);
            mostrarErro(senha, erroSenha, "Não foi possível realizar o login. Verifique a conexão com o servidor.");

        } finally {

            // Restaura o botão após finalizar a tentativa de login
            btnEntrar.disabled = false;
            btnEntrar.textContent = "Entrar";
        }
    });
});
