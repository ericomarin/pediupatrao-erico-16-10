package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.DashboardDTO;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.repository.PedidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

// Testes unitários das regras de cálculo do dashboard.
// O repositório é substituído por uma implementação em memória: os testes não dependem do MongoDB.
class DashboardServiceTest {

    private static final LocalDate INICIO = LocalDate.of(2026, 11, 1);
    private static final LocalDate FIM = LocalDate.of(2026, 11, 10);

    private final List<Pedido> pedidosNoBanco = new ArrayList<>();
    private DashboardService service;

    @BeforeEach
    void configurar() {
        // Repositório falso: responde buscarPorPeriodo filtrando a lista em memória
        PedidoRepository repositorioFalso = (PedidoRepository) Proxy.newProxyInstance(
                PedidoRepository.class.getClassLoader(),
                new Class<?>[]{PedidoRepository.class},
                (proxy, metodo, args) -> {
                    if (metodo.getName().equals("buscarPorPeriodo")) {
                        LocalDateTime de = (LocalDateTime) args[0];
                        LocalDateTime ate = (LocalDateTime) args[1];
                        return pedidosNoBanco.stream()
                                .filter(p -> !p.getDataHoraEntrada().isBefore(de) && p.getDataHoraEntrada().isBefore(ate))
                                .toList();
                    }
                    if (metodo.getName().equals("toString")) {
                        return "PedidoRepositoryFalso";
                    }
                    throw new UnsupportedOperationException(metodo.getName());
                });

        service = new DashboardService();
        ReflectionTestUtils.setField(service, "pedidoRepository", repositorioFalso);
    }

    // ---------------- Indicadores ----------------

    @Test
    @DisplayName("Resumo: calcula faturamento, ticket médio, descontos e taxa ignorando cancelados")
    void resumoCalculaIndicadores() {
        pedidosNoBanco.add(pedido("2026-11-05T12:00", StatusPedido.FINALIZADO, "100.00", "0.00"));
        pedidosNoBanco.add(pedido("2026-11-05T20:00", StatusPedido.FINALIZADO, "50.00", "5.00"));
        pedidosNoBanco.add(pedido("2026-11-06T19:00", StatusPedido.CANCELADO, "30.00", "0.00"));

        DashboardDTO.Resumo resumo = service.resumo(INICIO, FIM, null);

        assertValor("150.00", resumo.faturamento());      // cancelado não conta
        assertEquals(3, resumo.totalPedidos());
        assertEquals(2, resumo.pedidosValidos());
        assertEquals(1, resumo.pedidosCancelados());
        assertValor("75.00", resumo.ticketMedio());        // 150 / 2
        assertValor("5.00", resumo.totalDescontos());
        assertValor("33.3", resumo.taxaCancelamento());    // 1 de 3
    }

    @Test
    @DisplayName("Resumo: período sem pedidos retorna zeros (sem divisão por zero)")
    void resumoSemPedidos() {
        DashboardDTO.Resumo resumo = service.resumo(INICIO, FIM, null);

        assertValor("0", resumo.faturamento());
        assertValor("0", resumo.ticketMedio());
        assertValor("0", resumo.taxaCancelamento());
        assertEquals(0, resumo.totalPedidos());
    }

    @Test
    @DisplayName("Resumo: pedidos fora do período não são considerados")
    void resumoIgnoraForaDoPeriodo() {
        pedidosNoBanco.add(pedido("2026-10-31T23:59", StatusPedido.FINALIZADO, "999.00", "0.00"));
        pedidosNoBanco.add(pedido("2026-11-05T12:00", StatusPedido.FINALIZADO, "40.00", "0.00"));
        pedidosNoBanco.add(pedido("2026-11-11T00:00", StatusPedido.FINALIZADO, "999.00", "0.00"));

        DashboardDTO.Resumo resumo = service.resumo(INICIO, FIM, null);

        assertEquals(1, resumo.totalPedidos());
        assertValor("40.00", resumo.faturamento());
    }

