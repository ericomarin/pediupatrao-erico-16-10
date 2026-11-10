# PediuPatrão — Sistema de Pedidos de Pizzaria

Evolução do sistema **PediuPatrão** (pizzaria) com controle de perfis, regras de negócio de pedidos, auditoria e dashboard administrativo.

**Aluno:** Erico Marin
**Disciplina:** Análise e Projeto de Software
**Tecnologias:** Java 21+, Spring Boot 3.4, Spring Security, Spring Data MongoDB, Thymeleaf, Chart.js, JUnit 5, Mockito
**Versão:** 2.0.0
**Vídeo de apresentação (2 min 23 s):** [`docs/video/apresentacao.mp4`](docs/video/apresentacao.mp4)

> **Atividade 2 — Dashboard Administrativo:** documentação completa (requisitos, API, testes e evidências) em [`docs/DASHBOARD.md`](docs/DASHBOARD.md). Acesse a tela em `/dashboard` (perfis ADMIN e GERENTE).

---

## Como executar

### Pré-requisitos
- Java 21 ou superior
- MongoDB rodando localmente na porta `27017`, em modo **replica set** (necessário para as transações)

### Passos
```bash
# 1. Iniciar o MongoDB em modo replica set (em um terminal separado)
mongod --dbpath ~/mongodb-data --replSet rs0

# 1.1 Somente na primeira vez: iniciar o replica set (em outro terminal)
mongosh --eval 'rs.initiate()'

# 2. Definir as senhas iniciais (escolha as suas; mínimo de 8 caracteres)
export SENHA_ADMIN='<sua-senha>' SENHA_GERENTE='<sua-senha>' SENHA_ATENDENTE='<sua-senha>'

# 3. Executar a aplicação (no mesmo terminal do passo 2)
./mvnw spring-boot:run
```

Acesse **http://localhost:8080** (dashboard em **http://localhost:8080/dashboard**).

Por padrão a aplicação usa o MongoDB local (`mongodb://localhost:27017/?replicaSet=rs0`). Para usar outro servidor, defina a variável de ambiente `MONGODB_URI`.

### Executar os testes automatizados
```bash
./mvnw test
```
Com o MongoDB em execução (replica set `rs0`). Resultado esperado: `Tests run: 78, Failures: 0, Errors: 0` e `BUILD SUCCESS`. Os testes usam um banco separado (`pizzaria_teste`).

### Usuários criados automaticamente

Na primeira execução são criados os usuários abaixo, com as senhas lidas das variáveis de ambiente. **Nenhuma senha fica no código ou no repositório.**

| Usuário   | Perfil    | Variável de ambiente da senha |
|-----------|-----------|-------------------------------|
| admin     | ADMIN     | `SENHA_ADMIN`                 |
| gerente   | GERENTE   | `SENHA_GERENTE`               |
| atendente | ATENDENTE | `SENHA_ATENDENTE`             |

- Senha com menos de 8 caracteres ou variável não definida: o usuário **não** é criado e um aviso aparece no log (a senha nunca é registrada no log).
- As senhas são gravadas criptografadas (BCrypt). Se o usuário já existir no banco, ele não é recriado.

Também são cadastrados produtos de exemplo (pizzas e bebidas) na primeira execução e, com `app.dados-demo=true` (padrão), cerca de 170 pedidos de demonstração distribuídos nos últimos 30 dias para alimentar o dashboard.

---

## Atividade 2 — Dashboard Administrativo

Painel para o responsável pelo estabelecimento acompanhar o desempenho das vendas, a situação dos pedidos e os produtos mais vendidos. A tela consome uma API REST projetada e implementada para este fim.

