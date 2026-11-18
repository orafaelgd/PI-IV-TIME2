// Código feito por Livia Carvalho Lucizano RA: 25017514

document.addEventListener("DOMContentLoaded", () => {

    // CONFIGURAÇÕES

    const API = "http://localhost:8080/api";
    const PAGINA_LOGIN = "login-usuario.html";

    // ELEMENTOS DO FORMULÁRIO

    const formulario = document.getElementById("formCadastroLote");

    const campoProduto = document.getElementById("produto");
    const campoCodigo = document.getElementById("codigoLote");
    const campoQuantidade = document.getElementById("quantidadeRecebida");
    const campoEntrada = document.getElementById("dataEntrada");
    const campoValidade = document.getElementById("dataValidade");
    const campoValorUnitario = document.getElementById("valorUnitario");
    const campoCustoTotal = document.getElementById("custoTotalAquisicao");
    const campoObservacao = document.getElementById("observacao");

    const botaoSalvar = document.getElementById("botaoSalvar");
    const botaoCancelar = document.getElementById("botaoCancelar");
    const mensagem = document.getElementById("mensagemFormulario");

    // IDENTIFICAR EDIÇÃO

    const parametros = new URLSearchParams(window.location.search);
    const loteId = parametros.get("id");
    const editando = Boolean(loteId);

    let carregado = !editando;
    let enviando = false;

    // AUTENTICAÇÃO

    // Recupera o token do login
    function obterToken() {
        return sessionStorage.getItem("token");
    }

    // Remove os dados da sessão e retorna ao login
    function encerrarAcesso() {
        sessionStorage.removeItem("token");
        sessionStorage.removeItem("usuario");
        window.location.replace(PAGINA_LOGIN);
    }

    // Envia o token para a API
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

        // Sessão inválida ou expirada
        if (resposta.status === 401) {
            encerrarAcesso();
            throw new Error("Sessão expirada. Faça login novamente.");
        }

        if (resposta.status === 403) {
            throw new Error("Você não tem permissão para esta operação.");
        }

        return resposta;
    }

    // MENSAGENS

    function mostrarErro(texto) {
        mensagem.textContent = texto;
        mensagem.classList.remove("hidden");
    }

    function limparErro() {
        mensagem.textContent = "";
        mensagem.classList.add("hidden");
    }

    // RESPOSTAS DO BACKEND

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
                dados.mensagem || `Erro HTTP ${resposta.status}`
            );
        }

        return dados;
    }

    // CARREGAR PRODUTOS

    // Busca somente os produtos do usuário autenticado
    async function carregarProdutos() {

        campoProduto.disabled = true;
        campoProduto.replaceChildren();

        const carregando = document.createElement("option");
        carregando.value = "";
        carregando.textContent = "Carregando produtos...";
        campoProduto.appendChild(carregando);

        const resposta = await requisicaoAutenticada(`${API}/produtos`);
        const dados = await lerResposta(resposta);

        if (!Array.isArray(dados.produtos)) {
            throw new Error("Resposta de produtos inválida.");
        }

        campoProduto.replaceChildren();

        const inicial = document.createElement("option");
        inicial.value = "";
        inicial.textContent = "Selecione um produto cadastrado";
        campoProduto.appendChild(inicial);

        // Adiciona os produtos da conta na lista
        dados.produtos.forEach((produto) => {

            if (!produto.id || !produto.nome) {
                return;
            }

            // Produtos inativos não recebem lotes novos
            // Na edição, preserva a opção já vinculada
            if (!editando && produto.status === "inativo") {
                return;
            }

            const opcao = document.createElement("option");
            opcao.value = produto.id;
            opcao.textContent = produto.nome;

            campoProduto.appendChild(opcao);
        });

        campoProduto.disabled = false;
    }

    // CALCULAR CUSTO TOTAL

    // Multiplica a quantidade pelo valor unitário
    function calcularCustoTotal() {

        const quantidade = Number(campoQuantidade.value);
        const valor = Number(campoValorUnitario.value);

        if (
            !campoQuantidade.value ||
            !campoValorUnitario.value ||
            !Number.isFinite(quantidade) ||
            !Number.isFinite(valor) ||
            quantidade <= 0 ||
            valor <= 0
        ) {
            campoCustoTotal.value = "";
            return;
        }

        campoCustoTotal.value =
            (quantidade * valor).toFixed(2);
    }

    campoQuantidade.addEventListener("input", calcularCustoTotal);
    campoValorUnitario.addEventListener("input", calcularCustoTotal);

    formulario.addEventListener("reset", () => {
        campoCustoTotal.value = "";
    });

    // CONFIGURAR EDIÇÃO

    if (editando) {

        document.title =
            "Editar Lote | Sistema de Gestão e Redução de Desperdício";

        document.getElementById("caminhoPagina").textContent =
            "/ Editar Lote";

        document.getElementById("tituloFormulario").textContent =
            "Editar lote";

        botaoSalvar.textContent = "Salvar alterações";
        botaoSalvar.disabled = true;
    }

    // CARREGAR LOTE PARA EDIÇÃO

    async function carregarLote() {

        const resposta = await requisicaoAutenticada(
            `${API}/lotes/${encodeURIComponent(loteId)}`
        );

        const dados = await lerResposta(resposta);
        const lote = dados.lote || dados;

        // Preenche os dados do lote
        campoProduto.value = lote.itemId || "";

        if (!campoProduto.value) {
            throw new Error("Produto do lote não encontrado na lista.");
        }

        campoCodigo.value = lote.codigo || "";

        campoQuantidade.value =
            lote.quantidadeInicial ?? "";

        campoEntrada.value =
            String(lote.dataEntrada || "").slice(0, 10);

        campoValidade.value =
            String(lote.dataValidade || "").slice(0, 10);

        campoObservacao.value = lote.observacao || "";

        // Recupera o valor unitário do custo total
        const quantidade = Number(lote.quantidadeInicial);
        const custo = Number(lote.custoTotalAquisicao);

        if (quantidade > 0 && Number.isFinite(custo)) {
            campoValorUnitario.value =
                (custo / quantidade).toFixed(2);
        }

        calcularCustoTotal();

        // BLOQUEAR CAMPOS COM MOVIMENTAÇÕES

        // Não permite alterar produto e quantidade
        if (lote.possuiMovimentacoes === true) {

            campoProduto.disabled = true;
            campoQuantidade.readOnly = true;

            campoProduto.title =
                "Produto bloqueado: o lote possui movimentações.";

            campoQuantidade.title =
                "Quantidade bloqueada: o lote possui movimentações.";
        }

        carregado = true;
        botaoSalvar.disabled = false;
    }

    // BOTÃO CANCELAR

    botaoCancelar.addEventListener("click", () => {
        window.location.href = "todos-lotes.html";
    });

    // CADASTRAR OU ATUALIZAR LOTE

    formulario.addEventListener("submit", async (evento) => {

        evento.preventDefault();
        limparErro();

        if (enviando) return;

        if (!carregado) {
            mostrarErro("O lote ainda não foi carregado.");
            return;
        }

        if (!formulario.reportValidity()) {
            return;
        }

        // Monta os dados enviados ao backend
        const lote = {
            itemId: campoProduto.value,
            codigo: campoCodigo.value.trim(),
            quantidadeRecebida: campoQuantidade.value,
            dataEntrada: campoEntrada.value,
            dataValidade: campoValidade.value,
            valorUnitario: campoValorUnitario.value,
            observacao: campoObservacao.value.trim()
        };

        if (!lote.itemId) {
            mostrarErro("Selecione um produto.");
            return;
        }

        if (lote.dataValidade < lote.dataEntrada) {
            mostrarErro(
                "A validade não pode ser anterior à data de entrada."
            );
            return;
        }

        enviando = true;
        botaoSalvar.disabled = true;
        botaoSalvar.textContent =
            editando ? "Salvando..." : "Cadastrando...";

        try {

            const url = editando
                ? `${API}/lotes/${encodeURIComponent(loteId)}`
                : `${API}/lotes`;

            const resposta = await requisicaoAutenticada(url, {
                method: editando ? "PUT" : "POST",
                headers: {
                    "Content-Type": "application/json",
                    "Accept": "application/json"
                },
                body: JSON.stringify(lote)
            });

            const resultado = await lerResposta(resposta);

            alert(
                resultado.mensagem ||
                (editando
                    ? "Lote atualizado com sucesso!"
                    : "Lote cadastrado com sucesso!")
            );

            if (editando) {
                window.location.href = "todos-lotes.html";
            } else {
                formulario.reset();
                campoCustoTotal.value = "";
            }

        } catch (erro) {

            console.error("Erro ao salvar lote:", erro);
            mostrarErro(erro.message);

        } finally {

            enviando = false;
            botaoSalvar.disabled = false;

            botaoSalvar.textContent = editando
                ? "Salvar alterações"
                : "Cadastrar novo lote";
        }
    });

    // INICIALIZAR TELA

    async function iniciar() {

        if (!obterToken()) {
            encerrarAcesso();
            return;
        }

        try {

            await carregarProdutos();

            if (editando) {
                await carregarLote();
            }

        } catch (erro) {

            console.error("Erro ao carregar lote:", erro);
            mostrarErro(erro.message);
            botaoSalvar.disabled = true;
        }
    }

    iniciar();
});
