
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
import controller.PerfilUsuarioHandler;
import controller.ProdutosHandler;
import controller.RelatoriosHandler;
import controller.UsuariosHandler;
import controller.HistoricoDespHandler;
import controller.RecuperacaoSenhaHandler;

// SERVIDOR SOCKET

import model.RepositorioVencimentos;
import server.ServidorSocket;

public class Main {

    public static void main(String[] args) throws Exception {

        MongoClient cliente = MongoDBConnection.conectar();

        try {

            // CONEXÃO COM O BANCO

            MongoDatabase banco = cliente.getDatabase(
                "desperdicio_alimentos"
            );

            banco.runCommand(
                new Document("ping", 1)
            );

            // COLEÇÕES DO MONGODB

            MongoCollection<Document> usuarios =
                banco.getCollection("users");

            MongoCollection<Document> sessoes =
                banco.getCollection("sessoes");

            MongoCollection<Document> recuperacoesSenha =
                banco.getCollection("recuperacoes_senha");

            MongoCollection<Document> produtos =
                banco.getCollection("itens");

            MongoCollection<Document> lotes =
                banco.getCollection("lotes");

            MongoCollection<Document> movimentacoes =
                banco.getCollection("movimentacoes");

            MongoCollection<Document> categorias =
                banco.getCollection("categorias");

            // ÍNDICES DE USUÁRIOS E SESSÕES

            usuarios.createIndex(
                Indexes.ascending("email"),
                new IndexOptions().unique(true)
            );

            sessoes.createIndex(
                Indexes.ascending("token"),
                new IndexOptions().unique(true)
            );

            sessoes.createIndex(
                Indexes.ascending("expiraEm"),
                new IndexOptions().expireAfter(
                    0L,
                    TimeUnit.SECONDS
                )
            );

            // ÍNDICE DE RECUPERAÇÃO DE SENHA

            recuperacoesSenha.createIndex(
                Indexes.ascending("expiraEm"),
                new IndexOptions().expireAfter(
                    0L,
                    TimeUnit.SECONDS
                )
            );

            // SERVIDOR HTTP

            HttpServer servidor = HttpServer.create(
                new InetSocketAddress(
                    "127.0.0.1",
                    8080
                ),
                0
            );

            // CADASTRO DE USUÁRIOS

            servidor.createContext(
                "/api/usuarios",
                new UsuariosHandler(usuarios)
            );

            // LOGIN E SESSÕES

            LoginHandler loginHandler =
                new LoginHandler(
                    usuarios,
                    sessoes
                );

            servidor.createContext(
                "/api/login",
                loginHandler
            );

            servidor.createContext(
                "/api/logout",
                loginHandler
            );

            servidor.createContext(
                "/api/usuario",
                loginHandler
            );

            // RECUPERAÇÃO E REDEFINIÇÃO DE SENHA

            RecuperacaoSenhaHandler recuperacaoSenhaHandler =
                new RecuperacaoSenhaHandler(
                    usuarios,
                    sessoes,
                    recuperacoesSenha
                );

            servidor.createContext(
                "/api/senha/",
                recuperacaoSenhaHandler
            );

            // PERFIL DO USUÁRIO

            servidor.createContext(
                "/api/perfil",
                new PerfilUsuarioHandler(
                    usuarios,
                    sessoes
                )
            );

            // DASHBOARD

            DashboardHandler dashboardHandler =
                new DashboardHandler(
                    produtos,
                    lotes,
                    usuarios,
                    sessoes
                );

            servidor.createContext(
                "/api/dashboard/resumo",
                dashboardHandler
            );

            // RELATÓRIOS

            RelatoriosHandler relatoriosHandler =
                new RelatoriosHandler(
                    produtos,
                    lotes,
                    movimentacoes,
                    usuarios,
                    sessoes
                );

            servidor.createContext(
                "/api/relatorios/",
                relatoriosHandler
            );

            // PRODUTOS E CATEGORIAS

            ProdutosHandler produtosHandler =
                new ProdutosHandler(
                    produtos,
                    categorias,
                    lotes,
                    movimentacoes,
                    usuarios,
                    sessoes
                );

            servidor.createContext(
                "/api/produtos",
                produtosHandler
            );

            servidor.createContext(
                "/api/categorias",
                produtosHandler
            );

            // LOTES

            LotesHandler lotesHandler =
                new LotesHandler(
                    lotes,
                    produtos,
                    movimentacoes,
                    usuarios,
                    sessoes
                );

            servidor.createContext(
                "/api/lotes",
                lotesHandler
            );

            // MOVIMENTAÇÕES

            servidor.createContext(
                "/api/movimentacoes",
                new MovimentacoesHandler(
                    cliente,
                    lotes,
                    movimentacoes
                )
            );

            // HISTÓRICO DE DESPERDÍCIO

            HistoricoDespHandler historicoHandler =
                new HistoricoDespHandler(banco);

            servidor.createContext(
                "/api/saidas",
                historicoHandler
            );

            // REPOSITÓRIO DO SERVIDOR SOCKET

            RepositorioVencimentos repositorio =
                new RepositorioVencimentos(
                    lotes,
                    produtos
                );

            // THREAD DO SERVIDOR SOCKET

            Thread threadServidor = new Thread(
                new ServidorSocket(repositorio),
                "servidor-vencimentos"
            );

            // ENCERRAMENTO SEGURO

            Runtime.getRuntime().addShutdownHook(
                new Thread(() -> {

                    servidor.stop(0);
                    cliente.close();

                })
            );

            // INICIALIZAÇÃO DOS SERVIDORES

            servidor.start();

            threadServidor.start();

            System.out.println(
                "Servidor HTTP iniciado na porta 8080"
            );

            System.out.println(
                "MongoDB conectado com sucesso!"
            );

            System.out.println(
                "Servidor Socket inicializado!"
            );

        } catch (Exception erro) {

            cliente.close();
            throw erro;
        }
    }
}
