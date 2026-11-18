
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

            MongoCollection<Document> produtos =
                banco.getCollection("itens");

            MongoCollection<Document> lotes =
                banco.getCollection("lotes");

            MongoCollection<Document> movimentacoes =
                banco.getCollection("movimentacoes");

            MongoCollection<Document> categorias =
                banco.getCollection("categorias");

            // ÍNDICES DE USUÁRIOS E SESSÕES

            // Impede e-mails duplicados
            usuarios.createIndex(
                Indexes.ascending("email"),
                new IndexOptions().unique(true)
            );

            // Garante tokens únicos
            sessoes.createIndex(
                Indexes.ascending("token"),
                new IndexOptions().unique(true)
            );

            // Remove sessões expiradas
            sessoes.createIndex(
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

            // PERFIL DO USUÁRIO

            servidor.createContext(
                "/api/perfil",
                new PerfilUsuarioHandler(
                    usuarios,
                    sessoes
                )
            );

            // DASHBOARD

            // Valida a sessão e consulta somente
            // os produtos e lotes da conta autenticada
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

            // Valida a sessão e separa os indicadores,
            // gráficos e relatórios financeiros por usuário
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

            // Permite que cada usuário gerencie
            // somente seus próprios produtos e categorias
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

            // Valida a sessão e garante que os lotes
            // pertençam ao usuário autenticado
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

            // PRÓXIMOS VENCIMENTOS

            // Rota HTTP antiga mantida desativada
            // Os próximos vencimentos do Dashboard
            // já são consultados pelo DashboardService

            // servidor.createContext(
            //     "/api/lotes/proximos-vencimento",
            //     new ProximosVencimentoHandler(
            //         lotes,
            //         produtos
            //     )
            // );

            // MOVIMENTAÇÕES

            // Mantém a integração atual de movimentações
            servidor.createContext(
                "/api/movimentacoes",
                new MovimentacoesHandler(
                    cliente,
                    lotes,
                    movimentacoes
                )
            );

            // HISTÓRICO DE DESPERDÍCIO

            // Utiliza o SessaoService para autenticar
            // e consulta somente registros vinculados
            // aos lotes e produtos da conta logada
            HistoricoDespHandler historicoHandler =
                new HistoricoDespHandler(banco);

            servidor.createContext(
                "/api/saidas",
                historicoHandler
            );

            // REPOSITÓRIO DO SERVIDOR SOCKET

            // Compartilha as coleções de lotes e produtos
            // com o serviço responsável pelos vencimentos
            RepositorioVencimentos repositorio =
                new RepositorioVencimentos(
                    lotes,
                    produtos
                );

            // THREAD DO SERVIDOR SOCKET

            // Executa o servidor de vencimentos
            // separadamente do servidor HTTP
            Thread threadServidor = new Thread(
                new ServidorSocket(repositorio),
                "servidor-vencimentos"
            );

            // ENCERRAMENTO SEGURO

            // Fecha o servidor HTTP e a conexão
            // com o MongoDB ao encerrar a aplicação
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
