package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.DashboardDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;

// Tratamento de erros da API do dashboard, devolve 400 com corpo JSON padronizado

@RestControllerAdvice(assignableTypes = DashboardController.class)
public class DashboardExceptionHandler {

    // Regras de negócio dos filtros (datas invertidas, período longo, limite inválido)

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<DashboardDTO.Erro> parametroInvalido(IllegalArgumentException e) {
        return requisicaoInvalida(e.getMessage());
    }

    // Formato inválido (ex.: data "10/11/2026" ou status inexistente)
    
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<DashboardDTO.Erro> tipoInvalido(MethodArgumentTypeMismatchException e) {
        return requisicaoInvalida("Valor inválido para o parâmetro '" + e.getName() + "': " + e.getValue());
    }

    private ResponseEntity<DashboardDTO.Erro> requisicaoInvalida(String mensagem) {
        return ResponseEntity.badRequest()
                .body(new DashboardDTO.Erro(400, "Bad Request", mensagem, LocalDateTime.now()));
    }
}
