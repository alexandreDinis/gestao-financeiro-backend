package com.gestao.financeiro.controller.admin;

import com.gestao.financeiro.dto.response.ApiResponse;
import com.gestao.financeiro.dto.response.TenantAdminResponse;
import com.gestao.financeiro.dto.request.TenantCreateRequest;
import com.gestao.financeiro.entity.enums.StatusTenant;
import com.gestao.financeiro.service.AdminService;
import lombok.RequiredArgsConstructor;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')") // Requer que o usuário tenha role SUPER_ADMIN no JWT
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> getDashboard() {
        return ApiResponse.ok(adminService.getDashboardMetrics());
    }

    @GetMapping("/tenants")
    public ApiResponse<List<TenantAdminResponse>> listarTenants(Pageable pageable) {
        return ApiResponse.ok(adminService.listarTenants(pageable));
    }

    @PutMapping("/tenants/{id}/bloquear")
    public ApiResponse<Void> bloquearTenant(@PathVariable Long id) {
        adminService.alternarStatusTenant(id, StatusTenant.BLOQUEADO);
        return ApiResponse.noContent();
    }

    @PutMapping("/tenants/{id}/desbloquear")
    public ApiResponse<Void> desbloquearTenant(@PathVariable Long id) {
        adminService.alternarStatusTenant(id, StatusTenant.ATIVO);
        return ApiResponse.noContent();
    }

    @PostMapping("/tenants")
    public ApiResponse<TenantAdminResponse> criarTenant(@RequestBody @Valid TenantCreateRequest request) {
        return ApiResponse.ok(adminService.criarTenant(request));
    }
}