    @Test
    @DisplayName("Filtro de status (RF05): considera somente o status informado")
    void filtroPorStatus() {
        pedidosNoBanco.add(pedido("2026-11-05T12:00", StatusPedido.FINALIZADO, "100.00", "0.00"));
        pedidosNoBanco.add(pedido("2026-11-05T13:00", StatusPedido.CANCELADO, "30.00", "0.00"));

        DashboardDTO.Resumo resumo = service.resumo(INICIO, FIM, StatusPedido.CANCELADO);

        assertEquals(1, resumo.totalPedidos());
        assertValor("0", resumo.faturamento());
    }

    @Test
    @DisplayName("Período padrão: sem datas, considera os últimos 30 dias até hoje")
    void periodoPadrao() {
        DashboardDTO.Resumo resumo = service.resumo(null, null, null);

        assertEquals(LocalDate.now(), resumo.fim());
        assertEquals(LocalDate.now().minusDays(29), resumo.inicio());
    }

    // ---------------- Gráficos ----------------

    @Test
    @DisplayName("Vendas por dia: inclui dias sem venda com valor zero")
    void vendasPorDiaPreencheDiasVazios() {
        pedidosNoBanco.add(pedido("2026-11-02T12:00", StatusPedido.FINALIZADO, "80.00", "0.00"));

        List<DashboardDTO.VendaDia> dias = service.vendasPorDia(
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 3), null);

