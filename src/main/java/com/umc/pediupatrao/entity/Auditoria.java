package com.umc.pediupatrao.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// Registro de auditoria, depois de criado, não pode ser modificado.
@Document(collection = "auditoria")
public class Auditoria {

    @Id
    private String id;
    private String usuario;      // quem executou a operação
    private String perfil;       // perfil do usuário no momento da operação
    private LocalDateTime dataHora;
    private String operacao;     
    private String entidade;     // PEDIDO ou CLIENTE
    private String entidadeId;   // identificador do registro afetado
    private List<AlteracaoCampo> alteracoes = new ArrayList<>();
    private String justificativa;

    // Usado pelo Spring Data para ler os registros do MongoDB
    protected Auditoria() {
    }

    public Auditoria(String usuario, String perfil, String operacao, String entidade,
                     String entidadeId, List<AlteracaoCampo> alteracoes, String justificativa) {
        this.usuario = usuario;
        this.perfil = perfil;
        this.dataHora = LocalDateTime.now();
        this.operacao = operacao;
        this.entidade = entidade;
        this.entidadeId = entidadeId;
        this.alteracoes = alteracoes;
        this.justificativa = justificativa;
    }

    public String getId() {
        return id;
    }

    public String getUsuario() {
        return usuario;
    }

    public String getPerfil() {
        return perfil;
    }

    public LocalDateTime getDataHora() {
        return dataHora;
    }

    public String getOperacao() {
        return operacao;
    }

    public String getEntidade() {
        return entidade;
    }

    public String getEntidadeId() {
        return entidadeId;
    }

    public List<AlteracaoCampo> getAlteracoes() {
        return alteracoes;
    }

    public String getJustificativa() {
        return justificativa;
    }
}