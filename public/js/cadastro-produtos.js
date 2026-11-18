// Código feito por Livia Carvalho Lucizano RA: 25017514

document.addEventListener("DOMContentLoaded", async () => {

    // CONFIGURAÇÕES

    const API = "http://localhost:8080/api";
    const PAGINA_LOGIN = "login-usuario.html";

    // ELEMENTOS DO FORMULÁRIO

    const formulario =
        document.getElementById("formCadastroProduto");

    const campoNomeProduto =
        document.getElementById("nomeProduto");

    const campoCategoria =
        document.getElementById("categoria");

    const botaoSalvar =
        document.getElementById("botaoSalvar");

    const mensagem =
        document.getElementById("mensagemFormulario");

    // IDENTIFICAR EDIÇÃO

    const parametros = new URLSearchParams(
        window.location.search
    );

    const produtoId = parametros.get("id");
    const editando = Boolean(produtoId);

    // VERIFICAR SESSÃO

    // Recupera o token salvo pelo login
    function obterToken() {
        return sessionStorage.getItem("token");
    }

    // Limpa a sessão e retorna ao login
    function encerrarAcesso() {

        sessionStorage.removeItem("token");
        sessionStorage.removeItem("usuario");

        window.location.replace(PAGINA_LOGIN);
    }

    // Verifica se existe um token
    function verificarSessao() {

        const token = obterToken();

        if (!token || !token.trim()) {
            encerrarAcesso();
            return false;
        }

        return true;
    }

    // REQUISIÇÕES AUTENTICADAS

    // Envia o token para o backend validar a sessão
    async function requisicaoAutenticada(url, opcoes = {}) {

        const token = obterToken();

        if (!token || !token.trim()) {

            encerrarAcesso();

            throw new Error("Sessão não encontrada.");
        }

        const headers = new Headers(opcoes.headers || {});

        headers.set("Authorization", `Bearer ${token}`);

        const resposta = await fetch(url, {
            ...opcoes,
            headers,
            cache: "no-store"
        });

        // Redireciona quando a sessão expirou
        if (resposta.status === 401) {

            encerrarAcesso();

            throw new Error(
                "Sua sessão expirou. Faça login novamente."
            );
        }

        if (resposta.status === 403) {

            throw new Error(
                "Você não tem permissão para realizar esta operação."
            );
        }

        return resposta;
    }

    // MENSAGENS DE ERRO

    function mostrarErro(texto) {

        mensagem.textContent = texto;
        mensagem.classList.remove("hidden");
    }

    // LEITURA DAS RESPOSTAS

    // Converte a resposta da API para JSON
    async function lerResposta(resposta) {

        const texto = await resposta.text();
        let dados = {};

        if (texto) {

            try {
                dados = JSON.parse(texto);
            } catch {
                dados = { mensagem: texto };
            }
        }

        if (!resposta.ok) {

            throw new Error(
                dados.mensagem ||
                dados.erro ||
                `Erro HTTP ${resposta.status}`
            );
        }

        return dados;
    }

    // LISTAS DE SUGESTÕES

    // Guarda os nomes e categorias cadastrados
    let nomesProdutos = [];
    let categoriasProdutos = [];

    // Cria os dois campos com o mesmo comportamento
    function criarListaPersonalizada(
        idCampo,
        idLista,
        idBotao,
        idGrupo
    ) {

        const campo = document.getElementById(idCampo);
        const lista = document.getElementById(idLista);
        const botao = document.getElementById(idBotao);
        const grupo = document.getElementById(idGrupo);

        let opcoes = [];
        let indiceAtivo = -1;

        // VERIFICAR LISTA ABERTA

        function estaAberta() {
            return !lista.hidden;
        }

        // FECHAR LISTA

        function fechar() {

            lista.hidden = true;

            indiceAtivo = -1;

            campo.setAttribute("aria-expanded", "false");
            campo.removeAttribute("aria-activedescendant");

            botao.setAttribute("aria-expanded", "false");
        }

        // FILTRAR OPÇÕES

        // Filtra somente quando o usuário está digitando
        function filtrar(usarFiltro = true) {

            if (!usarFiltro) {
                return [...opcoes];
            }

            const pesquisa = campo.value
                .trim()
                .toLocaleLowerCase("pt-BR");

            if (!pesquisa) {
                return [...opcoes];
            }

            return opcoes.filter((opcao) =>
                opcao.toLocaleLowerCase("pt-BR")
                    .includes(pesquisa)
            );
        }

        // SELECIONAR OPÇÃO

        // Preenche o campo sem fechar antes do clique
        function selecionar(valor) {

            campo.value = valor;

            // Avisa outros eventos que o valor mudou
            campo.dispatchEvent(
                new Event("change", { bubbles: true })
            );

            fechar();
            campo.focus();
        }

        // DESTACAR OPÇÃO

        function destacar(indice) {

            const botoes = Array.from(
                lista.querySelectorAll(".opcao-sugestao")
            );

            if (botoes.length === 0) {
                indiceAtivo = -1;
                return;
            }

            indiceAtivo =
                (indice + botoes.length) % botoes.length;

            botoes.forEach((opcao, posicao) => {

                const ativo = posicao === indiceAtivo;

                opcao.classList.toggle("ativa", ativo);

                opcao.setAttribute(
                    "aria-selected",
                    String(ativo)
                );
            });

            const selecionada = botoes[indiceAtivo];

            if (selecionada) {

                campo.setAttribute(
                    "aria-activedescendant",
                    selecionada.id
                );

                selecionada.scrollIntoView({
                    block: "nearest"
                });
            }
        }

        // DESENHAR LISTA

        // Monta as opções sem utilizar o datalist nativo
        function desenhar(usarFiltro = true) {

            const filtradas = filtrar(usarFiltro);

            lista.replaceChildren();
            indiceAtivo = -1;

            campo.removeAttribute("aria-activedescendant");

            // MOSTRAR LISTA VAZIA

            if (filtradas.length === 0) {

                const aviso = document.createElement("div");

                aviso.className = "lista-vazia";

                aviso.textContent = opcoes.length === 0
                    ? "Nenhuma opção cadastrada."
                    : "Nenhuma opção encontrada.";

                lista.appendChild(aviso);

                return;
            }

            // CRIAR OPÇÕES

            filtradas.forEach((valor, indice) => {

                const opcao = document.createElement("button");

                opcao.type = "button";
                opcao.id = `${idLista}-opcao-${indice}`;
                opcao.className = "opcao-sugestao";

                opcao.setAttribute("role", "option");
                opcao.setAttribute("aria-selected", "false");

                opcao.textContent = valor;

                // Mantém o foco no input durante a seleção
                opcao.addEventListener("mousedown", (evento) => {
                    evento.preventDefault();
                });

                // Preenche o campo ao clicar na opção
                opcao.addEventListener("click", () => {
                    selecionar(valor);
                });

                lista.appendChild(opcao);
            });
        }

        // ABRIR LISTA

        function abrir(usarFiltro = true) {

            // Fecha a outra lista antes de abrir esta
            fecharTodas(this);

            desenhar(usarFiltro);

            lista.hidden = false;

            campo.setAttribute("aria-expanded", "true");
            botao.setAttribute("aria-expanded", "true");
        }

        // CLICAR NA SETINHA

        // Abre a lista completa mesmo com texto digitado
        botao.addEventListener("click", () => {

            if (estaAberta()) {

                fechar();

            } else {

                abrir(false);
                campo.focus();
            }
        });

        // DIGITAR NO CAMPO

        // Abre sugestões que correspondem ao texto
        campo.addEventListener("input", () => {

            abrir(true);
        });

        // NAVEGAÇÃO PELO TECLADO

        campo.addEventListener("keydown", (evento) => {

            if (evento.key === "ArrowDown") {

                evento.preventDefault();

                if (!estaAberta()) {
                    abrir(true);
                }

                destacar(indiceAtivo + 1);
            }

            if (evento.key === "ArrowUp") {

                evento.preventDefault();

                if (!estaAberta()) {
                    abrir(true);
                }

                destacar(indiceAtivo - 1);
            }

            // Seleciona a opção destacada
            if (
                evento.key === "Enter" &&
                estaAberta()
            ) {

                const botoes = lista.querySelectorAll(
                    ".opcao-sugestao"
                );

                if (indiceAtivo >= 0 && botoes[indiceAtivo]) {

                    evento.preventDefault();

                    selecionar(
                        botoes[indiceAtivo].textContent
                    );
                }
            }

            // Fecha as sugestões ao pressionar Escape
            if (evento.key === "Escape") {

                if (estaAberta()) {
                    evento.preventDefault();
                }

                fechar();
            }

            if (evento.key === "Tab") {
                fechar();
            }
        });

        // FECHAR AO CLICAR FORA

        document.addEventListener("pointerdown", (evento) => {

            if (!grupo.contains(evento.target)) {
                fechar();
            }
        });

        // ATUALIZAR DADOS

        // Recebe os nomes novos retornados pela API
        function atualizar(novasOpcoes) {

            const vistos = new Set();

            opcoes = novasOpcoes
                .map((valor) => String(valor).trim())
                .filter((valor) => {

                    const chave =
                        valor.toLocaleLowerCase("pt-BR");

                    if (!valor || vistos.has(chave)) {
                        return false;
                    }

                    vistos.add(chave);
                    return true;
                })
                .sort((a, b) =>
                    a.localeCompare(b, "pt-BR")
                );

            // Atualiza a lista sem fechar se estiver aberta
            if (estaAberta()) {
                desenhar(false);
            }
        }

        // Retorna as funções para utilizar na tela
        return {
            atualizar,
            fechar
        };
    }

    // CONTROLAR AS DUAS LISTAS

    let listaNome;
    let listaCategoria;

    function fecharTodas(excecao = null) {

        if (listaNome && listaNome !== excecao) {
            listaNome.fechar();
        }

        if (listaCategoria && listaCategoria !== excecao) {
            listaCategoria.fechar();
        }
    }

    // CRIAR LISTA DE NOMES

    listaNome = criarListaPersonalizada(
        "nomeProduto",
        "listaNomesProdutos",
        "setaNomeProduto",
        "grupoNomeProduto"
    );

    // CRIAR LISTA DE CATEGORIAS

    listaCategoria = criarListaPersonalizada(
        "categoria",
        "listaCategorias",
        "setaCategoria",
        "grupoCategoria"
    );

    // CARREGAR NOMES DOS PRODUTOS

    // Busca os produtos da conta autenticada
    async function carregarNomesProdutos() {

        try {

            const resposta = await requisicaoAutenticada(
                `${API}/produtos`
            );

            const dados = await lerResposta(resposta);

            const produtos = Array.isArray(dados)
                ? dados
                : dados.produtos || [];

            nomesProdutos = produtos.map((produto) =>
                produto.nomeProduto || produto.nome || ""
            );

            listaNome.atualizar(nomesProdutos);

        } catch (erro) {

            console.error(
                "Erro ao buscar nomes dos produtos:", erro
            );
        }
    }

    // CARREGAR CATEGORIAS

    // Busca as categorias da conta autenticada
    async function carregarCategorias() {

        try {

            const resposta = await requisicaoAutenticada(
                `${API}/categorias`
            );

            const dados = await lerResposta(resposta);

            const categorias = Array.isArray(dados)
                ? dados
                : dados.categorias || [];

            categoriasProdutos = categorias.map((categoria) =>
                typeof categoria === "string"
                    ? categoria
                    : categoria.nome || ""
            );

            listaCategoria.atualizar(categoriasProdutos);

        } catch (erro) {

            console.error(
                "Erro ao buscar categorias:", erro
            );
        }
    }

    // CARREGAR PRODUTO PARA EDIÇÃO

    async function carregarProduto() {

        botaoSalvar.disabled = true;

        try {

            const resposta = await requisicaoAutenticada(
                `${API}/produtos/${encodeURIComponent(produtoId)}`
            );

            const dados = await lerResposta(resposta);
            const produto = dados.produto || dados;

            // Preenche os dados do produto
            campoNomeProduto.value =
                produto.nomeProduto || produto.nome || "";

            campoCategoria.value =
                typeof produto.categoria === "object"
                    ? produto.categoria?.nome || ""
                    : produto.categoria || "";

            document.getElementById("quantidadeUnidade").value =
                produto.quantidadeUnidade ?? "";

            document.getElementById("unidadeMedida").value =
                String(produto.unidadeMedida || "").toLowerCase();

            document.getElementById("status").value =
                String(produto.status || "ativo").toLowerCase();

            document.getElementById("observacao").value =
                produto.observacao || "";

        } catch (erro) {

            mostrarErro(
                `Não foi possível carregar o produto: ${erro.message}`
            );

        } finally {

            botaoSalvar.disabled = false;
        }
    }

    // CONFIGURAR TELA DE EDIÇÃO

    function configurarEdicao() {

        if (!editando) {
            return;
        }

        document.title =
            "Editar Produto | Sistema de Gestão e Redução de Desperdício";

        document.getElementById("caminhoPagina").textContent =
            "/ Editar Produto";

        document.getElementById("tituloFormulario").textContent =
            "Editar produto";

        botaoSalvar.textContent = "Salvar alterações";
    }

    // BOTÃO CANCELAR

    document.getElementById("botaoCancelar")
        .addEventListener("click", () => {

            window.location.href = "todos-produtos.html";
        });

    // CADASTRAR OU ATUALIZAR PRODUTO

    formulario.addEventListener("submit", async (evento) => {

        evento.preventDefault();

        mensagem.classList.add("hidden");

        // Verifica a sessão antes de salvar
        if (!verificarSessao()) {
            return;
        }

        fecharTodas();

        // Recupera os dados preenchidos
        const produto = {

            nomeProduto:
                campoNomeProduto.value.trim(),

            categoria:
                campoCategoria.value.trim(),

            quantidadeUnidade:
                document.getElementById("quantidadeUnidade")
                    .value.trim(),

            unidadeMedida:
                document.getElementById("unidadeMedida").value,

            status:
                document.getElementById("status").value,

            observacao:
                document.getElementById("observacao")
                    .value.trim()
        };

        // Desativa o botão durante o cadastro
        botaoSalvar.disabled = true;

        botaoSalvar.textContent = editando
            ? "Salvando..."
            : "Cadastrando...";

        try {

            // Define a rota e o método
            const url = editando
                ? `${API}/produtos/${encodeURIComponent(produtoId)}`
                : `${API}/produtos`;

            const metodo = editando ? "PUT" : "POST";

            // Envia os dados com o token da sessão
            const resposta = await requisicaoAutenticada(url, {

                method: metodo,

                headers: {
                    "Content-Type": "application/json"
                },

                body: JSON.stringify(produto)
            });

            const dados = await lerResposta(resposta);

            alert(
                dados.mensagem ||
                (editando
                    ? "Produto atualizado com sucesso!"
                    : "Produto cadastrado com sucesso!")
            );

            if (editando) {

                window.location.href = "todos-produtos.html";

            } else {

                // Limpa os campos após o cadastro
                formulario.reset();

                // Atualiza as listas com os novos registros
                await carregarCategorias();
                await carregarNomesProdutos();
            }

        } catch (erro) {

            console.error(
                "Erro ao salvar produto:", erro
            );

            mostrarErro(erro.message);

        } finally {

            botaoSalvar.disabled = false;

            botaoSalvar.textContent = editando
                ? "Salvar alterações"
                : "Cadastrar novo produto";
        }
    });

    // INICIALIZAR TELA

    // Impede o acesso sem token
    if (!verificarSessao()) {
        return;
    }

    configurarEdicao();

    // Carrega as sugestões cadastradas na conta
    await carregarCategorias();
    await carregarNomesProdutos();

    // Busca o produto quando estiver editando
    if (editando && verificarSessao()) {
        await carregarProduto();
    }

});
