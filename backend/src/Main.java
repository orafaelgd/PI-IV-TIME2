
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.IndexOptions;

import org.bson.Document;

// CONTROLLERS

import controller.LoginHandler;
import controller.DashboardHandler;
import controller.LotesHandler;
import controller.MovimentacoesHandler;
import controller.PerfilUsuarioHandler;
import controller.ProdutosHandler;
import controller.RelatoriosHandler;
import controller.UsuariosHandler;
import controller.HistoricoDespHandler;
import controller.ProximosVencimentoHandler;

// SERVIDOR SOCKET

import model.RepositorioVencimentos;
import server.ServidorSocket;

import service.SessaoService;

public class Main {

        public static void main(String[] args) throws Exception {

                // CONEXÃO COM O MONGODB

                MongoClient cliente = MongoDBConnection.conectar();

                try {

                        // SELECIONAR BANCO DE DADOS

                        MongoDatabase banco = cliente.getDatabase(
                                        "desperdicio_alimentos");

                        // VERIFICAR CONEXÃO

                        banco.runCommand(
                                        new Document("ping", 1));

                        // COLEÇÕES DO MONGODB

                        MongoCollection<Document> usuarios = banco.getCollection("users");

                        MongoCollection<Document> sessoes = banco.getCollection("sessoes");

                        MongoCollection<Document> produtos = banco.getCollection("itens");

                        MongoCollection<Document> lotes = banco.getCollection("lotes");

                        MongoCollection<Document> movimentacoes = banco.getCollection("movimentacoes");

                        MongoCollection<Document> categorias = banco.getCollection("categorias");

                        // ÍNDICES DE USUÁRIOS

                        // Impede e-mails duplicados

                        usuarios.createIndex(
                                        Indexes.ascending("email"),
                                        new IndexOptions().unique(true));

                        // ÍNDICES DE SESSÕES

                        // Garante tokens únicos

                        sessoes.createIndex(
                                        Indexes.ascending("token"),
                                        new IndexOptions().unique(true));

                        // Remove automaticamente sessões expiradas

                        sessoes.createIndex(
                                        Indexes.ascending("expiraEm"),
                                        new IndexOptions().expireAfter(
                                                        0L,
                                                        TimeUnit.SECONDS));

                        // ÍNDICE DE MOVIMENTAÇÕES

                        // Impede registrar duas movimentações
                        // com a mesma chave de idempotência

                        // Mantém compatibilidade com registros
                        // antigos que não possuem idempotencyKey

                        movimentacoes.createIndex(
                                        Indexes.ascending("idempotencyKey"),
                                        new IndexOptions()
                                                        .unique(true)
                                                        .partialFilterExpression(
                                                                        new Document(
                                                                                        "idempotencyKey",
                                                                                        new Document("$exists",
                                                                                                        true))));

                        // SERVIDOR HTTP

                        HttpServer servidor = HttpServer.create(
                                        new InetSocketAddress(
                                                        "127.0.0.1",
                                                        8080),
                                        0);

                        // CADASTRO DE USUÁRIOS

                        servidor.createContext(
                                        "/api/usuarios",
                                        new UsuariosHandler(usuarios));

                        // LOGIN E SESSÕES

                        LoginHandler loginHandler = new LoginHandler(
                                        usuarios,
                                        sessoes);

                        servidor.createContext(
                                        "/api/login",
                                        loginHandler);

                        servidor.createContext(
                                        "/api/logout",
                                        loginHandler);

                        servidor.createContext(
                                        "/api/usuario",
                                        loginHandler);

                        // PERFIL DO USUÁRIO

                        // Consulta e atualiza os dados do usuário
                        // identificado pelo token de autenticação

                        PerfilUsuarioHandler perfilHandler = new PerfilUsuarioHandler(
                                        usuarios,
                                        sessoes);

                        servidor.createContext(
                                        "/api/perfil",
                                        perfilHandler);

                        // DASHBOARD

                        // Consulta somente os dados da conta logada

                        DashboardHandler dashboardHandler = new DashboardHandler(
                                        produtos,
                                        lotes,
                                        usuarios,
                                        sessoes);

                        servidor.createContext(
                                        "/api/dashboard/resumo",
                                        dashboardHandler);

                        // RELATÓRIOS

                        // Consulta os relatórios e indicadores
                        // pertencentes ao usuário autenticado

                        RelatoriosHandler relatoriosHandler = new RelatoriosHandler(
                                        produtos,
                                        lotes,
                                        movimentacoes,
                                        usuarios,
                                        sessoes);

                        servidor.createContext(
                                        "/api/relatorios/",
                                        relatoriosHandler);

                        // PRODUTOS E CATEGORIAS

                        // Gerencia os produtos e categorias
                        // associados ao usuário autenticado

                        ProdutosHandler produtosHandler = new ProdutosHandler(
                                        produtos,
                                        categorias,
                                        lotes,
                                        movimentacoes,
                                        usuarios,
                                        sessoes);

                        servidor.createContext(
                                        "/api/produtos",
                                        produtosHandler);

                        servidor.createContext(
                                        "/api/categorias",
                                        produtosHandler);

                        // LOTES

                        // Gerencia os lotes da conta autenticada

                        LotesHandler lotesHandler = new LotesHandler(
                                        lotes,
                                        produtos,
                                        movimentacoes,
                                        usuarios,
                                        sessoes);

                        servidor.createContext(
                                        "/api/lotes",
                                        lotesHandler);

                        // PRÓXIMOS VENCIMENTOS

                        // NOVO CONTROLLER:
                        // Recebe a requisição e valida a sessão.
                        //
                        // NOVO SERVICE:
                        // Consulta os lotes e produtos do usuário
                        // dentro do período solicitado.

                        ProximosVencimentoHandler vencimentosHandler = new ProximosVencimentoHandler(
                                        lotes,
                                        produtos,
                                        usuarios,
                                        sessoes);

                        servidor.createContext(
                                        "/api/lotes/proximos-vencimento",
                                        vencimentosHandler);

                        // MOVIMENTAÇÕES

                        // CONTROLLER:
                        // Recebe a requisição e autentica o usuário.
                        //
                        // SERVICE:
                        // Valida a saída, atualiza o estoque
                        // e registra a movimentação no MongoDB.

                        MovimentacoesHandler movimentacoesHandler = new MovimentacoesHandler(
                                        cliente,
                                        lotes,
                                        movimentacoes,
                                        produtos,
                                        usuarios,
                                        sessoes);

                        servidor.createContext(
                                        "/api/movimentacoes",
                                        movimentacoesHandler);

                        // HISTÓRICO DE DESPERDÍCIO

                        // Retorna somente movimentações associadas
                        // aos lotes e produtos do usuário autenticado

                        HistoricoDespHandler historicoHandler = new HistoricoDespHandler(banco);

                        servidor.createContext(
                                        "/api/saidas",
                                        historicoHandler);

                        // REPOSITÓRIO DO SERVIDOR SOCKET

                        // Compartilha as coleções de lotes e produtos
                        // com o serviço de vencimentos via Socket

                        RepositorioVencimentos repositorio = new RepositorioVencimentos(
                                        lotes,
                                        produtos);

                        SessaoService sessaoService = new SessaoService(usuarios, sessoes);

                        // THREAD DO SERVIDOR SOCKET

                        // Mantém o servidor Socket independente
                        // do servidor HTTP da porta 8080

                        Thread threadServidor = new Thread(
                                        new ServidorSocket(repositorio, sessaoService),
                                        "servidor-vencimentos");

                        // ENCERRAMENTO SEGURO

                        // Fecha o servidor HTTP e o MongoDB
                        // quando a aplicação for encerrada

                        Runtime.getRuntime().addShutdownHook(
                                        new Thread(() -> {

                                                servidor.stop(0);

                                                cliente.close();

                                        }));

                        // INICIALIZAR SERVIDOR HTTP

                        servidor.start();

                        // INICIALIZAR SERVIDOR SOCKET

                        threadServidor.start();

                        // MENSAGENS DO TERMINAL

                        System.out.println(
                                        "Servidor HTTP iniciado na porta 8080");

                        System.out.println(
                                        "MongoDB conectado com sucesso!");

                        System.out.println(
                                        "Servidor Socket inicializado!");

                        System.out.println(
                                        "Rota de próximos vencimentos ativada!");

                } catch (Exception erro) {

                        // ENCERRAR CONEXÃO SE A INICIALIZAÇÃO FALHAR

                        cliente.close();

                        throw erro;
                }
        }
}
