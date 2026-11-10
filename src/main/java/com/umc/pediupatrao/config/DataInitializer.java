package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.repository.UsuarioRepository;
import org.slf4j.Logger;                                        
import org.slf4j.LoggerFactory;                                 
import org.springframework.beans.factory.annotation.Value;      
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {

    // logger no lugar de System.out.println
    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private static final int TAMANHO_MINIMO_SENHA = 8;

    // senhas iniciais vindas de variáveis de ambiente
    // (SENHA_ADMIN, SENHA_GERENTE, SENHA_ATENDENTE). Nenhuma senha fica fixa no código.
    @Value("${app.senha-inicial.admin:}")
    private String senhaAdmin;

    @Value("${app.senha-inicial.gerente:}")
    private String senhaGerente;

    @Value("${app.senha-inicial.atendente:}")
    private String senhaAtendente;

    @Bean
    @Order(1) // (Dashboard): roda antes do gerador de dados de demonstração, que depende dos produtos
    CommandLineRunner initDatabase(UsuarioRepository repo, ProdutoRepository produtoRepo, PasswordEncoder encoder) {
        return args -> {
            // senha e nome da variável de ambiente correspondente
            criarSeNaoExistir(repo, encoder, "admin", senhaAdmin, "SENHA_ADMIN", Perfil.ADMIN);
            criarSeNaoExistir(repo, encoder, "gerente", senhaGerente, "SENHA_GERENTE", Perfil.GERENTE);
            criarSeNaoExistir(repo, encoder, "atendente", senhaAtendente, "SENHA_ATENDENTE", Perfil.ATENDENTE);
            criarProdutosIniciais(produtoRepo); // produtos de exemplo para registrar pedidos
        };
    }

    private void criarSeNaoExistir(UsuarioRepository repo, PasswordEncoder encoder,
                                   String username, String senha, String variavel, Perfil perfil) {

        repo.findByUsername(username).ifPresentOrElse(

            // Já existe, se estiver sem perfil (dados antigos), faz a correcao.

            existente -> {
                if (existente.getRole() == null) {
                    existente.setRole(perfil);
                    repo.save(existente);
                    log.info("Perfil {} atribuído ao usuário {}", perfil, username);
                }
            },

            // Não existe, cria novo usuário com o perfil especificado

            () -> {
                // só cria se a senha foi informada pela variável de ambiente
                if (senha == null || senha.isBlank()) {
                    log.warn("Usuário '{}' não criado: defina a variável de ambiente {}.", username, variavel);
                    return;
                }
                // exige tamanho mínimo de senha
                if (senha.length() < TAMANHO_MINIMO_SENHA) {
                    log.warn("Usuário '{}' não criado: a senha em {} deve ter ao menos {} caracteres.",
                            username, variavel, TAMANHO_MINIMO_SENHA);
                    return;
                }
                Usuario novo = new Usuario();
                novo.setUsername(username);
                novo.setPassword(encoder.encode(senha)); // a senha nunca é registrada em log
                novo.setRole(perfil);
                repo.save(novo);
                log.info("Usuário {} ({}) criado.", username, perfil);
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
        log.info("Produtos iniciais cadastrados.");
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