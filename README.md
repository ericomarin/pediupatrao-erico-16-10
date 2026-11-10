# PediuPatrão — Sistema de Pedidos de Pizzaria

Evolução do sistema **PediuPatrão** (pizzaria) com controle de perfis, regras de negócio de pedidos, auditoria e dashboard administrativo.

**Aluno:** Erico Marin
**Disciplina:** Análise e Projeto de Software
**Tecnologias:** Java 21+, Spring Boot 3.4, Spring Security, Spring Data MongoDB, Thymeleaf, Chart.js, JUnit 5

> **Atividade 2 — Dashboard Administrativo:** documentação completa (requisitos, API, testes e evidências) em [`docs/DASHBOARD.md`](docs/DASHBOARD.md). Acesse a tela em `/dashboard` (perfis ADMIN e GERENTE).

---

## Como executar

### Pré-requisitos
- Java 21 ou superior
- MongoDB rodando localmente na porta `27017`

### Passos
```bash
# 1. Iniciar o MongoDB (em um terminal separado)
mongod --dbpath ~/mongodb-data

# 2. Executar a aplicação
./mvnw spring-boot:run
```

Acesse **http://localhost:8080** (dashboard em **http://localhost:8080/dashboard**).

Por padrão a aplicação usa o MongoDB local. Para usar outro servidor, defina a variável de ambiente `MONGODB_URI`.

### Executar os testes automatizados
```bash
./mvnw test
```
Com o MongoDB em execução. Resultado esperado: `Tests run: 26, Failures: 0, Errors: 0` e `BUILD SUCCESS`.

### Usuários criados automaticamente

| Usuário   | Senha    | Perfil    |
|-----------|----------|-----------|
| admin     | teste123 | ADMIN     |
| gerente   | teste123 | GERENTE   |
| atendente | teste123 | ATENDENTE |

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

### Versionamento (Gitflow)
`main` (estável) ← `release/2.0.0` ← `develop` ← `feature/dashboard-api`, `feature/dashboard-ui`, `feature/dashboard-testes`, `feature/dashboard-docs`.

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
| `config/DataInitializer.java` | Usuários dos 3 perfis e produtos de exemplo |
| `service/UsuarioDetailsService.java` | Perfil real no login |
| `service/UsuarioService.java` | Validações e proteção contra autoelevação |
| `service/PedidoService.java` | Regras de RF02–RF04 e auditoria |
| `service/ClienteService.java` | Auditoria de alterações de clientes |
| `entity/Pedido.java`, `Usuario.java` | Novos campos / perfil como enum / senha oculta na API |
| Templates | Tela de pedidos, formulário de pedido, auditoria, botão Sair |
| `application.properties` | MongoDB via variável `MONGODB_URI` (credenciais fora do código) |