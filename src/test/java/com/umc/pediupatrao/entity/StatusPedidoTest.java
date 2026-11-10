package com.umc.pediupatrao.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Testes do fluxo de status do pedido e das regras de "antes da saída"
class StatusPedidoTest {

    @Test
    @DisplayName("fluxo completo de entrega é permitido")
    void fluxoDeEntrega() {
        assertTrue(StatusPedido.RECEBIDO.podeAvancarPara(StatusPedido.EM_PREPARACAO));
        assertTrue(StatusPedido.EM_PREPARACAO.podeAvancarPara(StatusPedido.PRONTO));
        assertTrue(StatusPedido.PRONTO.podeAvancarPara(StatusPedido.SAIU_PARA_ENTREGA));
        assertTrue(StatusPedido.SAIU_PARA_ENTREGA.podeAvancarPara(StatusPedido.FINALIZADO));
    }

    @Test
    @DisplayName("fluxo completo de retirada no balcão é permitido")
    void fluxoDeRetirada() {
        assertTrue(StatusPedido.PRONTO.podeAvancarPara(StatusPedido.RETIRADO));
        assertTrue(StatusPedido.RETIRADO.podeAvancarPara(StatusPedido.FINALIZADO));
    }

    @Test
    @DisplayName("não é possível pular etapas")
    void naoPulaEtapas() {
        assertFalse(StatusPedido.RECEBIDO.podeAvancarPara(StatusPedido.PRONTO));
        assertFalse(StatusPedido.RECEBIDO.podeAvancarPara(StatusPedido.FINALIZADO));
        assertFalse(StatusPedido.EM_PREPARACAO.podeAvancarPara(StatusPedido.SAIU_PARA_ENTREGA));
        assertFalse(StatusPedido.PRONTO.podeAvancarPara(StatusPedido.FINALIZADO));
    }

    @Test
    @DisplayName("não é possível voltar para uma etapa anterior")
    void naoVoltaEtapa() {
        assertFalse(StatusPedido.PRONTO.podeAvancarPara(StatusPedido.RECEBIDO));
        assertFalse(StatusPedido.EM_PREPARACAO.podeAvancarPara(StatusPedido.RECEBIDO));
        assertFalse(StatusPedido.FINALIZADO.podeAvancarPara(StatusPedido.PRONTO));
    }

    @ParameterizedTest(name = "{0} é estado final, sem transições")
    @EnumSource(value = StatusPedido.class, names = {"FINALIZADO", "CANCELADO"})
    void estadosFinaisSemTransicao(StatusPedido status) {
        assertTrue(status.proximosPermitidos().isEmpty());
    }

    @ParameterizedTest(name = "{0} não chega a CANCELADO pelo fluxo normal")
    @EnumSource(StatusPedido.class)
    void canceladoSoPeloGerente(StatusPedido status) {
        // O cancelamento é uma operação separada, exclusiva do gerente
        assertFalse(status.podeAvancarPara(StatusPedido.CANCELADO));
    }

    @ParameterizedTest(name = "{0} está antes da saída (permite cancelar/descontar)")
    @EnumSource(value = StatusPedido.class, names = {"RECEBIDO", "EM_PREPARACAO", "PRONTO"})
    void permiteAntesDaSaida(StatusPedido status) {
        assertTrue(status.antesDaSaida());
    }

    @ParameterizedTest(name = "{0} já saiu ou encerrou (bloqueia cancelar/descontar)")
    @EnumSource(value = StatusPedido.class, names = {"SAIU_PARA_ENTREGA", "RETIRADO", "FINALIZADO", "CANCELADO"})
    void bloqueiaAposSaida(StatusPedido status) {
        assertFalse(status.antesDaSaida());
    }

    @Test
    @DisplayName("só SAIU_PARA_ENTREGA e RETIRADO representam saída")
    void statusDeSaida() {
        assertTrue(StatusPedido.SAIU_PARA_ENTREGA.representaSaida());
        assertTrue(StatusPedido.RETIRADO.representaSaida());
        assertFalse(StatusPedido.RECEBIDO.representaSaida());
        assertFalse(StatusPedido.EM_PREPARACAO.representaSaida());
        assertFalse(StatusPedido.PRONTO.representaSaida());
        assertFalse(StatusPedido.FINALIZADO.representaSaida());
        assertFalse(StatusPedido.CANCELADO.representaSaida());
    }
}