| Requisito | Implementação |
|---|---|
| RF01 — Indicadores gerais | Faturamento, pedidos, ticket médio, descontos e taxa de cancelamento |
| RF02 — Análise de vendas | Gráficos de vendas por dia e de pedidos por horário (picos) |
| RF03 — Situação dos pedidos | Gráfico por status e tabela de pedidos recentes |
| RF04 — Produtos mais vendidos | Ranking por quantidade e faturamento |
| RF05 — Filtros | Período (início/fim), status e atalhos Hoje / 7 dias / 30 dias |
| RF06 — Integração com a API | A tela obtém todos os dados via `fetch` nos endpoints REST |
| RF07 — Atualização e tratamento | Atualização automática a cada 30 s, mensagens de erro, estados vazios e dias sem venda com zero |

### Endpoints (`GET`, perfis ADMIN e GERENTE)

| Endpoint | Retorno |
|---|---|
| `/api/dashboard/resumo` | Indicadores gerais |
| `/api/dashboard/vendas-por-dia` | Quantidade e faturamento por dia |
| `/api/dashboard/vendas-por-hora` | Pedidos por hora do dia |
| `/api/dashboard/pedidos-por-status` | Quantidade em cada status |
| `/api/dashboard/pedidos-recentes` | Últimos pedidos (`limite`, padrão 10) |
| `/api/dashboard/produtos-mais-vendidos` | Ranking de produtos (`limite`, padrão 5) |

Filtros comuns: `inicio` e `fim` (`yyyy-MM-dd`) e `status`. Parâmetros, exemplos de requisição e resposta e erros possíveis estão em [`docs/DASHBOARD.md`](docs/DASHBOARD.md).

### Testes
- **13 testes unitários** (`DashboardServiceTest`): regras de cálculo, filtros e validações.
- **13 testes de integração** (`DashboardApiIntegrationTest`): respostas da API, erros 400, permissões (403) e redirecionamento sem login.
- Evidências (prints e saída dos testes) em [`docs/evidencias`](docs/evidencias).

### Apresentação em vídeo
O vídeo [`docs/video/apresentacao.mp4`](docs/video/apresentacao.mp4) demonstra o funcionamento do dashboard (indicadores, gráficos, filtros, tratamento de erro, API em JSON e bloqueio do ATENDENTE) e justifica as principais decisões de análise e projeto. Para assistir, abra o arquivo no GitHub ou faça o download.

### Versionamento (Gitflow)
`main` (estável) ← `release/2.0.0` ← `develop` ← `feature/dashboard-api`, `feature/dashboard-ui`, `feature/dashboard-testes`, `feature/dashboard-docs`, `feature/correcoes-feedback`, `feature/apresentacao-video`.

---

## Correções do feedback da entrega 1

| Ponto do feedback | Correção |
|---|---|
| Senhas iniciais fixas | O `DataInitializer` lê as senhas das variáveis `SENHA_ADMIN`, `SENHA_GERENTE` e `SENHA_ATENDENTE`, exige no mínimo 8 caracteres e grava com BCrypt. Sem a variável, o usuário não é criado. |
| Senha padrão no `application.properties` | Removidas as linhas `spring.security.user.name` e `spring.security.user.password`. O arquivo apenas referencia as variáveis de ambiente, sem valores. |
| Falta de transações entre pedidos e auditoria | `MongoTransacaoConfig` registra o `MongoTransactionManager`, e `@Transactional` foi aplicado em todas as operações que gravam dados e auditoria (criar pedido, alterar status, cancelar, aplicar desconto, incluir/alterar/excluir cliente). Se a auditoria falhar, a alteração é desfeita (rollback). O MongoDB passa a rodar em *replica set*, requisito das transações. |
| Ausência de testes automatizados | 52 novos testes para as regras da entrega 1, somando **78 testes** no projeto (ver tabela abaixo). |

