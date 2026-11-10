package com.umc.pediupatrao.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Testes de integração da API do dashboard: sobem a aplicação completa (Spring + Security + MongoDB).
// Usam um banco separado (pizzaria_teste) para não alterar os dados de uso.
// Pré-requisito: MongoDB em execução em localhost:27017.
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.data.mongodb.database=pizzaria_teste",
        "app.dados-demo=true"
})
class DashboardApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // ---------------- Respostas de sucesso ----------------

    @Test
    @WithMockUser(roles = "GERENTE")
    @DisplayName("GET /resumo (GERENTE): 200 com os indicadores")
    void resumo() throws Exception {
        mockMvc.perform(get("/api/dashboard/resumo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.faturamento").isNumber())
                .andExpect(jsonPath("$.totalPedidos").isNumber())
                .andExpect(jsonPath("$.ticketMedio").isNumber())
                .andExpect(jsonPath("$.taxaCancelamento").isNumber());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /vendas-por-dia (ADMIN): 200 com um item por dia do período")
    void vendasPorDia() throws Exception {
        mockMvc.perform(get("/api/dashboard/vendas-por-dia")
                        .param("inicio", "2026-11-01").param("fim", "2026-11-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$[0].data").value("2026-11-01"));
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    @DisplayName("GET /vendas-por-hora: 200 com as 24 horas do dia")
    void vendasPorHora() throws Exception {
        mockMvc.perform(get("/api/dashboard/vendas-por-hora"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(24));
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    @DisplayName("GET /pedidos-por-status: 200 com todos os status")
    void pedidosPorStatus() throws Exception {
        mockMvc.perform(get("/api/dashboard/pedidos-por-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7));
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    @DisplayName("GET /produtos-mais-vendidos?limite=3: no máximo 3 produtos")
    void produtosMaisVendidos() throws Exception {
        mockMvc.perform(get("/api/dashboard/produtos-mais-vendidos").param("limite", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(lessThanOrEqualTo(3)));
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    @DisplayName("GET /pedidos-recentes?status=CANCELADO: somente pedidos cancelados")
    void pedidosRecentesFiltrados() throws Exception {
        mockMvc.perform(get("/api/dashboard/pedidos-recentes").param("status", "CANCELADO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.status != 'CANCELADO')]").isEmpty());
    }

    // ---------------- Erros de validação (400) ----------------

    @Test
    @WithMockUser(roles = "GERENTE")
    @DisplayName("400: data inicial posterior à final")
    void datasInvertidas() throws Exception {
        mockMvc.perform(get("/api/dashboard/resumo")
                        .param("inicio", "2026-11-10").param("fim", "2026-11-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.mensagem").value("A data inicial não pode ser posterior à data final."));
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    @DisplayName("400: data em formato inválido")
    void dataFormatoInvalido() throws Exception {
        mockMvc.perform(get("/api/dashboard/resumo").param("inicio", "10/11/2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").exists());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    @DisplayName("400: status inexistente")
    void statusInvalido() throws Exception {
        mockMvc.perform(get("/api/dashboard/resumo").param("status", "XPTO"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    @DisplayName("400: limite fora do intervalo")
    void limiteInvalido() throws Exception {
        mockMvc.perform(get("/api/dashboard/produtos-mais-vendidos").param("limite", "999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("O parâmetro 'limite' deve estar entre 1 e 50."));
    }

    // ---------------- Segurança ----------------

    @Test
    @WithMockUser(roles = "ATENDENTE")
    @DisplayName("403: ATENDENTE não acessa a API do dashboard")
    void atendenteSemPermissao() throws Exception {
        mockMvc.perform(get("/api/dashboard/resumo"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Sem login: redireciona para a tela de login")
    void semAutenticacao() throws Exception {
        mockMvc.perform(get("/api/dashboard/resumo"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    @DisplayName("Tela /dashboard (GERENTE): 200")
    void telaDashboard() throws Exception {
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk());
    }
}