package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.UsuarioRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner initDatabase(UsuarioRepository repo, PasswordEncoder encoder) {
        return args -> {
            criarSeNaoExistir(repo, encoder, "admin", "teste123", Perfil.ADMIN);
            criarSeNaoExistir(repo, encoder, "gerente", "teste123", Perfil.GERENTE);
            criarSeNaoExistir(repo, encoder, "atendente", "teste123", Perfil.ATENDENTE);
        };
    }

    private void criarSeNaoExistir(UsuarioRepository repo, PasswordEncoder encoder,
                                   String username, String senha, Perfil perfil) {

        repo.findByUsername(username).ifPresentOrElse(

            // Já existe, se estiver sem perfil (dados antigos), faz a correcao.

            existente -> {
                if (existente.getRole() == null) {
                    existente.setRole(perfil);
                    repo.save(existente);
                    System.out.println("Perfil " + perfil + " atribuído ao usuário " + username);
                }
            },

            // Não existe, cria novo usuário com o perfil especificado
            
            () -> {
                Usuario novo = new Usuario();
                novo.setUsername(username);
                novo.setPassword(encoder.encode(senha));
                novo.setRole(perfil);
                repo.save(novo);
                System.out.println("Usuário " + username + " (" + perfil + ") criado!");
            }
        );
    }
}