| Classe de teste | Tipo | Qtd. | O que verifica |
|---|---|---|---|
| `StatusPedidoTest` | Unitário | 21 | Fluxo de status, bloqueio de saltos e retrocessos, estados finais, regras de "antes da saída" |
| `PedidoServiceTest` | Unitário (Mockito) | 19 | Total no servidor, validações do pedido, desconto (limite de 20%), cancelamento (justificativa e saída), transições inválidas, auditoria |
| `UsuarioServiceTest` | Unitário (Mockito) | 10 | Perfil e senha obrigatórios, username único, senha criptografada, autoelevação e exclusão da própria conta bloqueadas |
| `PedidoTransacaoIntegrationTest` | Integração (MongoDB) | 2 | Rollback do pedido quando a auditoria falha e commit quando ela funciona |
| `DashboardServiceTest` | Unitário | 13 | Cálculos e filtros do dashboard |
| `DashboardApiIntegrationTest` | Integração | 13 | API do dashboard, erros 400 e permissões |

---

## Atividade 1 — Perfis, pedidos e auditoria

### RF01 — Controle de usuários e permissões
- Enum `Perfil` com **ADMIN**, **GERENTE** e **ATENDENTE**; o login (`UsuarioDetailsService`) atribui o perfil real do usuário.
- Autorização em **duas camadas no backend**:
  - Regras por rota e método HTTP no `SecurityConfig`;
  - `@PreAuthorize` nos métodos do `PedidoService` e do `AuditoriaService` (`@EnableMethodSecurity`).
- `/api/usuarios` deixou de ser pública (antes qualquer pessoa, sem login, podia criar um ADMIN).
- **Proteção contra autoelevação:** o usuário não pode alterar o próprio perfil nem excluir a própria conta; cadastro exige perfil válido e username único.
- Senha não é retornada pela API (`@JsonProperty(WRITE_ONLY)`).
- **CSRF habilitado** em todos os formulários; logout via POST; token CSRF enviado nas requisições `fetch`.

| Operação | ADMIN | GERENTE | ATENDENTE |
|---|:-:|:-:|:-:|
| Gerenciar usuários | ✅ | ❌ | ❌ |
| Consultar pedidos | ✅ | ✅ | ✅ |
| Registrar pedido | ❌ | ❌ | ✅ |
| Avançar status | ❌ | ✅ | ✅ |
| Aplicar desconto / cancelar | ❌ | ✅ | ❌ |
| Consultar clientes | ❌ | ✅ | ✅ |
| Cadastrar/alterar clientes | ❌ | ❌ | ✅ |
| Consultar auditoria | ✅ | ✅ | ❌ |
| Dashboard administrativo | ✅ | ✅ | ❌ |

### RF02 — Evolução do cadastro de pedidos
- Pedido com **itens** (`ItemPedido`: produto, quantidade, preço unitário, subtotal), desconto, valor total, responsável e data/hora de entrada.
- A requisição usa o DTO `PedidoRequest`, que **não possui campos de preço ou total**: o preço vem do cadastro do produto e o total é **calculado no servidor** (`BigDecimal`).

### RF03 — Entrada, saída e transições
- Enum `StatusPedido` define o fluxo: `RECEBIDO → EM_PREPARACAO → PRONTO → SAIU_PARA_ENTREGA / RETIRADO → FINALIZADO`.
- Transições inválidas são bloqueadas no service (ex.: finalizar um pedido em preparação).
- Entrada registra data/hora e usuário; saída registra obrigatoriamente data/hora e responsável.
- Pedido cancelado não aceita nenhuma alteração de status.

### RF04 — Cancelamentos e descontos
- Exclusivos do **GERENTE** (nem o ADMIN pode), verificados no `SecurityConfig` e com `@PreAuthorize`.
- Cancelamento exige justificativa, preserva os dados históricos e só é permitido antes da saída.
- Desconto percentual entre 0% e 20%, apenas antes da saída; o total é recalculado no servidor.

