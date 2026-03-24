package com.gestao.financeiro.controller;

import com.gestao.financeiro.dto.request.CartaoCreditoRequest;
import com.gestao.financeiro.dto.request.CompraCartaoRequest;
import com.gestao.financeiro.dto.response.ApiResponse;
import com.gestao.financeiro.dto.response.CartaoCreditoResponse;
import com.gestao.financeiro.dto.response.FaturaCartaoResponse;
import com.gestao.financeiro.service.CartaoCreditoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cartoes")
@RequiredArgsConstructor
public class CartaoCreditoController {

    private final CartaoCreditoService cartaoService;

    // ===== Cartão =====

    @GetMapping
    public ApiResponse<List<CartaoCreditoResponse>> listar(Pageable pageable) {
        return ApiResponse.ok(cartaoService.listarCartoes(pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<CartaoCreditoResponse> buscar(@PathVariable Long id) {
        return ApiResponse.ok(cartaoService.buscarCartao(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CartaoCreditoResponse> criar(@Valid @RequestBody CartaoCreditoRequest request) {
        return ApiResponse.created(cartaoService.criarCartao(request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletar(@PathVariable Long id) {
        cartaoService.deletarCartao(id);
    }

    @PutMapping("/{id}")
    public ApiResponse<CartaoCreditoResponse> editar(@PathVariable Long id, @Valid @RequestBody CartaoCreditoRequest request) {
        return ApiResponse.ok(cartaoService.editarCartao(id, request));
    }

    // ===== Compra =====

    @PostMapping("/compra")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<FaturaCartaoResponse> comprar(@Valid @RequestBody CompraCartaoRequest request) {
        return ApiResponse.created(cartaoService.comprar(request));
    }

    // ===== Faturas =====

    @GetMapping("/{cartaoId}/faturas")
    public ApiResponse<List<FaturaCartaoResponse>> listarFaturas(@PathVariable Long cartaoId) {
        return ApiResponse.ok(cartaoService.listarFaturas(cartaoId));
    }

    @GetMapping("/faturas/{faturaId}")
    public ApiResponse<FaturaCartaoResponse> buscarFatura(@PathVariable Long faturaId) {
        return ApiResponse.ok(cartaoService.buscarFatura(faturaId));
    }

    @PutMapping("/faturas/{faturaId}/pagar")
    public ApiResponse<FaturaCartaoResponse> pagarFatura(@PathVariable Long faturaId) {
        return ApiResponse.ok(cartaoService.pagarFatura(faturaId));
    }
}
