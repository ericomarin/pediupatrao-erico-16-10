package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// Gera pedidos de demonstração para o dashboard (ativado por app.dados-demo=true).
// Não passa pelos services, são dados de exemplo e, por isso, não geram auditoria.

@Configuration
public class DadosDemonstracao {

    // Horários com mais peso no almoço e no jantar (picos de uma pizzaria)

    private static final int[] HORARIOS = {11, 12, 12, 13, 18, 19, 19, 20, 20, 20, 21, 21, 22, 23};

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE) 
    @ConditionalOnProperty(name = "app.dados-demo", havingValue = "true")
    CommandLineRunner gerarDadosDemonstracao(PedidoRepository pedidoRepo,
                                             ClienteRepository clienteRepo,
                                             ProdutoRepository produtoRepo) {
        return args -> {
            if (pedidoRepo.count() >= 50) {
                return; 
            }
            List<Produto> produtos = produtoRepo.findAll().stream()
                    .filter(p -> Boolean.TRUE.equals(p.getAtivo()) && p.getPreco() != null)
                    .toList();
            if (produtos.isEmpty()) {
                return;
            }
            List<Cliente> clientes = garantirClientes(clienteRepo);

            Random random = new Random(42); 
            LocalDate hoje = LocalDate.now();
            List<Pedido> pedidos = new ArrayList<>();

            for (int diasAtras = 29; diasAtras >= 0; diasAtras--) {
                LocalDate data = hoje.minusDays(diasAtras);
                boolean fimDeSemana = data.getDayOfWeek() == DayOfWeek.FRIDAY
                        || data.getDayOfWeek() == DayOfWeek.SATURDAY
                        || data.getDayOfWeek() == DayOfWeek.SUNDAY;
                int quantidade = 3 + random.nextInt(4) + (fimDeSemana ? 3 : 0);

                for (int i = 0; i < quantidade; i++) {
                    pedidos.add(novoPedido(data, diasAtras == 0, clientes, produtos, random));
                }
            }

            pedidoRepo.saveAll(pedidos);
            System.out.println(pedidos.size() + " pedidos de demonstração gerados para o dashboard.");
        };
    }

    private List<Cliente> garantirClientes(ClienteRepository clienteRepo) {
        if (clienteRepo.count() == 0) {
            String[] nomes = {"Ana Souza", "Bruno Lima", "Carla Mendes", "Diego Alves", "Elisa Rocha"};
            for (String nome : nomes) {
                Cliente cliente = new Cliente();
                cliente.setNome(nome);
                cliente.setTelefone("(11) 90000-0000");
                clienteRepo.save(cliente);
            }
        }
        return clienteRepo.findAll();
    }

    private Pedido novoPedido(LocalDate data, boolean ehHoje, List<Cliente> clientes,
                              List<Produto> produtos, Random random) {
        Pedido pedido = new Pedido();
        Cliente cliente = clientes.get(random.nextInt(clientes.size()));
        pedido.setClienteId(cliente.getId());
        pedido.setClienteNome(cliente.getNome());

        // Itens (1 a 3 produtos, 1 a 3 unidades cada)

        int quantidadeItens = 1 + random.nextInt(3);
        for (int i = 0; i < quantidadeItens; i++) {
            Produto produto = produtos.get(random.nextInt(produtos.size()));
            int quantidade = 1 + random.nextInt(3);
            ItemPedido item = new ItemPedido();
            item.setProdutoId(produto.getId());
            item.setNomeProduto(produto.getNome());
            item.setQuantidade(quantidade);
            item.setPrecoUnitario(BigDecimal.valueOf(produto.getPreco()));
            item.setSubtotal(item.getPrecoUnitario().multiply(BigDecimal.valueOf(quantidade)));
            pedido.getItens().add(item);
        }

        // Valores (10% dos pedidos com desconto de 5, 10 ou 15%)

        BigDecimal valorItens = pedido.getItens().stream()
                .map(ItemPedido::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal percentual = random.nextInt(10) == 0
                ? BigDecimal.valueOf(5L * (1 + random.nextInt(3)))
                : BigDecimal.ZERO;
        BigDecimal valorDesconto = valorItens.multiply(percentual)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        pedido.setValorItens(valorItens);
        pedido.setPercentualDesconto(percentual);
        pedido.setValorDesconto(valorDesconto);
        pedido.setValorTotal(valorItens.subtract(valorDesconto));
        if (percentual.signum() > 0) {
            pedido.setDescontoAplicadoPor("gerente");
        }

        // Entrada
        int hora = HORARIOS[random.nextInt(HORARIOS.length)];
        if (ehHoje) {
            hora = Math.min(hora, LocalTime.now().getHour()); 
        }
        LocalDateTime entrada = data.atTime(hora, random.nextInt(60));
        pedido.setRegistradoPor("atendente");
        pedido.setDataHoraEntrada(entrada);

        // Status: dias anteriores já encerrados, hoje, pedidos em andamento

        StatusPedido status;
        if (ehHoje) {
            StatusPedido[] emAndamento = {StatusPedido.RECEBIDO, StatusPedido.EM_PREPARACAO,
                    StatusPedido.PRONTO, StatusPedido.SAIU_PARA_ENTREGA};
            status = emAndamento[random.nextInt(emAndamento.length)];
        } else {
            status = random.nextInt(100) < 10 ? StatusPedido.CANCELADO : StatusPedido.FINALIZADO;
        }
        pedido.setStatus(status);

        if (status == StatusPedido.CANCELADO) {
            pedido.setCanceladoPor("gerente");
            pedido.setDataHoraCancelamento(entrada.plusMinutes(10));
            pedido.setJustificativaCancelamento("Cliente desistiu");
        }
        if (status == StatusPedido.SAIU_PARA_ENTREGA || status == StatusPedido.FINALIZADO) {
            pedido.setResponsavelSaida("gerente");
            pedido.setDataHoraSaida(entrada.plusMinutes(35));
        }
        if (status == StatusPedido.FINALIZADO) {
            pedido.setDataHoraFinalizacao(entrada.plusMinutes(70));
        }
        return pedido;
    }
}
