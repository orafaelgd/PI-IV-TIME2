// DASHBOARD INICIAL

document.addEventListener("DOMContentLoaded", async () => {
  // CONFIGURAÇÕES

  const API_URL = "http://localhost:8080";
  const API_VENCIMENTOS = "http://localhost:8081/api/lotes/proximos-vencimento";
  const PAGINA_LOGIN = "login-usuario.html";

  // ELEMENTOS DA TELA

  const elementoNome = document.querySelector("#nome-usuario");

  const totalProdutos = document.querySelector("#total-produtos");

  const lotesProximos = document.querySelector("#lotes-proximos");

  const totalDesperdicios = document.querySelector("#total-desperdicios");

  const tabelaVencimentos = document.querySelector(
    "#tabela-proximos-vencimentos",
  );

  const elementoGrafico = document.querySelector("#area-chart");

  // AUTENTICAÇÃO

  // Recupera o token salvo no login
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

  // Envia o token ao backend
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
      cache: "no-store",
    });

    // Sessão inválida ou expirada
    if (resposta.status === 401) {
      encerrarAcesso();

      throw new Error("Sessão expirada. Faça login novamente.");
    }

    if (resposta.status === 403) {
      throw new Error("Você não tem permissão para esta operação.");
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
          mensagem: texto,
        };
      }
    }

    if (!resposta.ok) {
      throw new Error(dados.mensagem || `Erro HTTP ${resposta.status}`);
    }

    return dados;
  }

  // FORMATAR QUANTIDADE

  function formatarQuantidade(valor) {
    const numero = Number(valor);

    if (valor == null || valor === "" || !Number.isFinite(numero)) {
      return "-";
    }

    return numero.toLocaleString("pt-BR", {
      maximumFractionDigits: 3,
    });
  }

  // LIMPAR TABELA

  // Exibe uma mensagem quando não há registros
  function mostrarMensagemTabela(texto) {
    tabelaVencimentos.replaceChildren();

    const linha = document.createElement("tr");

    const coluna = document.createElement("td");

    coluna.colSpan = 3;

    coluna.className = "px-3 py-4 text-center text-text-secondary";

    coluna.textContent = texto;

    linha.appendChild(coluna);

    tabelaVencimentos.appendChild(linha);
  }

  // CARREGAR NOME DO USUÁRIO

  async function carregarNomeUsuario() {
    try {
      const resposta = await requisicaoAutenticada(`${API_URL}/api/usuario`);

      const usuario = await lerResposta(resposta);

      elementoNome.textContent = usuario.nome || "Usuário";
    } catch (erro) {
      console.error("Erro ao carregar usuário:", erro);

      elementoNome.textContent = "Usuário indisponível";
    }
  }

  // CARREGAR INDICADORES

  // Busca o resumo da conta autenticada
  async function carregarIndicadores() {
    try {
      const resposta = await requisicaoAutenticada(
        `${API_URL}/api/dashboard/resumo`,
      );

      const dados = await lerResposta(resposta);

      // TOTAL DE PRODUTOS

      totalProdutos.textContent = dados.totalProdutos ?? 0;

      // LOTES PRÓXIMOS DO VENCIMENTO

      lotesProximos.textContent = dados.lotesProximosVencimento ?? 0;

      // TABELA DE PRÓXIMOS VENCIMENTOS

      //   carregarProximosVencimentos(dados.proximosVencimentos || []);
    } catch (erro) {
      console.error("Erro ao carregar Dashboard:", erro);

      totalProdutos.textContent = "--";

      lotesProximos.textContent = "--";

      //   mostrarMensagemTabela("Não foi possível carregar os vencimentos.");
    }
  }

  // CARREGAR PRÓXIMOS VENCIMENTOS

  // Usa os lotes já filtrados pelo DashboardService
  function renderizarProximosVencimentos(lotes, dataInicio) {
    tabelaVencimentos.replaceChildren();

    if (!Array.isArray(lotes) || lotes.length === 0) {
      mostrarMensagemTabela("Nenhum lote próximo do vencimento.");

      return;
    }

    // CRIAR LINHAS

    lotes.forEach((lote) => {
      const linha = document.createElement("tr");

      linha.className = "border-b border-gray-200";

      // NOME DO PRODUTO

      const colunaProduto = document.createElement("td");

      colunaProduto.className = "px-3 py-3";

      colunaProduto.textContent = lote.produtoNome || "Produto não encontrado";

      // QUANTIDADE DISPONÍVEL

      const colunaQuantidade = document.createElement("td");

      colunaQuantidade.className = "px-3 py-3";

      const quantidade = formatarQuantidade(lote.quantidadeDisponivel);

      const unidade = lote.unidadeMedida || "";

      colunaQuantidade.textContent = `${quantidade} ${unidade}`.trim();

      // DATA DE VALIDADE

      const colunaValidade = document.createElement("td");

      colunaValidade.className = "px-3 py-3";

      const dataValidade = lote.dataValidade;

      const datasValidas =
        /^\d{4}-\d{2}-\d{2}$/.test(dataValidade) &&
        /^\d{4}-\d{2}-\d{2}$/.test(dataInicio);

      const diasRestantes = datasValidas
        ? Math.round(
            (Date.parse(dataValidade) - Date.parse(dataInicio)) / 86400000,
          )
        : NaN;

      if (diasRestantes === 0) {
        colunaValidade.textContent = "Vence hoje";
      } else if (diasRestantes === 1) {
        colunaValidade.textContent = "Vence amanhã";
      } else if (Number.isFinite(diasRestantes)) {
        colunaValidade.textContent = `Vence em ${diasRestantes} dias`;
      } else {
        colunaValidade.textContent = "Validade não informada";
      }

      // Destaca os lotes que vencem primeiro
      if (Number.isFinite(diasRestantes) && diasRestantes <= 1) {
        colunaValidade.classList.add("text-danger");
      }

      linha.append(colunaProduto, colunaQuantidade, colunaValidade);

      tabelaVencimentos.appendChild(linha);
    });
  }

  // CONFIGURAR GRÁFICO

  // Mantém o visual original do ApexCharts
  const options = {
    chart: {
      type: "area",
      height: 280,

      toolbar: {
        show: false,
      },
    },

    series: [
      {
        name: "Ocorrências de desperdício",
        data: [],
      },
    ],

    xaxis: {
      categories: [],
    },

    colors: ["#1F6F78"],

    stroke: {
      curve: "smooth",
      width: 3,
    },

    dataLabels: {
      enabled: false,
    },

    noData: {
      text: "Aguardando integração dos relatórios",
    },
  };

  // INICIALIZAR GRÁFICO

  // Não consulta relatórios globais de outros usuários
  async function iniciarGrafico() {
    if (!elementoGrafico || typeof ApexCharts === "undefined") {
      console.error("Biblioteca ou elemento do gráfico não encontrado.");

      return;
    }

    try {
      const grafico = new ApexCharts(elementoGrafico, options);

      await grafico.render();
    } catch (erro) {
      console.error("Erro ao carregar gráfico:", erro);
    }
  }

  // INICIALIZAR DASHBOARD

  async function iniciar() {
    // Verifica o token antes de carregar a página
    if (!obterToken()) {
      encerrarAcesso();

      return;
    }

    // Não usa os relatórios globais enquanto
    // suas rotas não estiverem protegidas
    totalDesperdicios.textContent = "--";

    await Promise.all([
      carregarNomeUsuario(),
      carregarIndicadores(),
      buscarProximosVencimentos(),
      iniciarGrafico(),
    ]);
  }

  iniciar();

  async function buscarProximosVencimentos() {
    try {
      const resposta = await requisicaoAutenticada(`${API_VENCIMENTOS}?dias=7`);

      const dados = await lerResposta(resposta);

      if (!Array.isArray(dados.lotes) || typeof dados.dataInicio !== "string") {
        throw new Error("Resposta inválida do servidor");
      }

      lotesProximos.textContent = dados.total;

      renderizarProximosVencimentos(dados.lotes, dados.dataInicio);
    } catch (erro) {
      console.error("Erro ao consultar vencimentos:", erro);

      lotesProximos.textContent = "--";

      mostrarMensagemTabela("Não foi possível carregar os vencimentos.");
    }
  }
});
