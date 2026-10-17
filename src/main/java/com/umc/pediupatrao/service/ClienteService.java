package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.AlteracaoCampo;                      
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.repository.ClienteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.ArrayList;                                            
import java.util.List;
import java.util.Optional;

@Service
public class ClienteService {

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private AuditoriaService auditoriaService;                          

    public Cliente novoCliente(Cliente cliente) {
        return salvar(cliente); // Passa pelo salvar para também ser auditado
    }

    public List<Cliente> listarClientes() {
        return clienteRepository.findAll();
    }

    // Método para excluir cliente
    public void excluir(String id) {
        // Registra a exclusão (apenas o nome, para não expor dados pessoais além do necessário)
        clienteRepository.findById(id).ifPresent(cliente -> {
            clienteRepository.deleteById(id);
            List<AlteracaoCampo> alteracoes = new ArrayList<>();
            AuditoriaService.comparar(alteracoes, "nome", cliente.getNome(), null);
            auditoriaService.registrar("EXCLUSÃO DE CLIENTE", "CLIENTE", id, alteracoes, null);
        });
    }

    public Optional<Cliente> buscarPorId(String id) {
        return clienteRepository.findById(id);
    }

    // Método para salvar um novo cliente ou atualizar um cliente existente
    public Cliente salvar(Cliente cliente) {
        // Se o cliente não tem ID (novo cliente), salva como novo
        if (cliente.getId() == null) {
            Cliente salvo = clienteRepository.save(cliente);  // Cria um novo cliente

            // Auditoria da inclusão (registra só o nome - minimização de dados pessoais)
            List<AlteracaoCampo> alteracoes = new ArrayList<>();
            AuditoriaService.comparar(alteracoes, "nome", null, salvo.getNome());
            auditoriaService.registrar("INCLUSÃO DE CLIENTE", "CLIENTE", salvo.getId(), alteracoes, null);

            return salvo;
        } // Se já tem ID (cliente existente), atualiza
        else {
            // Verifica se o cliente existe antes de atualizar
            // Busca a versão atual para comparar os valores anteriores e novos
            Cliente anterior = clienteRepository.findById(cliente.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Cliente não encontrado para atualização."));

            List<AlteracaoCampo> alteracoes = compararCliente(anterior, cliente);
            Cliente salvo = clienteRepository.save(cliente);  // Atualiza o cliente existente

            // Só registra auditoria se algum campo realmente mudou
            if (!alteracoes.isEmpty()) {
                auditoriaService.registrar("ALTERAÇÃO DE CLIENTE", "CLIENTE", salvo.getId(), alteracoes, null);
            }
            return salvo;
        }
    }

    // Compara campo a campo (dados cadastrais, telefone e endereço)
    private List<AlteracaoCampo> compararCliente(Cliente anterior, Cliente novo) {
        List<AlteracaoCampo> alteracoes = new ArrayList<>();
        AuditoriaService.comparar(alteracoes, "nome", anterior.getNome(), novo.getNome());
        AuditoriaService.comparar(alteracoes, "telefone", anterior.getTelefone(), novo.getTelefone());
        AuditoriaService.comparar(alteracoes, "cep", anterior.getCep(), novo.getCep());
        AuditoriaService.comparar(alteracoes, "logradouro", anterior.getLogradouro(), novo.getLogradouro());
        AuditoriaService.comparar(alteracoes, "numero", anterior.getNumero(), novo.getNumero());
        AuditoriaService.comparar(alteracoes, "complemento", anterior.getComplemento(), novo.getComplemento());
        AuditoriaService.comparar(alteracoes, "bairro", anterior.getBairro(), novo.getBairro());
        AuditoriaService.comparar(alteracoes, "cidade", anterior.getCidade(), novo.getCidade());
        AuditoriaService.comparar(alteracoes, "estado", anterior.getEstado(), novo.getEstado());
        return alteracoes;
    }

}
