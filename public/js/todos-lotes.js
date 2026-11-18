// Código feito por Livia Carvalho Lucizano RA: 25017514

document.addEventListener("DOMContentLoaded", () => {

    // CONFIGURAÇÕES

    const API = "http://localhost:8080/api";

    const PAGINA_LOGIN = "login-usuario.html";

    // ELEMENTOS DA TELA

    const tabela =
        document.getElementById("tabelaLotes");

    const mensagemErro =
        document.getElementById("mensagemErro");

    // BARRA DE PESQUISA POR CÓDIGO DO LOTE

    const campoPesquisa =
        document.getElementById("pesquisaLote");

    // MODAL DE EXCLUSÃO OU INATIVAÇÃO

    const modal =
        document.getElementById("modalLote");

    const modalTitulo =
        document.getElementById("modalTitulo");

    const modalTexto =
        document.getElementById("modalTexto");

    const modalConfirmar =
        document.getElementById("modalConfirmar");

    const modalCancelar =
        document.getElementById("modalCancelar");

    // VARIÁVEIS

    let acaoModal = null;
    let processando = false;

    // LOTES RETORNADOS PELO MONGODB

    let lotesCarregados = [];

    let carregandoLotes = false;
    let erroCarregamento = false;

    // AUTENTICAÇÃO

    function obterToken() {

        return sessionStorage.getItem("token");
    }

    // LIMPAR SESSÃO

    function encerrarAcesso() {

        sessionStorage.removeItem("token");
        sessionStorage.removeItem("usuario");

        window.location.replace(PAGINA_LOGIN);
    }

    // REQUISIÇÕES AUTENTICADAS

    async function requisicaoAutenticada(url, opcoes = {}) {

        const token = obterToken();

        if (!token || !token.trim()) {

            encerrarAcesso();

            throw new Error("Sessão não encontrada.");
        }

        // CONFIGURAR HEADERS

        const headers = new Headers(
            opcoes.headers || {}
        );

        // ENVIAR TOKEN DO USUÁRIO

        headers.set(
            "Authorization",
            `Bearer ${token}`
        );

        // REALIZAR REQUISIÇÃO

        const resposta = await fetch(url, {

            ...opcoes,

            headers,

            cache: "no-store"
        });

        // SESSÃO EXPIRADA

        if (resposta.status === 401) {

            encerrarAcesso();

            throw new Error(
                "Sua sessão expirou. Faça login novamente."
            );
        }

        // ACESSO NÃO AUTORIZADO

        if (resposta.status === 403) {

            throw new Error(
                "Você não tem permissão para esta operação."
            );
        }

        return resposta;
    }

    // ÍCONE DE EDIÇÃO

    const engrenagem = `
        <svg xmlns="http://www.w3.org/2000/svg"
            width="22" height="22"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
            stroke-width="1.8"
            aria-hidden="true">

            <path stroke-linecap="round"
                stroke-linejoin="round"
                d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 0 0 2.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 0 0 1.065 2.572c1.757.426 1.757 2.924 0 3.35a1.724 1.724 0 0 0-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 0 0-2.572 1.065c-.426 1.757-2.924 1.757-3.35 0a1.724 1.724 0 0 0-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 0 0-1.065-2.572c1.757-.426 1.757-2.924 0-3.35a1.724 1.724 0 0 0 1.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065Z"/>

            <path stroke-linecap="round"
                stroke-linejoin="round"
                d="M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0Z"/>
        </svg>
    `;

    // ÍCONE DE EXCLUSÃO

    const lixeira = `
        <svg xmlns="http://www.w3.org/2000/svg"
            width="22" height="22"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
            stroke-width="1.8"
            aria-hidden="true">

            <path stroke-linecap="round"
                stroke-linejoin="round"
                d="M3 6h18m-2 0-1 14H6L5 6m3 0V4h8v2M10 10v7m4-7v7"/>
        </svg>
    `;

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
                dados.erro ||
                `Erro HTTP ${resposta.status}`
            );
        }

        return dados;
    }

    // MENSAGENS DE ERRO

    function mostrarErro(texto) {

        mensagemErro.textContent = texto;

        mensagemErro.classList.remove("hidden");
    }

    function limparErro() {

        mensagemErro.textContent = "";

        mensagemErro.classList.add("hidden");
    }

    // FORMATAR MOEDA

    function formatarMoeda(valor) {

        const numero = Number(valor);

        return valor != null &&
            valor !== "" &&
            Number.isFinite(numero)

            ? numero.toLocaleString("pt-BR", {
                style: "currency",
                currency: "BRL"
            })

            : "-";
    }

    // FORMATAR QUANTIDADE

    function formatarQuantidade(valor) {

        const numero = Number(valor);

        return valor != null &&
            valor !== "" &&
            Number.isFinite(numero)

            ? numero.toLocaleString("pt-BR", {
                maximumFractionDigits: 3
            })

            : "-";
    }

    // FORMATAR DATA

    function formatarData(valor) {

        if (!valor) {
            return "-";
        }

        const partes = String(valor)
            .slice(0, 10)
            .split("-");

        if (partes.length !== 3) {
            return "-";
        }

        return `${partes[2]}/${partes[1]}/${partes[0]}`;
    }

    // CALCULAR STATUS DO LOTE

    function calcularStatus(lote) {

        // LOTE INATIVO

        if (lote.inativo === true) {
            return "Inativo";
        }

        const quantidade = Number(
            lote.quantidadeDisponivel
        );

        // LOTE ESGOTADO

        if (quantidade <= 0) {
            return "Esgotado";
        }

        const validade = String(
            lote.dataValidade || ""
        ).slice(0, 10);

        if (!/^\d{4}-\d{2}-\d{2}$/.test(validade)) {
            return "Ativo";
        }

        // DATA ATUAL

        const hoje = new Date();

        const dataHoje = [
            hoje.getFullYear(),
            String(hoje.getMonth() + 1).padStart(2, "0"),
            String(hoje.getDate()).padStart(2, "0")
        ].join("-");

        // LOTE VENCIDO

        if (validade < dataHoje) {
            return "Vencido";
        }

        const dias = Math.round(
            (
                new Date(`${validade}T00:00:00`) -
                new Date(`${dataHoje}T00:00:00`)
            ) / 86400000
        );

        // LOTE PRÓXIMO DO VENCIMENTO

        if (dias <= 30) {
            return "Próx. do vencimento";
        }

        return "Ativo";
    }

    // CRIAR CÉLULAS DA TABELA

    // MESMA FONTE DO HISTÓRICO

    function criarCelula(texto, classes = "") {

        const td = document.createElement("td");

        td.className =
            `px-3 py-3 font-semibold ${classes}`;

        td.textContent = String(texto);

        return td;
    }

    // CRIAR BOTÕES

    function criarBotao(titulo, icone, acao) {

        const botao = document.createElement("button");

        botao.type = "button";

        botao.title = titulo;

        botao.setAttribute("aria-label", titulo);

        botao.className =
            "flex h-[28px] w-[28px] cursor-pointer items-center justify-center rounded text-primary-dark hover:bg-primary-light";

        botao.innerHTML = icone;

        botao.addEventListener("click", acao);

        return botao;
    }

    // ABRIR MODAL

    function abrirModal(titulo, texto, nomeBotao, acao) {

        modalTitulo.textContent = titulo;

        modalTexto.textContent = texto;

        modalConfirmar.textContent = nomeBotao;

        modalConfirmar.disabled = false;

        acaoModal = acao;

        modal.classList.remove("hidden");

        modal.classList.add("flex");

        modalCancelar.focus();
    }

    // FECHAR MODAL

    function fecharModal() {

        modal.classList.add("hidden");

        modal.classList.remove("flex");

        acaoModal = null;
    }

    // EVENTOS DO MODAL

    modalCancelar.addEventListener("click", () => {

        if (!processando) {
            fecharModal();
        }
    });

    // FECHAR MODAL AO CLICAR NO FUNDO

    modal.addEventListener("click", (evento) => {

        if (
            evento.target === modal &&
            !processando
        ) {
            fecharModal();
        }
    });

    // FECHAR MODAL COM ESC

    document.addEventListener("keydown", (evento) => {

        if (
            evento.key === "Escape" &&
            !modal.classList.contains("hidden") &&
            !processando
        ) {
            fecharModal();
        }
    });

    // CONFIRMAR EXCLUSÃO OU INATIVAÇÃO

    modalConfirmar.addEventListener("click", async () => {

        if (!acaoModal || processando) {
            return;
        }

        processando = true;

        const acao = acaoModal;

        modalConfirmar.disabled = true;
        modalCancelar.disabled = true;

        try {

            // EXECUTAR AÇÃO CONFIRMADA

            const mensagem = await acao();

            fecharModal();

            // ATUALIZAR LOTES E MANTER A PESQUISA

            await carregarLotes();

            alert(mensagem);

        } catch (erro) {

            console.error(
                "Erro na operação do lote:",
                erro
            );

            mostrarErro(erro.message);

        } finally {

            processando = false;

            modalConfirmar.disabled = false;
            modalCancelar.disabled = false;
        }
    });

    // VERIFICAR POSSIBILIDADE DE EXCLUSÃO

    async function verificarExclusao(id) {

        limparErro();

        try {

            const resposta = await requisicaoAutenticada(
                `${API}/lotes/${encodeURIComponent(id)}/exclusao`
            );

            const dados = await lerResposta(resposta);

            // LOTE SEM MOVIMENTAÇÕES

            if (dados.podeExcluir === true) {

                abrirModal(
                    "Excluir lote",

                    "Tem certeza de que deseja excluir este lote? Esta ação não poderá ser desfeita.",

                    "Excluir lote",

                    async () => {

                        const resposta = await requisicaoAutenticada(
                            `${API}/lotes/${encodeURIComponent(id)}`,
                            {
                                method: "DELETE"
                            }
                        );

                        const resultado =
                            await lerResposta(resposta);

                        return resultado.mensagem ||
                            "Lote excluído com sucesso!";
                    }
                );

            } else {

                // LOTE COM MOVIMENTAÇÕES

                // OFERECER OPÇÃO DE INATIVAÇÃO

                abrirModal(
                    "Não é possível excluir o lote",

                    dados.mensagem ||
                    "Este lote possui movimentações. Deseja torná-lo inativo?",

                    "Tornar inativo",

                    async () => {

                        const resposta = await requisicaoAutenticada(
                            `${API}/lotes/${encodeURIComponent(id)}/status`,
                            {
                                method: "PATCH",

                                headers: {
                                    "Content-Type": "application/json"
                                },

                                body: JSON.stringify({
                                    inativo: true
                                })
                            }
                        );

                        const resultado =
                            await lerResposta(resposta);

                        return resultado.mensagem ||
                            "Lote inativado com sucesso!";
                    }
                );
            }

        } catch (erro) {

            mostrarErro(
                `Não foi possível verificar o lote: ${erro.message}`
            );
        }
    }

    // CRIAR LINHA DA TABELA

    function criarLinha(lote) {

        const id = lote.id;

        const linha = document.createElement("tr");

        // MESMA COR E BORDAS DO HISTÓRICO

        linha.className =
            "border-b border-gray-300 bg-[#e9ebee] text-text";

        // CÓDIGO DO LOTE

        linha.appendChild(
            criarCelula(lote.codigo || "-")
        );

        // PRODUTO

        linha.appendChild(
            criarCelula(
                lote.produto || "Produto não encontrado"
            )
        );

        // QUANTIDADE INICIAL

        linha.appendChild(
            criarCelula(
                formatarQuantidade(lote.quantidadeInicial)
            )
        );

        // QUANTIDADE DISPONÍVEL

        linha.appendChild(
            criarCelula(
                formatarQuantidade(lote.quantidadeDisponivel)
            )
        );

        // CUSTO DE AQUISIÇÃO

        linha.appendChild(
            criarCelula(
                formatarMoeda(lote.custoTotalAquisicao)
            )
        );

        // DATA DE ENTRADA

        linha.appendChild(
            criarCelula(
                formatarData(lote.dataEntrada)
            )
        );

        // DATA DE VALIDADE

        linha.appendChild(
            criarCelula(
                formatarData(lote.dataValidade)
            )
        );

        // STATUS DO LOTE

        linha.appendChild(
            criarCelula(
                calcularStatus(lote),
                "text-center"
            )
        );

        // BOTÕES DE AÇÃO

        const celula = document.createElement("td");

        celula.className = "px-3 py-3";

        const grupo = document.createElement("div");

        grupo.className =
            "flex items-center justify-center gap-3";

        // BOTÃO EDITAR

        const editar = criarBotao(
            "Editar lote",
            engrenagem,
            () => {

                window.location.href =
                    `cadastro-lotes.html?id=${encodeURIComponent(id)}`;
            }
        );

        // BOTÃO EXCLUIR

        const excluir = criarBotao(
            "Excluir lote",
            lixeira,
            () => verificarExclusao(id)
        );

        // DESATIVAR BOTÕES SEM ID

        if (!id) {

            editar.disabled = true;
            excluir.disabled = true;

            editar.title =
                "ID do lote não encontrado";

            excluir.title =
                "ID do lote não encontrado";
        }

        grupo.append(editar, excluir);

        celula.appendChild(grupo);

        linha.appendChild(celula);

        return linha;
    }

    // MOSTRAR MENSAGEM NA TABELA

    function mostrarMensagem(texto) {

        tabela.replaceChildren();

        const linha = document.createElement("tr");

        const celula = document.createElement("td");

        celula.colSpan = 9;

        celula.className =
            "px-3 py-6 text-center text-text-secondary";

        celula.textContent = texto;

        linha.appendChild(celula);

        tabela.appendChild(linha);
    }

    // NORMALIZAR TEXTO DA PESQUISA

    // IGNORAR ACENTOS E LETRAS MAIÚSCULAS

    function normalizarTexto(texto) {

        return String(texto || "")
            .normalize("NFD")
            .replace(/[\u0300-\u036f]/g, "")
            .toLowerCase()
            .trim();
    }

    // FILTRAR LOTES PELO CÓDIGO

    function filtrarLotes() {

        // EVITAR ALTERAR A TABELA DURANTE
        // O CARREGAMENTO OU ERRO DA API

        if (carregandoLotes || erroCarregamento) {
            return;
        }

        // RECUPERAR CÓDIGO PESQUISADO

        const pesquisa = normalizarTexto(
            campoPesquisa ? campoPesquisa.value : ""
        );

        // FILTRAR LOTES PELO CÓDIGO

        const lotesFiltrados = lotesCarregados.filter(
            (lote) => {

                const codigoLote = normalizarTexto(
                    lote.codigo
                );

                return codigoLote.includes(pesquisa);
            }
        );

        // LIMPAR TABELA

        tabela.replaceChildren();

        // VERIFICAR RESULTADOS

        if (lotesFiltrados.length === 0) {

            mostrarMensagem(
                pesquisa
                    ? "Nenhum lote encontrado com esse código."
                    : "Nenhum lote cadastrado."
            );

            return;
        }

        // PREENCHER TABELA COM OS LOTES FILTRADOS

        const fragmento =
            document.createDocumentFragment();

        lotesFiltrados.forEach((lote) => {

            fragmento.appendChild(
                criarLinha(lote)
            );
        });

        tabela.appendChild(fragmento);
    }

    // EVENTO DA BARRA DE PESQUISA

    // PESQUISAR ENQUANTO O USUÁRIO DIGITA

    if (campoPesquisa) {

        campoPesquisa.addEventListener(
            "input",
            filtrarLotes
        );
    }

    // CARREGAR LOTES DO MONGODB

    async function carregarLotes() {

        limparErro();

        // INICIAR CARREGAMENTO

        carregandoLotes = true;
        erroCarregamento = false;

        mostrarMensagem("Carregando lotes...");

        try {

            // CONSULTAR LOTES DA CONTA AUTENTICADA

            const resposta = await requisicaoAutenticada(
                `${API}/lotes`
            );

            const dados = await lerResposta(resposta);

            // VALIDAR DADOS RECEBIDOS

            if (!Array.isArray(dados.lotes)) {

                throw new Error(
                    "Resposta inválida do servidor."
                );
            }

            // ARMAZENAR LOTES DO USUÁRIO

            lotesCarregados = dados.lotes;

            // FINALIZAR CARREGAMENTO

            carregandoLotes = false;

            // PREENCHER TABELA RESPEITANDO
            // O CÓDIGO DIGITADO NA PESQUISA

            filtrarLotes();

        } catch (erro) {

            console.error(
                "Erro ao carregar lotes:",
                erro
            );

            // LIMPAR RESULTADOS EM CASO DE ERRO

            lotesCarregados = [];
            erroCarregamento = true;

            mostrarMensagem(
                "Não foi possível carregar os lotes."
            );

            mostrarErro(erro.message);

        } finally {

            carregandoLotes = false;
        }
    }

    // INICIALIZAR TELA

    // VERIFICAR SE O USUÁRIO ESTÁ LOGADO

    if (!obterToken()) {

        encerrarAcesso();

        return;
    }

    // CARREGAR LOTES DA CONTA

    carregarLotes();

});
