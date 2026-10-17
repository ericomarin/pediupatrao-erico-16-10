package com.umc.pediupatrao.entity;

import java.math.BigDecimal;

// Item de um pedido (fica no documento do Pedido no MongoDB)
public class ItemPedido {

    private String produtoId;
    private String nomeProduto;
    private int quantidade;
    private BigDecimal precoUnitario; // preço copiado do produto no momento do pedido
    private BigDecimal subtotal;      // precoUnitario x quantidade

    public String getProdutoId() {
        return produtoId;
    }

    public void setProdutoId(String produtoId) {
        this.produtoId = produtoId;
    }

    public String getNomeProduto() {
        return nomeProduto;
    }

    public void setNomeProduto(String nomeProduto) {
        this.nomeProduto = nomeProduto;
    }

    public int getQuantidade() {
        return quantidade;
    }

    public void setQuantidade(int quantidade) {
        this.quantidade = quantidade;
    }

    public BigDecimal getPrecoUnitario() {
        return precoUnitario;
    }

    public void setPrecoUnitario(BigDecimal precoUnitario) {
        this.precoUnitario = precoUnitario;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }
}