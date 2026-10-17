package com.umc.pediupatrao.dto;

import java.util.ArrayList;
import java.util.List;

// Dados que podem ser enviados ao registrar um pedido.
// Não existem campos de preço ou total, esses valores são sempre calculados no servidor.

public class PedidoRequest {

    private String clienteId;
    private List<Item> itens = new ArrayList<>();

    public static class Item {
        private String produtoId;
        private Integer quantidade;

        public String getProdutoId() {
            return produtoId;
        }

        public void setProdutoId(String produtoId) {
            this.produtoId = produtoId;
        }

        public Integer getQuantidade() {
            return quantidade;
        }

        public void setQuantidade(Integer quantidade) {
            this.quantidade = quantidade;
        }
    }

    public String getClienteId() {
        return clienteId;
    }

    public void setClienteId(String clienteId) {
        this.clienteId = clienteId;
    }

    public List<Item> getItens() {
        return itens;
    }

    public void setItens(List<Item> itens) {
        this.itens = itens;
    }
}