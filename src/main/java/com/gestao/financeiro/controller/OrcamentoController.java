package com.gestao.financeiro.controller;

import com.gestao.financeiro.dto.request.OrcamentoRequest;
import com.gestao.financeiro.dto.response.ApiResponse;
import com.gestao.financeiro.dto.response.OrcamentoResponse;
import com.gestao.financeiro.dto.response.OrcamentoResumoResponse;
import com.gestao.financeiro.service.OrcamentoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orcamentos")
@RequiredArgsConstructor
public class OrcamentoController {

    private final OrcamentoService orcamentoService;

    @GetMapping
    public ApiResponse<List<OrcamentoResponse>> listar(
            @RequestParam Integer mes,
            @RequestParam Integer ano) {
        return ApiResponse.ok(orcamentoService.listar(mes, ano));
    }

    @GetMapping("/resumo")
    public ApiResponse<List<OrcamentoResumoResponse>> resumo(
            @RequestParam Integer mes,
            @RequestParam Integer ano) {
        return ApiResponse.ok(orcamentoService.resumo(mes, ano));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrcamentoResponse> criar(@Valid @RequestBody OrcamentoRequest request) {
        return ApiResponse.created(orcamentoService.criar(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<OrcamentoResponse> atualizar(@PathVariable Long id, @Valid @RequestBody OrcamentoRequest request) {
        return ApiResponse.ok(orcamentoService.atualizar(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletar(@PathVariable Long id) {
        orcamentoService.deletar(id);
    }
}
