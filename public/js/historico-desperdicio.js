
// HISTÓRICO DE SAÍDA
// AUTENTICAÇÃO, PAGINAÇÃO, MOTIVOS E PESQUISA

document.addEventListener("DOMContentLoaded", () => {

    // CONFIGURAÇÕES

    const API_URL = "http://localhost:8080";
    const PAGINA_LOGIN = "login-usuario.html";

    // MOTIVOS DISPONÍVEIS

    const MOTIVOS_VALIDOS = [
        "Todos",
        "Consumido",
        "Vencimento",
        "Contaminado",
        "Danificado",
        "Outro"
    ];

    // ELEMENTOS DA TELA

    const tabela = document.getElementById(
        "historico-desperdicio"
    );

    const mensagemErro = document.getElementById(
        "mensagemErroHistorico"
    );

    const campoPesquisa = document.getElementById(
        "pesquisaProduto"
    );

    const botoesMotivo =
        document.querySelectorAll("[data-motivo]");

    // VARIÁVEIS

    let registrosCarregados = [];

    let motivoSelecionado = "Todos";

    let carregandoHistorico = false;
    let erroCarregamento = false;

    // VALIDAR ELEMENTOS

    if (
        !tabela ||
        !mensagemErro ||
        !campoPesquisa ||
        botoesMotivo.length === 0
    ) {

        console.error(
            "Elementos da tela de histórico não encontrados."
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

    // CONSULTAR API AUTENTICADA

    async function requisicaoAutenticada(url) {

        const token = obterToken();

        if (!token || !token.trim()) {

            encerrarAcesso();

            throw new Error(
                "Sessão não encontrada."
            );
        }

        // ENVIAR TOKEN DO USUÁRIO LOGADO

        const resposta = await fetch(url, {

            method: "GET",

            headers: {
                Authorization: `Bearer ${token}`,
                Accept: "application/json"
            },

            cache: "no-store"
        });

        // SESSÃO EXPIRADA

        if (resposta.status === 401) {

            encerrarAcesso();

            throw new Error(
                "Sessão inválida ou expirada."
            );
        }

        // ACESSO NEGADO

        if (resposta.status === 403) {

            throw new Error(
                "Você não tem permissão para acessar o histórico."
            );
        }

        return resposta;
    }

    // LER RESPOSTA DA API

    async function lerResposta(resposta) {

        const texto = await resposta.text();

        let dados = {};

        if (texto) {

            try {

                dados = JSON.parse(texto);

            } catch {

                throw new Error(
                    "Resposta inválida do servidor."
                );
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

    // FORMATAR DATA

    function formatarData(data) {

        if (!data) {
            return "-";
        }

        // DATA SOMENTE COM ANO, MÊS E DIA

        if (
            typeof data === "string" &&
            /^\d{4}-\d{2}-\d{2}$/.test(data)
        ) {

            const [ano, mes, dia] = data.split("-");

            return `${dia}/${mes}/${ano}`;
        }

        // DATA COMPLETA

        const dataConvertida = new Date(data);

        if (
            Number.isNaN(
                dataConvertida.getTime()
            )
        ) {
            return "-";
        }

        return new Intl.DateTimeFormat("pt-BR", {

            timeZone: "America/Sao_Paulo",
            day: "2-digit",
            month: "2-digit",
            year: "numeric"

        }).format(dataConvertida);
    }

    // FORMATAR QUANTIDADE

    function formatarQuantidade(
        quantidade,
        unidade
    ) {

        if (
            quantidade === null ||
            quantidade === undefined ||
            quantidade === ""
        ) {
            return "-";
        }

        // COMPATIBILIDADE COM DECIMAL128

        if (
            typeof quantidade === "object" &&
            "$numberDecimal" in quantidade
        ) {

            quantidade = quantidade.$numberDecimal;
        }

        const numero = Number(quantidade);

        if (!Number.isFinite(numero)) {
            return "-";
        }

        const valor = numero.toLocaleString(
            "pt-BR",
            {
                maximumFractionDigits: 6
            }
        );

        return `${valor} ${unidade || ""}`.trim();
    }

    // MOSTRAR ERRO

    function mostrarErro(texto) {

        mensagemErro.textContent = texto;

        mensagemErro.classList.remove("hidden");
    }

    // LIMPAR ERRO

    function limparErro() {

        mensagemErro.textContent = "";

        mensagemErro.classList.add("hidden");
    }

    // CRIAR CÉLULA

    function criarCelula(
        linha,
        texto,
        centralizar = false
    ) {

        const celula = document.createElement("td");

        celula.textContent = texto ?? "-";

        celula.className = centralizar
            ? "px-3 py-3 text-center font-semibold"
            : "px-3 py-3 font-semibold";

        linha.appendChild(celula);
    }

    // MOSTRAR MENSAGEM NA TABELA

    function mostrarMensagem(texto) {

        tabela.replaceChildren();

        const linha = document.createElement("tr");

        const coluna = document.createElement("td");

        coluna.colSpan = 5;

        coluna.textContent = texto;

        coluna.className =
            "px-3 py-6 text-center text-text-secondary";

        linha.appendChild(coluna);

        tabela.appendChild(linha);
    }

    // CRIAR LINHA DO HISTÓRICO

    function criarLinha(registro) {

        const linha = document.createElement("tr");

        // PADRÃO VISUAL DAS OUTRAS TELAS

        linha.className =
            "border-b border-gray-300 bg-[#e9ebee] text-text";

        // RESPONSÁVEL

        const responsavel =
            registro.origem === "AUTOMATICA"
                ? "Sistema"
                : registro.responsavelNome ||
                  "Não informado";

        // PRODUTO

        criarCelula(
            linha,
            registro.produtoNome ||
            "Produto não identificado"
        );

        // QUANTIDADE DE SAÍDA

        criarCelula(
            linha,
            formatarQuantidade(
                registro.quantidade,
                registro.unidade
            ),
            true
        );

        // MOTIVO

        criarCelula(
            linha,
            registro.motivo || "-",
            true
        );

        // DATA

        criarCelula(
            linha,
            formatarData(
                registro.dataOcorrencia
            ),
            true
        );

        // REGISTRADO POR

        criarCelula(
            linha,
            responsavel,
            true
        );

        return linha;
    }

    // NORMALIZAR TEXTO

    // IGNORAR ACENTOS E LETRAS MAIÚSCULAS

    function normalizarTexto(texto) {

        return String(texto || "")
            .normalize("NFD")
            .replace(/[\u0300-\u036f]/g, "")
            .toLowerCase()
            .trim();
    }

    // SELECIONAR FILTRO DE MOTIVO

    function selecionarFiltro(botaoAtivo) {

        // ATUALIZAR VISUAL DE TODOS OS BOTÕES

        botoesMotivo.forEach((botao) => {

            botao.classList.remove(
                "bg-primary-dark",
                "text-white"
            );

            botao.classList.add(
                "bg-[#e9ebee]",
                "text-text"
            );

            botao.setAttribute(
                "aria-pressed",
                "false"
            );
        });

        // DESTACAR BOTÃO SELECIONADO

        botaoAtivo.classList.remove(
            "bg-[#e9ebee]",
            "text-text"
        );

        botaoAtivo.classList.add(
            "bg-primary-dark",
            "text-white"
        );

        botaoAtivo.setAttribute(
            "aria-pressed",
            "true"
        );
    }

    // FILTRAR HISTÓRICO

    function filtrarHistorico() {

        // NÃO MODIFICAR RESULTADOS DURANTE CARREGAMENTO

        if (
            carregandoHistorico ||
            erroCarregamento
        ) {
            return;
        }

        // PESQUISA PELO NOME DO PRODUTO

        const pesquisa = normalizarTexto(
            campoPesquisa.value
        );

        // APLICAR FILTRO DE NOME E MOTIVO

        const registrosFiltrados =
            registrosCarregados.filter((registro) => {

                const nomeProduto = normalizarTexto(
                    registro.produtoNome
                );

                const motivoRegistro = normalizarTexto(
                    registro.motivo
                );

                const correspondeNome =
                    nomeProduto.includes(pesquisa);

                const correspondeMotivo =
                    motivoSelecionado === "Todos" ||
                    motivoRegistro === normalizarTexto(
                        motivoSelecionado
                    );

                return (
                    correspondeNome &&
                    correspondeMotivo
                );
            });

        // LIMPAR TABELA

        tabela.replaceChildren();

        // NENHUM RESULTADO

        if (registrosFiltrados.length === 0) {

            mostrarMensagem(
                pesquisa || motivoSelecionado !== "Todos"
                    ? "Nenhuma saída encontrada para os filtros selecionados."
                    : "Nenhuma saída registrada."
            );

            return;
        }

        // PREENCHER TABELA

        const fragmento =
            document.createDocumentFragment();

        registrosFiltrados.forEach((registro) => {

            fragmento.appendChild(
                criarLinha(registro)
            );
        });

        tabela.appendChild(fragmento);
    }

    // PESQUISAR ENQUANTO DIGITA

    campoPesquisa.addEventListener(
        "input",
        filtrarHistorico
    );

    // EVENTOS DOS FILTROS DE MOTIVO

    botoesMotivo.forEach((botao) => {

        botao.addEventListener("click", () => {

            const motivo = botao.dataset.motivo;

            // VALIDAR MOTIVO SELECIONADO

            if (!MOTIVOS_VALIDOS.includes(motivo)) {
                return;
            }

            // ATUALIZAR MOTIVO E APARÊNCIA

            motivoSelecionado = motivo;

            selecionarFiltro(botao);

            // APLICAR PESQUISA E MOTIVO JUNTOS

            filtrarHistorico();
        });
    });

    // CARREGAR TODAS AS SAÍDAS

    async function carregarHistorico() {

        limparErro();

        carregandoHistorico = true;
        erroCarregamento = false;

        registrosCarregados = [];

        mostrarMensagem(
            "Carregando histórico..."
        );

        try {

            const registros = [];

            let pagina = 1;
            let total = 0;

            // CONSULTAR TODAS AS PÁGINAS DA API

            do {

                const parametros =
                    new URLSearchParams({
                        pagina: String(pagina),
                        limite: "100",
                        somenteDesperdicio: "false"
                    });

                // REQUISIÇÃO COM TOKEN

                const resposta =
                    await requisicaoAutenticada(
                        `${API_URL}/api/saidas?${parametros.toString()}`
                    );

                const dados =
                    await lerResposta(resposta);

                // VALIDAR RESPOSTA

                if (
                    !Array.isArray(dados.itens) ||
                    !Number.isSafeInteger(
                        Number(dados.total)
                    ) ||
                    Number(dados.total) < 0
                ) {

                    throw new Error(
                        "Formato de resposta inválido."
                    );
                }

                const itens = dados.itens;

                total = Number(dados.total);

                // ARMAZENAR TODAS AS SAÍDAS

                registros.push(...itens);

                // VALIDAR PAGINAÇÃO

                if (
                    itens.length === 0 &&
                    registros.length < total
                ) {

                    throw new Error(
                        "Paginação inconsistente no servidor."
                    );
                }

                pagina++;

            } while (registros.length < total);

            // GUARDAR RESULTADOS PARA FILTRAR LOCALMENTE

            registrosCarregados = registros;

            carregandoHistorico = false;

            // EXIBIR TODOS OU APLICAR FILTROS ATIVOS

            filtrarHistorico();

        } catch (erro) {

            console.error(
                "Erro ao carregar histórico:",
                erro
            );

            registrosCarregados = [];
            erroCarregamento = true;

            mostrarMensagem(
                "Não foi possível carregar o histórico."
            );

            mostrarErro(erro.message);

        } finally {

            carregandoHistorico = false;
        }
    }

    // INICIALIZAÇÃO

    if (!obterToken()) {

        encerrarAcesso();

        return;
    }

    // FILTRO INICIAL: TODOS

    const filtroInicial =
        document.querySelector(
            '[data-motivo="Todos"]'
        );

    if (filtroInicial) {
        selecionarFiltro(filtroInicial);
    }

    // CARREGAR HISTÓRICO DA CONTA LOGADA

    carregarHistorico();
});
