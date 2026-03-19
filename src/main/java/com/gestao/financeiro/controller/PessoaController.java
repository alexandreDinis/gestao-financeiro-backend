package com.gestao.financeiro.controller;

import com.gestao.financeiro.dto.request.PessoaRequest;
import com.gestao.financeiro.dto.response.ApiResponse;
import com.gestao.financeiro.dto.response.PessoaResponse;
import com.gestao.financeiro.service.PessoaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pessoas")
@RequiredArgsConstructor
public class PessoaController {

    private final PessoaService pessoaService;

    @GetMapping
    public ApiResponse<List<PessoaResponse>> listar(Pageable pageable) {
        return ApiResponse.ok(pessoaService.listar(pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<PessoaResponse> buscarPorId(@PathVariable Long id) {
        return ApiResponse.ok(pessoaService.buscarPorId(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PessoaResponse> criar(@Valid @RequestBody PessoaRequest request) {
        return ApiResponse.created(pessoaService.criar(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<PessoaResponse> atualizar(@PathVariable Long id, @Valid @RequestBody PessoaRequest request) {
        return ApiResponse.ok(pessoaService.atualizar(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletar(@PathVariable Long id) {
        pessoaService.deletar(id);
    }
}