        assertEquals(3, dias.size());
        assertEquals(0, dias.get(0).quantidade());
        assertEquals(1, dias.get(1).quantidade());
        assertValor("80.00", dias.get(1).faturamento());
        assertEquals(0, dias.get(2).quantidade());
    }

    @Test
    @DisplayName("Vendas por hora: retorna as 24 horas e conta os pedidos de cada uma")
    void vendasPorHora() {
        pedidosNoBanco.add(pedido("2026-11-05T20:10", StatusPedido.FINALIZADO, "50.00", "0.00"));
        pedidosNoBanco.add(pedido("2026-11-06T20:45", StatusPedido.FINALIZADO, "50.00", "0.00"));

        List<DashboardDTO.VendaHora> horas = service.vendasPorHora(INICIO, FIM, null);

        assertEquals(24, horas.size());
        assertEquals(2, horas.get(20).quantidade());
        assertEquals(0, horas.get(12).quantidade());
    }

    // ---------------- Situação ----------------

    @Test
    @DisplayName("Pedidos por status: lista todos os status, inclusive os zerados")
    void pedidosPorStatus() {
        pedidosNoBanco.add(pedido("2026-11-05T12:00", StatusPedido.FINALIZADO, "10.00", "0.00"));
        pedidosNoBanco.add(pedido("2026-11-05T13:00", StatusPedido.FINALIZADO, "10.00", "0.00"));

        List<DashboardDTO.StatusQuantidade> lista = service.pedidosPorStatus(INICIO, FIM, null);

        assertEquals(StatusPedido.values().length, lista.size());
        assertEquals(2, quantidadeDoStatus(lista, "FINALIZADO"));
        assertEquals(0, quantidadeDoStatus(lista, "CANCELADO"));
    }

    @Test
    @DisplayName("Pedidos recentes: ordena do mais novo para o mais antigo e respeita o limite")
    void pedidosRecentes() {
        pedidosNoBanco.add(pedido("2026-11-03T12:00", StatusPedido.FINALIZADO, "10.00", "0.00"));
        pedidosNoBanco.add(pedido("2026-11-09T12:00", StatusPedido.FINALIZADO, "20.00", "0.00"));
        pedidosNoBanco.add(pedido("2026-11-06T12:00", StatusPedido.FINALIZADO, "30.00", "0.00"));

        List<DashboardDTO.PedidoResumo> recentes = service.pedidosRecentes(INICIO, FIM, null, 2);

        assertEquals(2, recentes.size());
        assertValor("20.00", recentes.get(0).valorTotal()); // 09/11
        assertValor("30.00", recentes.get(1).valorTotal()); // 06/11
    }

    // ---------------- Produtos ----------------

    @Test
    @DisplayName("Produtos mais vendidos: soma por produto, ordena por quantidade e ignora cancelados")
    void produtosMaisVendidos() {
        pedidosNoBanco.add(pedido("2026-11-05T12:00", StatusPedido.FINALIZADO, "0", "0",
                item("Pizza Calabresa", 2, "49.90"), item("Refrigerante 2L", 1, "12.00")));
        pedidosNoBanco.add(pedido("2026-11-06T12:00", StatusPedido.FINALIZADO, "0", "0",
                item("Pizza Calabresa", 1, "49.90")));
        pedidosNoBanco.add(pedido("2026-11-07T12:00", StatusPedido.CANCELADO, "0", "0",
                item("Refrigerante 2L", 10, "12.00")));

        List<DashboardDTO.ProdutoVendido> ranking = service.produtosMaisVendidos(INICIO, FIM, null, 5);

        assertEquals(2, ranking.size());
        assertEquals("Pizza Calabresa", ranking.get(0).produto());
        assertEquals(3, ranking.get(0).quantidade());
        assertValor("149.70", ranking.get(0).faturamento());
        assertEquals(1, ranking.get(1).quantidade());        // cancelado não somou
    }

    // ---------------- Validações ----------------

    @Test
    @DisplayName("Validação: data inicial posterior à final é rejeitada")
    void dataInicialPosterior() {
        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
                () -> service.resumo(FIM, INICIO, null));
        assertEquals("A data inicial não pode ser posterior à data final.", erro.getMessage());
    }

    @Test
    @DisplayName("Validação: período maior que 366 dias é rejeitado")
    void periodoMuitoLongo() {
        assertThrows(IllegalArgumentException.class,
                () -> service.resumo(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 11, 10), null));
    }

    @Test
    @DisplayName("Validação: limite fora do intervalo permitido é rejeitado")
    void limiteInvalido() {
        assertThrows(IllegalArgumentException.class,
                () -> service.produtosMaisVendidos(INICIO, FIM, null, 0));
        assertThrows(IllegalArgumentException.class,
                () -> service.produtosMaisVendidos(INICIO, FIM, null, 51));
    }

    // ---------------- Utilitários do teste ----------------

    private Pedido pedido(String entrada, StatusPedido status, String total, String desconto, ItemPedido... itens) {
        Pedido pedido = new Pedido();
        pedido.setId("P-" + pedidosNoBanco.size());
        pedido.setClienteNome("Cliente Teste");
        pedido.setDataHoraEntrada(LocalDateTime.parse(entrada));
        pedido.setStatus(status);
        pedido.setValorTotal(new BigDecimal(total));
        pedido.setValorDesconto(new BigDecimal(desconto));
        pedido.getItens().addAll(List.of(itens));
        return pedido;
    }

    private ItemPedido item(String produto, int quantidade, String preco) {
        ItemPedido item = new ItemPedido();
        item.setNomeProduto(produto);
        item.setQuantidade(quantidade);
        item.setPrecoUnitario(new BigDecimal(preco));
        item.setSubtotal(new BigDecimal(preco).multiply(BigDecimal.valueOf(quantidade)));
        return item;
    }

    private long quantidadeDoStatus(List<DashboardDTO.StatusQuantidade> lista, String status) {
        return lista.stream().filter(s -> s.status().equals(status)).findFirst().orElseThrow().quantidade();
    }

    // Compara valores monetários ignorando a escala (ex.: 0 == 0.00)
    private void assertValor(String esperado, BigDecimal atual) {
        assertEquals(0, new BigDecimal(esperado).compareTo(atual),
                "Esperado " + esperado + " mas foi " + atual);
    }
}