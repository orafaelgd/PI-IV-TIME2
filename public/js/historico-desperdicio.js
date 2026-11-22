
 // Código feito por Miriã Nascimento dos Anjos RA: 25892704

document.addEventListener("DOMContentLoaded", () => {

    // Configurações da API e da página de login
    const API_URL = "http://localhost:8080";
    const PAGINA_LOGIN = "login-usuario.html";

    // Motivos disponíveis para filtrar o histórico
    const MOTIVOS_VALIDOS = [
        "Todos",
        "Consumido",
        "Vencimento",
        "Contaminado",
        "Danificado",
        "Outro"
    ];

    // Elementos utilizados para exibir e filtrar os registros
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

    // Elementos responsáveis pela janela de descrição das saídas
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

    // Armazena os registros, o filtro selecionado e o estado da tela
    let registrosCarregados = [];

    let motivoSelecionado = "Todos";

    let carregandoHistorico = false;
    let erroCarregamento = false;

    let botaoOrigemModal = null;
    let overflowAnterior = "";

    // Verifica se todos os elementos necessários estão presentes na página
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

    // Recupera o token de autenticação armazenado na sessão
    function obterToken() {

        return sessionStorage.getItem("token");
    }

    // Remove os dados da sessão e redireciona o usuário para o login
    function encerrarAcesso() {

        sessionStorage.removeItem("token");
        sessionStorage.removeItem("usuario");

        window.location.replace(PAGINA_LOGIN);
    }

    // Realiza requisições à API utilizando o token do usuário autenticado
    async function requisicaoAutenticada(url) {

        const token = obterToken();

        if (!token || !token.trim()) {

            encerrarAcesso();

            throw new Error(
                "Sessão não encontrada."
            );
        }

        // Envia o token no cabeçalho para permitir a consulta dos registros
        const resposta = await fetch(url, {

            method: "GET",

            headers: {
                Authorization: `Bearer ${token}`,
                Accept: "application/json"
            },

            cache: "no-store"
        });

        // Redireciona para o login caso a sessão esteja inválida ou expirada
        if (resposta.status === 401) {

            encerrarAcesso();

            throw new Error(
                "Sessão inválida ou expirada."
            );
        }

        if (resposta.status === 403) {

            throw new Error(
                "Você não tem permissão para acessar o histórico."
            );
        }

        return resposta;
    }

    // Lê a resposta da API e verifica se ocorreu algum erro na requisição
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

    // Converte as datas recebidas para o formato brasileiro
    function formatarData(data) {

        if (!data) {
            return "-";
        }

        // Trata datas que possuem apenas ano, mês e dia
        if (
            typeof data === "string" &&
            /^\d{4}-\d{2}-\d{2}$/.test(data)
        ) {

            const [ano, mes, dia] = data.split("-");

            return `${dia}/${mes}/${ano}`;
        }

        // Formata datas completas considerando o fuso horário de São Paulo
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

    // Formata a quantidade de saída sem apresentar a unidade de medida
    function formatarQuantidade(quantidade) {

        if (
            quantidade === null ||
            quantidade === undefined ||
            quantidade === ""
        ) {
            return "-";
        }

        // Trata valores Decimal128 que podem ser retornados pelo MongoDB
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

    // Exibe mensagens de erro quando ocorre alguma falha
    function mostrarErro(texto) {

        mensagemErro.textContent = texto;

        mensagemErro.classList.remove("hidden");
    }

    // Limpa as mensagens de erro exibidas anteriormente
    function limparErro() {

        mensagemErro.textContent = "";

        mensagemErro.classList.add("hidden");
    }

    // Cria uma célula da tabela e define seu alinhamento
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

    // Mostra mensagens na tabela durante o carregamento ou quando não há registros
    function mostrarMensagem(texto) {

        tabela.replaceChildren();

        const linha = document.createElement("tr");

        const coluna = document.createElement("td");

        coluna.colSpan = 6;

        coluna.textContent = texto;

        coluna.className =
            "px-3 py-6 text-center text-text-secondary";

        linha.appendChild(coluna);

        tabela.appendChild(linha);
    }

    // Verifica se a janela de descrição está aberta
    function modalEstaAberto() {

        return !modalDescricao.classList.contains(
            "hidden"
        );
    }

    // Abre o modal com a descrição da saída selecionada
    function abrirModalDescricao(registro, botaoOrigem) {

        // Guarda o botão que abriu o modal para devolver o foco ao fechar
        botaoOrigemModal = botaoOrigem;

        // Recupera a observação registrada no banco de dados
        const descricao =
            typeof registro.observacao === "string"
                ? registro.observacao.trim()
                : "";

        textoModalDescricao.textContent =
            descricao ||
            "Nenhuma descrição registrada para esta saída.";

        // Exibe o modal e bloqueia a rolagem da página ao fundo
        modalDescricao.classList.remove("hidden");
        modalDescricao.classList.add("flex");

        overflowAnterior =
            document.body.style.overflow;

        document.body.style.overflow = "hidden";

        fecharModalDescricaoX.focus();
    }

    // Fecha a janela de descrição e restaura o estado da página
    function fecharModal() {

        if (!modalEstaAberto()) {
            return;
        }

        modalDescricao.classList.add("hidden");
        modalDescricao.classList.remove("flex");

        document.body.style.overflow =
            overflowAnterior;

        // Devolve o foco ao botão utilizado para abrir a descrição
        if (
            botaoOrigemModal &&
            botaoOrigemModal.isConnected
        ) {

            botaoOrigemModal.focus();
        }

        botaoOrigemModal = null;
    }

    // Permite fechar o modal pelos botões ou clicando fora da janela
    fecharModalDescricaoX.addEventListener(
        "click",
        fecharModal
    );

    fecharModalDescricao.addEventListener(
        "click",
        fecharModal
    );

    modalDescricao.addEventListener(
        "click",
        (evento) => {

            if (evento.target === modalDescricao) {

                fecharModal();
            }
        }
    );

    // Controla a navegação pelo teclado enquanto o modal estiver aberto
    document.addEventListener("keydown", (evento) => {

        if (!modalEstaAberto()) {
            return;
        }

        // Permite fechar a janela utilizando a tecla Escape
        if (evento.key === "Escape") {

            evento.preventDefault();

            fecharModal();

            return;
        }

        // Mantém a navegação por Tab entre os botões do modal
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

    // Monta uma linha da tabela com os dados de cada saída registrada
    function criarLinha(registro) {

        const linha = document.createElement("tr");

        linha.className =
            "border-b border-gray-300 bg-[#e9ebee] text-text";

        // Identifica se a saída foi registrada pelo sistema ou por um usuário
        const responsavel =
            registro.origem === "AUTOMATICA"
                ? "Sistema"
                : registro.responsavelNome ||
                  "Não informado";

        // Adiciona as informações principais da saída na tabela
        criarCelula(
            linha,
            registro.produtoNome ||
            "Produto não identificado"
        );

        criarCelula(
            linha,
            formatarQuantidade(
                registro.quantidade
            ),
            true
        );

        criarCelula(
            linha,
            registro.motivo || "-",
            true
        );

        criarCelula(
            linha,
            formatarData(
                registro.dataOcorrencia
            ),
            true
        );

        criarCelula(
            linha,
            responsavel,
            true
        );

        // Cria o botão para visualizar a descrição do registro
        const celulaDetalhes =
            document.createElement("td");

        celulaDetalhes.className =
            "px-3 py-3 text-center";

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

        // Abre o modal com as informações da saída escolhida
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

    // Padroniza o texto para facilitar pesquisas sem diferenciar acentos ou maiúsculas
    function normalizarTexto(texto) {

        return String(texto || "")
            .normalize("NFD")
            .replace(/[\u0300-\u036f]/g, "")
            .toLowerCase()
            .trim();
    }

    // Atualiza o destaque visual do botão de filtro selecionado
    function selecionarFiltro(botaoAtivo) {

        botoesMotivo.forEach((botao) => {

            botao.classList.remove("ativo");

            botao.setAttribute(
                "aria-pressed",
                "false"
            );
        });

        botaoAtivo.classList.add("ativo");

        botaoAtivo.setAttribute(
            "aria-pressed",
            "true"
        );
    }

    // Filtra os registros carregados pelo nome do produto e pelo motivo da saída
    function filtrarHistorico() {

        // Evita atualizar a tabela durante o carregamento ou após um erro
        if (
            carregandoHistorico ||
            erroCarregamento
        ) {
            return;
        }

        // Fecha a descrição aberta antes de atualizar os resultados
        if (modalEstaAberto()) {

            fecharModal();
        }

        const pesquisa = normalizarTexto(
            campoPesquisa.value
        );

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

        tabela.replaceChildren();

        // Informa quando nenhum registro corresponde aos filtros escolhidos
        if (registrosFiltrados.length === 0) {

            mostrarMensagem(
                pesquisa || motivoSelecionado !== "Todos"
                    ? "Nenhuma saída encontrada para os filtros selecionados."
                    : "Nenhuma saída registrada."
            );

            return;
        }

        // Preenche a tabela com os registros encontrados
        const fragmento =
            document.createDocumentFragment();

        registrosFiltrados.forEach((registro) => {

            fragmento.appendChild(
                criarLinha(registro)
            );
        });

        tabela.appendChild(fragmento);
    }

    // Atualiza os resultados conforme o usuário digita na barra de pesquisa
    campoPesquisa.addEventListener(
        "input",
        filtrarHistorico
    );

    // Aplica o filtro de motivo escolhido pelo usuário
    botoesMotivo.forEach((botao) => {

        botao.addEventListener("click", () => {

            const motivo = botao.dataset.motivo;

            if (!MOTIVOS_VALIDOS.includes(motivo)) {
                return;
            }

            motivoSelecionado = motivo;

            selecionarFiltro(botao);

            filtrarHistorico();
        });
    });

    // Busca o histórico de saídas na API e prepara os registros para a tabela
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

            // Consulta todas as páginas para carregar o histórico completo
            do {

                const parametros =
                    new URLSearchParams({
                        pagina: String(pagina),
                        limite: "100",
                        somenteDesperdicio: "false"
                    });

                const resposta =
                    await requisicaoAutenticada(
                        `${API_URL}/api/saidas?${parametros.toString()}`
                    );

                const dados =
                    await lerResposta(resposta);

                // Confere se os dados retornados possuem o formato esperado
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

                registros.push(...itens);

                // Verifica se a paginação está retornando todos os registros
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

            // Guarda os dados carregados e aplica os filtros da tela
            registrosCarregados = registros;

            carregandoHistorico = false;

            filtrarHistorico();

        } catch (erro) {

            // Exibe uma mensagem caso não seja possível consultar o histórico
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

    // Verifica se existe uma sessão antes de iniciar o carregamento
    if (!obterToken()) {

        encerrarAcesso();

        return;
    }

    // Seleciona o filtro "Todos" como padrão ao abrir a página
    const filtroInicial =
        document.querySelector(
            '[data-motivo="Todos"]'
        );

    if (filtroInicial) {

        selecionarFiltro(filtroInicial);
    }

    // Inicia a consulta dos registros do histórico
    carregarHistorico();
});
