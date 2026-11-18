
document.addEventListener("DOMContentLoaded", () => {

    // CONFIGURAÇÕES

    const API_URL = "http://localhost:8080";
    const PAGINA_LOGIN = "login-usuario.html";

    // ELEMENTOS DA TELA

    const tabela =
        document.getElementById("historico-desperdicio");

    const mensagemErro =
        document.getElementById("mensagemErroHistorico");

    if (!tabela) {

        console.error(
            "Tabela do histórico não encontrada."
        );

        return;
    }

    // AUTENTICAÇÃO

    // Recupera o token salvo no login
    function obterToken() {

        return sessionStorage.getItem("token");
    }

    // Encerra a sessão local
    function redirecionarLogin() {

        sessionStorage.removeItem("token");
        sessionStorage.removeItem("usuario");

        window.location.replace(PAGINA_LOGIN);
    }

    // REQUISIÇÕES AUTENTICADAS

    // Envia o token em todas as consultas
    async function requisicaoAutenticada(
        url,
        opcoes = {}
    ) {

        const token = obterToken();

        if (!token || !token.trim()) {

            redirecionarLogin();

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

        headers.set(
            "Accept",
            "application/json"
        );

        const resposta = await fetch(url, {
            ...opcoes,
            headers,
            cache: "no-store"
        });

        // SESSÃO EXPIRADA

        if (resposta.status === 401) {

            redirecionarLogin();

            throw new Error(
                "Sessão expirada. Faça login novamente."
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

            throw new Error(
                dados.mensagem ||
                `Erro HTTP ${resposta.status}`
            );
        }

        return dados;
    }

    // MENSAGENS DE ERRO

    function mostrarErro(texto) {

        if (!mensagemErro) {
            return;
        }

        mensagemErro.textContent = texto;
        mensagemErro.classList.remove("hidden");
    }

    function limparErro() {

        if (!mensagemErro) {
            return;
        }

        mensagemErro.textContent = "";
        mensagemErro.classList.add("hidden");
    }

    // FORMATAR DATA

    function formatarData(data) {

        if (!data) {
            return "-";
        }

        // Datas enviadas somente no formato AAAA-MM-DD
        if (
            /^\d{4}-\d{2}-\d{2}$/.test(data)
        ) {

            const [ano, mes, dia] =
                data.split("-");

            return `${dia}/${mes}/${ano}`;
        }

        const dataConvertida =
            new Date(data);

        if (
            Number.isNaN(
                dataConvertida.getTime()
            )
        ) {
            return "-";
        }

        // Usa o fuso horário do sistema
        return new Intl.DateTimeFormat(
            "pt-BR",
            {
                timeZone: "America/Sao_Paulo",
                day: "2-digit",
                month: "2-digit",
                year: "numeric"
            }
        ).format(dataConvertida);
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

        const numero =
            Number(quantidade);

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

    // CRIAR CÉLULAS DA TABELA

    function criarCelula(
        linha,
        texto,
        centralizar = false
    ) {

        const celula =
            document.createElement("td");

        celula.textContent =
            texto ?? "-";

        celula.className = centralizar
            ? "px-3 py-3 text-center font-semibold"
            : "px-3 py-3 font-semibold";

        linha.appendChild(celula);
    }

    // MOSTRAR MENSAGEM NA TABELA

    function mostrarMensagem(texto) {

        tabela.replaceChildren();

        const linha =
            document.createElement("tr");

        const celula =
            document.createElement("td");

        celula.colSpan = 5;

        celula.className =
            "px-3 py-6 text-center text-text-secondary";

        celula.textContent = texto;

        linha.appendChild(celula);
        tabela.appendChild(linha);
    }

    // PREENCHER TABELA

    function preencherTabela(registros) {

        tabela.replaceChildren();

        // HISTÓRICO VAZIO

        if (
            !Array.isArray(registros) ||
            registros.length === 0
        ) {

            mostrarMensagem(
                "Nenhum desperdício encontrado."
            );

            return;
        }

        // CRIAR LINHAS DO HISTÓRICO

        registros.forEach((registro) => {

            const linha =
                document.createElement("tr");

            // Mesmo visual de Todos os Produtos e Lotes
            linha.className =
                "border-b border-gray-300 bg-[#e9ebee] text-text";

            // IDENTIFICAR RESPONSÁVEL

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

            // QUANTIDADE DESPERDIÇADA

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

            // RESPONSÁVEL

            criarCelula(
                linha,
                responsavel,
                true
            );

            tabela.appendChild(linha);
        });
    }

    // CARREGAR HISTÓRICO

    // Consulta somente os registros da conta autenticada
    async function carregarHistorico() {

        limparErro();

        mostrarMensagem(
            "Carregando histórico..."
        );

        try {

            const registros = [];

            let pagina = 1;
            let total = 0;

            // BUSCAR TODAS AS PÁGINAS

            do {

                const parametros =
                    new URLSearchParams({
                        pagina: String(pagina),
                        limite: "100",
                        somenteDesperdicio: "true"
                    });

                const resposta =
                    await requisicaoAutenticada(
                        `${API_URL}/api/saidas?${parametros.toString()}`
                    );

                const dados =
                    await lerResposta(resposta);

                // VALIDAR DADOS

                if (
                    !Array.isArray(dados.itens) ||
                    !Number.isFinite(
                        Number(dados.total)
                    )
                ) {

                    throw new Error(
                        "Formato de resposta inválido."
                    );
                }

                const itens = dados.itens;

                total = Number(
                    dados.total
                );

                registros.push(...itens);

                // PROTEGER PAGINAÇÃO

                if (
                    itens.length === 0 &&
                    registros.length < total
                ) {

                    throw new Error(
                        "Paginação inconsistente no servidor."
                    );
                }

                pagina++;

            } while (
                registros.length < total
            );

            // MOSTRAR DESPERDÍCIOS

            const desperdicios =
                registros.filter(
                    (registro) =>
                        registro.desperdicio === true
                );

            preencherTabela(
                desperdicios
            );

        } catch (erro) {

            console.error(
                "Erro ao carregar histórico:",
                erro
            );

            mostrarMensagem(
                "Não foi possível carregar o histórico."
            );

            mostrarErro(
                erro.message
            );
        }
    }

    // INICIALIZAR TELA

    if (!obterToken()) {

        redirecionarLogin();
        return;
    }

    carregarHistorico();
});
