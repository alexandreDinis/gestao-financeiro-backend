package com.gestao.financeiro.controller;

import com.gestao.financeiro.dto.request.DepositoMetaRequest;
import com.gestao.financeiro.dto.request.MetaFinanceiraRequest;
import com.gestao.financeiro.dto.response.ApiResponse;
import com.gestao.financeiro.dto.response.MetaFinanceiraResponse;
import com.gestao.financeiro.service.MetaFinanceiraService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/metas")
@RequiredArgsConstructor
public class MetaFinanceiraController {

    private final MetaFinanceiraService metaService;

    @GetMapping
    public ApiResponse<List<MetaFinanceiraResponse>> listar(Pageable pageable) {
        return ApiResponse.ok(metaService.listar(pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<MetaFinanceiraResponse> buscarPorId(@PathVariable Long id) {
        return ApiResponse.ok(metaService.buscarPorId(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MetaFinanceiraResponse> criar(@Valid @RequestBody MetaFinanceiraRequest request) {
        return ApiResponse.created(metaService.criar(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<MetaFinanceiraResponse> atualizar(@PathVariable Long id, @Valid @RequestBody MetaFinanceiraRequest request) {
        return ApiResponse.ok(metaService.atualizar(id, request));
    }

    @PutMapping("/{id}/depositar")
    public ApiResponse<MetaFinanceiraResponse> depositar(@PathVariable Long id, @Valid @RequestBody DepositoMetaRequest request) {
        return ApiResponse.ok(metaService.depositar(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletar(@PathVariable Long id) {
        metaService.deletar(id);
    }
}
