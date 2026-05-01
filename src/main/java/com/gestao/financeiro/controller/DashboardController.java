package com.gestao.financeiro.controller;

import com.gestao.financeiro.dto.response.ApiResponse;
import com.gestao.financeiro.dto.response.DashboardResponse;
import com.gestao.financeiro.dto.response.DashboardResponse.Vencimento;
import com.gestao.financeiro.service.DashboardService;
import com.gestao.financeiro.config.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public ApiResponse<DashboardResponse> dashboard() {
        return ApiResponse.ok(dashboardService.getDashboard());
    }

    @GetMapping("/vencimentos")
    public ApiResponse<List<Vencimento>> vencimentos(
            @RequestParam(required = false) Integer mes,
            @RequestParam(required = false) Integer ano) {
        return ApiResponse.ok(dashboardService.getTodosVencimentos(TenantContext.getTenantId(), mes, ano));
    }
}
