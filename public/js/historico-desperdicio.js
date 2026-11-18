
// HISTÓRICO DE DESPERDÍCIO

document.addEventListener("DOMContentLoaded", () => {

    // CONFIGURAÇÕES

    const API_URL = "http://localhost:8080";
    const PAGINA_LOGIN = "login-usuario.html";

    // ELEMENTOS DA TELA

    const tabela = document.getElementById(
        "historico-desperdicio"
    );

    const mensagemErro = document.getElementById(
        "mensagemErroHistorico"
    );

    if (!tabela) {

        console.error(
            "Tabela do histórico não encontrada."
        );

        return;
    }

    // AUTENTICAÇÃO

    function obterToken() {

        return sessionStorage.getItem("token");
    }

    // ENCERRAR ACESSO

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

        // Envia o token da conta logada
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

    // LER RESPOSTA

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
                `Erro HTTP ${resposta.status}`
            );
        }

        return dados;
    }

    // FORMATAR DATA

    function formatarData(data) {

        if (!data) return "-";

        // Data somente com ano, mês e dia
        if (/^\d{4}-\d{2}-\d{2}$/.test(data)) {

            const [ano, mes, dia] = data.split("-");

            return `${dia}/${mes}/${ano}`;
        }

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

        // Aceita Decimal128 do MongoDB
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

    // EXIBIR MENSAGEM DE ERRO

    function mostrarErro(texto) {

        if (!mensagemErro) return;

        mensagemErro.textContent = texto;

        mensagemErro.classList.remove("hidden");
    }

    // LIMPAR ERRO

    function limparErro() {

        if (!mensagemErro) return;

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

    // PREENCHER TABELA

    function preencherTabela(registros) {

        tabela.replaceChildren();

        // NENHUM REGISTRO

        if (
            !Array.isArray(registros) ||
            registros.length === 0
        ) {

            mostrarMensagem(
                "Nenhum desperdício encontrado."
            );

            return;
        }

        // CRIAR LINHAS

        registros.forEach((registro) => {

            const linha = document.createElement("tr");

            // Mantém o padrão visual já definido
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

            // QUANTIDADE

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

            tabela.appendChild(linha);
        });
    }

    // CARREGAR HISTÓRICO DO MONGODB

    async function carregarHistorico() {

        limparErro();

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
                        somenteDesperdicio: "true"
                    });

                // A API identifica o usuário pelo token
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

                // O backend já filtra pela conta
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

            // MOSTRAR SOMENTE DESPERDÍCIOS

            const desperdicios = registros.filter(
                registro => registro.desperdicio === true
            );

            preencherTabela(desperdicios);

        } catch (erro) {

            console.error(
                "Erro ao carregar histórico:",
                erro
            );

            mostrarMensagem(
                "Não foi possível carregar o histórico."
            );

            mostrarErro(erro.message);
        }
    }

    // INICIALIZAÇÃO

    if (!obterToken()) {

        encerrarAcesso();

        return;
    }

    carregarHistorico();
});
