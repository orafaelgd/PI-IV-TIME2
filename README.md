# Sistema de Gestão e Redução de Desperdício de Alimentos

O EcoStock foi criado para ajudar estabelecimentos do setor alimentício a controlar o estoque, acompanhar validades e reduzir o desperdício. O projeto faz parte da disciplina de Projeto Integrador 4 da PUC-Campinas.

## Funcionalidades

- Cadastro e login de usuários.
- Cadastro de produtos e lotes, com controle de estoque.
- Consulta de produtos próximos do vencimento.
- Registro de saídas e histórico de desperdícios.
- Dashboard e relatórios.

## Tecnologias

- HTML, Tailwind CSS e JavaScript no frontend.
- Java 21 e Maven no backend.
- MongoDB para armazenamento dos dados.

## Como executar

É necessário ter JDK 21 ou superior, Maven, Node.js com npm e acesso a um banco MongoDB com suporte a transações, como o Atlas.

1. Na raiz do projeto, instale as dependências e gere o CSS:

   ```sh
   npm ci
   npm run build
   ```

2. Copie `backend/config.example.properties` para `backend/config.properties` e preencha `MONGO_URI` com a URI de conexão do banco.

   Para a avaliação, use uma URI de testes fornecida pela equipe ou a de um banco próprio com suporte a transações. A aplicação usa o banco `desperdicio_alimentos`.

3. Ainda na raiz, compile e inicie o backend:

   ```sh
   mvn -f backend/pom.xml compile exec:java "-Dexec.mainClass=Main"
   ```

   Confira se aparecem as mensagens:

   ```text
   Servidor HTTP iniciado na porta 8080
   MongoDB conectado com sucesso!
   ```

   A API usa a porta `8080` e o serviço de próximos vencimentos usa a `8081`. Mantenha esse terminal aberto durante o uso. Para encerrar, pressione `Ctrl+C`.

4. Abra a raiz do projeto no VS Code, com a extensão Live Server instalada. Clique com o botão direito em `views/pages/tela-inicial.html` e escolha **Open with Live Server**. Use a porta `5500` e acesse [a tela inicial](http://localhost:5500/views/pages/tela-inicial.html).

Para atualizar o CSS enquanto edita as telas, execute `npm run dev` em outro terminal.

## Roteiro para testar

Uma conta nova começa sem produtos e lotes. Para testar o fluxo principal:

1. Crie uma conta e faça login. O cadastro pede nome completo, celular com DDD (11 dígitos) e senha de 8 a 50 caracteres.
2. Cadastre um produto com status **Ativo**.
3. Cadastre um lote desse produto com um código de identificação, quantidade maior que zero e valor unitário maior que zero. Use a data de hoje para a entrada e uma validade nos próximos três dias.
4. Consulte a tela de próximos vencimentos e selecione **Próx. 3 dias** para conferir o lote.
5. Registre uma saída com quantidade menor que o saldo do lote e motivo **Danificado**, para testar o registro de desperdício.
6. Confira a redução do estoque, o histórico de desperdícios e os indicadores no dashboard e nos relatórios.

## Recuperação de senha

Para testar o envio do e-mail de recuperação, configure estas variáveis de ambiente antes de iniciar o backend. Exemplo no PowerShell:

```powershell
$env:GMAIL_USER = "seu-email@gmail.com"
$env:GMAIL_APP_PASSWORD = "SUA_SENHA_DE_APLICATIVO"
$env:APP_RESET_URL = "http://localhost:5500/views/pages/redefinir-senha.html"
```

Substitua os exemplos pelos dados de uma conta Gmail com senha de aplicativo e execute o comando do backend nesse mesmo terminal. Depois, use **Esqueceu a senha?** na tela de login com o e-mail de uma conta cadastrada no sistema. Essa configuração é necessária apenas para a recuperação de senha.

## Problemas comuns

- **`config.properties` não encontrado:** confira se o arquivo foi criado em `backend/` e se o comando está sendo executado na raiz do projeto.
- **Falha na conexão com o MongoDB:** confira a URI, as credenciais e se o banco permite acesso a partir do computador usado na avaliação.
- **Telas sem dados ou erro de comunicação:** confira se o backend continua aberto e se o frontend está em `http://localhost:5500` ou `http://127.0.0.1:5500`. Abra as páginas pelo Live Server.
- **Porta `8080`, `8081` ou `5500` ocupada:** encerre a execução anterior ou o programa que estiver usando a porta e tente novamente.
- **`npm` bloqueado pelo PowerShell:** use `npm.cmd` no lugar de `npm`, por exemplo, `npm.cmd ci` e `npm.cmd run build`.

## Versões e atualizações

| Versão  | Etapa                              | Prazo previsto (2026) | Status    | Data de conclusão (2026) |
| ------- | ---------------------------------- | --------------------- | --------- | ------------------------ |
| `0.1.0` | Estrutura inicial e banco de dados | 05/10                 | Concluído | 06/10                    |
| `0.2.0` | Frontend                           | 12/10                 | Concluído | 13/10                    |
| `0.3.0` | Base do backend                    | 19/10                 | Concluído | 18/10                    |
| `0.4.0` | Regras de negócio                  | 26/10                 | Concluído | 25/10                    |
| `0.5.0` | Integração frontend e backend      | 02/11                 | Concluído | 03/11                    |
| `0.6.0` | Integração geral                   | 09/11                 | Concluído | 10/11                    |
| `0.7.0` | Testes e correções                 | 16/11                 | Concluído | 15/11                    |
| `1.0.0` | Entrega final                      | 23/11                 | Concluído | 22/11                    |

## Equipe

- Laura Cristine Soares
- Livia Carvalho Lucizano
- Lucas David de Sousa
- Miriã Nascimento dos Anjos
- Rafael Gaudencio Dias
