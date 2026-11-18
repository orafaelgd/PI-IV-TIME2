
const API_URL = "http://127.0.0.1:8080";

document.addEventListener("DOMContentLoaded", () => {
    const tabela = document.getElementById("historico-desperdicio");
    const token = sessionStorage.getItem("token");

    if (!tabela) {
        console.error("Tabela do histórico não encontrada.");
        return;
    }

    function redirecionarLogin() {
        sessionStorage.removeItem("token");
        sessionStorage.removeItem("usuario");
        window.location.replace("login-usuario.html");
    }

    if (!token) {
        redirecionarLogin();
        return;
    }

    function formatarData(data) {
        if (!data) return "-";

        if (/^\d{4}-\d{2}-\d{2}$/.test(data)) {
            const [ano, mes, dia] = data.split("-");
            return `${dia}/${mes}/${ano}`;
        }

        const dataConvertida = new Date(data);

        if (Number.isNaN(dataConvertida.getTime())) {
            return "-";
        }

        return new Intl.DateTimeFormat("pt-BR", {
            timeZone: "America/Sao_Paulo",
            day: "2-digit",
            month: "2-digit",
            year: "numeric"
        }).format(dataConvertida);
    }

    function formatarQuantidade(quantidade, unidade) {
        if (
            quantidade === null ||
            quantidade === undefined ||
            quantidade === ""
        ) {
            return "-";
        }

        const numero = Number(quantidade);

        if (!Number.isFinite(numero)) return "-";

        const valor = numero.toLocaleString("pt-BR", {
            maximumFractionDigits: 6
        });

        return `${valor} ${unidade || ""}`.trim();
    }

    function criarCelula(linha, texto, centralizar = false) {
        const celula = document.createElement("td");

        celula.textContent = texto ?? "-";

        celula.className = centralizar
            ? "px-6 py-2 text-center"
            : "px-6 py-2";

        linha.appendChild(celula);
    }

    function mostrarMensagem(texto) {
        tabela.replaceChildren();

        const linha = document.createElement("tr");
        const celula = document.createElement("td");

        celula.colSpan = 5;
        celula.textContent = texto;
        celula.className =
            "px-6 py-6 text-center text-text-secondary";

        linha.appendChild(celula);
        tabela.appendChild(linha);
    }

    function preencherTabela(registros) {
        tabela.replaceChildren();

        if (!registros.length) {
            mostrarMensagem("Nenhum desperdício encontrado.");
            return;
        }

        registros.forEach((registro, indice) => {
            const linha = document.createElement("tr");

            linha.className = `
                text-[14px]
                font-semibold
                text-text
                ${indice % 2 === 0
                    ? "bg-[#E4E5E5]"
                    : "bg-[#F1F1F1]"}
            `;

            const responsavel =
                registro.origem === "AUTOMATICA"
                    ? "Sistema"
                    : registro.responsavelNome || "Não informado";

            criarCelula(
                linha,
                registro.produtoNome || "Produto não identificado"
            );

            criarCelula(
                linha,
                formatarQuantidade(
                    registro.quantidade,
                    registro.unidade
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
                formatarData(registro.dataOcorrencia),
                true
            );

            criarCelula(
                linha,
                responsavel,
                true
            );

            tabela.appendChild(linha);
        });
    }

    async function carregarHistorico() {
        mostrarMensagem("Carregando histórico...");

        try {
            const registros = [];
            let pagina = 1;
            let total = 0;

            do {
                const parametros = new URLSearchParams({
                    pagina: String(pagina),
                    limite: "100",
                    somenteDesperdicio: "true"
                });

                const resposta = await fetch(
                    `${API_URL}/api/saidas?${parametros.toString()}`,
                    {
                        method: "GET",
                        headers: {
                            Authorization: `Bearer ${token}`,
                            Accept: "application/json"
                        },
                        cache: "no-store"
                    }
                );

                if (resposta.status === 401) {
                    redirecionarLogin();
                    return;
                }

                if (resposta.status === 403) {
                    mostrarMensagem(
                        "Você não tem permissão para acessar o histórico."
                    );
                    return;
                }

                if (!resposta.ok) {
                    throw new Error(
                        `Erro HTTP ${resposta.status}`
                    );
                }

                const dados = await resposta.json();

                if (
                    !Array.isArray(dados.itens) ||
                    !Number.isFinite(Number(dados.total))
                ) {
                    throw new Error(
                        "Formato de resposta inválido."
                    );
                }

                const itens = dados.itens;
                total = Number(dados.total);

                registros.push(...itens);

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
                "Não foi possível carregar o histórico. Verifique a conexão com o servidor."
            );
        }
    }

    carregarHistorico();
});
