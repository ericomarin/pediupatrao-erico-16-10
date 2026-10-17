package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.Auditoria;
import com.umc.pediupatrao.repository.AuditoriaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class AuditoriaService {

    @Autowired
    private AuditoriaRepository auditoriaRepository;

    // Registra um evento de auditoria com o usuário autenticado e seu perfil
    public void registrar(String operacao, String entidade, String entidadeId,
                          List<AlteracaoCampo> alteracoes, String justificativa) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String usuario = (auth != null) ? auth.getName() : "sistema";
        String perfil = (auth != null)
                ? auth.getAuthorities().stream()
                      .map(GrantedAuthority::getAuthority)
                      .map(a -> a.replace("ROLE_", ""))
                      .collect(Collectors.joining(","))
                : "-";

        auditoriaRepository.save(new Auditoria(usuario, perfil, operacao, entidade,
                entidadeId, alteracoes, justificativa));
    }

    // Consulta exclusiva de ADMIN e GERENTE
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public List<Auditoria> listar() {
        return auditoriaRepository.findAllByOrderByDataHoraDesc();
    }

    // Adiciona o campo à lista somente se o valor realmente mudou
    public static void comparar(List<AlteracaoCampo> alteracoes, String campo, Object anterior, Object novo) {
        String valorAnterior = normalizar(anterior);
        String valorNovo = normalizar(novo);
        if (!Objects.equals(valorAnterior, valorNovo)) {
            alteracoes.add(new AlteracaoCampo(campo, valorAnterior, valorNovo));
        }
    }

    // Trata vazio e nulo como equivalentes (evita registrar "alterações" que não existiram)
    private static String normalizar(Object valor) {
        if (valor == null || valor.toString().isBlank()) {
            return null;
        }
        return valor.toString().trim();
    }
}
