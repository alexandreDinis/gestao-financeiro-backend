package com.gestao.financeiro.controller;

import com.gestao.financeiro.dto.response.ApiResponse;
import com.gestao.financeiro.dto.response.FluxoMensalResponse;
import com.gestao.financeiro.dto.response.GastoPorCategoriaResponse;
import com.gestao.financeiro.dto.response.RelatorioGastosMensaisResponse;
import com.gestao.financeiro.service.RelatorioService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/relatorios")
@RequiredArgsConstructor
@Validated
public class RelatorioController {

    private final RelatorioService relatorioService;

    @GetMapping("/gastos-por-categoria")
    public ApiResponse<List<GastoPorCategoriaResponse>> gastosPorCategoria(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return ApiResponse.ok(relatorioService.gastosPorCategoria(inicio, fim));
    }

    @GetMapping("/fluxo-mensal")
    public ApiResponse<List<FluxoMensalResponse>> fluxoMensal(
            @RequestParam(defaultValue = "6") int meses) {
        return ApiResponse.ok(relatorioService.fluxoMensal(meses));
    }

    @GetMapping("/gastos-mensais")
    public ApiResponse<RelatorioGastosMensaisResponse> gastosMensais(
            @RequestParam @Min(1) @Max(12) int mes,
            @RequestParam @Min(2000) int ano) {
        return ApiResponse.ok(relatorioService.gastosMensaisDetalhados(ano, mes));
    }
}
