package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.service.UsuarioService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    // Endpoint para listar todos os usuários
    @GetMapping
    public ResponseEntity<Iterable<Usuario>> listarTodos() {
        Iterable<Usuario> usuarios = usuarioService.listarTodos();
        return ResponseEntity.ok(usuarios);
    }

    // Endpoint para buscar um usuário por ID
    @GetMapping("/{id}")
    public ResponseEntity<Usuario> buscarPorId(@PathVariable String id) {
        Optional<Usuario> usuario = usuarioService.buscarPorId(id);
        return usuario.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    // Endpoint para criar um novo usuário
    @PostMapping
    public ResponseEntity<?> criarUsuario(@RequestBody Usuario usuario) {
        try {
            usuarioService.salvarUsuario(usuario);
            return ResponseEntity.status(HttpStatus.CREATED).body(usuario);
        } catch (IllegalArgumentException e) {
            // Dados inválidos (sem perfil, sem senha ou username repetido) retornam 400
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Endpoint para atualizar um usuário
    @PutMapping("/{id}")
    public ResponseEntity<?> atualizarUsuario(@PathVariable String id, @RequestBody Usuario usuarioAtualizado) {
        // A regra de atualização fica no service (inclui o bloqueio de autoelevação)
        try {
            usuarioService.atualizarUsuario(id, usuarioAtualizado);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            // Tentativa de alterar o próprio perfil retorna 403
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        }
    }

    // Endpoint para deletar um usuário
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletarUsuario(@PathVariable String id) {
        Optional<Usuario> usuarioExistente = usuarioService.buscarPorId(id);

        if (usuarioExistente.isPresent()) {
            try {
                usuarioService.deletarUsuario(id);
                return ResponseEntity.noContent().build();
            } catch (IllegalStateException e) {
                // Tentativa de excluir o próprio usuário retorna 403
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
            }
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}
