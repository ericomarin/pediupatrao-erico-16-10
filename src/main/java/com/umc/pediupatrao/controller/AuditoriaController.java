package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.entity.Auditoria;
import com.umc.pediupatrao.service.AuditoriaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

// Somente consulta. Não existem rotas para alterar ou excluir registros de auditoria.
@Controller
public class AuditoriaController {

    @Autowired
    private AuditoriaService auditoriaService;

    // Tela de auditoria
    @GetMapping("/auditoria")
    public String listar(Model model) {
        model.addAttribute("registros", auditoriaService.listar());
        model.addAttribute("content", "auditoria/lista :: content");
        return "layout";
    }

    // API de auditoria (JSON)
    @GetMapping("/api/auditoria")
    @ResponseBody
    public List<Auditoria> listarApi() {
        return auditoriaService.listar();
    }
}
