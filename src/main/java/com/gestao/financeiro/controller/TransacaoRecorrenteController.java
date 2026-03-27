package com.gestao.financeiro.controller;

import com.gestao.financeiro.dto.request.TransacaoRecorrenteRequest;
import com.gestao.financeiro.dto.response.ApiResponse;
import com.gestao.financeiro.dto.response.TransacaoRecorrenteResponse;
import com.gestao.financeiro.service.TransacaoRecorrenteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/recorrencias")
@RequiredArgsConstructor
public class TransacaoRecorrenteController {

    private final TransacaoRecorrenteService recorrenteService;

    @GetMapping
    public ApiResponse<List<TransacaoRecorrenteResponse>> listar(Pageable pageable) {
        return ApiResponse.ok(recorrenteService.listar(pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<TransacaoRecorrenteResponse> buscarPorId(@PathVariable Long id) {
        return ApiResponse.ok(recorrenteService.buscarPorId(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TransacaoRecorrenteResponse> criar(@Valid @RequestBody TransacaoRecorrenteRequest request) {
        return ApiResponse.created(recorrenteService.criar(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<TransacaoRecorrenteResponse> atualizar(@PathVariable Long id, @Valid @RequestBody TransacaoRecorrenteRequest request) {
        return ApiResponse.ok(recorrenteService.atualizar(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletar(@PathVariable Long id) {
        recorrenteService.deletar(id);
    }
}
