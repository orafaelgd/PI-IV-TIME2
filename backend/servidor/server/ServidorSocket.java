// Author: Rafael Gaudencio Dias
// Ra: 25018953

package server;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDate;

import helper.RespostaHttp;
import model.LoteProximoVencimento;
import model.RepositorioVencimentos;
import helper.ParametroDias;
import helper.JsonVencimentos;

import java.time.ZoneId;
import java.util.List;
import service.SessaoService;

import org.bson.Document;
import org.bson.types.ObjectId;

/**
 * Recebe consultas de próximos vencimentos por conexões TCP e interpreta
 * as requisições HTTP manualmente. A sessão identifica o usuário; o repositório
 * busca seus lotes, e os helpers montam o JSON e a resposta HTTP.
 */
public class ServidorSocket implements Runnable {

  private final RepositorioVencimentos repositorio;
  private final SessaoService sessaoService;

  private final int porta;

  @Override
  public void run() {
    // Mantém a porta aberta enquanto o servidor estiver em execução.
    // O try-with-resources fecha o socket de escuta se esse bloco terminar.
    try (ServerSocket servidor = new ServerSocket(porta)) {

      System.out.println("Servidor iniciado na porta " + porta);

      while (true) {
        // accept() aguarda uma conexão. Cada cliente é atendido em outra
        // thread, permitindo que o laço continue recebendo novas conexões.
        Socket cliente = servidor.accept();

        Thread thread = new Thread(() -> atenderCliente(cliente));
        thread.start();
      }

    } catch (Exception erro) {
      erro.printStackTrace();
    }
  }

  // As dependências são fornecidas pela aplicação para compartilhar o acesso
  // aos dados e a autenticação; este servidor não cria uma conexão própria ao banco.
  public ServidorSocket(
      RepositorioVencimentos repo,
      SessaoService sessaoServ,
      int porta) {

    this.repositorio = repo;
    this.sessaoService = sessaoServ;
    this.porta = porta;
  }

  /**
   * Processa uma requisição e encerra a conexão ao final, inclusive em caso
   * de erro. A entrada contém o HTTP enviado pelo cliente, e RespostaHttp
   * escreve os cabeçalhos e o corpo JSON na saída da conexão.
   */
  private void atenderCliente(Socket cliente) {
    try (Socket conexao = cliente) {

      BufferedReader entrada = new BufferedReader(
          new InputStreamReader(conexao.getInputStream()));

      OutputStream saida = conexao.getOutputStream();

      // A primeira linha informa método, recurso e versão do protocolo.
      // Uma conexão sem essa linha não pode ser tratada como uma requisição válida.
      String primeiraLinha = entrada.readLine();

      if (primeiraLinha == null || primeiraLinha.isBlank()) {
        RespostaHttp.enviar(saida, 400, "Bad Request",
            "{\"erro\":\"Requisicao invalida\"}");
        return;
      }

      System.out.println("Requisição: " + primeiraLinha);

      // Os cabeçalhos terminam em uma linha vazia. Origin é usado na resposta
      // CORS, e Authorization fornece o token de sessão para a autenticação.

      String origem = null;
      String autorizacao = null;
      String linha;

      while ((linha = entrada.readLine()) != null && !linha.isEmpty()) {

        if (linha.regionMatches(true, 0, "Origin:", 0, 7)) {
          origem = linha.substring(7).trim();
        }

        if (linha.regionMatches(true, 0, "Authorization:", 0, 14)) {
          autorizacao = linha.substring(14).trim();
        }
      }

      // Exemplo: "GET /api/lotes/proximos-vencimento?dias=7 HTTP/1.1".
      // A rota é verificada sem a query, mas o recurso completo é preservado
      // para que ParametroDias interprete o período solicitado mais adiante.
      String[] partes = primeiraLinha.split(" ");

      if (partes.length != 3) {
        RespostaHttp.enviar(saida, 400, "Bad Request",
            "{\"erro\":\"Requisicao invalida\"}", origem);
        return;
      }

      String metodo = partes[0];
      String recurso = partes[1];

      String caminho = recurso.split("\\?", 2)[0];

      // Este servidor atende somente a consulta de próximos vencimentos.
      if (!caminho.equals("/api/lotes/proximos-vencimento")) {
        RespostaHttp.enviar(saida, 404, "Not Found",
            "{\"erro\":\"Rota nao encontrada\"}", origem);
        return;
      }

      // O navegador pode enviar OPTIONS antes do GET para verificar o CORS.
      // Essa verificação é respondida antes de exigir uma sessão de usuário.
      if (metodo.equals("OPTIONS")) {
        RespostaHttp.enviar(
            saida, 200, "OK", "{}", origem);
        return;
      }

      // Permite somente GET para consultar os lotes
      if (!metodo.equals("GET")) {
        RespostaHttp.enviar(saida, 405, "Method Not Allowed",
            "{\"erro\":\"Metodo nao permitido\"}", origem);
        return;
      }

      try {

        // Resolve o cabeçalho Bearer para um usuário com sessão válida.
        // Sem autenticação, o fluxo termina antes de consultar os lotes.
        String token = SessaoService.extrairToken(autorizacao);

        Document usuario = sessaoService.autenticarToken(token);

        // Rejeita sessões inválidas ou ausentes
        if (usuario == null) {
          RespostaHttp.enviar(
              saida,
              401,
              "Unauthorized",
              "{\"erro\":\"Sessao invalida ou expirada\"}",
              origem);
          return;
        }

        // O identificador limita a busca aos dados da conta autenticada.
        // Um documento de usuário sem esse campo não pode autorizar a consulta.
        ObjectId usuarioId = usuario.getObjectId("_id");

        if (usuarioId == null) {
          RespostaHttp.enviar(
              saida,
              401,
              "Unauthorized",
              "{\"erro\":\"Usuario invalido\"}",
              origem);
          return;
        }

        // Usa o período validado e a data atual de São Paulo como base da busca.
        // O repositório aplica os filtros; o serializador transforma o resultado
        // em JSON antes de enviá-lo com status HTTP 200.
        int dias = ParametroDias.obter(recurso);

        LocalDate hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo"));

        List<LoteProximoVencimento> lotes = repositorio.buscar(dias, hoje, usuarioId);

        String json = JsonVencimentos.gerar(lotes, hoje, dias);

        RespostaHttp.enviar(
            saida,
            200,
            "OK",
            json, origem);

      } catch (IllegalArgumentException erro) {

        // Erros de interpretação ou validação dos argumentos retornam HTTP 400.
        RespostaHttp.enviar(
            saida,
            400,
            "Bad Request",
            "{\"erro\":\"O parametro dias deve ser 1, 3 ou 7\"}", origem);

      } catch (Exception erro) {
        // Falhas inesperadas da consulta são registradas no terminal e retornam
        // uma mensagem genérica ao cliente, sem expor detalhes internos no JSON.
        erro.printStackTrace();

        RespostaHttp.enviar(
            saida,
            500,
            "Internal Server Error",
            "{\"erro\":\"Erro interno ao consultar lotes\"}", origem);
      }

    } catch (Exception erro) {
      // Falhas na leitura ou escrita da conexão também ficam no terminal.
      // O try-with-resources ainda garante o fechamento do socket do cliente.
      erro.printStackTrace();
    }
  }

}
