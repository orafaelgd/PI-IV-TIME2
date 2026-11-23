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

3. Ainda na raiz, compile e inicie o backend:

   ```sh
   mvn -f backend/pom.xml compile exec:java "-Dexec.mainClass=Main"
   ```

   A API usa a porta `8080` e o serviço de próximos vencimentos usa a `8081`.

4. Abra a raiz do projeto no VS Code e use a extensão Live Server na porta `5500`. Acesse [a tela inicial](http://localhost:5500/views/pages/tela-inicial.html).

Para atualizar o CSS enquanto edita as telas, execute `npm run dev` em outro terminal.

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
