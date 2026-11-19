// PRÓXIMOS DO VENCIMENTO
// AUTENTICAÇÃO, TABELA PADRONIZADA E PESQUISA

document.addEventListener("DOMContentLoaded", () => {
  // CONFIGURAÇÕES

  const API_URL = "http://localhost:8081/api/lotes/proximos-vencimento";

  const PAGINA_LOGIN = "login-usuario.html";

  const PERIODOS_VALIDOS = [1, 3, 7, 15, 30];

  // ELEMENTOS DA TELA

  const botoes = document.querySelectorAll("[data-dias]");

  const tabela = document.getElementById("lista-vencimentos");

  const mensagem = document.getElementById("mensagem-vencimentos");

  const campoPesquisa = document.getElementById("pesquisaProduto");

  // VARIÁVEIS

  let requisicaoAtual = null;
  let modalAberto = null;

  // LOTES DO PERÍODO SELECIONADO

  let lotesCarregados = [];

  let carregando = false;
  let erroCarregamento = false;

  // VALIDAR ELEMENTOS

  if (!tabela || !mensagem || !campoPesquisa) {
    console.error("Elementos da tela de vencimentos não encontrados.");

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

  async function requisicaoAutenticada(url, sinal) {
    const token = obterToken();

    if (!token || !token.trim()) {
      encerrarAcesso();

      throw new Error("Sessão não encontrada.");
    }

    // ENVIAR TOKEN DO USUÁRIO

    const resposta = await fetch(url, {
      method: "GET",

      headers: {
        Authorization: `Bearer ${token}`,
        Accept: "application/json",
      },

      signal: sinal,
      cache: "no-store",
    });

    // SESSÃO EXPIRADA

    if (resposta.status === 401) {
      encerrarAcesso();

      throw new Error("Sessão inválida ou expirada.");
    }

    // ACESSO NEGADO

    if (resposta.status === 403) {
      throw new Error("Você não tem permissão para acessar os lotes.");
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
        throw new Error("Resposta inválida do servidor.");
      }
    }

    if (!resposta.ok) {
      throw new Error(
        dados.mensagem || dados.erro || `Erro HTTP ${resposta.status}`,
      );
    }

    return dados;
  }

  // FORMATAR DATA

  function formatarData(valor) {
    const data = String(valor || "").substring(0, 10);

    if (!/^\d{4}-\d{2}-\d{2}$/.test(data)) {
      return "-";
    }

    const [ano, mes, dia] = data.split("-");

    return `${dia}/${mes}/${ano}`;
  }

  // FORMATAR QUANTIDADE

  function formatarQuantidade(quantidade, unidade) {
    if (quantidade === null || quantidade === undefined || quantidade === "") {
      return "-";
    }

    // COMPATIBILIDADE COM DECIMAL128

    if (typeof quantidade === "object" && "$numberDecimal" in quantidade) {
      quantidade = quantidade.$numberDecimal;
    }

    const numero = Number(quantidade);

    if (!Number.isFinite(numero)) {
      return "-";
    }

    const valor = numero.toLocaleString("pt-BR", {
      maximumFractionDigits: 6,
    });

    return `${valor} ${unidade || ""}`.trim();
  }

  // CALCULAR DIAS RESTANTES

  function calcularDiasRestantes(dataISO) {
    const data = String(dataISO || "").substring(0, 10);

    if (!/^\d{4}-\d{2}-\d{2}$/.test(data)) {
      return null;
    }

    const [ano, mes, dia] = data.split("-").map(Number);

    const destino = Date.UTC(ano, mes - 1, dia);

    // DATA ATUAL NO FUSO DE SÃO PAULO

    const partes = new Intl.DateTimeFormat("en-US", {
      timeZone: "America/Sao_Paulo",
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
    }).formatToParts(new Date());

    const obter = (tipo) =>
      Number(partes.find((parte) => parte.type === tipo).value);

    const hoje = Date.UTC(obter("year"), obter("month") - 1, obter("day"));

    return Math.round((destino - hoje) / 86400000);
  }

  // IDENTIFICAR SITUAÇÃO DO LOTE

  function obterSituacao(lote) {
    const dias = calcularDiasRestantes(lote.dataValidade);

    if (dias === null) {
      return "Data não disponível";
    }

    if (dias < 0) {
      return `Vencido há ${Math.abs(dias)} dia(s)`;
    }

    if (dias === 0) {
      return "Vence hoje";
    }

    if (dias === 1) {
      return "Vence amanhã";
    }

    return `Vence em ${dias} dias`;
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

  // MOSTRAR MENSAGEM NA TABELA

  function mostrarMensagemTabela(texto) {
    tabela.replaceChildren();

    const linha = document.createElement("tr");
    const celula = document.createElement("td");

    celula.colSpan = 5;

    celula.className = "px-3 py-6 text-center text-text-secondary";

    celula.textContent = texto;

    linha.appendChild(celula);
    tabela.appendChild(linha);
  }

  // CRIAR CÉLULA

  function criarCelula(linha, texto, centralizar = false) {
    const celula = document.createElement("td");

    celula.className = centralizar
      ? "px-3 py-3 text-center font-semibold"
      : "px-3 py-3 font-semibold";

    celula.textContent = texto ?? "-";

    linha.appendChild(celula);

    return celula;
  }

  // CRIAR TEXTO DO MODAL

  function criarTexto(tag, texto, classes) {
    const elemento = document.createElement(tag);

    elemento.textContent = texto;
    elemento.className = classes;

    return elemento;
  }

  // MODAL DE DETALHES DO LOTE

  function abrirModalDetalhes(lote, botaoOrigem) {
    // EVITAR DOIS MODAIS ABERTOS

    if (modalAberto) {
      modalAberto.fechar();
    }

    // FUNDO ESCURECIDO

    const overlay = document.createElement("div");

    overlay.className =
      "fixed inset-0 z-50 flex items-center " +
      "justify-center bg-black/50 p-4";

    // JANELA DO MODAL

    const modal = document.createElement("section");

    modal.className =
      "w-full max-w-[480px] max-h-[90vh] " +
      "overflow-y-auto rounded-lg bg-white p-6 shadow-lg";

    modal.setAttribute("role", "dialog");
    modal.setAttribute("aria-modal", "true");

    // TÍTULO E BOTÃO FECHAR

    const cabecalho = document.createElement("div");

    cabecalho.className = "mb-5 flex items-center justify-between gap-4";

    const titulo = criarTexto(
      "h2",
      "Detalhes do lote",
      "text-lg font-semibold text-text",
    );

    titulo.id = "titulo-modal-vencimento";

    modal.setAttribute("aria-labelledby", titulo.id);

    const botaoX = document.createElement("button");

    botaoX.type = "button";
    botaoX.textContent = "×";

    botaoX.className = "cursor-pointer text-2xl text-text-secondary";

    botaoX.setAttribute("aria-label", "Fechar detalhes");

    cabecalho.append(titulo, botaoX);

    // DESCRIÇÃO

    const descricao = criarTexto(
      "p",
      "Informações do lote próximo ao vencimento.",
      "mb-5 text-xs text-text-secondary",
    );

    // SITUAÇÃO DO VENCIMENTO

    const situacao = obterSituacao(lote);

    const status = criarTexto(
      "p",
      situacao,
      "mb-4 text-sm font-semibold text-text",
    );

    // ÁREA DAS INFORMAÇÕES

    const informacoes = document.createElement("div");

    informacoes.className = "rounded-md bg-[#e9ebee] px-4 py-2";

    // ADICIONAR INFORMAÇÃO AO MODAL

    function adicionarCampo(rotulo, valor) {
      const linha = document.createElement("div");

      linha.className =
        "flex items-start justify-between gap-4 " +
        "border-b border-gray-300 py-3";

      const nome = criarTexto("span", rotulo, "text-xs text-text-secondary");

      const conteudo = criarTexto(
        "strong",
        valor ?? "-",
        "break-words text-right text-xs font-semibold text-text",
      );

      linha.append(nome, conteudo);

      informacoes.appendChild(linha);
    }

    // DADOS DO LOTE

    adicionarCampo("Produto", lote.produtoNome || "Produto sem nome");

    adicionarCampo("Código do lote", lote.codigo || "-");

    adicionarCampo(
      "Quantidade disponível",
      formatarQuantidade(lote.quantidadeDisponivel, lote.unidadeMedida),
    );

    adicionarCampo("Data de validade", formatarData(lote.dataValidade));

    adicionarCampo("Situação", situacao);

    // BOTÃO FECHAR

    const rodape = document.createElement("div");

    rodape.className = "mt-6 flex justify-end";

    const botaoFechar = document.createElement("button");

    botaoFechar.type = "button";
    botaoFechar.textContent = "Fechar";

    botaoFechar.className =
      "cursor-pointer rounded bg-primary-dark " +
      "px-5 py-2 text-xs font-semibold text-white";

    rodape.appendChild(botaoFechar);

    // MONTAR MODAL

    modal.append(cabecalho, descricao, status, informacoes, rodape);

    overlay.appendChild(modal);
    document.body.appendChild(overlay);

    // BLOQUEAR ROLAGEM DO FUNDO

    const overflowAnterior = document.body.style.overflow;

    document.body.style.overflow = "hidden";

    // FECHAR MODAL

    function fechar() {
      document.removeEventListener("keydown", tratarTeclado);

      overlay.remove();

      document.body.style.overflow = overflowAnterior;

      modalAberto = null;

      if (botaoOrigem && botaoOrigem.isConnected) {
        botaoOrigem.focus();
      }
    }

    // TRATAR TECLADO

    function tratarTeclado(evento) {
      if (evento.key === "Escape") {
        fechar();
        return;
      }

      // MANTER FOCO NO MODAL

      if (evento.key === "Tab") {
        const primeiro = botaoX;
        const ultimo = botaoFechar;

        if (evento.shiftKey && document.activeElement === primeiro) {
          evento.preventDefault();
          ultimo.focus();
        } else if (!evento.shiftKey && document.activeElement === ultimo) {
          evento.preventDefault();
          primeiro.focus();
        }
      }
    }

    // EVENTOS DO MODAL

    botaoX.addEventListener("click", fechar);
    botaoFechar.addEventListener("click", fechar);

    overlay.addEventListener("click", (evento) => {
      if (evento.target === overlay) {
        fechar();
      }
    });

    document.addEventListener("keydown", tratarTeclado);

    modalAberto = { fechar };

    botaoX.focus();
  }

  // CRIAR LINHA DA TABELA

  function criarLinha(lote) {
    const linha = document.createElement("tr");

    // ESTILO IGUAL À TABELA DE TODOS OS LOTES

    linha.className = "border-b border-gray-300 bg-[#e9ebee] text-text";

    // PRODUTO

    criarCelula(linha, lote.produtoNome || "Produto não encontrado");

    // CÓDIGO DO LOTE

    criarCelula(linha, lote.codigo || "-");

    // QUANTIDADE DISPONÍVEL

    criarCelula(
      linha,
      formatarQuantidade(lote.quantidadeDisponivel, lote.unidadeMedida),
      true,
    );

    // VALIDADE

    criarCelula(linha, formatarData(lote.dataValidade), true);

    // AÇÕES

    const celulaAcoes = document.createElement("td");

    celulaAcoes.className = "px-3 py-3 text-center";

    const grupo = document.createElement("div");

    grupo.className = "flex items-center justify-center";

    // BOTÃO DE DETALHES

    const detalhes = document.createElement("button");

    detalhes.type = "button";
    detalhes.textContent = "Detalhes";

    detalhes.className =
      "cursor-pointer rounded px-3 py-1 " +
      "text-xs font-semibold text-primary-dark " +
      "hover:bg-primary-light";

    detalhes.setAttribute(
      "aria-label",
      `Ver detalhes do lote ${lote.codigo || ""}`,
    );

    detalhes.addEventListener("click", () => {
      abrirModalDetalhes(lote, detalhes);
    });

    grupo.appendChild(detalhes);

    celulaAcoes.appendChild(grupo);
    linha.appendChild(celulaAcoes);

    return linha;
  }

  // NORMALIZAR TEXTO DA PESQUISA

  // IGNORA ACENTOS E LETRAS MAIÚSCULAS

  function normalizarTexto(texto) {
    return String(texto || "")
      .normalize("NFD")
      .replace(/[\u0300-\u036f]/g, "")
      .toLowerCase()
      .trim();
  }

  // PESQUISAR PRODUTOS PELO NOME

  function filtrarProdutos() {
    // EVITAR ALTERAR A TABELA DURANTE O CARREGAMENTO

    if (carregando || erroCarregamento) {
      return;
    }

    // RECUPERAR O TEXTO PESQUISADO

    const pesquisa = normalizarTexto(campoPesquisa.value);

    // FILTRAR OS LOTES RETORNADOS PELA API

    const lotesFiltrados = lotesCarregados.filter((lote) => {
      const nomeProduto = normalizarTexto(lote.produtoNome);

      return nomeProduto.includes(pesquisa);
    });

    // LIMPAR TABELA

    tabela.replaceChildren();

    // NENHUM RESULTADO

    if (lotesFiltrados.length === 0) {
      mostrarMensagemTabela(
        pesquisa
          ? "Nenhum produto encontrado com esse nome neste período."
          : "Nenhum lote encontrado para este período.",
      );

      return;
    }

    // PREENCHER TABELA COM RESULTADOS FILTRADOS

    const fragmento = document.createDocumentFragment();

    lotesFiltrados.forEach((lote) => {
      fragmento.appendChild(criarLinha(lote));
    });

    tabela.appendChild(fragmento);
  }

  // EVENTO DA BARRA DE PESQUISA

  // FILTRA AUTOMATICAMENTE ENQUANTO DIGITA

  campoPesquisa.addEventListener("input", filtrarProdutos);

  // ATUALIZAR BOTÕES DOS FILTROS

  function selecionarFiltro(botaoAtivo) {
    botoes.forEach((botao) => {
      botao.classList.remove("bg-primary-dark", "text-white");

      botao.classList.add("bg-[#e9ebee]", "text-text");

      botao.setAttribute("aria-pressed", "false");
    });

    botaoAtivo.classList.remove("bg-[#e9ebee]", "text-text");

    botaoAtivo.classList.add("bg-primary-dark", "text-white");

    botaoAtivo.setAttribute("aria-pressed", "true");
  }

  // CARREGAR LOTES AUTENTICADOS

  async function carregarLotes(dias) {
    // CANCELAR REQUISIÇÃO ANTERIOR

    if (requisicaoAtual) {
      requisicaoAtual.abort();
    }

    const controlador = new AbortController();

    requisicaoAtual = controlador;

    // FECHAR MODAL ABERTO

    if (modalAberto) {
      modalAberto.fechar();
    }

    // LIMPAR DADOS ANTERIORES

    lotesCarregados = [];

    carregando = true;
    erroCarregamento = false;

    limparErro();

    mostrarMensagemTabela("Carregando lotes...");

    try {
      // CONSULTAR API COM TOKEN DE AUTENTICAÇÃO

      const resposta = await requisicaoAutenticada(
        `${API_URL}?dias=${dias}`,
        controlador.signal,
      );

      const dados = await lerResposta(resposta);

      // IGNORAR REQUISIÇÕES CANCELADAS

      if (controlador.signal.aborted) {
        return;
      }

      // VALIDAR DADOS

      if (!Array.isArray(dados.lotes)) {
        throw new Error("Resposta inválida do servidor.");
      }

      // ARMAZENAR OS LOTES DO PERÍODO

      lotesCarregados = dados.lotes;

      // FINALIZAR CARREGAMENTO

      carregando = false;

      // APLICAR PESQUISA ATUAL

      filtrarProdutos();
    } catch (erro) {
      // IGNORAR REQUISIÇÃO CANCELADA

      if (erro.name === "AbortError" || controlador.signal.aborted) {
        return;
      }

      console.error("Erro ao carregar próximos vencimentos:", erro);

      lotesCarregados = [];
      erroCarregamento = true;

      mostrarMensagemTabela("Não foi possível carregar os lotes.");

      mostrarErro(erro.message);
    } finally {
      // SOMENTE A REQUISIÇÃO MAIS RECENTE
      // PODE ALTERAR O ESTADO DO CARREGAMENTO

      if (requisicaoAtual === controlador) {
        carregando = false;
        requisicaoAtual = null;
      }
    }
  }

  // EVENTOS DOS CINCO FILTROS

  botoes.forEach((botao) => {
    botao.addEventListener("click", () => {
      const dias = Number(botao.dataset.dias);

      // ACEITAR 1, 3, 7, 15 E 30 DIAS

      if (!PERIODOS_VALIDOS.includes(dias)) {
        return;
      }

      selecionarFiltro(botao);

      carregarLotes(dias);
    });
  });

  // INICIALIZAÇÃO

  // VERIFICAR SESSÃO

  if (!obterToken()) {
    encerrarAcesso();
    return;
  }

  // INICIAR COM FILTRO HOJE

  const filtroInicial = document.querySelector('[data-dias="1"]');

  if (filtroInicial) {
    selecionarFiltro(filtroInicial);

    carregarLotes(1);
  }
});
