
// REGISTRAR SAÍDA
// AUTENTICAÇÃO, VALIDAÇÕES E INTEGRAÇÃO COM MONGODB

document.addEventListener("DOMContentLoaded", () => {

    // CONFIGURAÇÕES

    const API = "http://localhost:8080/api";
    const PAGINA_LOGIN = "login-usuario.html";

    const MOTIVOS_VALIDOS = [
        "consumido",
        "vencimento",
        "contaminado",
        "danificado",
        "outro"
    ];

    // ELEMENTOS DO FORMULÁRIO

    const formulario = document.getElementById(
        "form-registrar-saida"
    );

    if (!formulario) {
        console.error("Formulário de saída não encontrado.");
        return;
    }

    const produtoSelect =
        document.getElementById("produto");

    const loteSelect =
        document.getElementById("lote");

    const quantidadeDisponivel =
        document.getElementById("quantidade-disponivel");

    const quantidadeSaida =
        document.getElementById("quantidade-saida");

    const dataSaida =
        document.getElementById("data-saida");

    const motivo =
        document.getElementById("motivo");

    const observacao =
        document.getElementById("observacao");

    const botaoEnviar =
        document.getElementById("botaoRegistrar");

    const botaoCancelar =
        document.getElementById("botaoCancelar");

    const mensagem =
        document.getElementById("mensagemFormulario");

    // DADOS CARREGADOS

    let produtos = [];
    let lotes = [];

    // CONTROLE DO FORMULÁRIO

    let carregandoDados = false;
    let enviando = false;

    // PROTEÇÃO CONTRA REGISTRO DUPLICADO

    let operacaoPendente = null;

    // VERIFICAR ELEMENTOS OBRIGATÓRIOS

    if (
        !produtoSelect ||
        !loteSelect ||
        !quantidadeDisponivel ||
        !quantidadeSaida ||
        !dataSaida ||
        !motivo ||
        !observacao ||
        !botaoEnviar ||
        !botaoCancelar ||
        !mensagem
    ) {

        console.error(
            "Campos do formulário de saída não encontrados."
        );

        return;
    }

    // AUTENTICAÇÃO

    function obterToken() {

        return sessionStorage.getItem("token");
    }

    // ENCERRAR SESSÃO

    function encerrarAcesso() {

        sessionStorage.removeItem("token");
        sessionStorage.removeItem("usuario");

        window.location.replace(PAGINA_LOGIN);
    }

    // VERIFICAR SESSÃO

    function verificarSessao() {

        const token = obterToken();

        if (!token || !token.trim()) {

            encerrarAcesso();
            return false;
        }

        return true;
    }

    // REQUISIÇÕES AUTENTICADAS

    async function requisicaoAutenticada(
        url,
        opcoes = {}
    ) {

        const token = obterToken();

        if (!token || !token.trim()) {

            encerrarAcesso();

            throw new Error(
                "Sessão não encontrada."
            );
        }

        // ENVIAR TOKEN PARA O SESSAOSERVICE

        const headers = new Headers(
            opcoes.headers || {}
        );

        headers.set(
            "Authorization",
            `Bearer ${token}`
        );

        const resposta = await fetch(url, {

            ...opcoes,

            headers,

            cache: "no-store"
        });

        // SESSÃO INVÁLIDA OU EXPIRADA

        if (resposta.status === 401) {

            encerrarAcesso();

            throw new Error(
                "Sua sessão expirou. Faça login novamente."
            );
        }

        // ACESSO NEGADO

        if (resposta.status === 403) {

            throw new Error(
                "Você não tem permissão para realizar esta operação."
            );
        }

        return resposta;
    }

    // LER RESPOSTA DO BACKEND

    async function lerResposta(resposta) {

        const texto = await resposta.text();

        let dados = {};

        if (texto) {

            try {

                dados = JSON.parse(texto);

            } catch {

                dados = {
                    mensagem: texto
                };
            }
        }

        if (!resposta.ok) {

            const erro = new Error(
                dados.mensagem ||
                dados.erro ||
                `Erro HTTP ${resposta.status}`
            );

            erro.status = resposta.status;

            throw erro;
        }

        return dados;
    }

    // MENSAGENS DO FORMULÁRIO

    function mostrarErro(texto) {

        mensagem.textContent = texto;

        mensagem.classList.remove("hidden");
    }

    function limparErro() {

        mensagem.textContent = "";

        mensagem.classList.add("hidden");
    }

    // DATA ATUAL EM SÃO PAULO

    function dataAtual() {

        const partes = new Intl.DateTimeFormat(
            "en-US",
            {
                timeZone: "America/Sao_Paulo",
                year: "numeric",
                month: "2-digit",
                day: "2-digit"
            }
        ).formatToParts(new Date());

        const valores = Object.fromEntries(
            partes.map((parte) => [
                parte.type,
                parte.value
            ])
        );

        return (
            `${valores.year}-` +
            `${valores.month}-` +
            `${valores.day}`
        );
    }

    // CONFIGURAR DATA

    function configurarData() {

        const hoje = dataAtual();

        dataSaida.max = hoje;
        dataSaida.value = hoje;
    }

    // CONVERTER DECIMAL128 DO MONGODB

    function converterNumero(valor) {

        if (
            valor &&
            typeof valor === "object" &&
            "$numberDecimal" in valor
        ) {

            valor = valor.$numberDecimal;
        }

        return Number(valor);
    }

    // OBTER IDENTIFICADOR

    function obterId(registro) {

        if (!registro) {
            return "";
        }

        const id =
            registro.id ??
            registro._id ??
            registro.produtoId ??
            registro.itemId ??
            "";

        if (typeof id === "string") {
            return id;
        }

        if (id && typeof id === "object") {

            return id.$oid ??
                id.oid ??
                "";
        }

        return "";
    }

    // OBTER NOME DO PRODUTO

    function obterNomeProduto(produto) {

        return String(
            produto.nomeProduto ??
            produto.nome ??
            produto.titulo ??
            ""
        ).trim();
    }

    // NORMALIZAR TEXTO

    function normalizarTexto(texto) {

        return String(texto ?? "")
            .normalize("NFD")
            .replace(/[\u0300-\u036f]/g, "")
            .trim()
            .toLowerCase();
    }

    // CRIAR OPÇÃO

    function criarOpcao(valor, texto) {

        const opcao =
            document.createElement("option");

        opcao.value = valor;
        opcao.textContent = texto;

        return opcao;
    }

    // OBTER PRODUTO SELECIONADO

    function produtoSelecionado() {

        return produtos.find(
            (produto) =>
                produto.id === produtoSelect.value
        );
    }

    // OBTER LOTE SELECIONADO

    function loteSelecionado() {

        return lotes.find(
            (lote) =>
                lote.id === loteSelect.value
        );
    }

    // VERIFICAR SE O LOTE PERTENCE AO PRODUTO

    function mesmoProduto(lote, produto) {

        if (!lote || !produto) {
            return false;
        }

        // PRIORIZAR RELACIONAMENTO POR ID

        if (lote.itemId) {

            return lote.itemId === produto.id;
        }

        // COMPATIBILIDADE COM A API DE LOTES
        // QUE RETORNA SOMENTE O NOME DO PRODUTO

        return normalizarTexto(lote.produto) ===
            normalizarTexto(produto.nome);
    }

    // VERIFICAR DISPONIBILIDADE DO LOTE

    function loteDisponivel(lote) {

        if (!lote) {
            return false;
        }

        // IGNORAR LOTES INATIVOS OU ARQUIVADOS

        if (
            lote.inativo === true ||
            lote.arquivado === true
        ) {
            return false;
        }

        const saldo = converterNumero(
            lote.quantidadeDisponivel
        );

        return (
            Number.isFinite(saldo) &&
            saldo > 0
        );
    }

    // LISTAR LOTES DISPONÍVEIS DO PRODUTO

    function lotesDoProduto(produto) {

        if (!produto) {
            return [];
        }

        return lotes.filter(
            (lote) =>
                mesmoProduto(lote, produto) &&
                loteDisponivel(lote)
        );
    }

    // FORMATAR QUANTIDADE

    function formatarQuantidade(valor) {

        const numero = converterNumero(valor);

        if (!Number.isFinite(numero)) {
            return "-";
        }

        return numero.toLocaleString("pt-BR", {
            maximumFractionDigits: 6
        });
    }

    // LIMPAR LOTE

    function limparLotes() {

        loteSelect.replaceChildren(
            criarOpcao(
                "",
                "Selecione um produto primeiro"
            )
        );

        loteSelect.value = "";

        quantidadeDisponivel.value = "";
        quantidadeSaida.value = "";

        loteSelect.disabled = true;
    }

    // ATUALIZAR BLOQUEIO DOS CAMPOS

    function atualizarBloqueio() {

        const bloquear =
            carregandoDados ||
            enviando ||
            operacaoPendente !== null;

        // CAMPOS DE EDIÇÃO

        const campos = [
            produtoSelect,
            loteSelect,
            quantidadeSaida,
            dataSaida,
            motivo,
            observacao
        ];

        campos.forEach((campo) => {

            campo.disabled = bloquear;
        });

        // SALDO SEMPRE SOMENTE LEITURA

        quantidadeDisponivel.readOnly = true;

        // HABILITAR SOMENTE PRODUTOS DISPONÍVEIS

        if (!bloquear) {

            produtoSelect.disabled =
                produtos.length === 0;

            const produto =
                produtoSelecionado();

            loteSelect.disabled =
                lotesDoProduto(produto).length === 0;
        }

        // O BOTÃO PERMITE REPETIR A MESMA OPERAÇÃO
        // EM CASO DE RESULTADO NÃO CONFIRMADO

        botaoEnviar.disabled =
            carregandoDados ||
            enviando ||
            (
                operacaoPendente === null &&
                produtos.length === 0
            );

        botaoCancelar.disabled = bloquear;

        botaoEnviar.textContent = enviando
            ? "Registrando..."
            : operacaoPendente
                ? "Tentar novamente"
                : "Registrar saída";
    }

    // CARREGAR PRODUTOS E LOTES

    async function carregarDados() {

        carregandoDados = true;

        atualizarBloqueio();
        limparErro();

        try {

            // CONSULTAR SOMENTE COM TOKEN

            const [
                respostaProdutos,
                respostaLotes
            ] = await Promise.all([

                requisicaoAutenticada(
                    `${API}/produtos`
                ),

                requisicaoAutenticada(
                    `${API}/lotes`
                )
            ]);

            // LER RESPOSTAS

            const dadosProdutos =
                await lerResposta(respostaProdutos);

            const dadosLotes =
                await lerResposta(respostaLotes);

            // VALIDAR FORMATO

            const listaProdutos =
                Array.isArray(dadosProdutos)
                    ? dadosProdutos
                    : dadosProdutos.produtos ??
                      dadosProdutos.itens;

            const listaLotes =
                dadosLotes.lotes;

            if (
                !Array.isArray(listaProdutos) ||
                !Array.isArray(listaLotes)
            ) {

                throw new Error(
                    "Formato inválido dos dados retornados."
                );
            }

            // MANTER SOMENTE PRODUTOS ATIVOS

            produtos = listaProdutos
                .filter((produto) => {

                    return (
                        normalizarTexto(
                            produto.status ?? "ativo"
                        ) === "ativo"
                    );
                })
                .map((produto) => ({

                    ...produto,

                    id: obterId(produto),

                    nome: obterNomeProduto(produto)
                }))
                .filter((produto) => {

                    return (
                        produto.id &&
                        produto.nome
                    );
                });

            // NORMALIZAR LOTES

            lotes = listaLotes.map((lote) => ({

                ...lote,

                id: obterId(lote),

                itemId:
                    typeof lote.itemId === "object"
                        ? (
                            lote.itemId?.$oid ??
                            lote.itemId?.oid ??
                            ""
                        )
                        : (
                            lote.itemId ?? ""
                        )
            }));

            // PREENCHER SELECT DE PRODUTOS

            produtoSelect.replaceChildren(
                criarOpcao(
                    "",
                    "Selecione um produto cadastrado"
                )
            );

            produtos.forEach((produto) => {

                const lotesDisponiveis =
                    lotesDoProduto(produto);

                // EXIBIR SOMENTE PRODUTOS COM LOTES
                // QUE TENHAM QUANTIDADE DISPONÍVEL

                if (lotesDisponiveis.length > 0) {

                    produtoSelect.appendChild(
                        criarOpcao(
                            produto.id,
                            produto.nome
                        )
                    );
                }
            });

            // ATUALIZAR LISTA DE PRODUTOS
            // EFETIVAMENTE DISPONÍVEIS

            produtos = produtos.filter(
                (produto) =>
                    lotesDoProduto(produto).length > 0
            );

            limparLotes();

            // INFORMAR LISTA VAZIA

            if (produtos.length === 0) {

                mostrarErro(
                    "Nenhum produto com lote disponível foi encontrado."
                );
            }

        } catch (erro) {

            console.error(
                "Erro ao carregar produtos e lotes:",
                erro
            );

            produtos = [];
            lotes = [];

            produtoSelect.replaceChildren(
                criarOpcao(
                    "",
                    "Não foi possível carregar produtos"
                )
            );

            limparLotes();

            mostrarErro(
                `Não foi possível carregar os dados: ${erro.message}`
            );

        } finally {

            carregandoDados = false;

            atualizarBloqueio();
        }
    }

    // QUANDO O PRODUTO É ALTERADO

    produtoSelect.addEventListener("change", () => {

        if (operacaoPendente || enviando) {
            return;
        }

        limparErro();
        limparLotes();

        const produto =
            produtoSelecionado();

        if (!produto) {

            atualizarBloqueio();
            return;
        }

        // CARREGAR LOTES DO PRODUTO

        const disponiveis =
            lotesDoProduto(produto);

        loteSelect.replaceChildren(
            criarOpcao(
                "",
                "Selecione um lote disponível"
            )
        );

        disponiveis.forEach((lote) => {

            const codigo =
                lote.codigo || lote.id;

            const saldo = formatarQuantidade(
                lote.quantidadeDisponivel
            );

            const texto =
                `${codigo} — Saldo: ${saldo} ` +
                `${produto.unidadeMedida || ""}`;

            loteSelect.appendChild(
                criarOpcao(
                    lote.id,
                    texto.trim()
                )
            );
        });

        if (disponiveis.length === 0) {

            loteSelect.replaceChildren(
                criarOpcao(
                    "",
                    "Nenhum lote disponível"
                )
            );
        }

        atualizarBloqueio();
    });

    // QUANDO O LOTE É ALTERADO

    loteSelect.addEventListener("change", () => {

        if (operacaoPendente || enviando) {
            return;
        }

        limparErro();

        const lote = loteSelecionado();

        // PREENCHER SALDO AUTOMATICAMENTE

        quantidadeDisponivel.value = lote
            ? formatarQuantidade(
                lote.quantidadeDisponivel
            )
            : "";

        quantidadeSaida.value = "";
    });

    // CANCELAR FORMULÁRIO

    botaoCancelar.addEventListener("click", () => {

        if (enviando || operacaoPendente) {
            return;
        }

        formulario.reset();

        limparErro();
        limparLotes();
        configurarData();

        atualizarBloqueio();
    });

    // INVALIDAR QUANTIDADES INCORRETAS

    quantidadeSaida.addEventListener("input", () => {

        limparErro();

        const lote = loteSelecionado();

        if (!lote) {
            return;
        }

        const texto = quantidadeSaida.value
            .trim()
            .replace(",", ".");

        if (!texto) {
            return;
        }

        const quantidade = Number(texto);

        const saldo = converterNumero(
            lote.quantidadeDisponivel
        );

        if (
            !Number.isFinite(quantidade) ||
            quantidade <= 0
        ) {

            mostrarErro(
                "Informe uma quantidade maior que zero."
            );

            return;
        }

        if (quantidade > saldo) {

            mostrarErro(
                "A quantidade de saída não pode superar o saldo disponível."
            );
        }
    });

    // VALIDAR E PREPARAR OPERAÇÃO

    function prepararOperacao() {

        const produto =
            produtoSelecionado();

        const lote =
            loteSelecionado();

        // VALIDAR PRODUTO E LOTE

        if (!produto || !lote) {

            throw new Error(
                "Selecione um produto e um lote válido."
            );
        }

        if (!mesmoProduto(lote, produto)) {

            throw new Error(
                "O lote não pertence ao produto selecionado."
            );
        }

        if (!loteDisponivel(lote)) {

            throw new Error(
                "O lote selecionado não está disponível."
            );
        }

        // VALIDAR QUANTIDADE

        const quantidadeTexto =
            quantidadeSaida.value
                .trim()
                .replace(",", ".");

        const quantidade = Number(
            quantidadeTexto
        );

        const saldo = converterNumero(
            lote.quantidadeDisponivel
        );

        if (
            !quantidadeTexto ||
            !Number.isFinite(quantidade) ||
            quantidade <= 0
        ) {

            throw new Error(
                "Informe uma quantidade de saída válida."
            );
        }

        if (
            !Number.isFinite(saldo) ||
            quantidade > saldo
        ) {

            throw new Error(
                "A quantidade de saída não pode superar o saldo disponível."
            );
        }

        // VALIDAR DATA

        if (!dataSaida.value) {

            throw new Error(
                "Informe a data da saída."
            );
        }

        if (dataSaida.value > dataAtual()) {

            throw new Error(
                "A data de saída não pode ser futura."
            );
        }

        // VALIDAR MOTIVO

        if (
            !MOTIVOS_VALIDOS.includes(
                motivo.value
            )
        ) {

            throw new Error(
                "Selecione um motivo válido."
            );
        }

        // VALIDAR OBSERVAÇÃO

        const textoObservacao =
            observacao.value.trim();

        if (
            !textoObservacao ||
            textoObservacao.length > 1000
        ) {

            throw new Error(
                "A observação deve ter entre 1 e 1000 caracteres."
            );
        }

        // GERAR CHAVE ÚNICA DA OPERAÇÃO

        if (!globalThis.crypto?.randomUUID) {

            throw new Error(
                "O navegador não conseguiu gerar o identificador da operação."
            );
        }

        // PREPARAR DADOS PARA O BACKEND

        return {

            itemId: produto.id,

            loteId: lote.id,

            quantidade: quantidadeTexto,

            dataSaida: dataSaida.value,

            motivo: motivo.value,

            observacao: textoObservacao,

            idempotencyKey:
                crypto.randomUUID()
        };
    }

    // REGISTRAR SAÍDA

    formulario.addEventListener(
        "submit",
        async (evento) => {

            evento.preventDefault();

            // IMPEDIR ENVIO DUPLO SIMULTÂNEO

            if (enviando || carregandoDados) {
                return;
            }

            limparErro();

            // VERIFICAR SESSÃO ANTES DO ENVIO

            if (!verificarSessao()) {
                return;
            }

            // NOVA OPERAÇÃO

            if (!operacaoPendente) {

                try {

                    // VALIDAR TODOS OS CAMPOS

                    if (!formulario.reportValidity()) {
                        return;
                    }

                    const dados = prepararOperacao();

                    operacaoPendente = {
                        dados
                    };

                } catch (erro) {

                    mostrarErro(erro.message);

                    return;
                }
            }

            enviando = true;

            atualizarBloqueio();

            try {

                // REQUISIÇÃO AUTENTICADA AO CONTROLLER

                const resposta =
                    await requisicaoAutenticada(
                        `${API}/movimentacoes`,
                        {
                            method: "POST",

                            headers: {
                                "Content-Type": "application/json"
                            },

                            body: JSON.stringify(
                                operacaoPendente.dados
                            )
                        }
                    );

                const resultado =
                    await lerResposta(resposta);

                // OPERAÇÃO CONFIRMADA

                operacaoPendente = null;

                // LIMPAR FORMULÁRIO

                formulario.reset();

                limparLotes();
                configurarData();

                alert(
                    resultado.mensagem ||
                    "Saída registrada com sucesso!"
                );

            } catch (erro) {

                console.error(
                    "Erro ao registrar saída:",
                    erro
                );

                // ERROS 4XX NÃO REPRESENTAM
                // UMA OPERAÇÃO PENDENTE INCERTA
                // EXCETO ERROS DE AUTENTICAÇÃO,
                // QUE REDIRECIONAM AO LOGIN

                if (
                    Number.isInteger(erro.status) &&
                    erro.status >= 400 &&
                    erro.status < 500
                ) {

                    operacaoPendente = null;

                    mostrarErro(
                        erro.message
                    );

                } else {

                    // CONEXÃO OU RESULTADO NÃO CONFIRMADO
                    // PRESERVAR DADOS E A MESMA CHAVE
                    // PARA EVITAR BAIXA DUPLICADA

                    mostrarErro(
                        "Não foi possível confirmar a operação. " +
                        "Os dados foram preservados. " +
                        "Clique em Tentar novamente para " +
                        "reenviar a mesma operação. " +
                        erro.message
                    );
                }

            } finally {

                enviando = false;

                atualizarBloqueio();
            }

            // ATUALIZAR SALDO DEPOIS DA CONFIRMAÇÃO

            if (!operacaoPendente) {

                await carregarDados();
            }
        }
    );

    // INICIALIZAR TELA

    if (!verificarSessao()) {
        return;
    }

    configurarData();

    carregarDados();
});
