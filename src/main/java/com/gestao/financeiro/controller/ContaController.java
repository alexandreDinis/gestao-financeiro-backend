package com.gestao.financeiro.controller;

import com.gestao.financeiro.dto.request.ContaRequest;
import com.gestao.financeiro.dto.response.ApiResponse;
import com.gestao.financeiro.dto.response.ContaResponse;
import com.gestao.financeiro.service.ContaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/contas")
@RequiredArgsConstructor
public class ContaController {

    private final ContaService contaService;

    @GetMapping
    public ApiResponse<List<ContaResponse>> listar(Pageable pageable) {
        return ApiResponse.ok(contaService.listar(pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<ContaResponse> buscarPorId(@PathVariable Long id) {
        return ApiResponse.ok(contaService.buscarPorId(id));
    }

    @GetMapping("/{id}/saldo")
    public ApiResponse<BigDecimal> saldo(@PathVariable Long id) {
        return ApiResponse.ok(contaService.calcularSaldo(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ContaResponse> criar(@Valid @RequestBody ContaRequest request) {
        return ApiResponse.created(contaService.criar(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<ContaResponse> atualizar(@PathVariable Long id, @Valid @RequestBody ContaRequest request) {
        return ApiResponse.ok(contaService.atualizar(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletar(@PathVariable Long id) {
        contaService.deletar(id);
    }
}
