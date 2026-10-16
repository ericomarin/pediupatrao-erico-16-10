package com.umc.pediupatrao.config;

import com.umc.pediupatrao.service.UsuarioDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final UsuarioDetailsService usuarioDetailsService;

    public SecurityConfig(UsuarioDetailsService usuarioDetailsService) {
        this.usuarioDetailsService = usuarioDetailsService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(usuarioDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) 
                .authorizeHttpRequests(auth -> auth

                        // Público
                        .requestMatchers("/login", "/error", "/css/**", "/dist/**",
                                         "/plugins/**", "/js/**", "/images/**").permitAll()

                        // Usuários, somente ADMIN
                        .requestMatchers("/usuarios/**", "/api/usuarios/**").hasRole("ADMIN")

                        // Auditoria, ADMIN e GERENTE
                        .requestMatchers("/auditoria/**", "/api/auditoria/**").hasAnyRole("ADMIN", "GERENTE")

                        // Pedidos 
                        // Cancelar e aplicar desconto, SOMENTE GERENTE
                        .requestMatchers("/api/pedidos/*/cancelar", "/api/pedidos/*/desconto").hasRole("GERENTE")
                        // Registrar pedido, ATENDENTE
                        .requestMatchers(HttpMethod.POST, "/api/pedidos").hasRole("ATENDENTE")
                        // Mudar status, ATENDENTE e GERENTE
                        .requestMatchers(HttpMethod.PUT, "/api/pedidos/*/status").hasAnyRole("ATENDENTE", "GERENTE")
                        // Consultar pedidos, para os três perfis
                        .requestMatchers(HttpMethod.GET, "/pedidos/**", "/api/pedidos/**")
                                .hasAnyRole("ADMIN", "GERENTE", "ATENDENTE")

                        // Clientes
                        .requestMatchers(HttpMethod.GET, "/clientes", "/api/clientes/**")
                                .hasAnyRole("GERENTE", "ATENDENTE")
                        .requestMatchers(HttpMethod.DELETE, "/api/clientes/**").hasRole("GERENTE")
                        .requestMatchers("/clientes/**", "/api/clientes/**").hasRole("ATENDENTE")

                        // Produtos
                        .requestMatchers(HttpMethod.GET, "/produtos/**", "/api/produtos/**").authenticated()
                        .requestMatchers("/produtos/**", "/api/produtos/**").hasAnyRole("ADMIN", "GERENTE")

                        // Telas administrativas
                        .requestMatchers("/admin", "/teste", "/configuracoes").hasRole("ADMIN")

                        // Qualquer outra rota, necessario estar logado
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .permitAll()
                        .successHandler(authenticationSuccessHandler())
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                );

        return http.build();
    }

    @Bean
    public AuthenticationSuccessHandler authenticationSuccessHandler() {
        return (request, response, authentication) -> response.sendRedirect("/");
    }
}
