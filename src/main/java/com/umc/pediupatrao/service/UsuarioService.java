package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.UsuarioRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // Busca um usuário por ID
    public Optional<Usuario> buscarPorId(String id) {
        return usuarioRepository.findById(id);
    }

    // Busca um usuário por username
    public Optional<Usuario> buscarPorUsername(String username) {
        return usuarioRepository.findByUsername(username);
    }

    // Salva um novo usuário (criptografando a senha)
    public void salvarUsuario(Usuario usuario) {
        // Validações para impedir cadastro sem perfil, sem senha ou com username repetido
        if (usuario.getRole() == null) {
            throw new IllegalArgumentException("O perfil do usuário é obrigatório.");
        }
        if (usuario.getPassword() == null || usuario.getPassword().isBlank()) {
            throw new IllegalArgumentException("A senha é obrigatória.");
        }
        if (usuarioRepository.findByUsername(usuario.getUsername()).isPresent()) {
            throw new IllegalArgumentException("Já existe um usuário com este username.");
        }

        usuario.setId(null); // AJUSTE: garante que é um cadastro novo, e não a sobrescrita de outro usuário
        usuario.setPassword(passwordEncoder.encode(usuario.getPassword())); // Criptografa a senha
        usuarioRepository.save(usuario);
    }

    // Deleta um usuário pelo ID
    public void deletarUsuario(String id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));

        // Impede que o usuário logado exclua a própria conta
        if (usuario.getUsername().equals(usernameLogado())) {
            throw new IllegalStateException("Você não pode excluir o seu próprio usuário.");
        }

        usuarioRepository.deleteById(id);
    }

    // Lista todos os usuários
    public Iterable<Usuario> listarTodos() {
        return usuarioRepository.findAll();
    }

    // Atualiza o usuário usuários
    public void atualizarUsuario(String id, Usuario usuarioAtualizado) {
        Usuario usuarioExistente = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));

        // Proteção contra autoelevação, ninguém altera o próprio perfil
        boolean ehOProprioUsuario = usuarioExistente.getUsername().equals(usernameLogado());
        boolean mudouPerfil = usuarioAtualizado.getRole() != null
                && usuarioAtualizado.getRole() != usuarioExistente.getRole();

        if (ehOProprioUsuario && mudouPerfil) {
            throw new IllegalStateException("Você não pode alterar o seu próprio perfil.");
        }

        usuarioExistente.setUsername(usuarioAtualizado.getUsername());
        if (usuarioAtualizado.getRole() != null) {
            usuarioExistente.setRole(usuarioAtualizado.getRole());
        }

        // Só atualiza a senha se uma nova foi informada
        if (usuarioAtualizado.getPassword() != null && !usuarioAtualizado.getPassword().isBlank()) {
            usuarioExistente.setPassword(passwordEncoder.encode(usuarioAtualizado.getPassword()));
        }

        usuarioRepository.save(usuarioExistente);
    }

    // Retorna o username de quem está logado no momento
    private String usernameLogado() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}