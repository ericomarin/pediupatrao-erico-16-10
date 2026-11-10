package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Testes das regras de usuários: perfil obrigatório, username único, senha criptografada,
// proteção contra autoelevação e contra exclusão da própria conta.
@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UsuarioService usuarioService;

    @BeforeEach
    void preparar() {
        usuarioService = new UsuarioService(usuarioRepository, passwordEncoder);
    }

    @AfterEach
    void limparLogin() {
        SecurityContextHolder.clearContext();
    }

    // ===================== Cadastro =====================

    @Test
    @DisplayName("cadastro sem perfil é recusado")
    void cadastroSemPerfilRecusado() {
        Usuario usuario = usuario(null, "novo", null, "SenhaForte1");

        assertThrows(IllegalArgumentException.class, () -> usuarioService.salvarUsuario(usuario));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("cadastro sem senha é recusado")
    void cadastroSemSenhaRecusado() {
        Usuario usuario = usuario(null, "novo", Perfil.ATENDENTE, "   ");

        assertThrows(IllegalArgumentException.class, () -> usuarioService.salvarUsuario(usuario));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("username repetido é recusado")
    void usernameDuplicadoRecusado() {
        Usuario existente = usuario("u1", "gerente", Perfil.GERENTE, "hash");
        when(usuarioRepository.findByUsername("gerente")).thenReturn(Optional.of(existente));

        Usuario novo = usuario(null, "gerente", Perfil.ATENDENTE, "SenhaForte1");

        assertThrows(IllegalArgumentException.class, () -> usuarioService.salvarUsuario(novo));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("senha é gravada criptografada e o ID enviado é descartado")
    void cadastroValidoCriptografaSenha() {
        when(usuarioRepository.findByUsername("novo")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("SenhaForte1")).thenReturn("HASH-BCRYPT");

        // Tentativa de sobrescrever outro usuário enviando um ID existente
        Usuario usuario = usuario("id-de-outro", "novo", Perfil.ATENDENTE, "SenhaForte1");
        usuarioService.salvarUsuario(usuario);

        assertEquals("HASH-BCRYPT", usuario.getPassword()); // nunca em texto puro
        assertNull(usuario.getId());                        // sempre cadastro novo
        verify(usuarioRepository).save(usuario);
    }

    // ===================== Exclusão =====================

    @Test
    @DisplayName("usuário não pode excluir a própria conta")
    void naoExcluiPropriaConta() {
        logarComo("admin");
        when(usuarioRepository.findById("u1"))
                .thenReturn(Optional.of(usuario("u1", "admin", Perfil.ADMIN, "hash")));

        assertThrows(IllegalStateException.class, () -> usuarioService.deletarUsuario("u1"));
        verify(usuarioRepository, never()).deleteById(anyString());
    }

    @Test
    @DisplayName("admin pode excluir outro usuário")
    void excluiOutroUsuario() {
        logarComo("admin");
        when(usuarioRepository.findById("u2"))
                .thenReturn(Optional.of(usuario("u2", "atendente", Perfil.ATENDENTE, "hash")));

        usuarioService.deletarUsuario("u2");

        verify(usuarioRepository).deleteById("u2");
    }

    @Test
    @DisplayName("Excluir usuário inexistente gera erro")
    void excluirInexistente() {
        when(usuarioRepository.findById("nao-existe")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> usuarioService.deletarUsuario("nao-existe"));
    }

    // ===================== Atualização =====================

    @Test
    @DisplayName("usuário não pode alterar o próprio perfil (autoelevação)")
    void bloqueiaAutoelevacao() {
        logarComo("atendente");
        when(usuarioRepository.findById("u2"))
                .thenReturn(Optional.of(usuario("u2", "atendente", Perfil.ATENDENTE, "hash")));

        Usuario tentativa = usuario(null, "atendente", Perfil.ADMIN, null);

        assertThrows(IllegalStateException.class, () -> usuarioService.atualizarUsuario("u2", tentativa));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("admin pode alterar o perfil de outro usuário")
    void adminAlteraPerfilDeOutro() {
        logarComo("admin");
        Usuario existente = usuario("u2", "atendente", Perfil.ATENDENTE, "hash");
        when(usuarioRepository.findById("u2")).thenReturn(Optional.of(existente));

        usuarioService.atualizarUsuario("u2", usuario(null, "atendente", Perfil.GERENTE, null));

        assertEquals(Perfil.GERENTE, existente.getRole());
        verify(usuarioRepository).save(existente);
    }

    @Test
    @DisplayName("Atualizar sem informar senha mantém a senha atual")
    void atualizarSemSenhaMantemSenha() {
        logarComo("admin");
        Usuario existente = usuario("u2", "atendente", Perfil.ATENDENTE, "hash-antigo");
        when(usuarioRepository.findById("u2")).thenReturn(Optional.of(existente));

        usuarioService.atualizarUsuario("u2", usuario(null, "atendente", Perfil.ATENDENTE, ""));

        assertEquals("hash-antigo", existente.getPassword());
        verify(passwordEncoder, never()).encode(anyString());
    }

    // ===================== Auxiliares =====================

    private static void logarComo(String username) {
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken(username, null));
    }

    private static Usuario usuario(String id, String username, Perfil role, String senha) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setUsername(username);
        u.setRole(role);
        u.setPassword(senha);
        return u;
    }
}