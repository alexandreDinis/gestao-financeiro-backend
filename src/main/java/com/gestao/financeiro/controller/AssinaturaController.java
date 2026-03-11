package com.gestao.financeiro.controller;

import com.gestao.financeiro.dto.response.ApiResponse;
import com.gestao.financeiro.dto.response.AssinaturaResponse;
import com.gestao.financeiro.dto.response.UsoPlanoResponse;
import com.gestao.financeiro.service.AssinaturaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assinatura")
@RequiredArgsConstructor
public class AssinaturaController {

    private final AssinaturaService assinaturaService;

    @GetMapping
    public ApiResponse<AssinaturaResponse> getAssinatura() {
        return ApiResponse.ok(assinaturaService.buscarAssinaturaAtual());
    }

    @GetMapping("/uso")
    public ApiResponse<UsoPlanoResponse> getUso() {
        return ApiResponse.ok(assinaturaService.buscarUsoAtual());
    }
}
