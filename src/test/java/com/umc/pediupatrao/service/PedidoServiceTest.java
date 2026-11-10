package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.PedidoRequest;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// Testes unitários das regras de negócio da entrega 1 (RF02, RF03, RF04 e RF05).
// Os repositórios e a auditoria são simulados com Mockito: não precisa de banco.
// A autorização por perfil (@PreAuthorize) é coberta nos testes de integração.
@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private ClienteRepository clienteRepository;

    @Mock
    private ProdutoRepository produtoRepository;

    @Mock
    private AuditoriaService auditoriaService;

    @InjectMocks
    private PedidoService pedidoService;

    // Simula um usuário logado (o service registra quem fez cada operação)
    @BeforeEach
    void logarUsuario() {
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken("usuario.teste", null));
    }

    @AfterEach
    void limparLogin() {
        SecurityContextHolder.clearContext();
    }

    // ===================== RF02: itens e total calculado no servidor =====================

    @Test
    @DisplayName("RF02: total é calculado no servidor a partir do preço cadastrado")
    void totalCalculadoNoServidor() {
        when(clienteRepository.findById("c1")).thenReturn(Optional.of(cliente()));
        when(produtoRepository.findById("p1")).thenReturn(Optional.of(produto("p1", "Pizza Calabresa", 40.0)));
        when(produtoRepository.findById("p2")).thenReturn(Optional.of(produto("p2", "Refrigerante 2L", 12.5)));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));

        PedidoRequest request = request(item("p1", 2), item("p2", 1));
        Pedido pedido = pedidoService.criarPedido(request);

        // 2 x 40,00 + 1 x 12,50 = 92,50
        assertIgual("92.50", pedido.getValorItens());
        assertIgual("0.00", pedido.getValorDesconto());
        assertIgual("92.50", pedido.getValorTotal());
        assertEquals(StatusPedido.RECEBIDO, pedido.getStatus());
        assertEquals("usuario.teste", pedido.getRegistradoPor());
        assertEquals(2, pedido.getItens().size());
    }

    @Test
    @DisplayName("RF05: inclusão de pedido gera registro de auditoria")
    void inclusaoGeraAuditoria() {
        when(clienteRepository.findById("c1")).thenReturn(Optional.of(cliente()));
        when(produtoRepository.findById("p1")).thenReturn(Optional.of(produto("p1", "Pizza Calabresa", 40.0)));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));

        pedidoService.criarPedido(request(item("p1", 1)));

        verify(auditoriaService).registrar(eq("INCLUSÃO DE PEDIDO"), eq("PEDIDO"), any(), anyList(), isNull());
    }

    @Test
    @DisplayName("RF02: pedido sem itens (quantidades zeradas) é recusado")
    void pedidoSemItensRecusado() {
        when(clienteRepository.findById("c1")).thenReturn(Optional.of(cliente()));

        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
                () -> pedidoService.criarPedido(request(item("p1", 0))));

        assertEquals("O pedido precisa ter pelo menos um item.", erro.getMessage());
        verify(pedidoRepository, never()).save(any());
        verifyNoInteractions(auditoriaService);
    }

    @Test
    @DisplayName("RF02: quantidade negativa é recusada")
    void quantidadeNegativaRecusada() {
        when(clienteRepository.findById("c1")).thenReturn(Optional.of(cliente()));

        assertThrows(IllegalArgumentException.class,
                () -> pedidoService.criarPedido(request(item("p1", -1))));
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    @DisplayName("RF02: produto inativo não pode ser vendido")
    void produtoInativoRecusado() {
        Produto inativo = produto("p1", "Pizza Antiga", 30.0);
        inativo.setAtivo(false);
        when(clienteRepository.findById("c1")).thenReturn(Optional.of(cliente()));
        when(produtoRepository.findById("p1")).thenReturn(Optional.of(inativo));

        assertThrows(IllegalArgumentException.class,
                () -> pedidoService.criarPedido(request(item("p1", 1))));
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    @DisplayName("RF02: pedido sem cliente é recusado")
    void pedidoSemClienteRecusado() {
        PedidoRequest request = request(item("p1", 1));
        request.setClienteId(" ");

        assertThrows(IllegalArgumentException.class, () -> pedidoService.criarPedido(request));
        verify(pedidoRepository, never()).save(any());
    }

    // ===================== RF04: desconto exclusivo do gerente (máx. 20%) =====================

    @Test
    @DisplayName("RF04: desconto de 10% recalcula o total e é auditado")
    void descontoValidoRecalculaTotal() {
        when(pedidoRepository.findById("ped1")).thenReturn(Optional.of(pedidoDe100(StatusPedido.RECEBIDO)));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));

        Pedido pedido = pedidoService.aplicarDesconto("ped1", new BigDecimal("10"));

        assertIgual("100.00", pedido.getValorItens());
        assertIgual("10.00", pedido.getValorDesconto());
        assertIgual("90.00", pedido.getValorTotal());
        assertEquals("usuario.teste", pedido.getDescontoAplicadoPor());
        verify(auditoriaService).registrar(eq("APLICAÇÃO DE DESCONTO"), eq("PEDIDO"), any(), anyList(), isNull());
    }

    @Test
    @DisplayName("RF04: desconto de exatamente 20% é aceito (limite)")
    void descontoNoLimiteAceito() {
        when(pedidoRepository.findById("ped1")).thenReturn(Optional.of(pedidoDe100(StatusPedido.RECEBIDO)));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));

        Pedido pedido = pedidoService.aplicarDesconto("ped1", new BigDecimal("20"));

        assertIgual("80.00", pedido.getValorTotal());
    }

    @Test
    @DisplayName("RF04: desconto acima de 20% é recusado")
    void descontoAcimaDoLimiteRecusado() {
        when(pedidoRepository.findById("ped1")).thenReturn(Optional.of(pedidoDe100(StatusPedido.RECEBIDO)));

        assertThrows(IllegalArgumentException.class,
                () -> pedidoService.aplicarDesconto("ped1", new BigDecimal("20.01")));
        verify(pedidoRepository, never()).save(any());
        verifyNoInteractions(auditoriaService);
    }

    @Test
    @DisplayName("RF04: desconto zero ou negativo é recusado")
    void descontoZeroRecusado() {
        when(pedidoRepository.findById("ped1")).thenReturn(Optional.of(pedidoDe100(StatusPedido.RECEBIDO)));

        assertThrows(IllegalArgumentException.class,
                () -> pedidoService.aplicarDesconto("ped1", BigDecimal.ZERO));
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    @DisplayName("RF04: desconto em pedido que já saiu é recusado")
    void descontoAposSaidaRecusado() {
        when(pedidoRepository.findById("ped1")).thenReturn(Optional.of(pedidoDe100(StatusPedido.FINALIZADO)));

        assertThrows(IllegalStateException.class,
                () -> pedidoService.aplicarDesconto("ped1", new BigDecimal("10")));
        verify(pedidoRepository, never()).save(any());
    }

    // ===================== RF04: cancelamento exclusivo do gerente =====================

    @Test
    @DisplayName("RF04: cancelamento com justificativa preserva o histórico e é auditado")
    void cancelamentoValido() {
        when(pedidoRepository.findById("ped1")).thenReturn(Optional.of(pedidoDe100(StatusPedido.RECEBIDO)));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));

        Pedido pedido = pedidoService.cancelarPedido("ped1", "  Cliente desistiu  ");

        assertEquals(StatusPedido.CANCELADO, pedido.getStatus());
        assertEquals("usuario.teste", pedido.getCanceladoPor());
        assertEquals("Cliente desistiu", pedido.getJustificativaCancelamento());
        assertIgual("100.00", pedido.getValorTotal()); // histórico preservado
        verify(auditoriaService).registrar(eq("CANCELAMENTO DE PEDIDO"), eq("PEDIDO"), any(), anyList(),
                eq("Cliente desistiu"));
    }

    @Test
    @DisplayName("RF04: cancelamento sem justificativa é recusado")
    void cancelamentoSemJustificativaRecusado() {
        when(pedidoRepository.findById("ped1")).thenReturn(Optional.of(pedidoDe100(StatusPedido.RECEBIDO)));

        assertThrows(IllegalArgumentException.class, () -> pedidoService.cancelarPedido("ped1", "   "));
        verify(pedidoRepository, never()).save(any());
        verifyNoInteractions(auditoriaService);
    }

    @Test
    @DisplayName("RF04: cancelamento de pedido que já saiu é recusado")
    void cancelamentoAposSaidaRecusado() {
        when(pedidoRepository.findById("ped1")).thenReturn(Optional.of(pedidoDe100(StatusPedido.FINALIZADO)));

        assertThrows(IllegalStateException.class,
                () -> pedidoService.cancelarPedido("ped1", "Motivo qualquer"));
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    @DisplayName("RF04: pedido já cancelado não pode ser cancelado de novo")
    void cancelamentoDuplicadoRecusado() {
        when(pedidoRepository.findById("ped1")).thenReturn(Optional.of(pedidoDe100(StatusPedido.CANCELADO)));

        assertThrows(IllegalStateException.class,
                () -> pedidoService.cancelarPedido("ped1", "Motivo qualquer"));
        verify(pedidoRepository, never()).save(any());
    }

    // ===================== RF03: fluxo de status =====================

    @Test
    @DisplayName("RF03: pedido cancelado não pode ter o status alterado")
    void canceladoEhImutavel() {
        when(pedidoRepository.findById("ped1")).thenReturn(Optional.of(pedidoDe100(StatusPedido.CANCELADO)));

        assertThrows(IllegalStateException.class,
                () -> pedidoService.atualizarStatus("ped1", StatusPedido.RECEBIDO));
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    @DisplayName("RF03: cancelar pela troca de status é bloqueado (só pelo cancelamento do gerente)")
    void cancelarPeloStatusBloqueado() {
        when(pedidoRepository.findById("ped1")).thenReturn(Optional.of(pedidoDe100(StatusPedido.RECEBIDO)));

        assertThrows(IllegalStateException.class,
                () -> pedidoService.atualizarStatus("ped1", StatusPedido.CANCELADO));
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    @DisplayName("RF03: transição que pula etapas é recusada (RECEBIDO → FINALIZADO)")
    void transicaoInvalidaRecusada() {
        when(pedidoRepository.findById("ped1")).thenReturn(Optional.of(pedidoDe100(StatusPedido.RECEBIDO)));

        assertThrows(IllegalStateException.class,
                () -> pedidoService.atualizarStatus("ped1", StatusPedido.FINALIZADO));
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Pedido inexistente gera erro")
    void pedidoInexistente() {
        when(pedidoRepository.findById("nao-existe")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> pedidoService.cancelarPedido("nao-existe", "Motivo"));
    }

    // ===================== Auxiliares =====================

    private static void assertIgual(String esperado, BigDecimal atual) {
        // compareTo ignora a escala (100.0 == 100.00)
        assertEquals(0, new BigDecimal(esperado).compareTo(atual),
                "Esperado " + esperado + " mas foi " + atual);
    }

    private static Cliente cliente() {
        Cliente c = new Cliente();
        c.setId("c1");
        c.setNome("Cliente Teste");
        return c;
    }

    private static Produto produto(String id, String nome, double preco) {
        Produto p = new Produto();
        p.setId(id);
        p.setNome(nome);
        p.setPreco(preco);
        p.setAtivo(true);
        return p;
    }

    private static PedidoRequest.Item item(String produtoId, int quantidade) {
        PedidoRequest.Item item = new PedidoRequest.Item();
        item.setProdutoId(produtoId);
        item.setQuantidade(quantidade);
        return item;
    }

    private static PedidoRequest request(PedidoRequest.Item... itens) {
        PedidoRequest request = new PedidoRequest();
        request.setClienteId("c1");
        for (PedidoRequest.Item item : itens) {
            request.getItens().add(item);
        }
        return request;
    }

    // Pedido já existente com 1 item de R$ 100,00, sem desconto
    private static Pedido pedidoDe100(StatusPedido status) {
        ItemPedido item = new ItemPedido();
        item.setProdutoId("p1");
        item.setNomeProduto("Pizza Teste");
        item.setQuantidade(1);
        item.setPrecoUnitario(new BigDecimal("100.00"));
        item.setSubtotal(new BigDecimal("100.00"));

        Pedido pedido = new Pedido();
        pedido.getItens().add(item);
        pedido.setStatus(status);
        pedido.setPercentualDesconto(BigDecimal.ZERO);
        pedido.setValorItens(new BigDecimal("100.00"));
        pedido.setValorDesconto(BigDecimal.ZERO);
        pedido.setValorTotal(new BigDecimal("100.00"));
        return pedido;
    }
}
