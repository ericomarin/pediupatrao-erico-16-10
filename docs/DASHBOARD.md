# Dashboard Administrativo — PediuPatrão

Documentação da atividade de **Dashboard Administrativo** integrado ao sistema de pedidos PediuPatrão.

**Aluno:** Erico Marin
**Disciplina:** Análise e Projeto de Software
**Tecnologias:** Java 21+, Spring Boot 3.4, Spring Security, Spring Data MongoDB, Thymeleaf, Chart.js, JUnit 5, Mockito
**Versão:** 2.0.0

---

## Sumário

1. [Visão geral](#1-visão-geral)
2. [Requisitos funcionais](#2-requisitos-funcionais)
3. [Requisitos não funcionais](#3-requisitos-não-funcionais)
4. [Decisões de análise e projeto](#4-decisões-de-análise-e-projeto)
5. [Documentação da API](#5-documentação-da-api)
6. [Testes e evidências](#6-testes-e-evidências)
7. [Versionamento (Gitflow)](#7-versionamento-gitflow)
8. [Como executar](#8-como-executar)
9. [Correções da entrega 1](#9-correções-do-feedback-da-entrega-1)

---

## 1. Visão geral

O dashboard permite que o responsável pelo estabelecimento (perfis **ADMIN** e **GERENTE**) acompanhe o desempenho das vendas, a situação dos pedidos e os produtos mais vendidos, apoiando a tomada de decisão.

A solução é dividida em duas partes:

| Parte | Responsabilidade |
|---|---|
| **API REST** (`/api/dashboard/**`) | Consulta os pedidos no MongoDB, aplica filtros, calcula indicadores e devolve JSON |
| **Tela** (`/dashboard`) | Consome a API via `fetch`, exibe cards, gráficos e tabelas e se atualiza automaticamente |

```
Navegador (dashboard.html + Chart.js)
        │  fetch GET /api/dashboard/...
        ▼
DashboardController ──► DashboardService ──► PedidoRepository ──► MongoDB (coleção pedidos)
   (HTTP / parâmetros)    (regras e cálculos)    (consulta por período)
        │
DashboardExceptionHandler (erros 400 padronizados)
```

---

## 2. Requisitos funcionais

| ID | Requisito | Como foi atendido |
|---|---|---|
| **RF01** | Indicadores gerais de vendas | Cards com faturamento, pedidos válidos/total, ticket médio, total de descontos e cancelamentos (quantidade e taxa). Endpoint `GET /resumo`. |
| **RF02** | Análise de vendas (gráficos) | Gráfico combinado de faturamento (linha) e pedidos (barras) por dia; gráfico de barras de pedidos por horário, que evidencia os picos de almoço e jantar. Endpoints `GET /vendas-por-dia` e `GET /vendas-por-hora`. |
| **RF03** | Situação dos pedidos | Gráfico de rosca com a quantidade em cada status e tabela dos pedidos mais recentes com status colorido. Endpoints `GET /pedidos-por-status` e `GET /pedidos-recentes`. |
| **RF04** | Produtos mais vendidos | Ranking com posição, produto, quantidade vendida e faturamento. Endpoint `GET /produtos-mais-vendidos`. |
| **RF05** | Filtros de consulta | Data inicial, data final e status, com atalhos *Hoje*, *7 dias* e *30 dias*. Os filtros são aplicados em todos os endpoints. |
| **RF06** | Integração com a API | A tela não recebe dados do servidor na renderização: todos os dados são obtidos por requisições `fetch` aos endpoints REST, em paralelo (`Promise.all`). |
| **RF07** | Atualização e tratamento de dados | Atualização automática a cada 30 s (desativável), indicação da última atualização, indicador de carregamento, mensagens de erro vindas da API, manutenção dos últimos dados em caso de falha, estados vazios ("Nenhuma venda no período") e preenchimento de dias e horas sem venda com zero. |

### Regras de negócio

| Regra | Descrição |
|---|---|
| RN01 | O **faturamento** considera apenas pedidos **não cancelados** e usa o valor total já com desconto. |
| RN02 | **Ticket médio** = faturamento ÷ pedidos não cancelados (zero quando não há pedidos). |
| RN03 | **Taxa de cancelamento** = cancelados ÷ total de pedidos × 100, com 1 casa decimal. |
| RN04 | Sem datas informadas, o período padrão são os **últimos 30 dias** até hoje. |
| RN05 | A data inicial não pode ser posterior à final; o período máximo é de **366 dias**. |
| RN06 | O ranking de produtos ordena por quantidade vendida e, em caso de empate, por faturamento; pedidos cancelados não entram. |
| RN07 | Somente **ADMIN** e **GERENTE** acessam o dashboard e sua API. |

---

## 3. Requisitos não funcionais

| ID | Categoria | Requisito |
|---|---|---|
| **RNF01** | Segurança | Acesso restrito a ADMIN e GERENTE em duas camadas: regras de rota no `SecurityConfig` e `@PreAuthorize` em cada método do `DashboardService`. Usuários sem login são redirecionados ao login; perfis sem permissão recebem 403. |
| **RNF02** | Segurança | A API do dashboard é somente leitura (apenas `GET`). Dados exibidos na tela passam por escape de HTML, evitando injeção de conteúdo. |
| **RNF03** | Desempenho | O filtro por período é feito no próprio MongoDB (`$gte` / `$lt` em `dataHoraEntrada`); as seis consultas da tela são feitas em paralelo. A atualização automática é suspensa quando a aba está em segundo plano. |
| **RNF04** | Confiabilidade | Valores monetários calculados com `BigDecimal` e arredondamento `HALF_UP` (2 casas); valores nulos tratados como zero; nenhuma divisão por zero. |
| **RNF05** | Usabilidade | Layout responsivo (Bootstrap 5), valores no formato brasileiro (R$ 1.234,56), datas dd/mm/aaaa, rótulos de status em português e cores consistentes por status. |
| **RNF06** | Manutenibilidade | Arquitetura em camadas (controller → service → repository), DTOs imutáveis (`record`), tratamento de erros centralizado (`@RestControllerAdvice`) e código comentado. |
| **RNF07** | Interoperabilidade | Respostas em JSON, datas no padrão ISO-8601 (`yyyy-MM-dd`) e corpo de erro padronizado. |
| **RNF08** | Testabilidade | 78 testes automatizados (unitários e de integração), cobrindo o dashboard e as regras da entrega 1, executados com `./mvnw test`. |
| **RNF09** | Portabilidade | Executa com Java 21 ou superior e MongoDB local; URI do banco configurável pela variável `MONGODB_URI`. |
| **RNF10** | Segurança | Nenhuma senha no código ou no repositório: as senhas iniciais vêm das variáveis de ambiente `SENHA_ADMIN`, `SENHA_GERENTE` e `SENHA_ATENDENTE` (mínimo de 8 caracteres) e são gravadas com BCrypt. Sem a variável, o usuário não é criado. |
| **RNF11** | Consistência | Toda operação que grava um pedido ou cliente e o seu registro de auditoria roda em uma transação MongoDB (`@Transactional`): ou os dois são gravados, ou nenhum. Requer o MongoDB em modo *replica set*. |

---

## 4. Decisões de análise e projeto

| Decisão | Justificativa |
|---|---|
| **Endpoints separados por recurso** (resumo, vendas, status, produtos) em vez de um único endpoint | Cada gráfico consome apenas o que precisa, os endpoints podem ser reutilizados por outros clientes e cada um é testado de forma isolada. |
| **Cálculos no `DashboardService`** (Java), com filtro por período no MongoDB | O volume de uma pizzaria (centenas de pedidos por mês) é pequeno; o código em Java é mais legível, reutiliza as entidades existentes e é testável sem banco. Para volumes maiores, a evolução natural é usar o *Aggregation Pipeline* do MongoDB. |
| **Cancelados fora do faturamento**, mas contados no total | O faturamento deve refletir a receita real; os cancelamentos continuam visíveis como indicador de qualidade da operação. |
| **Dias e horas sem venda retornados com zero** | Gráficos contínuos e sem "buracos", o que facilita identificar tendências e quedas. |
| **Todos os status retornados, mesmo zerados** | O cliente da API não precisa conhecer a lista de status para montar o gráfico. |
| **DTOs `record`** em vez de expor a entidade `Pedido` | A API devolve apenas o necessário (não expõe dados internos) e o contrato fica estável mesmo se a entidade mudar. |
| **Validação no service + `@RestControllerAdvice`** | Regras de validação ficam em um só lugar e todos os erros têm o mesmo formato JSON. |
| **Atualização por polling a cada 30 s** | Simples, compatível com a arquitetura atual e suficiente para o ritmo de uma pizzaria (WebSocket seria um exagero para o caso). |
| **Chart.js via CDN** | Biblioteca leve, sem etapa de build, integra-se a páginas Thymeleaf. |
| **Gerador de dados de demonstração** (`app.dados-demo`) | Permite demonstrar os gráficos com 30 dias de histórico; pode ser desligado em produção. |

---

## 5. Documentação da API

### Informações gerais

| Item | Valor |
|---|---|
| URL base | `http://localhost:8080/api/dashboard` |
| Método | `GET` (todos os endpoints são somente leitura) |
| Formato | `application/json` |
| Autenticação | Sessão do Spring Security (login em `/login`) |
| Perfis permitidos | `ADMIN`, `GERENTE` |

### Parâmetros comuns (filtros — RF05)

Aceitos por **todos** os endpoints. Todos são opcionais.

| Parâmetro | Tipo | Formato | Padrão | Descrição |
|---|---|---|---|---|
| `inicio` | data | `yyyy-MM-dd` | hoje − 29 dias | Data inicial (inclusiva) |
| `fim` | data | `yyyy-MM-dd` | hoje | Data final (inclusiva) |
| `status` | texto | `RECEBIDO`, `EM_PREPARACAO`, `PRONTO`, `SAIU_PARA_ENTREGA`, `RETIRADO`, `FINALIZADO`, `CANCELADO` | todos | Considera somente pedidos com este status |

### Formato de erro

Todos os erros de validação retornam **400 Bad Request** com o corpo:

```json
{
  "status": 400,
  "erro": "Bad Request",
  "mensagem": "A data inicial não pode ser posterior à data final.",
  "timestamp": "2026-11-10T11:12:03.512"
}
```

### Erros possíveis (todos os endpoints)

| Código | Situação | Mensagem / comportamento |
|---|---|---|
| 400 | `inicio` posterior a `fim` | `A data inicial não pode ser posterior à data final.` |
| 400 | Período maior que 366 dias | `O período máximo de consulta é de 366 dias.` |
| 400 | Data em formato inválido (ex.: `10/11/2026`) | `Valor inválido para o parâmetro 'inicio': 10/11/2026` |
| 400 | Status inexistente (ex.: `XPTO`) | `Valor inválido para o parâmetro 'status': XPTO` |
| 400 | `limite` fora do intervalo (endpoints com limite) | `O parâmetro 'limite' deve estar entre 1 e N.` |
| 302 | Usuário não autenticado | Redireciona para `/login` |
| 403 | Usuário autenticado sem perfil ADMIN/GERENTE (ex.: ATENDENTE) | Acesso negado |

---

### 5.1 `GET /api/dashboard/resumo` — Indicadores gerais (RF01)

**Parâmetros:** apenas os comuns.

**Requisição**
```http
GET /api/dashboard/resumo?inicio=2026-10-12&fim=2026-11-10
```

**Resposta 200**
```json
{
  "inicio": "2026-10-12",
  "fim": "2026-11-10",
  "faturamento": 21791.16,
  "totalPedidos": 175,
  "pedidosValidos": 157,
  "pedidosCancelados": 18,
  "ticketMedio": 138.80,
  "totalDescontos": 312.54,
  "taxaCancelamento": 10.3
}
```

| Campo | Descrição |
|---|---|
| `inicio`, `fim` | Período efetivamente consultado (após aplicar os padrões) |
| `faturamento` | Soma do valor total dos pedidos não cancelados |
| `totalPedidos` | Todos os pedidos do período |
| `pedidosValidos` | Pedidos não cancelados |
| `pedidosCancelados` | Pedidos cancelados |
| `ticketMedio` | Faturamento ÷ pedidos válidos |
| `totalDescontos` | Soma dos descontos dos pedidos não cancelados |
| `taxaCancelamento` | Percentual de pedidos cancelados |

---

### 5.2 `GET /api/dashboard/vendas-por-dia` — Vendas diárias (RF02)

**Parâmetros:** apenas os comuns. Retorna **um item para cada dia** do período, inclusive dias sem venda.

**Requisição**
```http
GET /api/dashboard/vendas-por-dia?inicio=2026-11-04&fim=2026-11-06
```

**Resposta 200**
```json
[
  { "data": "2026-11-04", "quantidade": 4, "faturamento": 74.30 },
  { "data": "2026-11-05", "quantidade": 3, "faturamento": 467.70 },
  { "data": "2026-11-06", "quantidade": 8, "faturamento": 1164.16 }
]
```

| Campo | Descrição |
|---|---|
| `data` | Dia (`yyyy-MM-dd`) |
| `quantidade` | Pedidos não cancelados no dia |
| `faturamento` | Faturamento do dia |

---

### 5.3 `GET /api/dashboard/vendas-por-hora` — Horários de pico (RF02)

**Parâmetros:** apenas os comuns. Retorna sempre **24 itens** (0h a 23h).

**Requisição**
```http
GET /api/dashboard/vendas-por-hora
```

**Resposta 200** (trecho)
```json
[
  { "hora": 0,  "quantidade": 0 },
  { "hora": 12, "quantidade": 22 },
  { "hora": 20, "quantidade": 35 },
  { "hora": 23, "quantidade": 15 }
]
```

---

### 5.4 `GET /api/dashboard/pedidos-por-status` — Situação dos pedidos (RF03)

**Parâmetros:** apenas os comuns. Retorna **todos os status**, inclusive os que têm zero pedidos.

**Requisição**
```http
GET /api/dashboard/pedidos-por-status
```

**Resposta 200**
```json
[
  { "status": "RECEBIDO",          "quantidade": 2 },
  { "status": "EM_PREPARACAO",     "quantidade": 2 },
  { "status": "PRONTO",            "quantidade": 0 },
  { "status": "SAIU_PARA_ENTREGA", "quantidade": 0 },
  { "status": "RETIRADO",          "quantidade": 0 },
  { "status": "FINALIZADO",        "quantidade": 153 },
  { "status": "CANCELADO",         "quantidade": 18 }
]
```

---

### 5.5 `GET /api/dashboard/pedidos-recentes` — Últimos pedidos (RF03)

**Parâmetros:** os comuns e mais:

| Parâmetro | Tipo | Padrão | Faixa | Descrição |
|---|---|---|---|---|
| `limite` | inteiro | 10 | 1 a 100 | Quantidade máxima de pedidos |

Ordenados do **mais recente** para o mais antigo.

**Requisição**
```http
GET /api/dashboard/pedidos-recentes?status=CANCELADO&limite=2
```

**Resposta 200**
```json
[
  {
    "id": "6730a1f2c9e77b1d2a4f0c11",
    "cliente": "Paulo P",
    "status": "CANCELADO",
    "valorTotal": 135.80,
    "dataHoraEntrada": "2026-11-09T20:35:00",
    "quantidadeItens": 5
  },
  {
    "id": "6730a1f2c9e77b1d2a4f0c0e",
    "cliente": "Paulo P",
    "status": "CANCELADO",
    "valorTotal": 100.80,
    "dataHoraEntrada": "2026-11-09T19:24:00",
    "quantidadeItens": 2
  }
]
```

**Erro adicional:** `400` — `O parâmetro 'limite' deve estar entre 1 e 100.`

---

### 5.6 `GET /api/dashboard/produtos-mais-vendidos` — Ranking (RF04)

**Parâmetros:** os comuns e mais:

| Parâmetro | Tipo | Padrão | Faixa | Descrição |
|---|---|---|---|---|
| `limite` | inteiro | 5 | 1 a 50 | Quantidade de produtos no ranking |

**Requisição**
```http
GET /api/dashboard/produtos-mais-vendidos?limite=3
```

**Resposta 200**
```json
[
  { "produto": "Pizza Calabresa", "quantidade": 157, "faturamento": 7834.30 },
  { "produto": "Suco 500ml",      "quantidade": 143, "faturamento": 1215.50 },
  { "produto": "Pizza Mussarela", "quantidade": 124, "faturamento": 5691.60 }
]
```

**Erro adicional:** `400` — `O parâmetro 'limite' deve estar entre 1 e 50.`

---

## 6. Testes e evidências

### 6.1 Como executar

Pré-requisito: MongoDB em execução em `localhost:27017`, em modo *replica set* `rs0` (ver seção 8).

```bash
./mvnw test
```

Os testes de integração usam um banco separado (`pizzaria_teste`), sem alterar os dados de uso.

### 6.2 Resultado

```
Tests run: 21, Failures: 0, Errors: 0, Skipped: 0 -- in StatusPedidoTest
Tests run: 13, Failures: 0, Errors: 0, Skipped: 0 -- in DashboardApiIntegrationTest
Tests run: 19, Failures: 0, Errors: 0, Skipped: 0 -- in PedidoServiceTest
Tests run: 13, Failures: 0, Errors: 0, Skipped: 0 -- in DashboardServiceTest
Tests run: 10, Failures: 0, Errors: 0, Skipped: 0 -- in UsuarioServiceTest
Tests run:  2, Failures: 0, Errors: 0, Skipped: 0 -- in PedidoTransacaoIntegrationTest
Tests run: 78, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Saída completa: [`evidencias/resultado-testes.txt`](evidencias/resultado-testes.txt)

### 6.3 Testes unitários — `DashboardServiceTest` (13)

Testam as regras de cálculo com um repositório em memória (sem banco).

| # | Teste | Requisito |
|---|---|---|
| 1 | Calcula faturamento, ticket médio, descontos e taxa ignorando cancelados | RF01 / RN01–RN03 |
| 2 | Período sem pedidos retorna zeros (sem divisão por zero) | RF07 / RNF04 |
| 3 | Pedidos fora do período não são considerados | RF05 |
| 4 | Filtro de status considera somente o status informado | RF05 |
| 5 | Sem datas, considera os últimos 30 dias | RN04 |
| 6 | Vendas por dia incluem dias sem venda com zero | RF02 / RF07 |
| 7 | Vendas por hora retornam as 24 horas | RF02 |
| 8 | Pedidos por status listam todos os status | RF03 |
| 9 | Pedidos recentes ordenados e limitados | RF03 |
| 10 | Ranking soma por produto, ordena e ignora cancelados | RF04 / RN06 |
| 11 | Data inicial posterior à final é rejeitada | RN05 |
| 12 | Período maior que 366 dias é rejeitado | RN05 |
| 13 | Limite fora do intervalo é rejeitado | RF04 |

### 6.4 Testes de integração — `DashboardApiIntegrationTest` (13)

Sobem a aplicação completa (Spring, Security e MongoDB) e chamam a API via `MockMvc`.

| # | Requisição | Perfil | Esperado |
|---|---|---|---|
| 1 | `GET /resumo` | GERENTE | 200 com indicadores |
| 2 | `GET /vendas-por-dia?inicio=2026-11-01&fim=2026-11-07` | ADMIN | 200 com 7 dias |
| 3 | `GET /vendas-por-hora` | GERENTE | 200 com 24 horas |
| 4 | `GET /pedidos-por-status` | GERENTE | 200 com 7 status |
| 5 | `GET /produtos-mais-vendidos?limite=3` | GERENTE | 200 com até 3 produtos |
| 6 | `GET /pedidos-recentes?status=CANCELADO` | GERENTE | 200 somente cancelados |
| 7 | `GET /resumo?inicio=2026-11-10&fim=2026-11-01` | GERENTE | 400 datas invertidas |
| 8 | `GET /resumo?inicio=10/11/2026` | GERENTE | 400 formato inválido |
| 9 | `GET /resumo?status=XPTO` | GERENTE | 400 status inválido |
| 10 | `GET /produtos-mais-vendidos?limite=999` | GERENTE | 400 limite inválido |
| 11 | `GET /resumo` | ATENDENTE | 403 |
| 12 | `GET /resumo` | sem login | redirecionamento para o login |
| 13 | `GET /dashboard` (tela) | GERENTE | 200 |

### 6.5 Testes das regras da entrega 1 (correção do feedback)

| Classe | Tipo | Qtd. | O que garante |
|---|---|---|---|
| `StatusPedidoTest` | Unitário | 21 | Fluxo de status (entrega e retirada), sem pular nem voltar etapas, estados finais sem transição, cancelamento só pela operação do gerente, regras de "antes da saída". |
| `PedidoServiceTest` | Unitário (Mockito) | 19 | Total calculado no servidor; pedido sem itens, sem cliente, com quantidade negativa ou produto inativo recusado; desconto de 10% e 20% aceito, acima de 20%, zero ou após a saída recusado; cancelamento com justificativa preserva o histórico; sem justificativa, após a saída ou duplicado recusado; transições inválidas bloqueadas; auditoria registrada em cada operação. |
| `UsuarioServiceTest` | Unitário (Mockito) | 10 | Perfil e senha obrigatórios, username único, senha gravada criptografada, ID enviado descartado no cadastro, usuário não exclui a própria conta, bloqueio de autoelevação de perfil, senha mantida quando não informada. |
| `PedidoTransacaoIntegrationTest` | Integração (MongoDB) | 2 | Se a auditoria falhar, o pedido **não** fica gravado (rollback); com a auditoria funcionando, o pedido é gravado (commit). |

### 6.6 Testes manuais na interface (evidências)

| # | Cenário | Resultado | Evidência |
|---|---|---|---|
| 1 | Dashboard com os últimos 30 dias | Cards, gráficos, ranking e pedidos recentes exibidos | `evidencias/01-dashboard-30-dias.png` |
| 2 | Filtro "7 dias" | Período e gráficos atualizados para 7 dias | `evidencias/02-filtro-7-dias.png` |
| 3 | Filtro de status "Cancelado" | Faturamento R$ 0,00; 100% cancelados; "Nenhuma venda no período" | `evidencias/03-filtro-cancelado.png` |
| 4 | Data inicial posterior à final | Mensagem de erro da API exibida; últimos dados mantidos | `evidencias/04-erro-datas-invertidas.png` |
| 5 | Atualização automática | Horário da última atualização alterado sem ação do usuário | `evidencias/05-atualizacao-automatica.png` |
| 6 | ATENDENTE acessa `/dashboard` | 403 — Acesso negado | `evidencias/06-atendente-403.png` |
| 7 | Execução dos testes automatizados | 78 testes, 0 falhas, BUILD SUCCESS | `evidencias/07-testes-automatizados.png` |
| 8 | Pedido criado pelo ATENDENTE com transação ativa | Pedido e "INCLUSÃO DE PEDIDO" gravados juntos na auditoria | `evidencias/08-auditoria-transacao.png` |

---

## 7. Versionamento (Gitflow)

| Branch | Finalidade |
|---|---|
| `main` | Versão estável entregue (produção) |
| `develop` | Integração das funcionalidades |
| `feature/dashboard-api` | Endpoints da API e regras de cálculo |
| `feature/dashboard-ui` | Tela do dashboard |
| `feature/dashboard-testes` | Testes automatizados |
| `feature/dashboard-docs` | Esta documentação |
| `feature/correcoes-feedback` | Correções do feedback da entrega 1 (senhas, transações e testes) |
| `release/2.0.0` | Preparação da versão para a `main` |

Fluxo: cada `feature/*` nasce da `develop` e volta para ela com `merge --no-ff`. A `release/2.0.0` nasce da `develop`, é integrada à `main` e recebe a tag `v2.0.0`.

---

## 8. Como executar

```bash
# 1. MongoDB em modo replica set (terminal separado; necessário para as transações)
mongod --dbpath ~/mongodb-data --replSet rs0

# 1.1 Somente na primeira vez: iniciar o replica set
mongosh --eval 'rs.initiate()'

# 2. Senhas iniciais (defina as suas; mínimo de 8 caracteres)
export SENHA_ADMIN='<sua-senha>' SENHA_GERENTE='<sua-senha>' SENHA_ATENDENTE='<sua-senha>'

# 3. Aplicação
./mvnw spring-boot:run
```

Na primeira execução são criados os usuários `admin`, `gerente` e `atendente` com as senhas definidas nas variáveis. Acesse **http://localhost:8080/dashboard** com `gerente` ou `admin`.

Com `app.dados-demo=true` (padrão), cerca de 170 pedidos de demonstração são gerados nos últimos 30 dias na primeira execução.

---

## 9. Correções do feedback da entrega 1

| Ponto do feedback | Correção | Onde |
|---|---|---|
| Senhas iniciais fixas no código | Senhas lidas de variáveis de ambiente (`SENHA_ADMIN`, `SENHA_GERENTE`, `SENHA_ATENDENTE`), com tamanho mínimo de 8 caracteres e criptografia BCrypt. Sem a variável, o usuário não é criado e um aviso é registrado no log (a senha nunca é registrada). | `config/DataInitializer.java`, `application.properties` |
| Senha padrão no `application.properties` | Removidas as linhas `spring.security.user.name` e `spring.security.user.password`. O arquivo só referencia as variáveis, sem valores. | `application.properties` |
| Ausência de transações entre pedidos e auditoria | `MongoTransactionManager` configurado e `@Transactional` nas operações que gravam dados e auditoria: criar pedido, alterar status, cancelar, aplicar desconto, incluir, alterar e excluir cliente. MongoDB executado em *replica set*, requisito para transações. | `config/MongoTransacaoConfig.java`, `service/PedidoService.java`, `service/ClienteService.java` |
| Ausência de testes automatizados | 52 novos testes cobrindo as regras da entrega 1 (status, pedidos, desconto, cancelamento, usuários) e a consistência transacional, somando 78 testes no projeto. | `src/test/java/...` (seção 6.5) |

---

## Arquivos da implementação

| Arquivo | Responsabilidade |
|---|---|
| `controller/DashboardController.java` | Endpoints REST `/api/dashboard/**` |
| `controller/DashboardExceptionHandler.java` | Respostas de erro 400 padronizadas |
| `controller/DashboardViewController.java` | Rota da tela `/dashboard` |
| `service/DashboardService.java` | Validação de filtros e cálculos dos indicadores |
| `dto/DashboardDTO.java` | Objetos de resposta da API (`record`) |
| `repository/PedidoRepository.java` | Consulta de pedidos por período |
| `config/DadosDemonstracao.java` | Geração de dados de demonstração |
| `config/SecurityConfig.java` | Restrição de acesso a ADMIN e GERENTE |
| `templates/dashboard.html` | Interface (cards, gráficos, filtros e atualização) |
| `test/.../DashboardServiceTest.java` | Testes unitários |
| `test/.../DashboardApiIntegrationTest.java` | Testes de integração |
| `config/MongoTransacaoConfig.java` | Gerenciador de transações do MongoDB |
| `config/DataInitializer.java` | Usuários iniciais com senhas das variáveis de ambiente |
| `test/.../StatusPedidoTest.java` | Testes do fluxo de status |
| `test/.../PedidoServiceTest.java` | Testes das regras de pedidos, desconto e cancelamento |
| `test/.../UsuarioServiceTest.java` | Testes das regras de usuários |
| `test/.../PedidoTransacaoIntegrationTest.java` | Teste de rollback/commit da transação |
