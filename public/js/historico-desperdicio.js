
// HISTÓRICO DE SAÍDA
// AUTENTICAÇÃO, PAGINAÇÃO, FILTROS, PESQUISA E DESCRIÇÃO

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

    // ELEMENTOS DO MODAL

    const modalDescricao = document.getElementById(
        "modalDescricao"
    );

    const textoModalDescricao = document.getElementById(
        "textoModalDescricao"
    );

    const fecharModalDescricao = document.getElementById(
        "fecharModalDescricao"
    );

    const fecharModalDescricaoX = document.getElementById(
        "fecharModalDescricaoX"
    );

    // VARIÁVEIS

    let registrosCarregados = [];

    let motivoSelecionado = "Todos";

    let carregandoHistorico = false;
    let erroCarregamento = false;

    let botaoOrigemModal = null;
    let overflowAnterior = "";

    // VALIDAR ELEMENTOS

    if (
        !tabela ||
        !mensagemErro ||
        !campoPesquisa ||
        botoesMotivo.length === 0 ||
        !modalDescricao ||
        !textoModalDescricao ||
        !fecharModalDescricao ||
        !fecharModalDescricaoX
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

    // REQUISIÇÃO AUTENTICADA

    async function requisicaoAutenticada(url) {

        const token = obterToken();

        if (!token || !token.trim()) {

            encerrarAcesso();

            throw new Error(
                "Sessão não encontrada."
            );
        }

        // ENVIAR TOKEN DA CONTA LOGADA

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

    // FORMATAR QUANTIDADE DE SAÍDA
    // MOSTRAR SOMENTE O NÚMERO

    function formatarQuantidade(quantidade) {

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

        return numero.toLocaleString(
            "pt-BR",
            {
                maximumFractionDigits: 6
            }
        );
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

        return celula;
    }

    // MOSTRAR MENSAGEM NA TABELA

    function mostrarMensagem(texto) {

        tabela.replaceChildren();

        const linha = document.createElement("tr");

        const coluna = document.createElement("td");

        // SEIS COLUNAS NO HISTÓRICO

        coluna.colSpan = 6;

        coluna.textContent = texto;

        coluna.className =
            "px-3 py-6 text-center text-text-secondary";

        linha.appendChild(coluna);

        tabela.appendChild(linha);
    }

    // VERIFICAR SE O MODAL ESTÁ ABERTO

    function modalEstaAberto() {

        return !modalDescricao.classList.contains(
            "hidden"
        );
    }

    // ABRIR MODAL DE DESCRIÇÃO

    function abrirModalDescricao(registro, botaoOrigem) {

        // GUARDAR BOTÃO QUE ABRIU O MODAL

        botaoOrigemModal = botaoOrigem;

        // RECUPERAR DESCRIÇÃO DA SAÍDA

        const descricao =
            typeof registro.observacao === "string"
                ? registro.observacao.trim()
                : "";

        // EXIBIR DESCRIÇÃO DO MONGODB

        textoModalDescricao.textContent =
            descricao ||
            "Nenhuma descrição registrada para esta saída.";

        // ABRIR MODAL

        modalDescricao.classList.remove("hidden");
        modalDescricao.classList.add("flex");

        // BLOQUEAR ROLAGEM DO FUNDO

        overflowAnterior =
            document.body.style.overflow;

        document.body.style.overflow = "hidden";

        // POSICIONAR FOCO

        fecharModalDescricaoX.focus();
    }

    // FECHAR MODAL

    function fecharModal() {

        if (!modalEstaAberto()) {
            return;
        }

        // OCULTAR MODAL

        modalDescricao.classList.add("hidden");
        modalDescricao.classList.remove("flex");

        // RESTAURAR ROLAGEM

        document.body.style.overflow =
            overflowAnterior;

        // DEVOLVER FOCO AO BOTÃO ORIGINAL

        if (
            botaoOrigemModal &&
            botaoOrigemModal.isConnected
        ) {

            botaoOrigemModal.focus();
        }

        botaoOrigemModal = null;
    }

    // FECHAR MODAL PELO X

    fecharModalDescricaoX.addEventListener(
        "click",
        fecharModal
    );

    // FECHAR MODAL PELO BOTÃO FECHAR

    fecharModalDescricao.addEventListener(
        "click",
        fecharModal
    );

    // FECHAR AO CLICAR NO FUNDO

    modalDescricao.addEventListener(
        "click",
        (evento) => {

            if (evento.target === modalDescricao) {

                fecharModal();
            }
        }
    );

    // CONTROLAR TECLADO DO MODAL

    document.addEventListener("keydown", (evento) => {

        if (!modalEstaAberto()) {
            return;
        }

        // FECHAR COM ESCAPE

        if (evento.key === "Escape") {

            evento.preventDefault();

            fecharModal();

            return;
        }

        // MANTER FOCO DENTRO DO MODAL

        if (evento.key === "Tab") {

            const primeiro = fecharModalDescricaoX;
            const ultimo = fecharModalDescricao;

            if (
                evento.shiftKey &&
                document.activeElement === primeiro
            ) {

                evento.preventDefault();

                ultimo.focus();

            } else if (
                !evento.shiftKey &&
                document.activeElement === ultimo
            ) {

                evento.preventDefault();

                primeiro.focus();
            }
        }
    });

    // CRIAR LINHA DO HISTÓRICO

    function criarLinha(registro) {

        const linha = document.createElement("tr");

        // PADRÃO VISUAL DA TABELA

        linha.className =
            "border-b border-gray-300 bg-[#e9ebee] text-text";

        // RESPONSÁVEL PELO REGISTRO

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
        // SEM UNIDADE DE MEDIDA

        criarCelula(
            linha,
            formatarQuantidade(
                registro.quantidade
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

        // COLUNA DE DETALHES

        const celulaDetalhes =
            document.createElement("td");

        celulaDetalhes.className =
            "px-3 py-3 text-center";

        // BOTÃO DESCRIÇÃO

        const botaoDescricao =
            document.createElement("button");

        botaoDescricao.type = "button";

        botaoDescricao.textContent = "Descrição";

        botaoDescricao.className = "botao-descricao";

        botaoDescricao.setAttribute(
            "aria-label",
            `Ver descrição da saída de ${
                registro.produtoNome || "produto"
            }`
        );

        // ABRIR MODAL COM A DESCRIÇÃO DA LINHA

        botaoDescricao.addEventListener(
            "click",
            () => {

                abrirModalDescricao(
                    registro,
                    botaoDescricao
                );
            }
        );

        celulaDetalhes.appendChild(
            botaoDescricao
        );

        linha.appendChild(celulaDetalhes);

        return linha;
    }

    // NORMALIZAR TEXTO DA PESQUISA

    function normalizarTexto(texto) {

        return String(texto || "")
            .normalize("NFD")
            .replace(/[\u0300-\u036f]/g, "")
            .toLowerCase()
            .trim();
    }

    // SELECIONAR FILTRO DE MOTIVO

    function selecionarFiltro(botaoAtivo) {

        // REMOVER DESTAQUE DOS OUTROS BOTÕES

        botoesMotivo.forEach((botao) => {

            botao.classList.remove("ativo");

            botao.setAttribute(
                "aria-pressed",
                "false"
            );
        });

        // DESTACAR O FILTRO SELECIONADO

        botaoAtivo.classList.add("ativo");

        botaoAtivo.setAttribute(
            "aria-pressed",
            "true"
        );
    }

    // FILTRAR HISTÓRICO

    function filtrarHistorico() {

        // NÃO ALTERAR DURANTE CARREGAMENTO OU ERRO

        if (
            carregandoHistorico ||
            erroCarregamento
        ) {
            return;
        }

        // FECHAR MODAL ANTES DE ATUALIZAR A TABELA

        if (modalEstaAberto()) {

            fecharModal();
        }

        // RECUPERAR NOME PESQUISADO

        const pesquisa = normalizarTexto(
            campoPesquisa.value
        );

        // FILTRAR POR PRODUTO E MOTIVO

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

    // EVENTO DA BARRA DE PESQUISA

    campoPesquisa.addEventListener(
        "input",
        filtrarHistorico
    );

    // EVENTOS DOS FILTROS DE MOTIVO

    botoesMotivo.forEach((botao) => {

        botao.addEventListener("click", () => {

            const motivo = botao.dataset.motivo;

            // VALIDAR MOTIVO

            if (!MOTIVOS_VALIDOS.includes(motivo)) {
                return;
            }

            // ATUALIZAR FILTRO

            motivoSelecionado = motivo;

            // DESTACAR BOTÃO

            selecionarFiltro(botao);

            // ATUALIZAR RESULTADOS

            filtrarHistorico();
        });
    });

    // CARREGAR HISTÓRICO DO MONGODB

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

            // CONSULTAR TODAS AS PÁGINAS

            do {

                const parametros =
                    new URLSearchParams({
                        pagina: String(pagina),
                        limite: "100",
                        somenteDesperdicio: "false"
                    });

                // CONSULTAR COM AUTENTICAÇÃO

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

                // ARMAZENAR SAÍDAS

                registros.push(...itens);

                // CONFERIR PAGINAÇÃO

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

            // GUARDAR REGISTROS PARA A PESQUISA

            registrosCarregados = registros;

            carregandoHistorico = false;

            // APLICAR FILTROS

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

    // VERIFICAR TOKEN DA CONTA

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

    // CARREGAR HISTÓRICO

    carregarHistorico();
});
