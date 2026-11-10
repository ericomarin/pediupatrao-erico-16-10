package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.PedidoRequest;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

// Prova a consistência entre pedidos e auditoria 
// se o registro de auditoria falhar, a gravação do pedido é desfeita (rollback).
// Pré-requisito: MongoDB em modo replica set (rs0).

@SpringBootTest
@TestPropertySource(properties = {
        "spring.data.mongodb.database=pizzaria_teste",
        "app.dados-demo=false"
})
class PedidoTransacaoIntegrationTest {

    @Autowired
    private PedidoService pedidoService;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private ProdutoRepository produtoRepository;

    // Substitui a auditoria real por um "dublê" que podemos fazer falhar
    @MockitoBean
    private AuditoriaService auditoriaService;

    @Test
    @WithMockUser(username = "atendente", roles = "ATENDENTE")
    @DisplayName("Falha na auditoria desfaz a gravação do pedido (rollback)")
    void falhaNaAuditoriaDesfazPedido() {
        PedidoRequest request = novoPedidoRequest();
        doThrow(new RuntimeException("Falha simulada na auditoria"))
                .when(auditoriaService).registrar(any(), any(), any(), any(), any());

        long pedidosAntes = pedidoRepository.count();

        assertThrows(RuntimeException.class, () -> pedidoService.criarPedido(request));

        assertEquals(pedidosAntes, pedidoRepository.count(),
                "O pedido não pode ficar gravado sem o registro de auditoria");
    }

    @Test
    @WithMockUser(username = "atendente", roles = "ATENDENTE")
    @DisplayName("Com a auditoria funcionando, o pedido é gravado normalmente (commit)")
    void auditoriaComSucessoGravaPedido() {
        PedidoRequest request = novoPedidoRequest();
        long pedidosAntes = pedidoRepository.count();

        pedidoService.criarPedido(request);

        assertEquals(pedidosAntes + 1, pedidoRepository.count());
    }

    // Cria um cliente e um produto no banco de teste e monta a requisição do pedido
    private PedidoRequest novoPedidoRequest() {
        Cliente cliente = new Cliente();
        cliente.setNome("Cliente Teste Transação");
        cliente = clienteRepository.save(cliente);

        Produto produto = new Produto();
        produto.setNome("Pizza Teste Transação");
        produto.setTipo("Pizza");
        produto.setPreco(40.0);
        produto.setAtivo(true);
        produto = produtoRepository.save(produto);

        PedidoRequest.Item item = new PedidoRequest.Item();
        item.setProdutoId(produto.getId());
        item.setQuantidade(2);

        PedidoRequest request = new PedidoRequest();
        request.setClienteId(cliente.getId());
        request.getItens().add(item);
        return request;
    }
}