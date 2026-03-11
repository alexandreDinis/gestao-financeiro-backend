package com.gestao.financeiro.controller;

import com.gestao.financeiro.dto.request.DividaRequest;
import com.gestao.financeiro.dto.request.PagarParcelaDividaRequest;
import com.gestao.financeiro.dto.response.ApiResponse;
import com.gestao.financeiro.dto.response.DividaResponse;
import com.gestao.financeiro.dto.response.ParcelaDividaResponse;
import com.gestao.financeiro.entity.enums.TipoDivida;
import com.gestao.financeiro.service.DividaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dividas")
@RequiredArgsConstructor
public class DividaController {

    private final DividaService dividaService;

    @GetMapping
    public ApiResponse<Page<DividaResponse>> listar(
            @RequestParam(required = false) TipoDivida tipo,
            Pageable pageable) {
        return ApiResponse.ok(dividaService.listar(tipo, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<DividaResponse> buscarPorId(@PathVariable Long id) {
        return ApiResponse.ok(dividaService.buscarPorId(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DividaResponse> criar(@Valid @RequestBody DividaRequest request) {
        return ApiResponse.created(dividaService.criar(request));
    }

    @PutMapping("/parcelas/{parcelaId}/pagar")
    public ApiResponse<ParcelaDividaResponse> pagarParcela(
            @PathVariable Long parcelaId,
            @Valid @RequestBody PagarParcelaDividaRequest request) {
        return ApiResponse.ok(dividaService.pagarParcela(parcelaId, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletar(@PathVariable Long id) {
        dividaService.deletar(id);
    }
}
