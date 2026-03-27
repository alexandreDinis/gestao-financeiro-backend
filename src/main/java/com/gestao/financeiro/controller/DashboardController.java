package com.gestao.financeiro.controller;

import com.gestao.financeiro.dto.response.ApiResponse;
import com.gestao.financeiro.dto.response.DashboardResponse;
import com.gestao.financeiro.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public ApiResponse<DashboardResponse> dashboard() {
        return ApiResponse.ok(dashboardService.getDashboard());
    }
}
