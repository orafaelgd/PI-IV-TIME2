// Código feito por Lucas David de Sousa RA: 25895152

// CÓDIGO ORIGINAL DO PERFIL: LUCAS DAVID DE SOUSA
// INTEGRAÇÃO COM A AUTENTICAÇÃO POR USUÁRIO

document.addEventListener("DOMContentLoaded", () => {

    // CONFIGURAÇÕES

    const API_URL = "http://127.0.0.1:8080";
    const PAGINA_LOGIN = "login-usuario.html";

    const $ = (id) => document.getElementById(id);

    // ELEMENTOS GERAIS

    const statusPerfil = $("statusPerfil");
    const nomeUsuario = $("nomeUsuario");
    const emailUsuario = $("emailUsuario");
    const telefoneUsuario = $("telefoneUsuario");
    const cargoUsuario = $("cargoUsuario");

    // ELEMENTOS DO E-MAIL

    const visualizacaoEmail = $("visualizacaoEmail");
    const edicaoEmail = $("edicaoEmail");
    const inputEmailUsuario = $("inputEmailUsuario");
    const erroEmailUsuario = $("erroEmailUsuario");

    const btnEditarEmail = $("btnEditarEmail");
    const btnSalvarEmail = $("btnSalvarEmail");
    const btnCancelarEmail = $("btnCancelarEmail");

    // ELEMENTOS DO TELEFONE

    const visualizacaoTelefone = $("visualizacaoTelefone");
    const edicaoTelefone = $("edicaoTelefone");
    const inputTelefoneUsuario = $("inputTelefoneUsuario");
    const erroTelefoneUsuario = $("erroTelefoneUsuario");

    const btnEditarTelefone = $("btnEditarTelefone");
    const btnSalvarTelefone = $("btnSalvarTelefone");
    const btnCancelarTelefone = $("btnCancelarTelefone");

    // ELEMENTOS DA SENHA

    const modalAlterarSenha = $("modalAlterarSenha");
    const senhaAtual = $("senhaAtual");
    const novaSenha = $("novaSenha");
    const confirmarNovaSenha = $("confirmarNovaSenha");

    const erroSenhaUsuario = $("erroSenhaUsuario");

    const btnAlterarSenha = $("btnAlterarSenha");
    const btnSalvarSenha = $("btnSalvarSenha");
    const btnCancelarSenha = $("btnCancelarSenha");

    const btnMostrarNovaSenha = $("btnMostrarNovaSenha");
    const btnMostrarConfirmacaoSenha =
        $("btnMostrarConfirmacaoSenha");

    // ELEMENTOS DA FOTO

    const fotoUsuario = $("fotoUsuario");
    const fotoPlaceholder = $("fotoPlaceholder");
    const btnAlterarFoto = $("btnAlterarFoto");
    const inputFotoUsuario = $("inputFotoUsuario");
    const erroFotoUsuario = $("erroFotoUsuario");

    // VARIÁVEIS

    let usuario = null;
    let salvando = false;

    // AUTENTICAÇÃO

    function obterToken() {
        return sessionStorage.getItem("token");
    }

    // Limpa os dados locais e retorna ao login
    function redirecionarLogin() {

        sessionStorage.removeItem("token");
        sessionStorage.removeItem("usuario");

        window.location.replace(PAGINA_LOGIN);
    }

    // COMUNICAÇÃO COM A API

    async function chamarAPI(caminho, opcoes = {}) {

        const token = obterToken();

        if (!token || !token.trim()) {

            const erro = new Error(
                "Faça login para acessar seu perfil."
            );

            erro.status = 401;
            throw erro;
        }

        // Envia o token da sessão para o backend
        const headers = new Headers(opcoes.headers || {});

        headers.set("Authorization", `Bearer ${token}`);
        headers.set("Accept", "application/json");

        if (opcoes.body !== undefined) {
            headers.set(
                "Content-Type",
                "application/json"
            );
        }

        const resposta = await fetch(
            `${API_URL}${caminho}`,
            {
                ...opcoes,
                headers,
                cache: "no-store"
            }
        );

        const dados = await resposta.json()
            .catch(() => ({}));

        if (!resposta.ok) {

            const erro = new Error(
                dados.mensagem ||
                "Não foi possível concluir a operação."
            );

            erro.status = resposta.status;
            throw erro;
        }

        return dados;
    }

    // MENSAGENS GERAIS

    function mostrarStatus(texto, tipo = "normal") {

        statusPerfil.textContent = texto;

        statusPerfil.classList.remove(
            "hidden",
            "text-danger",
            "text-primary-darker"
        );

        if (tipo === "erro") {
            statusPerfil.classList.add("text-danger");
        }

        if (tipo === "sucesso") {
            statusPerfil.classList.add(
                "text-primary-darker"
            );
        }
    }

    function limparStatus() {

        statusPerfil.textContent = "";
        statusPerfil.classList.add("hidden");
    }

    // HABILITAR EDIÇÃO

    function habilitarEdicao(habilitado) {

        [
            btnEditarEmail,
            btnEditarTelefone,
            btnAlterarSenha,
            btnAlterarFoto
        ].forEach((botao) => {
            botao.disabled = !habilitado;
        });
    }

    // SESSÃO EXPIRADA

    function sessaoExpirada() {

        usuario = null;

        habilitarEdicao(false);
        fecharEdicaoEmail();
        fecharEdicaoTelefone();
        fecharModalSenha();

        redirecionarLogin();
    }

    // TRATAR ERROS

    function mensagemErro(erro) {

        if (erro.status === 401) {

            sessaoExpirada();

            return "Sessão expirada. Faça login novamente.";
        }

        if (erro instanceof TypeError) {

            return (
                "Falha de conexão com a API. " +
                "Confira se o backend Java está ligado."
            );
        }

        return erro.message ||
            "Ocorreu um erro inesperado.";
    }

    // FORMATAR TELEFONE

    function obterNumerosTelefone(valor) {

        let numeros = String(valor || "")
            .replace(/\D/g, "");

        if (
            numeros.length === 13 &&
            numeros.startsWith("55")
        ) {
            numeros = numeros.substring(2);
        }

        return numeros;
    }

    function aplicarMascaraTelefone(valor) {

        const numeros = obterNumerosTelefone(valor)
            .substring(0, 11);

        if (!numeros) return "";

        if (numeros.length <= 2) {
            return `(${numeros}`;
        }

        if (numeros.length <= 7) {

            return (
                `(${numeros.substring(0, 2)}) ` +
                numeros.substring(2)
            );
        }

        return (
            `(${numeros.substring(0, 2)}) ` +
            `${numeros.substring(2, 7)}-` +
            numeros.substring(7)
        );
    }

    function formatarTelefone(valor) {

        const numeros = obterNumerosTelefone(valor);

        if (numeros.length !== 11) {
            return "Não informado";
        }

        return aplicarMascaraTelefone(numeros);
    }

    // ATUALIZAR DADOS NA TELA

    function atualizarTela() {

        if (!usuario) return;

        nomeUsuario.textContent =
            usuario.nome || "Não informado";

        emailUsuario.textContent =
            usuario.email || "Não informado";

        telefoneUsuario.textContent =
            formatarTelefone(usuario.celular);

        cargoUsuario.textContent =
            usuario.cargo || "Não informado";

        atualizarFoto();
    }

    // CARREGAR PERFIL

    async function carregarPerfil() {

        habilitarEdicao(false);

        mostrarStatus("Carregando seus dados...");

        try {

            // O backend identifica o usuário pelo token
            usuario = await chamarAPI("/api/perfil");

            // Confere se o backend retornou o ID
            if (!usuario.id) {
                throw new Error(
                    "O servidor não retornou o ID do usuário."
                );
            }

            atualizarTela();

            // Atualiza somente os dados públicos da sessão
            sessionStorage.setItem(
                "usuario",
                JSON.stringify(usuario)
            );

            habilitarEdicao(true);
            limparStatus();

        } catch (erro) {

            nomeUsuario.textContent =
                "Dados indisponíveis";

            emailUsuario.textContent = "—";
            telefoneUsuario.textContent = "—";

            cargoUsuario.textContent =
                "Não informado";

            mostrarStatus(
                mensagemErro(erro),
                "erro"
            );
        }
    }

    // ATUALIZAR CAMPO NO MONGODB

    async function atualizarCampo(campo, valor) {

        const resposta = await chamarAPI(
            "/api/perfil",
            {
                method: "PATCH",
                body: JSON.stringify({
                    [campo]: valor
                })
            }
        );

        if (!resposta.usuario?.id) {
            throw new Error(
                "Resposta de atualização inválida."
            );
        }

        usuario = resposta.usuario;

        sessionStorage.setItem(
            "usuario",
            JSON.stringify(usuario)
        );

        atualizarTela();
    }

    // VALIDAÇÕES DOS CAMPOS

    function mostrarErro(elemento, input, texto) {

        elemento.textContent = texto;
        elemento.classList.remove("hidden");

        input.setAttribute(
            "aria-invalid",
            "true"
        );
    }

    function removerErro(elemento, input) {

        elemento.textContent = "";
        elemento.classList.add("hidden");

        input.removeAttribute("aria-invalid");
    }

    // FECHAR EDIÇÃO DO E-MAIL

    function fecharEdicaoEmail() {

        removerErro(
            erroEmailUsuario,
            inputEmailUsuario
        );

        edicaoEmail.classList.add("hidden");
        visualizacaoEmail.classList.remove("hidden");
    }

    // ABRIR EDIÇÃO DO E-MAIL

    btnEditarEmail.addEventListener("click", () => {

        if (!usuario || salvando) return;

        fecharEdicaoTelefone();
        limparStatus();

        inputEmailUsuario.value =
            usuario.email || "";

        removerErro(
            erroEmailUsuario,
            inputEmailUsuario
        );

        visualizacaoEmail.classList.add("hidden");
        edicaoEmail.classList.remove("hidden");

        inputEmailUsuario.focus();
        inputEmailUsuario.select();
    });

    // CANCELAR EDIÇÃO DO E-MAIL

    btnCancelarEmail.addEventListener("click", () => {

        if (!salvando) {
            fecharEdicaoEmail();
        }
    });

    inputEmailUsuario.addEventListener("input", () => {

        removerErro(
            erroEmailUsuario,
            inputEmailUsuario
        );
    });

    inputEmailUsuario.addEventListener(
        "keydown",
        (evento) => {

            if (evento.key === "Enter") {

                evento.preventDefault();
                salvarEmail();

            } else if (
                evento.key === "Escape" &&
                !salvando
            ) {

                fecharEdicaoEmail();
            }
        }
    );

    btnSalvarEmail.addEventListener(
        "click",
        salvarEmail
    );

    // SALVAR E-MAIL

    async function salvarEmail() {

        if (!usuario || salvando) return;

        const email = inputEmailUsuario.value
            .trim()
            .toLowerCase();

        if (
            !email ||
            email.length > 150 ||
            !inputEmailUsuario.checkValidity()
        ) {

            mostrarErro(
                erroEmailUsuario,
                inputEmailUsuario,
                "Digite um e-mail válido."
            );

            return;
        }

        if (email === usuario.email) {
            fecharEdicaoEmail();
            return;
        }

        salvando = true;

        btnSalvarEmail.disabled = true;
        btnSalvarEmail.textContent = "Salvando...";

        try {

            await atualizarCampo("email", email);

            fecharEdicaoEmail();

            mostrarStatus(
                "E-mail atualizado com sucesso!",
                "sucesso"
            );

        } catch (erro) {

            const texto = mensagemErro(erro);

            if (erro.status !== 401) {

                mostrarErro(
                    erroEmailUsuario,
                    inputEmailUsuario,
                    texto
                );
            }

        } finally {

            salvando = false;

            btnSalvarEmail.disabled = false;
            btnSalvarEmail.textContent = "Salvar";
        }
    }

    // FECHAR EDIÇÃO DO TELEFONE

    function fecharEdicaoTelefone() {

        removerErro(
            erroTelefoneUsuario,
            inputTelefoneUsuario
        );

        edicaoTelefone.classList.add("hidden");

        visualizacaoTelefone.classList.remove(
            "hidden"
        );
    }

    // ABRIR EDIÇÃO DO TELEFONE

    btnEditarTelefone.addEventListener(
        "click",
        () => {

            if (!usuario || salvando) return;

            fecharEdicaoEmail();
            limparStatus();

            inputTelefoneUsuario.value =
                formatarTelefone(usuario.celular);

            removerErro(
                erroTelefoneUsuario,
                inputTelefoneUsuario
            );

            visualizacaoTelefone.classList.add(
                "hidden"
            );

            edicaoTelefone.classList.remove(
                "hidden"
            );

            inputTelefoneUsuario.focus();
            inputTelefoneUsuario.select();
        }
    );

    // CANCELAR EDIÇÃO DO TELEFONE

    btnCancelarTelefone.addEventListener(
        "click",
        () => {

            if (!salvando) {
                fecharEdicaoTelefone();
            }
        }
    );

    // MÁSCARA DO TELEFONE

    inputTelefoneUsuario.addEventListener(
        "input",
        () => {

            inputTelefoneUsuario.value =
                aplicarMascaraTelefone(
                    inputTelefoneUsuario.value
                );

            removerErro(
                erroTelefoneUsuario,
                inputTelefoneUsuario
            );
        }
    );

    inputTelefoneUsuario.addEventListener(
        "keydown",
        (evento) => {

            if (evento.key === "Enter") {

                evento.preventDefault();
                salvarTelefone();

            } else if (
                evento.key === "Escape" &&
                !salvando
            ) {

                fecharEdicaoTelefone();
            }
        }
    );

    btnSalvarTelefone.addEventListener(
        "click",
        salvarTelefone
    );

    // SALVAR TELEFONE

    async function salvarTelefone() {

        if (!usuario || salvando) return;

        const celular = obterNumerosTelefone(
            inputTelefoneUsuario.value
        );

        if (!/^\d{11}$/.test(celular)) {

            mostrarErro(
                erroTelefoneUsuario,
                inputTelefoneUsuario,
                "Digite um celular válido com DDD."
            );

            return;
        }

        if (celular === usuario.celular) {
            fecharEdicaoTelefone();
            return;
        }

        salvando = true;

        btnSalvarTelefone.disabled = true;
        btnSalvarTelefone.textContent = "Salvando...";

        try {

            await atualizarCampo(
                "celular",
                celular
            );

            fecharEdicaoTelefone();

            mostrarStatus(
                "Celular atualizado com sucesso!",
                "sucesso"
            );

        } catch (erro) {

            const texto = mensagemErro(erro);

            if (erro.status !== 401) {

                mostrarErro(
                    erroTelefoneUsuario,
                    inputTelefoneUsuario,
                    texto
                );
            }

        } finally {

            salvando = false;

            btnSalvarTelefone.disabled = false;
            btnSalvarTelefone.textContent = "Salvar";
        }
    }

    // FECHAR MODAL DA SENHA

    function fecharModalSenha() {

        senhaAtual.value = "";
        novaSenha.value = "";
        confirmarNovaSenha.value = "";

        novaSenha.type = "password";
        confirmarNovaSenha.type = "password";

        btnMostrarNovaSenha.setAttribute(
            "aria-label",
            "Mostrar senha"
        );

        btnMostrarConfirmacaoSenha.setAttribute(
            "aria-label",
            "Mostrar senha"
        );

        erroSenhaUsuario.textContent = "";
        erroSenhaUsuario.classList.add("hidden");

        modalAlterarSenha.classList.add("hidden");
        modalAlterarSenha.classList.remove("flex");
    }

    // ABRIR MODAL DA SENHA

    btnAlterarSenha.addEventListener("click", () => {

        if (!usuario || salvando) return;

        fecharEdicaoEmail();
        fecharEdicaoTelefone();
        limparStatus();

        erroSenhaUsuario.textContent = "";
        erroSenhaUsuario.classList.add("hidden");

        modalAlterarSenha.classList.remove("hidden");
        modalAlterarSenha.classList.add("flex");

        senhaAtual.focus();
    });

    // CANCELAR ALTERAÇÃO DA SENHA

    btnCancelarSenha.addEventListener("click", () => {

        if (!salvando) {
            fecharModalSenha();
        }
    });

    // MOSTRAR OU OCULTAR SENHA

    function alternarVisibilidadeSenha(input, botao) {

        const mostrar = input.type === "password";

        input.type = mostrar
            ? "text"
            : "password";

        botao.setAttribute(
            "aria-label",
            mostrar ? "Ocultar senha" : "Mostrar senha"
        );
    }

    btnMostrarNovaSenha.addEventListener("click", () => {

        alternarVisibilidadeSenha(
            novaSenha,
            btnMostrarNovaSenha
        );
    });

    btnMostrarConfirmacaoSenha.addEventListener(
        "click",
        () => {

            alternarVisibilidadeSenha(
                confirmarNovaSenha,
                btnMostrarConfirmacaoSenha
            );
        }
    );

    // FECHAR MODAL COM ESC

    document.addEventListener("keydown", (evento) => {

        if (
            evento.key === "Escape" &&
            !salvando &&
            !modalAlterarSenha.classList.contains("hidden")
        ) {

            fecharModalSenha();
        }
    });

    // SALVAR NOVA SENHA

    btnSalvarSenha.addEventListener(
        "click",
        async () => {

            if (!usuario || salvando) return;

            const atual = senhaAtual.value;
            const nova = novaSenha.value;
            const confirmar = confirmarNovaSenha.value;

            // MENSAGEM DE ERRO

            function exibirErro(texto) {

                erroSenhaUsuario.textContent = texto;
                erroSenhaUsuario.classList.remove(
                    "hidden"
                );
            }

            erroSenhaUsuario.textContent = "";
            erroSenhaUsuario.classList.add("hidden");

            // VALIDAR SENHA ATUAL

            if (!atual) {

                exibirErro(
                    "Informe sua senha atual."
                );

                return;
            }

            // VALIDAR NOVA SENHA

            if (
                nova.length < 8 ||
                nova.length > 50 ||
                new TextEncoder().encode(nova).length > 72
            ) {

                exibirErro(
                    "A nova senha deve ter de 8 a 50 caracteres e no máximo 72 bytes."
                );

                return;
            }

            // CONFIRMAR SENHA

            if (confirmar !== nova) {

                exibirErro(
                    "As senhas não coincidem."
                );

                return;
            }

            salvando = true;

            btnSalvarSenha.disabled = true;
            btnSalvarSenha.textContent = "Salvando...";

            try {

                // O backend usa o ID da sessão
                await chamarAPI(
                    "/api/perfil/senha",
                    {
                        method: "PATCH",

                        body: JSON.stringify({
                            senhaAtual: atual,
                            novaSenha: nova
                        })
                    }
                );

                // O backend encerra todas as sessões
                fecharModalSenha();

                usuario = null;
                habilitarEdicao(false);

                sessionStorage.removeItem("token");
                sessionStorage.removeItem("usuario");

                mostrarStatus(
                    "Senha alterada com sucesso! Faça login novamente.",
                    "sucesso"
                );

                // Retorna ao login após a confirmação
                alert(
                    "Senha alterada com sucesso! Faça login novamente."
                );

                window.location.replace(
                    PAGINA_LOGIN
                );

            } catch (erro) {

                const texto = mensagemErro(erro);

                if (erro.status !== 401) {
                    exibirErro(texto);
                }

            } finally {

                salvando = false;

                btnSalvarSenha.disabled = false;
                btnSalvarSenha.textContent = "Salvar";
            }
        }
    );

    // FOTO DO USUÁRIO

    // A foto continua salva somente no navegador
    // Cada usuário possui sua própria chave

    function chaveFotoLocal() {

        return usuario?.id
            ? `psicomManagerFoto:${usuario.id}`
            : "";
    }

    // ATUALIZAR FOTO

    function atualizarFoto() {

        if (!usuario?.id) return;

        let foto = null;

        try {

            foto = localStorage.getItem(
                chaveFotoLocal()
            );

        } catch (erro) {

            console.warn(
                "Armazenamento de fotos indisponível.",
                erro
            );
        }

        if (
            foto &&
            /^data:image\/(jpeg|png|webp);base64,/.test(foto)
        ) {

            fotoUsuario.src = foto;

            fotoUsuario.classList.remove("hidden");
            fotoPlaceholder.classList.add("hidden");

        } else {

            fotoUsuario.removeAttribute("src");
            fotoUsuario.classList.add("hidden");

            fotoPlaceholder.textContent =
                (usuario.nome || "?")
                    .charAt(0)
                    .toUpperCase();

            fotoPlaceholder.classList.remove("hidden");
        }
    }

    // MOSTRAR ERRO DA FOTO

    function mostrarErroFoto(texto) {

        erroFotoUsuario.textContent = texto;

        erroFotoUsuario.classList.remove(
            "hidden"
        );
    }

    // SELECIONAR FOTO

    btnAlterarFoto.addEventListener("click", () => {

        if (usuario && !salvando) {

            inputFotoUsuario.click();
        }
    });

    // VALIDAR E SALVAR FOTO

    inputFotoUsuario.addEventListener("change", () => {

        if (!usuario?.id) return;

        erroFotoUsuario.textContent = "";
        erroFotoUsuario.classList.add("hidden");

        const arquivo = inputFotoUsuario.files[0];

        if (!arquivo) return;

        // TIPOS PERMITIDOS

        const tiposPermitidos = [
            "image/jpeg",
            "image/png",
            "image/webp"
        ];

        if (!tiposPermitidos.includes(arquivo.type)) {

            mostrarErroFoto(
                "Escolha uma imagem JPG, PNG ou WEBP."
            );

            inputFotoUsuario.value = "";
            return;
        }

        // LIMITE DE 2 MB

        if (arquivo.size > 2 * 1024 * 1024) {

            mostrarErroFoto(
                "A imagem deve ter no máximo 2 MB."
            );

            inputFotoUsuario.value = "";
            return;
        }

        const usuarioId = usuario.id;
        const leitor = new FileReader();

        // SALVAR FOTO NO NAVEGADOR

        leitor.addEventListener("load", () => {

            // Impede salvar a imagem em outra conta
            if (
                !usuario ||
                usuario.id !== usuarioId
            ) {
                return;
            }

            if (
                typeof leitor.result !== "string" ||
                !/^data:image\/(jpeg|png|webp);base64,/.test(
                    leitor.result
                )
            ) {

                mostrarErroFoto(
                    "Não foi possível ler a imagem."
                );

                return;
            }

            try {

                localStorage.setItem(
                    chaveFotoLocal(),
                    leitor.result
                );

                atualizarFoto();

                mostrarStatus(
                    "Foto salva somente neste navegador.",
                    "normal"
                );

            } catch (erro) {

                mostrarErroFoto(
                    "Não foi possível salvar a foto neste navegador."
                );
            }
        });

        leitor.addEventListener("error", () => {

            mostrarErroFoto(
                "Não foi possível ler a imagem."
            );
        });

        leitor.readAsDataURL(arquivo);
        inputFotoUsuario.value = "";
    });

    // INICIALIZAR PERFIL

    if (!obterToken()) {

        redirecionarLogin();
        return;
    }

    carregarPerfil();
});
