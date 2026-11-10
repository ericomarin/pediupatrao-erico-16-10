package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.DashboardDTO;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.repository.PedidoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class DashboardService {

    static final int PERIODO_PADRAO_DIAS = 30;
    static final int PERIODO_MAXIMO_DIAS = 366;

    @Autowired
    private PedidoRepository pedidoRepository;

    // Indicadores gerais
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public DashboardDTO.Resumo resumo(LocalDate inicio, LocalDate fim, StatusPedido status) {
        Periodo periodo = validarPeriodo(inicio, fim);
        List<Pedido> pedidos = buscarPedidos(periodo, status);
        List<Pedido> validos = naoCancelados(pedidos);

        BigDecimal faturamento = somar(validos, Pedido::getValorTotal);
        BigDecimal descontos = somar(validos, Pedido::getValorDesconto);
        long cancelados = pedidos.size() - validos.size();

        BigDecimal ticketMedio = validos.isEmpty()
                ? BigDecimal.ZERO
                : faturamento.divide(BigDecimal.valueOf(validos.size()), 2, RoundingMode.HALF_UP);

        BigDecimal taxaCancelamento = pedidos.isEmpty()
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(cancelados * 100)
                        .divide(BigDecimal.valueOf(pedidos.size()), 1, RoundingMode.HALF_UP);

        return new DashboardDTO.Resumo(periodo.inicio(), periodo.fim(), faturamento,
                pedidos.size(), validos.size(), cancelados, ticketMedio, descontos, taxaCancelamento);
    }

    // Faturamento e quantidade por dia (inclui dias sem venda com valor zero)
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public List<DashboardDTO.VendaDia> vendasPorDia(LocalDate inicio, LocalDate fim, StatusPedido status) {
        Periodo periodo = validarPeriodo(inicio, fim);
        Map<LocalDate, List<Pedido>> porDia = naoCancelados(buscarPedidos(periodo, status)).stream()
                .collect(Collectors.groupingBy(p -> p.getDataHoraEntrada().toLocalDate()));

        List<DashboardDTO.VendaDia> resultado = new ArrayList<>();
        for (LocalDate dia = periodo.inicio(); !dia.isAfter(periodo.fim()); dia = dia.plusDays(1)) {
            List<Pedido> doDia = porDia.getOrDefault(dia, List.of());
            resultado.add(new DashboardDTO.VendaDia(dia, doDia.size(), somar(doDia, Pedido::getValorTotal)));
        }
        return resultado;
    }

    // Pedidos por hora do dia (0 a 23), para identificar horários de pico
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public List<DashboardDTO.VendaHora> vendasPorHora(LocalDate inicio, LocalDate fim, StatusPedido status) {
        Periodo periodo = validarPeriodo(inicio, fim);
        Map<Integer, Long> porHora = naoCancelados(buscarPedidos(periodo, status)).stream()
                .collect(Collectors.groupingBy(p -> p.getDataHoraEntrada().getHour(), Collectors.counting()));

        List<DashboardDTO.VendaHora> resultado = new ArrayList<>();
        for (int hora = 0; hora < 24; hora++) {
            resultado.add(new DashboardDTO.VendaHora(hora, porHora.getOrDefault(hora, 0L)));
        }
        return resultado;
    }

    // Quantidade de pedidos em cada status (todos os status aparecem, mesmo com zero)
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public List<DashboardDTO.StatusQuantidade> pedidosPorStatus(LocalDate inicio, LocalDate fim, StatusPedido status) {
        Periodo periodo = validarPeriodo(inicio, fim);
        Map<StatusPedido, Long> porStatus = buscarPedidos(periodo, status).stream()
                .filter(p -> p.getStatus() != null)
                .collect(Collectors.groupingBy(Pedido::getStatus, Collectors.counting()));

        List<DashboardDTO.StatusQuantidade> resultado = new ArrayList<>();
        for (StatusPedido s : StatusPedido.values()) {
            resultado.add(new DashboardDTO.StatusQuantidade(s.name(), porStatus.getOrDefault(s, 0L)));
        }
        return resultado;
    }

    // Pedidos mais recentes do período
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public List<DashboardDTO.PedidoResumo> pedidosRecentes(LocalDate inicio, LocalDate fim,
                                                          StatusPedido status, int limite) {
        validarLimite(limite, 100);
        Periodo periodo = validarPeriodo(inicio, fim);

        return buscarPedidos(periodo, status).stream()
                .sorted(Comparator.comparing(Pedido::getDataHoraEntrada).reversed())
                .limit(limite)
                .map(p -> new DashboardDTO.PedidoResumo(
                        p.getId(),
                        p.getClienteNome(),
                        p.getStatus() != null ? p.getStatus().name() : null,
                        valorOuZero(p.getValorTotal()),
                        p.getDataHoraEntrada(),
                        itens(p).mapToLong(ItemPedido::getQuantidade).sum()))
                .toList();
    }

    // Produtos mais vendidos (por quantidade; desempate por faturamento)
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public List<DashboardDTO.ProdutoVendido> produtosMaisVendidos(LocalDate inicio, LocalDate fim,
                                                                 StatusPedido status, int limite) {
        validarLimite(limite, 50);
        Periodo periodo = validarPeriodo(inicio, fim);

        Map<String, List<ItemPedido>> porProduto = naoCancelados(buscarPedidos(periodo, status)).stream()
                .flatMap(this::itens)
                .filter(i -> i.getNomeProduto() != null)
                .collect(Collectors.groupingBy(ItemPedido::getNomeProduto));

        return porProduto.entrySet().stream()
                .map(e -> new DashboardDTO.ProdutoVendido(
                        e.getKey(),
                        e.getValue().stream().mapToLong(ItemPedido::getQuantidade).sum(),
                        e.getValue().stream().map(ItemPedido::getSubtotal).filter(Objects::nonNull)
                                .reduce(BigDecimal.ZERO, BigDecimal::add)
                                .setScale(2, RoundingMode.HALF_UP)))
                .sorted(Comparator.comparingLong(DashboardDTO.ProdutoVendido::quantidade).reversed()
                        .thenComparing(DashboardDTO.ProdutoVendido::faturamento, Comparator.reverseOrder()))
                .limit(limite)
                .toList();
    }

   
    // Validação dos filtros e tratamento dos dados
    

    // Sem datas, últimos 30 dias. Valida ordem das datas e período máximo.

    Periodo validarPeriodo(LocalDate inicio, LocalDate fim) {
        LocalDate dataFim = (fim != null) ? fim : LocalDate.now();
        LocalDate dataInicio = (inicio != null) ? inicio : dataFim.minusDays(PERIODO_PADRAO_DIAS - 1);

        if (dataInicio.isAfter(dataFim)) {
            throw new IllegalArgumentException("A data inicial não pode ser posterior à data final.");
        }
        if (ChronoUnit.DAYS.between(dataInicio, dataFim) + 1 > PERIODO_MAXIMO_DIAS) {
            throw new IllegalArgumentException("O período máximo de consulta é de " + PERIODO_MAXIMO_DIAS + " dias.");
        }
        return new Periodo(dataInicio, dataFim);
    }

    private void validarLimite(int limite, int maximo) {
        if (limite < 1 || limite > maximo) {
            throw new IllegalArgumentException("O parâmetro 'limite' deve estar entre 1 e " + maximo + ".");
        }
    }

    // Busca no MongoDB pelo período e aplica o filtro opcional de status

    private List<Pedido> buscarPedidos(Periodo periodo, StatusPedido status) {
        return pedidoRepository
                .buscarPorPeriodo(periodo.inicio().atStartOfDay(), periodo.fim().plusDays(1).atStartOfDay())
                .stream()
                .filter(p -> p.getDataHoraEntrada() != null)
                .filter(p -> status == null || status == p.getStatus())
                .toList();
    }

    private List<Pedido> naoCancelados(List<Pedido> pedidos) {
        return pedidos.stream().filter(p -> p.getStatus() != StatusPedido.CANCELADO).toList();
    }

    // Soma valores tratando nulos como zero
    private BigDecimal somar(List<Pedido> pedidos, Function<Pedido, BigDecimal> campo) {
        return pedidos.stream()
                .map(campo)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal valorOuZero(BigDecimal valor) {
        return valor != null ? valor : BigDecimal.ZERO;
    }

    private Stream<ItemPedido> itens(Pedido pedido) {
        return pedido.getItens() != null ? pedido.getItens().stream() : Stream.empty();
    }

    // Período validado da consulta
    record Periodo(LocalDate inicio, LocalDate fim) {}
}
