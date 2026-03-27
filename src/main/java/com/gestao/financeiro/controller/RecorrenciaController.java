package com.gestao.financeiro.controller;

import com.gestao.financeiro.entity.Recorrencia;
import com.gestao.financeiro.repository.RecorrenciaRepository;
import com.gestao.financeiro.service.RecorrenciaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/recorrencias-old")
@RequiredArgsConstructor
public class RecorrenciaController {

    private final RecorrenciaRepository repository;
    private final RecorrenciaService service;

    @GetMapping
    public List<Recorrencia> listar() {
        return repository.findAll();
    }

    @PostMapping
    public Recorrencia salvar(@RequestBody Recorrencia recorrencia) {
        return repository.save(recorrencia);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/gerar-manual")
    public ResponseEntity<String> gerarManual() {
        service.processarRecorrenciasAgendadas();
        return ResponseEntity.ok("Processamento de recorrências disparado com sucesso.");
    }
}
