package com.gestao.financeiro.mapper;

import com.gestao.financeiro.dto.request.UsuarioRequest;
import com.gestao.financeiro.dto.response.UsuarioResponse;
import com.gestao.financeiro.entity.Usuario;
import org.springframework.stereotype.Component;

@Component
public class UsuarioMapper {

    public UsuarioResponse toResponse(Usuario entity) {
        return new UsuarioResponse(
                entity.getId(),
                entity.getNome(),
                entity.getEmail(),
                entity.getRole(),
                entity.getAtivo(),
                entity.getTenantId(),
                entity.getCreatedAt()
        );
    }

    public Usuario toEntity(UsuarioRequest request) {
        return Usuario.builder()
                .nome(request.nome())
                .email(request.email())
                .senha(request.senha())
                .role(request.role())
                .ativo(true)
                .build();
    }

    public void updateEntity(Usuario entity, UsuarioRequest request) {
        entity.setNome(request.nome());
        entity.setEmail(request.email());
        entity.setRole(request.role());
        if (request.senha() != null && !request.senha().isBlank()) {
            entity.setSenha(request.senha());
        }
    }
}
