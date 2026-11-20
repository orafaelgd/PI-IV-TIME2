
document.addEventListener("DOMContentLoaded", () => {

    // =====================================================
    // CONFIGURAÇÕES E ELEMENTOS DO FORMULÁRIO
    // Define a API utilizada, os motivos permitidos
    // e os campos necessários para registrar uma saída.
    // =====================================================

    const API = "http://localhost:8080/api";
    const PAGINA_LOGIN = "login-usuario.html";

    const MOTIVOS_VALIDOS = [
        "consumido",
        "vencimento",
        "contaminado",
        "danificado",
        "outro"
    ];

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

    // Armazena os dados recebidos da API e controla
    // os estados de carregamento e envio do formulário.

    let produtos = [];
    let lotes = [];

    let carregandoDados = false;
    let enviando = false;

    // Guarda os dados de uma operação cujo resultado
    // ainda não foi confirmado, evitando baixas duplicadas.

    let operacaoPendente = null;

    // Confirma a existência dos campos obrigatórios.

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

    // =====================================================
    // AUTENTICAÇÃO E CONTROLE DE SESSÃO
    // Recupera o token do usuário e o utiliza nas
    // requisições. Caso a sessão seja inválida ou
    // expirada, limpa os dados locais e redireciona
    // o usuário para a página de login.
    // =====================================================

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

    // =====================================================
    // COMUNICAÇÃO AUTENTICADA COM O BACKEND
    // Adiciona o token Bearer às requisições para
    // permitir que o SessaoService identifique o usuário.
    // Também identifica respostas de acesso negado
    // e sessões expiradas.
    // =====================================================

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

    // =====================================================
    // LEITURA DAS RESPOSTAS DA API
    // Interpreta o conteúdo retornado pelo backend
    // e transforma respostas com erro em exceções
    // que podem ser tratadas pelo formulário.
    // =====================================================

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

    // =====================================================
    // EXIBIÇÃO DAS MENSAGENS DO FORMULÁRIO
    // Apresenta avisos de validação e erros de
    // comunicação com o backend ao usuário.
    // =====================================================

    function mostrarErro(texto) {
        mensagem.textContent = texto;
        mensagem.classList.remove("hidden");
    }

    function limparErro() {
        mensagem.textContent = "";
        mensagem.classList.add("hidden");
    }

    // =====================================================
    // CONFIGURAÇÃO DA DATA DE SAÍDA
    // Obtém a data atual considerando o fuso horário
    // de São Paulo, define o valor inicial do campo
    // e impede a seleção de datas futuras.
    // =====================================================

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

    function configurarData() {
        const hoje = dataAtual();

        dataSaida.max = hoje;
        dataSaida.value = hoje;
    }

    // =====================================================
    // TRATAMENTO DOS DADOS RECEBIDOS DO MONGODB
    // Converte valores Decimal128, recupera diferentes
    // formatos de identificadores e padroniza nomes
    // utilizados nas respostas de produtos e lotes.
    // =====================================================

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

    function obterNomeProduto(produto) {
        return String(
            produto.nomeProduto ??
            produto.nome ??
            produto.titulo ??
            ""
        ).trim();
    }

    function normalizarTexto(texto) {
        return String(texto ?? "")
            .normalize("NFD")
            .replace(/[\u0300-\u036f]/g, "")
            .trim()
            .toLowerCase();
    }

    // =====================================================
    // SELEÇÃO DE PRODUTOS E LOTES
    // Identifica os registros escolhidos pelo usuário
    // e verifica se o lote pertence ao produto,
    // está disponível e possui saldo positivo.
    // =====================================================

    function criarOpcao(valor, texto) {
        const opcao =
            document.createElement("option");

        opcao.value = valor;
        opcao.textContent = texto;

        return opcao;
    }

    function produtoSelecionado() {
        return produtos.find(
            (produto) =>
                produto.id === produtoSelect.value
        );
    }

    function loteSelecionado() {
        return lotes.find(
            (lote) =>
                lote.id === loteSelect.value
        );
    }

    function mesmoProduto(lote, produto) {
        if (!lote || !produto) {
            return false;
        }

        // Prioriza o relacionamento pelo identificador.
        // Quando necessário, utiliza o nome do produto
        // para compatibilidade com a resposta da API.

        if (lote.itemId) {
            return lote.itemId === produto.id;
        }

        return normalizarTexto(lote.produto) ===
            normalizarTexto(produto.nome);
    }

    function loteDisponivel(lote) {
        if (!lote) {
            return false;
        }

        // Lotes inativos, arquivados ou sem saldo
        // não podem ser selecionados para uma saída.

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

    function formatarQuantidade(valor) {
        const numero = converterNumero(valor);

        if (!Number.isFinite(numero)) {
            return "-";
        }

        return numero.toLocaleString("pt-BR", {
            maximumFractionDigits: 6
        });
    }

    // =====================================================
    // CONTROLE DOS CAMPOS DO FORMULÁRIO
    // Limpa as informações dos lotes e bloqueia
    // alterações enquanto os dados estão carregando,
    // uma saída está sendo enviada ou existe uma
    // operação pendente de confirmação.
    // =====================================================

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

    function atualizarBloqueio() {
        const bloquear =
            carregandoDados ||
            enviando ||
            operacaoPendente !== null;

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

        // O saldo disponível é apenas informativo
        // e não pode ser editado diretamente.

        quantidadeDisponivel.readOnly = true;

        if (!bloquear) {
            produtoSelect.disabled =
                produtos.length === 0;

            const produto =
                produtoSelecionado();

            loteSelect.disabled =
                lotesDoProduto(produto).length === 0;
        }

        // Quando existe uma operação pendente,
        // o botão permite reenviar os mesmos dados
        // mantendo a chave de idempotência original.

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

    // =====================================================
    // CARREGAMENTO DE PRODUTOS E LOTES
    // Consulta os dados da conta autenticada na API,
    // identifica produtos ativos, normaliza os registros
    // e disponibiliza apenas produtos que possuem
    // lotes válidos com quantidade em estoque.
    // =====================================================

    async function carregarDados() {
        carregandoDados = true;

        atualizarBloqueio();
        limparErro();

        try {
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

            // Converte as respostas e verifica se
            // produtos e lotes foram retornados como listas.

            const dadosProdutos =
                await lerResposta(respostaProdutos);

            const dadosLotes =
                await lerResposta(respostaLotes);

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

            // Mantém apenas produtos ativos e padroniza
            // seus identificadores e nomes.

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

            // Padroniza os identificadores dos lotes
            // para permitir a associação com os produtos.

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

            // Preenche o seletor apenas com produtos
            // que possuem pelo menos um lote disponível.

            produtoSelect.replaceChildren(
                criarOpcao(
                    "",
                    "Selecione um produto cadastrado"
                )
            );

            produtos.forEach((produto) => {
                const lotesDisponiveis =
                    lotesDoProduto(produto);

                if (lotesDisponiveis.length > 0) {
                    produtoSelect.appendChild(
                        criarOpcao(
                            produto.id,
                            produto.nome
                        )
                    );
                }
            });

            produtos = produtos.filter(
                (produto) =>
                    lotesDoProduto(produto).length > 0
            );

            limparLotes();

            if (produtos.length === 0) {
                mostrarErro(
                    "Nenhum produto com lote disponível foi encontrado."
                );
            }

        } catch (erro) {

            // Em caso de falha, limpa as listas e
            // informa ao usuário que os dados não
            // puderam ser carregados.

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

    // =====================================================
    // ATUALIZAÇÃO DOS LOTES CONFORME O PRODUTO
    // Quando um produto é selecionado, apresenta
    // somente seus lotes disponíveis, mostrando
    // o código e a quantidade existente em estoque.
    // =====================================================

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

    // =====================================================
    // ATUALIZAÇÃO DO SALDO DO LOTE
    // Ao selecionar um lote, apresenta automaticamente
    // a quantidade disponível e limpa a quantidade
    // anteriormente informada para a saída.
    // =====================================================

    loteSelect.addEventListener("change", () => {
        if (operacaoPendente || enviando) {
            return;
        }

        limparErro();

        const lote = loteSelecionado();

        quantidadeDisponivel.value = lote
            ? formatarQuantidade(
                lote.quantidadeDisponivel
            )
            : "";

        quantidadeSaida.value = "";
    });

    // =====================================================
    // CANCELAMENTO E LIMPEZA DO FORMULÁRIO
    // Restaura os campos, limpa mensagens e redefine
    // a data inicial, desde que não exista uma operação
    // em andamento ou pendente de confirmação.
    // =====================================================

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

    // =====================================================
    // VALIDAÇÃO IMEDIATA DA QUANTIDADE
    // Verifica durante a digitação se a quantidade
    // informada é positiva e não ultrapassa o saldo
    // disponível no lote selecionado.
    // =====================================================

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

    // =====================================================
    // VALIDAÇÃO E PREPARAÇÃO DA MOVIMENTAÇÃO
    // Confere todas as informações antes de registrar
    // uma saída: produto, lote, saldo, quantidade,
    // data, motivo e observação.
    // Também gera uma chave única para identificar
    // a operação e impedir registros duplicados.
    // =====================================================

    function prepararOperacao() {
        const produto =
            produtoSelecionado();

        const lote =
            loteSelecionado();

        // Confirma a seleção de um produto e de um lote
        // pertencente a ele e disponível para utilização.

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

        // Valida se a quantidade é positiva e
        // não supera o saldo disponível.

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

        // Impede datas futuras e motivos diferentes
        // dos aceitos pelo sistema.

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

        if (
            !MOTIVOS_VALIDOS.includes(
                motivo.value
            )
        ) {
            throw new Error(
                "Selecione um motivo válido."
            );
        }

        // Exige uma observação preenchida com
        // no máximo 1000 caracteres.

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

        // Confirma que o navegador pode gerar
        // o identificador único da movimentação.

        if (!globalThis.crypto?.randomUUID) {
            throw new Error(
                "O navegador não conseguiu gerar o identificador da operação."
            );
        }

        // Organiza os dados que serão enviados
        // ao backend para registrar a saída.

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

    // =====================================================
    // ENVIO DO REGISTRO DE SAÍDA
    // Valida os campos e envia os dados ao Controller
    // por uma requisição POST autenticada.
    // Controla o estado do formulário e mantém os
    // dados de operações ainda não confirmadas.
    // =====================================================

    formulario.addEventListener(
        "submit",
        async (evento) => {
            evento.preventDefault();

            // Impede envios simultâneos enquanto
            // outra operação estiver em andamento.

            if (enviando || carregandoDados) {
                return;
            }

            limparErro();

            if (!verificarSessao()) {
                return;
            }

            // Prepara uma nova operação apenas quando
            // não existe outra pendente de confirmação.

            if (!operacaoPendente) {
                try {
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

                // Envia os dados da movimentação junto
                // ao token do usuário autenticado.

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

                // A confirmação do backend encerra
                // a operação pendente e permite
                // limpar os campos do formulário.

                operacaoPendente = null;

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

                // Erros HTTP 4xx são tratados como
                // operações rejeitadas pelo servidor.
                // Nesse caso, a pendência é removida.

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

                    // Se houver falha de conexão ou
                    // resultado não confirmado, os dados
                    // e a chave de idempotência são mantidos.
                    // Assim, o usuário pode tentar novamente
                    // sem provocar uma nova baixa no estoque.

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

            // Atualiza os produtos e os saldos dos lotes
            // apenas quando não existe operação pendente.

            if (!operacaoPendente) {
                await carregarDados();
            }
        }
    );

    // =====================================================
    // INICIALIZAÇÃO DA TELA
    // Verifica se existe uma sessão autenticada,
    // configura a data atual e carrega os produtos
    // e lotes disponíveis para o usuário.
    // =====================================================

    if (!verificarSessao()) {
        return;
    }

    configurarData();

    carregarDados();
});
