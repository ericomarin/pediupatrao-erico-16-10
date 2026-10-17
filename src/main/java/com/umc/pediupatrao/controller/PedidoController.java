package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.PedidoRequest;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.service.PedidoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    @Autowired
    private PedidoService pedidoService;

    // Registrar pedido (ATENDENTE)
    @PostMapping
    public ResponseEntity<?> criarPedido(@RequestBody PedidoRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(pedidoService.criarPedido(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Consultar pedidos (todos os perfis)
    @GetMapping
    public List<Pedido> listarPedidos() {
        return pedidoService.listarPedidos();
    }

    // Avançar status (ATENDENTE e GERENTE)
    @PutMapping("/{id}/status")
    public ResponseEntity<?> atualizarStatus(@PathVariable String id, @RequestParam StatusPedido status) {
        try {
            return ResponseEntity.ok(pedidoService.atualizarStatus(id, status));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
    }

    // Cancelar pedido (GERENTE)
    @PostMapping("/{id}/cancelar")
    public ResponseEntity<?> cancelarPedido(@PathVariable String id, @RequestParam String justificativa) {
        try {
            return ResponseEntity.ok(pedidoService.cancelarPedido(id, justificativa));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
    }

    // Aplicar desconto (GERENTE)
    @PostMapping("/{id}/desconto")
    public ResponseEntity<?> aplicarDesconto(@PathVariable String id, @RequestParam BigDecimal percentual) {
        try {
            return ResponseEntity.ok(pedidoService.aplicarDesconto(id, percentual));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
    }
}
