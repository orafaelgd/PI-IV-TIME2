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

public class ServidorSocket implements Runnable {

  private final RepositorioVencimentos repositorio;
  private static final int PORTA = 8081;

  @Override
  public void run() {
    try (ServerSocket servidor = new ServerSocket(PORTA)) {

      System.out.println("Servidor iniciado na porta " + PORTA);

      while (true) {
        Socket cliente = servidor.accept();

        Thread thread = new Thread(() -> atenderCliente(cliente));
        thread.start();
      }

    } catch (Exception erro) {
      erro.printStackTrace();
    }
  }

  public ServidorSocket(RepositorioVencimentos repo) {
    repositorio = repo;
  }

  private void atenderCliente(Socket cliente) {
    try (Socket conexao = cliente) {

      BufferedReader entrada = new BufferedReader(
          new InputStreamReader(conexao.getInputStream()));

      OutputStream saida = conexao.getOutputStream();

      // Lê a primeira linha da requisição HTTP
      String primeiraLinha = entrada.readLine();

      if (primeiraLinha == null || primeiraLinha.isBlank()) {
        RespostaHttp.enviar(saida, 400, "Bad Request",
            "{\"erro\":\"Requisicao invalida\"}");
        return;
      }

      System.out.println("Requisição: " + primeiraLinha);

      // Lê os demais cabeçalhos HTTP
      String origem = null;
      String linha;

      while ((linha = entrada.readLine()) != null && !linha.isEmpty()) {
        System.out.println(linha);

        if (linha.regionMatches(true, 0, "Origin:", 0, 7)) {
          origem = linha.substring(7).trim();
        }
      }

      // Separa a primeira linha em partes
      String[] partes = primeiraLinha.split(" ");

      if (partes.length != 3) {
        RespostaHttp.enviar(saida, 400, "Bad Request",
            "{\"erro\":\"Requisicao invalida\"}", origem);
        return;
      }

      String metodo = partes[0];
      String recurso = partes[1];

      // Remove os parâmetros da URL
      String caminho = recurso.split("\\?", 2)[0];

      // Verifica o método HTTP
      if (!metodo.equals("GET")) {
        RespostaHttp.enviar(saida, 405, "Method Not Allowed",
            "{\"erro\":\"Metodo nao permitido\"}", origem);
        return;
      }

      // Verifica a rota solicitada
      if (!caminho.equals("/api/lotes/proximos-vencimento")) {
        RespostaHttp.enviar(saida, 404, "Not Found",
            "{\"erro\":\"Rota nao encontrada\"}", origem);
        return;
      }

      try {
        int dias = ParametroDias.obter(recurso);

        LocalDate hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo"));

        List<LoteProximoVencimento> lotes = repositorio.buscar(dias, hoje);

        String json = JsonVencimentos.gerar(lotes, hoje, dias);

        RespostaHttp.enviar(
            saida,
            200,
            "OK",
            json, origem);

      } catch (IllegalArgumentException erro) {

        RespostaHttp.enviar(
            saida,
            400,
            "Bad Request",
            "{\"erro\":\"O parametro dias deve ser 1, 3 ou 7\"}", origem);

      } catch (Exception erro) {
        erro.printStackTrace();

        RespostaHttp.enviar(
            saida,
            500,
            "Internal Server Error",
            "{\"erro\":\"Erro interno ao consultar lotes\"}", origem);
      }

    } catch (Exception erro) {
      erro.printStackTrace();
    }
  }

}
