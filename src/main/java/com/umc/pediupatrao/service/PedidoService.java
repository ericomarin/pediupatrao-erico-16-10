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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class PedidoService {

    private static final BigDecimal DESCONTO_MAXIMO = new BigDecimal("20");

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private ProdutoRepository produtoRepository;

    // Registra um novo pedido. Preços e total são calculados aqui, nunca recebidos da requisição
    @PreAuthorize("hasRole('ATENDENTE')")
    public Pedido criarPedido(PedidoRequest request) {
        if (request.getClienteId() == null || request.getClienteId().isBlank()) {
            throw new IllegalArgumentException("Informe o cliente do pedido.");
        }
        Cliente cliente = clienteRepository.findById(request.getClienteId())
                .orElseThrow(() -> new IllegalArgumentException("Cliente não encontrado."));

        Pedido pedido = new Pedido();
        pedido.setClienteId(cliente.getId());
        pedido.setClienteNome(cliente.getNome());

        for (PedidoRequest.Item item : request.getItens()) {
            // Produtos com quantidade vazia ou zero no formulário não entram no pedido
            if (item.getQuantidade() == null || item.getQuantidade() == 0) {
                continue;
            }
            if (item.getQuantidade() < 0) {
                throw new IllegalArgumentException("A quantidade não pode ser negativa.");
            }

            Produto produto = produtoRepository.findById(item.getProdutoId())
                    .orElseThrow(() -> new IllegalArgumentException("Produto não encontrado."));
            if (!Boolean.TRUE.equals(produto.getAtivo()) || produto.getPreco() == null) {
                throw new IllegalArgumentException("Produto indisponível: " + produto.getNome());
            }

            ItemPedido itemPedido = new ItemPedido();
            itemPedido.setProdutoId(produto.getId());
            itemPedido.setNomeProduto(produto.getNome());
            itemPedido.setQuantidade(item.getQuantidade());
            itemPedido.setPrecoUnitario(BigDecimal.valueOf(produto.getPreco()));
            itemPedido.setSubtotal(itemPedido.getPrecoUnitario()
                    .multiply(BigDecimal.valueOf(item.getQuantidade())));
            pedido.getItens().add(itemPedido);
        }

        if (pedido.getItens().isEmpty()) {
            throw new IllegalArgumentException("O pedido precisa ter pelo menos um item.");
        }

        pedido.setPercentualDesconto(BigDecimal.ZERO);
        recalcularValores(pedido);

        // Entrada do pedido, com data/hora e responsável
        pedido.setStatus(StatusPedido.RECEBIDO);
        pedido.setRegistradoPor(usuarioLogado());
        pedido.setDataHoraEntrada(LocalDateTime.now());

        return pedidoRepository.save(pedido);
    }

    public List<Pedido> listarPedidos() {
        return pedidoRepository.findAll();
    }

    // Avança o pedido no fluxo, bloqueando transições inválidas
    @PreAuthorize("hasAnyRole('ATENDENTE', 'GERENTE')")
    public Pedido atualizarStatus(String id, StatusPedido novoStatus) {
        Pedido pedido = buscarPedido(id);

        if (pedido.getStatus() == StatusPedido.CANCELADO) {
            throw new IllegalStateException("Pedido cancelado não pode ter o status alterado.");
        }
        if (novoStatus == StatusPedido.CANCELADO) {
            throw new IllegalStateException("Para cancelar, use a operação de cancelamento (exclusiva do gerente).");
        }
        if (!pedido.getStatus().podeAvancarPara(novoStatus)) {
            throw new IllegalStateException("Transição inválida: " + pedido.getStatus() + " → " + novoStatus + ".");
        }

        pedido.setStatus(novoStatus);

        // Saída obrigatoriamente registra responsável e data/hora
        if (novoStatus.representaSaida()) {
            pedido.setResponsavelSaida(usuarioLogado());
            pedido.setDataHoraSaida(LocalDateTime.now());
        }
        if (novoStatus == StatusPedido.FINALIZADO) {
            pedido.setDataHoraFinalizacao(LocalDateTime.now());
        }

        return pedidoRepository.save(pedido);
    }

    // Cancelamento exclusivo do GERENTE, com justificativa, antes da saída
    @PreAuthorize("hasRole('GERENTE')")
    public Pedido cancelarPedido(String id, String justificativa) {
        Pedido pedido = buscarPedido(id);

        if (justificativa == null || justificativa.isBlank()) {
            throw new IllegalArgumentException("A justificativa do cancelamento é obrigatória.");
        }
        if (pedido.getStatus() == StatusPedido.CANCELADO) {
            throw new IllegalStateException("O pedido já está cancelado.");
        }
        if (!pedido.getStatus().antesDaSaida()) {
            throw new IllegalStateException("Não é possível cancelar um pedido que já saiu para entrega ou foi retirado.");
        }

        // Os dados históricos (itens, valores, entrada) são preservados
        pedido.setStatus(StatusPedido.CANCELADO);
        pedido.setCanceladoPor(usuarioLogado());
        pedido.setDataHoraCancelamento(LocalDateTime.now());
        pedido.setJustificativaCancelamento(justificativa.trim());

        return pedidoRepository.save(pedido);
    }

    // Desconto percentual exclusivo do GERENTE, limitado a 20%, antes da saída
    @PreAuthorize("hasRole('GERENTE')")
    public Pedido aplicarDesconto(String id, BigDecimal percentual) {
        Pedido pedido = buscarPedido(id);

        if (percentual == null
                || percentual.compareTo(BigDecimal.ZERO) <= 0
                || percentual.compareTo(DESCONTO_MAXIMO) > 0) {
            throw new IllegalArgumentException("O desconto deve ser maior que 0% e no máximo 20%.");
        }
        if (!pedido.getStatus().antesDaSaida()) {
            throw new IllegalStateException("O desconto só pode ser aplicado em pedidos ativos, antes da saída.");
        }

        pedido.setPercentualDesconto(percentual);
        pedido.setDescontoAplicadoPor(usuarioLogado());
        recalcularValores(pedido);

        return pedidoRepository.save(pedido);
    }

    // Calcula valor dos itens, desconto e total (sempre no servidor)
    private void recalcularValores(Pedido pedido) {
        BigDecimal valorItens = pedido.getItens().stream()
                .map(ItemPedido::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal valorDesconto = valorItens
                .multiply(pedido.getPercentualDesconto())
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

        pedido.setValorItens(valorItens);
        pedido.setValorDesconto(valorDesconto);
        pedido.setValorTotal(valorItens.subtract(valorDesconto));
    }

    private Pedido buscarPedido(String id) {
        return pedidoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Pedido não encontrado."));
    }

    private String usuarioLogado() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}