### RF05 — Auditoria
- Entidade `Auditoria` persistida na coleção `auditoria` do MongoDB.
- Auditados: inclusão de pedido, mudança de status, desconto, cancelamento, inclusão/alteração/exclusão de cliente.
- Cada registro contém: usuário, perfil, data/hora, operação, entidade, id, **campos alterados com valor anterior e novo** e justificativa.
- **Integridade:** `AuditoriaRepository` estende apenas `Repository` e expõe somente `save` e consulta — não existem métodos ou rotas de alteração/exclusão; a entidade não possui setters.
- Consulta (`/auditoria` e `/api/auditoria`) restrita a ADMIN e GERENTE.
- **Proteção de dados:** senhas nunca são auditadas; inclusão e exclusão de cliente registram apenas o nome; alterações registram somente os campos que mudaram.

---

## Principais alterações

### Atividade 2 — Dashboard

| Arquivo | Alteração |
|---|---|
| `controller/DashboardController.java` | Novo — endpoints REST `/api/dashboard/**` |
| `controller/DashboardExceptionHandler.java` | Novo — respostas de erro 400 padronizadas |
| `controller/DashboardViewController.java` | Novo — rota da tela `/dashboard` |
| `service/DashboardService.java` | Novo — validação de filtros e cálculo dos indicadores |
| `dto/DashboardDTO.java` | Novo — objetos de resposta da API (`record`) |
| `config/DadosDemonstracao.java` | Novo — gerador de pedidos de demonstração |
| `templates/dashboard.html` | Novo — interface com cards, gráficos (Chart.js) e filtros |
| `repository/PedidoRepository.java` | Consulta de pedidos por período |
| `config/SecurityConfig.java` | Acesso ao dashboard restrito a ADMIN e GERENTE |
| `src/test/...` | Novos — testes unitários e de integração |
| `docs/DASHBOARD.md` | Novo — requisitos, documentação da API e evidências |
| `docs/video/apresentacao.mp4` | Novo — vídeo de apresentação da solução |

### Atividade 1 — Perfis, pedidos e auditoria

| Arquivo | Alteração |
|---|---|
| `entity/Perfil.java` | Novo — perfis de acesso |
| `entity/StatusPedido.java` | Novo — estados e transições do pedido |
| `entity/ItemPedido.java` | Novo — item do pedido |
| `entity/Auditoria.java`, `AlteracaoCampo.java` | Novos — registro de auditoria |
| `dto/PedidoRequest.java` | Novo — dados aceitos na criação do pedido |
| `repository/AuditoriaRepository.java` | Novo — repositório somente inclusão/consulta |
| `service/AuditoriaService.java` | Novo — registro e consulta de auditoria |
| `controller/AuditoriaController.java` | Novo — tela e API de auditoria (somente GET) |
| `config/SecurityConfig.java` | Regras por perfil, CSRF habilitado, `@EnableMethodSecurity` |
| `config/DataInitializer.java` | Usuários dos 3 perfis (senhas por variável de ambiente) e produtos de exemplo |
| `service/UsuarioDetailsService.java` | Perfil real no login |
| `service/UsuarioService.java` | Validações e proteção contra autoelevação |
| `service/PedidoService.java` | Regras de RF02–RF04 e auditoria |
| `service/ClienteService.java` | Auditoria de alterações de clientes |
| `entity/Pedido.java`, `Usuario.java` | Novos campos / perfil como enum / senha oculta na API |
| Templates | Tela de pedidos, formulário de pedido, auditoria, botão Sair |
| `application.properties` | MongoDB via variável `MONGODB_URI` (credenciais fora do código); senha padrão do Spring Security removida |

### Correções do feedback da entrega 1

| Arquivo | Alteração |
|---|---|
| `config/MongoTransacaoConfig.java` | Novo — gerenciador de transações do MongoDB |
| `config/DataInitializer.java` | Senhas iniciais lidas de variáveis de ambiente |
| `application.properties` | Replica set na URI padrão; senhas via `${SENHA_*}`; remoção da senha padrão |
| `service/PedidoService.java` | `@Transactional` nas operações com auditoria |
| `service/ClienteService.java` | `@Transactional` nas operações com auditoria |
| `src/test/...` | Novos — `StatusPedidoTest`, `PedidoServiceTest`, `UsuarioServiceTest`, `PedidoTransacaoIntegrationTest` |