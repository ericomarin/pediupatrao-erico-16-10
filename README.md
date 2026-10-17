# PediuPatrão — Atividade Prática M1 (Parte 2)

Evolução do sistema **PediuPatrão** (pizzaria) com controle de perfis, regras de negócio de pedidos e auditoria.

**Aluno:** Erico Marin
**Disciplina:** Análise e Projeto de Software
**Tecnologias:** Java 21+, Spring Boot 3.4, Spring Security, Spring Data MongoDB, Thymeleaf

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

Acesse **http://localhost:8080**.

Por padrão a aplicação usa o MongoDB local. Para usar outro servidor, defina a variável de ambiente `MONGODB_URI`.

### Usuários criados automaticamente

| Usuário   | Senha    | Perfil    |
|-----------|----------|-----------|
| admin     | teste123 | ADMIN     |
| gerente   | teste123 | GERENTE   |
| atendente | teste123 | ATENDENTE |

Também são cadastrados produtos de exemplo (pizzas e bebidas) na primeira execução.

---

## Requisitos implementados

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

