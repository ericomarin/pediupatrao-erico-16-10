package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.DashboardDTO;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.service.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

// API do Dashboard Administrativo (somente leitura - GET). Acesso: ADMIN e GERENTE.
// Filtros comuns, inicio e fim (yyyy-MM-dd) e status (opcional).

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    @Autowired
    private DashboardService dashboardService;

    @GetMapping("/resumo")
    public DashboardDTO.Resumo resumo(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) StatusPedido status) {
        return dashboardService.resumo(inicio, fim, status);
    }

    @GetMapping("/vendas-por-dia")
    public List<DashboardDTO.VendaDia> vendasPorDia(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) StatusPedido status) {
        return dashboardService.vendasPorDia(inicio, fim, status);
    }

    @GetMapping("/vendas-por-hora")
    public List<DashboardDTO.VendaHora> vendasPorHora(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) StatusPedido status) {
        return dashboardService.vendasPorHora(inicio, fim, status);
    }

    @GetMapping("/pedidos-por-status")
    public List<DashboardDTO.StatusQuantidade> pedidosPorStatus(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) StatusPedido status) {
        return dashboardService.pedidosPorStatus(inicio, fim, status);
    }

    @GetMapping("/pedidos-recentes")
    public List<DashboardDTO.PedidoResumo> pedidosRecentes(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) StatusPedido status,
            @RequestParam(defaultValue = "10") int limite) {
        return dashboardService.pedidosRecentes(inicio, fim, status, limite);
    }

    @GetMapping("/produtos-mais-vendidos")
    public List<DashboardDTO.ProdutoVendido> produtosMaisVendidos(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) StatusPedido status,
            @RequestParam(defaultValue = "5") int limite) {
        return dashboardService.produtosMaisVendidos(inicio, fim, status, limite);
    }
}