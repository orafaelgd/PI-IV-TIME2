const API_URL = "http://127.0.0.1:8080";

document.addEventListener("DOMContentLoaded", () => {
    const form = document.getElementById("formLogin");
    if (!form) return;

    const email = document.getElementById("email");
    const senha = document.getElementById("senha");
    const erroEmail = document.getElementById("erroEmail");
    const erroSenha = document.getElementById("erroSenha");
    const btnMostrarSenha = document.getElementById("btnMostrarSenha");
    const btnEntrar = form.querySelector('button[type="submit"]');

    function mostrarErro(campo, elementoErro, mensagem) {
        elementoErro.textContent = mensagem;
        elementoErro.classList.remove("hidden");
        campo.setAttribute("aria-invalid", "true");
    }

    function limparErro(campo, elementoErro) {
        elementoErro.textContent = "";
        elementoErro.classList.add("hidden");
        campo.removeAttribute("aria-invalid");
    }

    email.addEventListener("input", () => limparErro(email, erroEmail));
    senha.addEventListener("input", () => limparErro(senha, erroSenha));

    btnMostrarSenha.addEventListener("click", () => {
        const mostrando = senha.type === "text";
        senha.type = mostrando ? "password" : "text";
        btnMostrarSenha.setAttribute("aria-label", mostrando ? "Mostrar senha" : "Ocultar senha");
    });

    form.addEventListener("submit", async (evento) => {
        evento.preventDefault();
        if (btnEntrar.disabled) return;

        limparErro(email, erroEmail);
        limparErro(senha, erroSenha);
        const emailDigitado = email.value.trim().toLowerCase();
        const senhaDigitada = senha.value;
        let valido = true;

        if (!emailDigitado) {
            mostrarErro(email, erroEmail, "Informe seu e-mail.");
            valido = false;
        } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(emailDigitado) || emailDigitado.length > 150) {
            mostrarErro(email, erroEmail, "Informe um e-mail válido.");
            valido = false;
        }
        if (!senhaDigitada) {
            mostrarErro(senha, erroSenha, "Informe sua senha.");
            valido = false;
        }
        if (!valido) return;

        btnEntrar.disabled = true;
        btnEntrar.textContent = "Entrando...";

        try {
            const resposta = await fetch(`${API_URL}/api/login`, {
                method: "POST",
                headers: { "Content-Type": "application/json", "Accept": "application/json" },
                cache: "no-store",
                body: JSON.stringify({ email: emailDigitado, senha: senhaDigitada })
            });

            const dados = await resposta.json();
            if (!resposta.ok) {
                mostrarErro(senha, erroSenha, resposta.status === 401
                    ? "E-mail ou senha incorretos."
                    : (dados.mensagem || "Não foi possível realizar o login."));
                return;
            }
            if (typeof dados.token !== "string" || !dados.token || !dados.usuario) {
                throw new Error("Resposta de login incompleta.");
            }

            sessionStorage.setItem("token", dados.token);
            sessionStorage.setItem("usuario", JSON.stringify(dados.usuario));
            window.location.replace("dashboard.html");
        } catch (erro) {
            console.error("Erro no login:", erro);
            mostrarErro(senha, erroSenha, "Não foi possível realizar o login. Verifique a conexão com o servidor.");
        } finally {
            btnEntrar.disabled = false;
            btnEntrar.textContent = "Entrar";
        }
    });
});
