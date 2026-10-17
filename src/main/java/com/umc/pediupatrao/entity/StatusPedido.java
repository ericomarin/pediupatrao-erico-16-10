package com.umc.pediupatrao.entity;

import java.util.List;

// Estados do pedido e as transições permitidas entre eles
public enum StatusPedido {
    RECEBIDO,
    EM_PREPARACAO,
    PRONTO,
    SAIU_PARA_ENTREGA,
    RETIRADO,
    FINALIZADO,
    CANCELADO;

    // Para quais status o pedido pode avançar a partir do status atual
    public List<StatusPedido> proximosPermitidos() {
        return switch (this) {
            case RECEBIDO -> List.of(EM_PREPARACAO);
            case EM_PREPARACAO -> List.of(PRONTO);
            case PRONTO -> List.of(SAIU_PARA_ENTREGA, RETIRADO);
            case SAIU_PARA_ENTREGA, RETIRADO -> List.of(FINALIZADO);
            case FINALIZADO, CANCELADO -> List.of(); // estados finais: nenhuma transição
        };
    }

    public boolean podeAvancarPara(StatusPedido novoStatus) {
        return proximosPermitidos().contains(novoStatus);
    }

    // Cancelamento e desconto só são permitidos antes da saída do pedido
    public boolean antesDaSaida() {
        return this == RECEBIDO || this == EM_PREPARACAO || this == PRONTO;
    }

    // Status que representam a saída do pedido (exigem responsável e data/hora)
    public boolean representaSaida() {
        return this == SAIU_PARA_ENTREGA || this == RETIRADO;
    }
}
