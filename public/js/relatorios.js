
document.addEventListener("DOMContentLoaded", async () => {

    // CONFIGURAÇÕES

    const API_URL = "http://localhost:8080";
    const PAGINA_LOGIN = "login-usuario.html";

    // ELEMENTOS DA TELA

    const indicadorDesperdicados =
        document.getElementById("indicador-desperdicados");

    const indicadorRegistrados =
        document.getElementById("indicador-registrados");

    const indicadorPerdas =
        document.getElementById("indicador-perdas");

    const indicadorMotivo =
        document.getElementById("indicador-motivo");

    const filtroUnidade =
        document.getElementById("filtro-unidade");

    const botaoExportar =
        document.getElementById("exportar-relatorio");

    const mensagem =
        document.getElementById("mensagemRelatorios");

    let graficoPizza = null;
    let graficoBarras = null;
    let registrosPorProduto = [];
    let exportando = false;

    // AUTENTICAÇÃO

    // Recupera o token da conta logada
    function obterToken() {
        return sessionStorage.getItem("token");
    }

    // Limpa a sessão e retorna ao login
    function encerrarAcesso() {

        sessionStorage.removeItem("token");
        sessionStorage.removeItem("usuario");

        window.location.replace(PAGINA_LOGIN);
    }

    // REQUISIÇÕES AUTENTICADAS

    // Envia o token nas consultas à API
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

        // Sessão expirada
        if (resposta.status === 401) {

            encerrarAcesso();

            throw new Error(
                "Sua sessão expirou. Faça login novamente."
            );
        }

        // Acesso negado
        if (resposta.status === 403) {

            throw new Error(
                "Você não tem permissão para esta operação."
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
                dados = { mensagem: texto };
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

        mensagem.textContent = texto;
        mensagem.classList.remove("hidden");
    }

    function limparErro() {

        mensagem.textContent = "";
        mensagem.classList.add("hidden");
    }

    // CONVERTER NÚMEROS DO MONGODB

    // Aceita valores comuns e Decimal128
    function converterNumero(valor) {

        if (
            valor != null &&
            typeof valor === "object" &&
            "$numberDecimal" in valor
        ) {
            valor = valor.$numberDecimal;
        }

        const numero = Number(valor);

        return Number.isFinite(numero)
            ? numero
            : 0;
    }

    // FORMATAR MOEDA

    function formatarMoeda(valor) {

        const numero = converterNumero(valor);

        return new Intl.NumberFormat("pt-BR", {
            style: "currency",
            currency: "BRL"
        }).format(numero);
    }

    // CONFIGURAÇÕES DO GRÁFICO PIZZA

    const opcoesPizza = {

        chart: {
            type: "pie",
            height: 280,
            toolbar: {
                show: false
            }
        },

        series: [],
        labels: [],

        colors: [
            "#269DD8",
            "#5145AC",
            "#E5602D",
            "#16A34A"
        ],

        legend: {
            position: "bottom"
        },

        noData: {
            text: "Nenhum desperdício registrado"
        },

        dataLabels: {
            enabled: false
        },

        stroke: {
            width: 0
        }
    };

    // CONFIGURAÇÕES DO GRÁFICO DE BARRAS

    const opcoesBarras = {

        chart: {
            type: "bar",
            height: 280,

            toolbar: {
                show: false
            }
        },

        series: [
            {
                name: "Quantidade Desperdiçada",
                data: []
            }
        ],

        noData: {
            text: "Nenhum desperdício registrado"
        },

        xaxis: {
            categories: [],

            labels: {
                style: {
                    fontWeight: 400
                }
            }
        },

        yaxis: {
            min: 0,

            labels: {
                style: {
                    fontWeight: 400
                }
            }
        },

        colors: ["#16474C"],

        plotOptions: {
            bar: {
                columnWidth: "65%"
            }
        },

        dataLabels: {
            enabled: false
        },

        legend: {
            show: true,
            showForSingleSeries: true,
            position: "bottom"
        }
    };

    // INICIALIZAR GRÁFICOS

    async function iniciarGraficos() {

        if (typeof ApexCharts === "undefined") {

            mostrarErro(
                "Não foi possível carregar a biblioteca dos gráficos."
            );

            return;
        }

        const elementoPizza =
            document.getElementById("pizza-chart");

        const elementoBarras =
            document.getElementById("barras-chart");

        graficoPizza = new ApexCharts(
            elementoPizza,
            opcoesPizza
        );

        graficoBarras = new ApexCharts(
            elementoBarras,
            opcoesBarras
        );

        await Promise.all([
            graficoPizza.render(),
            graficoBarras.render()
        ]);
    }

    // CARREGAR INDICADORES

    // Consulta somente os indicadores da conta
    async function carregarIndicadoresRelatorios() {

        try {

            const resposta = await requisicaoAutenticada(
                `${API_URL}/api/relatorios/indicadores`
            );

            const dados = await lerResposta(resposta);

            indicadorDesperdicados.textContent =
                dados.desperdicados ?? 0;

            indicadorRegistrados.textContent =
                dados.registrados ?? 0;

            indicadorMotivo.textContent =
                dados.motivoMaisFrequente || "Nenhum";

            indicadorPerdas.textContent =
                formatarMoeda(
                    dados.perdasFinanceiras
                );

        } catch (erro) {

            console.error(
                "Erro ao carregar indicadores:",
                erro
            );

            mostrarErro(erro.message);
        }
    }

    // CARREGAR GRÁFICO DE MOTIVOS

    async function carregarGraficoMotivos() {

        try {

            const resposta = await requisicaoAutenticada(
                `${API_URL}/api/relatorios/desperdicios/por-motivo`
            );

            const dados = await lerResposta(resposta);

            const registros =
                dados.desperdiciosPorMotivo || [];

            if (!Array.isArray(registros)) {

                throw new Error(
                    "Formato inesperado dos motivos."
                );
            }

            const motivos = registros.map(
                (registro) =>
                    registro.motivo || "Não informado"
            );

            const ocorrencias = registros.map(
                (registro) =>
                    converterNumero(
                        registro.totalOcorrencias
                    )
            );

            if (graficoPizza) {

                await graficoPizza.updateOptions({
                    labels: motivos
                });

                await graficoPizza.updateSeries(
                    ocorrencias
                );
            }

        } catch (erro) {

            console.error(
                "Erro ao carregar motivos:",
                erro
            );

            mostrarErro(erro.message);
        }
    }

    // ATUALIZAR GRÁFICO DE PRODUTOS

    // Filtra as quantidades pela unidade escolhida
    async function atualizarBarras() {

        if (!graficoBarras) {
            return;
        }

        const unidadeSelecionada =
            filtroUnidade.value;

        const filtrados = registrosPorProduto.filter(
            (registro) =>
                registro.unidadeMedida === unidadeSelecionada
        );

        const nomes = filtrados.map(
            (registro) => registro.nome || "Sem nome"
        );

        const quantidades = filtrados.map(
            (registro) =>
                converterNumero(
                    registro.quantidadeTotal
                )
        );

        await graficoBarras.updateOptions({

            xaxis: {
                categories: nomes
            },

            series: [
                {
                    name:
                        `Quantidade desperdiçada (${unidadeSelecionada})`,

                    data: quantidades
                }
            ]
        });
    }

    // CARREGAR GRÁFICO DE PRODUTOS

    async function carregarGraficoProdutos() {

        try {

            const resposta = await requisicaoAutenticada(
                `${API_URL}/api/relatorios/desperdicios/por-produto`
            );

            const dados = await lerResposta(resposta);

            const registros =
                dados.desperdiciosPorProduto;

            if (!Array.isArray(registros)) {

                throw new Error(
                    "Formato inesperado do relatório por produto."
                );
            }

            registrosPorProduto = registros;

            // BUSCAR UNIDADES DISPONÍVEIS

            const unidades = [
                ...new Set(
                    registros.map(
                        (registro) =>
                            registro.unidadeMedida
                    )
                )
            ].filter(Boolean);

            unidades.sort(
                (a, b) => a.localeCompare(b, "pt-BR")
            );

            filtroUnidade.replaceChildren();

            // NENHUM DESPERDÍCIO

            if (unidades.length === 0) {

                const opcao =
                    document.createElement("option");

                opcao.value = "";
                opcao.textContent = "Sem dados";

                filtroUnidade.appendChild(opcao);
                filtroUnidade.disabled = true;

                if (graficoBarras) {

                    await graficoBarras.updateOptions({
                        xaxis: {
                            categories: []
                        },
                        series: [
                            {
                                name: "Quantidade Desperdiçada",
                                data: []
                            }
                        ]
                    });
                }

                return;
            }

            // CRIAR OPÇÕES DO FILTRO

            unidades.forEach((unidade) => {

                const opcao =
                    document.createElement("option");

                opcao.value = unidade;
                opcao.textContent = unidade;

                filtroUnidade.appendChild(opcao);
            });

            filtroUnidade.disabled = false;

            // Exibe a primeira unidade encontrada
            await atualizarBarras();

        } catch (erro) {

            console.error(
                "Erro ao carregar produtos:",
                erro
            );

            mostrarErro(erro.message);
        }
    }

    // EVENTO DO FILTRO

    filtroUnidade.addEventListener(
        "change",
        atualizarBarras
    );

    // FORMATAR MOEDA PARA CSV

    // Preserva a precisão dos valores monetários
    function formatarMoedaCSV(valor) {

        if (valor == null) {
            return "Não disponível";
        }

        const partes =
            /^(-?)(\d+)(?:\.(\d+))?$/.exec(
                String(valor)
            );

        if (!partes) {

            throw new Error(
                "Valor monetário inválido no relatório."
            );
        }

        const [, sinal, inteiro, fracao = ""] = partes;

        let centavos =
            BigInt(inteiro) * 100n +
            BigInt((fracao + "00").slice(0, 2));

        if (
            fracao.length > 2 &&
            fracao[2] >= "5"
        ) {
            centavos += 1n;
        }

        const reais = (centavos / 100n)
            .toString()
            .replace(
                /\B(?=(\d{3})+(?!\d))/g,
                "."
            );

        const casas = (centavos % 100n)
            .toString()
            .padStart(2, "0");

        return `R$ ${
            sinal && centavos !== 0n ? "-" : ""
        }${reais},${casas}`;
    }

    // GERAR ARQUIVO CSV

    function gerarCsvFinanceiro(produtos) {

        const linhas = [[
            "Produto",
            "Qtd. Desperdiçada",
            "Valor unit.",
            "Valor total p/ estoque",
            "Valor perdido"
        ]];

        for (const produto of produtos) {

            const quantidade = String(
                produto.quantidadeDesperdicada ?? ""
            );

            if (
                !/^-?\d+(?:\.\d+)?$/.test(
                    quantidade
                )
            ) {

                throw new Error(
                    "Quantidade inválida no relatório."
                );
            }

            linhas.push([
                `${produto.nome ?? ""}${
                    produto.unidadeMedida
                        ? ` (${produto.unidadeMedida})`
                        : ""
                }`,

                quantidade.replace(".", ","),

                formatarMoedaCSV(
                    produto.valorUnitario
                ),

                formatarMoedaCSV(
                    produto.valorTotalEstoque
                ),

                formatarMoedaCSV(
                    produto.valorPerdido
                )
            ]);
        }

        // Protege textos que poderiam ser interpretados
        // como fórmulas ao abrir o CSV
        return "\uFEFF" + linhas.map((linha) =>

            linha.map((valor) => {

                let texto = String(valor);

                if (
                    /^[\s\u0000-\u001f]*[=+\-@]|^[\t\r\n]/u
                        .test(texto)
                ) {
                    texto = "'" + texto;
                }

                return `"${texto.replace(/"/g, '""')}"`;

            }).join(";")

        ).join("\r\n");
    }

    // EXPORTAR RELATÓRIO

    async function exportarRelatorio() {

        if (exportando) {
            return;
        }

        exportando = true;

        const textoOriginal =
            botaoExportar.textContent;

        botaoExportar.disabled = true;
        botaoExportar.textContent = "Exportando...";

        try {

            const resposta = await requisicaoAutenticada(
                `${API_URL}/api/relatorios/financeiro-por-produto`
            );

            const dados = await lerResposta(resposta);

            const produtos =
                dados.financeiroPorProduto;

            if (!Array.isArray(produtos)) {

                throw new Error(
                    "Formato inesperado do relatório financeiro."
                );
            }

            if (produtos.length === 0) {

                alert(
                    "Não existem desperdícios para exportar."
                );

                return;
            }

            // CRIAR CSV

            const arquivo = new Blob(
                [gerarCsvFinanceiro(produtos)],
                {
                    type: "text/csv;charset=utf-8;"
                }
            );

            const url = URL.createObjectURL(
                arquivo
            );

            // INICIAR DOWNLOAD

            const link = document.createElement("a");

            link.href = url;

            link.download =
                "relatorio-desperdicios.csv";

            document.body.appendChild(link);

            link.click();
            link.remove();

            setTimeout(() => {
                URL.revokeObjectURL(url);
            }, 1000);

        } catch (erro) {

            console.error(
                "Erro ao exportar relatório:",
                erro
            );

            mostrarErro(erro.message);

            alert(
                "Não foi possível exportar o relatório."
            );

        } finally {

            exportando = false;
            botaoExportar.disabled = false;

            botaoExportar.textContent =
                textoOriginal;
        }
    }

    botaoExportar.addEventListener(
        "click",
        exportarRelatorio
    );

    // INICIALIZAR RELATÓRIOS

    async function iniciar() {

        if (!obterToken()) {

            encerrarAcesso();

            return;
        }

        limparErro();

        // Cria os gráficos antes de atualizar os dados
        try {

            await iniciarGraficos();

        } catch (erro) {

            console.error(
                "Erro ao iniciar gráficos:",
                erro
            );

            mostrarErro(erro.message);
        }

        // Busca os relatórios da conta autenticada
        await Promise.all([
            carregarIndicadoresRelatorios(),
            carregarGraficoMotivos(),
            carregarGraficoProdutos()
        ]);
    }

    iniciar();
});
