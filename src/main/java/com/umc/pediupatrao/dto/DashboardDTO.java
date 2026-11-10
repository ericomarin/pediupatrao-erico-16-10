package com.umc.pediupatrao.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

// Objetos de resposta da API do dashboard.
// "record" = classe imutável com construtor e getters gerados automaticamente (serializada em JSON pelo Spring).

public final class DashboardDTO {

    private DashboardDTO() {
    }

    // indicadores gerais de vendas no período
    public record Resumo(
            LocalDate inicio,
            LocalDate fim,
            BigDecimal faturamento,        // soma dos pedidos não cancelados (já com desconto)
            long totalPedidos,             // todos os pedidos do período
            long pedidosValidos,           // pedidos não cancelados
            long pedidosCancelados,
            BigDecimal ticketMedio,        // faturamento / pedidos válidos
            BigDecimal totalDescontos,
            BigDecimal taxaCancelamento    // percentual de pedidos cancelados
    ) {}

    // vendas por dia (gráfico de linha)
    public record VendaDia(LocalDate data, long quantidade, BigDecimal faturamento) {}

    // pedidos por hora do dia (gráfico de barras / horários de pico)
    public record VendaHora(int hora, long quantidade) {}

    // quantidade de pedidos em cada status
    public record StatusQuantidade(String status, long quantidade) {}

    // linha da tabela de pedidos recentes
    public record PedidoResumo(String id, String cliente, String status, BigDecimal valorTotal,
                               LocalDateTime dataHoraEntrada, long quantidadeItens) {}

    // ranking de produtos
    public record ProdutoVendido(String produto, long quantidade, BigDecimal faturamento) {}

    // Corpo padrão das respostas de erro da API
    public record Erro(int status, String erro, String mensagem, LocalDateTime timestamp) {}
}
