package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.Produto;                    // AJUSTE
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.ProdutoRepository;      // AJUSTE
import com.umc.pediupatrao.repository.UsuarioRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner initDatabase(UsuarioRepository repo, ProdutoRepository produtoRepo, PasswordEncoder encoder) {
        return args -> {
            criarSeNaoExistir(repo, encoder, "admin", "teste123", Perfil.ADMIN);
            criarSeNaoExistir(repo, encoder, "gerente", "teste123", Perfil.GERENTE);
            criarSeNaoExistir(repo, encoder, "atendente", "teste123", Perfil.ATENDENTE);
            criarProdutosIniciais(produtoRepo); // AJUSTE: produtos de exemplo para registrar pedidos
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

    // Cadastra produtos de exemplo, caso ainda não exista nenhum
    private void criarProdutosIniciais(ProdutoRepository produtoRepo) {
        if (produtoRepo.count() > 0) {
            return;
        }
        produtoRepo.save(novoProduto("Pizza Calabresa", "Pizza", 49.90));
        produtoRepo.save(novoProduto("Pizza Mussarela", "Pizza", 45.90));
        produtoRepo.save(novoProduto("Pizza Portuguesa", "Pizza", 54.90));
        produtoRepo.save(novoProduto("Refrigerante 2L", "Bebida", 12.00));
        produtoRepo.save(novoProduto("Suco 500ml", "Bebida", 8.50));
        System.out.println("Produtos iniciais cadastrados!");
    }

    // Monta um produto ativo com nome, tipo e preço
    private Produto novoProduto(String nome, String tipo, double preco) {
        Produto produto = new Produto();
        produto.setNome(nome);
        produto.setTipo(tipo);
        produto.setPreco(preco);
        produto.setAtivo(true);
        return produto;
    }
}
