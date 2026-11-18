// Código feito por Livia Carvalho Lucizano RA: 25017514

document.addEventListener("DOMContentLoaded", () => {

    // CONFIGURAÇÕES

    const API = "http://localhost:8080/api";
    const PAGINA_LOGIN = "login-usuario.html";

    // ELEMENTOS DA TELA

    const tabela =
        document.getElementById("tabelaProdutos");

    const mensagemErro =
        document.getElementById("mensagemErro");

    const modal =
        document.getElementById("modalProduto");

    const modalTitulo =
        document.getElementById("modalTitulo");

    const modalTexto =
        document.getElementById("modalTexto");

    const modalConfirmar =
        document.getElementById("modalConfirmar");

    const modalCancelar =
        document.getElementById("modalCancelar");

    let acaoModal = null;

    // VERIFICAÇÃO DE SESSÃO

    function obterToken() {
        return sessionStorage.getItem("token");
    }

    function encerrarAcesso() {

        sessionStorage.removeItem("token");
        sessionStorage.removeItem("usuario");

        window.location.replace(PAGINA_LOGIN);
    }

    function verificarSessao() {

        const token = obterToken();

        if (!token || !token.trim()) {
            encerrarAcesso();
            return false;
        }

        return true;
    }

    // REQUISIÇÕES AUTENTICADAS

    async function requisicaoAutenticada(url, opcoes = {}) {

        const token = obterToken();

        if (!token || !token.trim()) {
            encerrarAcesso();
            throw new Error("Sessão não encontrada.");
        }

        const headers = new Headers(opcoes.headers || {});

        headers.set(
            "Authorization",
            `Bearer ${token}`
        );

        const resposta = await fetch(url, {
            ...opcoes,
            headers,
            cache: "no-store"
        });

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

    // ÍCONE DE EDIÇÃO

    const iconeEngrenagem = `
        <svg xmlns="http://www.w3.org/2000/svg"
            width="22" height="22"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.8"
            stroke-linecap="round"
            stroke-linejoin="round"
            aria-hidden="true">

            <path d="M12 8.5a3.5 3.5 0 1 0 0 7 3.5 3.5 0 0 0 0-7Z"/>

            <path d="M19.4 15a1.7 1.7 0 0 0 .34 1.87l.06.06-2.84 2.84-.06-.06A1.7 1.7 0 0 0 15 19.4a1.7 1.7 0 0 0-1.06 1.56V21h-4v-.04A1.7 1.7 0 0 0 8.88 19.4 1.7 1.7 0 0 0 7 19.74l-.06.06L4.1 16.96l.06-.06A1.7 1.7 0 0 0 4.5 15a1.7 1.7 0 0 0-1.56-1.06H2.9v-4h.04A1.7 1.7 0 0 0 4.5 8.88a1.7 1.7 0 0 0-.34-1.88l-.06-.06L6.94 4.1l.06.06A1.7 1.7 0 0 0 8.88 4.5a1.7 1.7 0 0 0 1.06-1.56V2.9h4v.04A1.7 1.7 0 0 0 15 4.5a1.7 1.7 0 0 0 1.87-.34l.06-.06 2.84 2.84-.06.06A1.7 1.7 0 0 0 19.4 8.88a1.7 1.7 0 0 0 1.56 1.06H21v4h-.04A1.7 1.7 0 0 0 19.4 15Z"/>
        </svg>
    `;

    // ÍCONE DE EXCLUSÃO

    const iconeLixeira = `
        <svg xmlns="http://www.w3.org/2000/svg"
            width="22" height="22"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.8"
            stroke-linecap="round"
            stroke-linejoin="round"
            aria-hidden="true">

            <path d="M3 6h18M8 6V4h8v2M5 6l1 15h12l1-15M10 10v7M14 10v7"/>
        </svg>
    `;

    // MENSAGENS DE ERRO

    function mostrarErro(texto) {

        mensagemErro.textContent = texto;
        mensagemErro.classList.remove("hidden");
    }

    // LEITURA DAS RESPOSTAS

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

    // IDENTIFICAR PRODUTO

    function obterId(produto) {

        const id =
            produto._id ??
            produto.id ??
            produto.produtoId;

        if (typeof id === "string") {
            return id;
        }

        if (id && typeof id === "object") {
            return id.$oid ?? id.oid ?? null;
        }

        return null;
    }

    // FORMATAR TEXTOS

    function valorTexto(valor, alternativa = "-") {

        if (
            valor === undefined ||
            valor === null ||
            valor === ""
        ) {
            return alternativa;
        }

        if (typeof valor === "object") {

            return valor.nome ||
                valor.$numberDecimal ||
                alternativa;
        }

        return String(valor);
    }

    // FORMATAR DATA

    function formatarData(valor) {

        if (!valor) return "-";

        if (typeof valor === "object") {
            valor = valor.$date || valor.data || null;
        }

        if (!valor) return "-";

        const texto = String(valor);

        if (/^\d{4}-\d{2}-\d{2}/.test(texto)) {

            const [ano, mes, dia] =
                texto.slice(0, 10).split("-");

            return `${dia}/${mes}/${ano}`;
        }

        return texto;
    }

    // FORMATAR PREÇO

    function formatarPreco(valor) {

        if (
            valor === null ||
            valor === undefined ||
            valor === ""
        ) {
            return "-";
        }

        if (typeof valor === "object") {
            valor = valor.$numberDecimal;
        }

        const numero = Number(valor);

        return Number.isFinite(numero)
            ? numero.toLocaleString("pt-BR", {
                style: "currency",
                currency: "BRL"
            })
            : "-";
    }

    // CRIAR CÉLULAS DA TABELA

    // PADRONIZADO COM A TABELA DE HISTÓRICO

    function criarCelula(linha, texto, classeExtra = "") {

        const celula = document.createElement("td");

        celula.className =
            `px-3 py-3 font-semibold ${classeExtra}`;

        celula.textContent = texto;

        linha.appendChild(celula);

        return celula;
    }

    // FECHAR MODAL

    function fecharModal() {

        modal.classList.add("hidden");
        modal.classList.remove("flex");

        acaoModal = null;
    }

    // ABRIR MODAL

    function abrirModal(titulo, texto, botao, acao) {

        modalTitulo.textContent = titulo;
        modalTexto.textContent = texto;
        modalConfirmar.textContent = botao;

        modalConfirmar.disabled = false;

        acaoModal = acao;

        modal.classList.remove("hidden");
        modal.classList.add("flex");

        modalCancelar.focus();
    }

    // EVENTOS DO MODAL

    modalCancelar.addEventListener(
        "click",
        fecharModal
    );

    modal.addEventListener("click", (evento) => {

        if (evento.target === modal) {
            fecharModal();
        }
    });

    document.addEventListener("keydown", (evento) => {

        if (
            evento.key === "Escape" &&
            !modal.classList.contains("hidden")
        ) {
            fecharModal();
        }
    });

    // CONFIRMAR AÇÃO

    modalConfirmar.addEventListener("click", async () => {

        if (!acaoModal) return;

        modalConfirmar.disabled = true;

        const acao = acaoModal;

        try {

            await acao();

            fecharModal();

            // Atualiza a tabela após a operação
            await carregarProdutos();

        } catch (erro) {

            fecharModal();

            mostrarErro(erro.message);

        } finally {

            modalConfirmar.disabled = false;
        }
    });

    // VERIFICAR EXCLUSÃO

    async function conferirExclusao(id) {

        mensagemErro.classList.add("hidden");

        try {

            const resposta = await requisicaoAutenticada(
                `${API}/produtos/${encodeURIComponent(id)}/exclusao`
            );

            const dados = await lerResposta(resposta);

            // PRODUTO SEM LOTES OU MOVIMENTAÇÕES

            if (dados.podeExcluir === true) {

                abrirModal(
                    "Excluir produto",

                    "Tem certeza de que deseja excluir este produto? Esta ação não poderá ser desfeita.",

                    "Excluir produto",

                    async () => {

                        const resposta = await requisicaoAutenticada(
                            `${API}/produtos/${encodeURIComponent(id)}`,
                            {
                                method: "DELETE"
                            }
                        );

                        const retorno = await lerResposta(resposta);

                        alert(
                            retorno.mensagem ||
                            "Produto excluído com sucesso!"
                        );
                    }
                );

            } else {

                // PRODUTO COM LOTES OU MOVIMENTAÇÕES

                abrirModal(
                    "Não é possível excluir o produto",

                    dados.mensagem ||
                    "Este produto possui lotes ou movimentações registradas. Deseja torná-lo inativo?",

                    "Tornar inativo",

                    async () => {

                        const resposta = await requisicaoAutenticada(
                            `${API}/produtos/${encodeURIComponent(id)}/status`,
                            {
                                method: "PATCH",

                                headers: {
                                    "Content-Type": "application/json"
                                },

                                body: JSON.stringify({
                                    status: "inativo"
                                })
                            }
                        );

                        const retorno = await lerResposta(resposta);

                        alert(
                            retorno.mensagem ||
                            "Produto inativado com sucesso!"
                        );
                    }
                );
            }

        } catch (erro) {

            mostrarErro(
                `Não foi possível verificar a exclusão: ${erro.message}`
            );
        }
    }

    // CRIAR BOTÕES

    function criarBotao(acao, titulo, icone) {

        const botao = document.createElement("button");

        botao.type = "button";
        botao.title = titulo;

        botao.setAttribute("aria-label", titulo);

        botao.className =
            "flex h-[28px] w-[28px] cursor-pointer items-center justify-center rounded text-primary-dark hover:bg-primary-light";

        botao.innerHTML = icone;

        botao.addEventListener("click", acao);

        return botao;
    }

    // CARREGAR PRODUTOS

    async function carregarProdutos() {

        mensagemErro.classList.add("hidden");

        tabela.replaceChildren();

        const carregando = document.createElement("tr");

        criarCelula(
            carregando,
            "Carregando produtos..."
        ).colSpan = 8;

        tabela.appendChild(carregando);

        try {

            // CONSULTAR PRODUTOS DO USUÁRIO AUTENTICADO

            const resposta = await requisicaoAutenticada(
                `${API}/produtos`
            );

            const dados = await lerResposta(resposta);

            const produtos = Array.isArray(dados)
                ? dados
                : dados.produtos || dados.itens || [];

            if (!Array.isArray(produtos)) {

                throw new Error(
                    "Formato inválido da lista de produtos."
                );
            }

            tabela.replaceChildren();

            // NENHUM PRODUTO CADASTRADO

            if (produtos.length === 0) {

                const linha = document.createElement("tr");

                const celula = criarCelula(
                    linha,
                    "Nenhum produto cadastrado.",
                    "text-center text-text-secondary"
                );

                celula.colSpan = 8;

                tabela.appendChild(linha);

                return;
            }

            // MONTAR TABELA

            produtos.forEach((produto) => {

                const id = obterId(produto);

                const linha = document.createElement("tr");

                // FUNDO IGUAL AO HISTÓRICO
                linha.className =
                    "border-b border-gray-300 bg-[#e9ebee] text-text";

                // NOME DO PRODUTO

                criarCelula(
                    linha,
                    valorTexto(
                        produto.nomeProduto ??
                        produto.nome ??
                        produto.titulo
                    )
                );

                // CATEGORIA

                criarCelula(
                    linha,
                    valorTexto(produto.categoria)
                );

                // UNIDADE DE MEDIDA

                const unidade = `
                    ${valorTexto(produto.quantidadeUnidade, "")}
                    ${valorTexto(produto.unidadeMedida, "")}
                `.trim();

                criarCelula(
                    linha,
                    unidade || "-"
                );

                // QUANTIDADE TOTAL

                criarCelula(
                    linha,
                    `${valorTexto(
                        produto.quantidadeTotal ??
                        produto.quantidadeDisponivel ??
                        0,
                        "0"
                    )} un.`
                );

                // PREÇO UNITÁRIO

                criarCelula(
                    linha,
                    formatarPreco(
                        produto.precoUnitario ??
                        produto.valorUnitario
                    )
                );

                // DATA DE VALIDADE

                criarCelula(
                    linha,
                    formatarData(
                        produto.validade ??
                        produto.dataValidade
                    )
                );

                // STATUS DO PRODUTO

                const status = valorTexto(
                    produto.status,
                    "ativo"
                );

                criarCelula(
                    linha,
                    status.charAt(0).toUpperCase() +
                    status.slice(1)
                );

                // BOTÕES DE AÇÃO

                const acoes = document.createElement("td");

                acoes.className = "px-3 py-3";

                const grupo = document.createElement("div");

                grupo.className =
                    "flex items-center justify-center gap-2";

                // BOTÃO EDITAR

                const editar = criarBotao(
                    () => {

                        window.location.href =
                            `cadastro-produtos.html?id=${encodeURIComponent(id)}`;
                    },

                    "Editar produto",

                    iconeEngrenagem
                );

                // BOTÃO EXCLUIR

                const excluir = criarBotao(
                    () => conferirExclusao(id),

                    "Excluir produto",

                    iconeLixeira
                );

                // DESATIVAR BOTÕES SEM ID

                if (!id) {

                    editar.disabled = true;
                    excluir.disabled = true;

                    editar.title =
                        "ID do produto não encontrado";

                    excluir.title =
                        "ID do produto não encontrado";
                }

                grupo.append(editar, excluir);

                acoes.appendChild(grupo);

                linha.appendChild(acoes);

                tabela.appendChild(linha);
            });

        } catch (erro) {

            tabela.replaceChildren();

            mostrarErro(
                `Erro ao carregar produtos: ${erro.message}`
            );
        }
    }

    // INICIALIZAR TELA

    if (!verificarSessao()) {
        return;
    }

    carregarProdutos();

});
