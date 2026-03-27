package com.gestao.financeiro.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantCreateRequest {

    @NotBlank(message = "O nome do tenant é obrigatório")
    private String tenantNome;

    @NotBlank(message = "O subdomínio é obrigatório")
    private String subdominio;

    @NotNull(message = "O ID do plano é obrigatório")
    private Long planoId;

    @NotBlank(message = "O nome do administrador é obrigatório")
    private String adminNome;

    @NotBlank(message = "O email do administrador é obrigatório")
    @Email(message = "Email inválido")
    private String adminEmail;

    @NotBlank(message = "A senha do administrador é obrigatória")
    @Size(min = 6, message = "A senha deve ter no mínimo 6 caracteres")
    private String adminSenha;
}
