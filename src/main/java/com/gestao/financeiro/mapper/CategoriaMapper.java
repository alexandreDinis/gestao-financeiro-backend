package com.gestao.financeiro.mapper;

import com.gestao.financeiro.dto.request.CategoriaRequest;
import com.gestao.financeiro.dto.response.CategoriaResponse;
import com.gestao.financeiro.entity.Categoria;
import org.springframework.stereotype.Component;

@Component
public class CategoriaMapper {

    public CategoriaResponse toResponse(Categoria entity) {
        return new CategoriaResponse(
                entity.getId(),
                entity.getNome(),
                entity.getTipo(),
                entity.getCor(),
                entity.getIcone(),
                entity.getCategoriaPai() != null ? entity.getCategoriaPai().getId() : null,
                entity.getCategoriaPai() != null ? entity.getCategoriaPai().getNome() : null,
                entity.getCreatedAt()
        );
    }

    public Categoria toEntity(CategoriaRequest request) {
        return Categoria.builder()
                .nome(request.nome())
                .tipo(request.tipo())
                .cor(request.cor())
                .icone(request.icone())
                .build();
    }

    public void updateEntity(Categoria entity, CategoriaRequest request) {
        entity.setNome(request.nome());
        entity.setTipo(request.tipo());
        entity.setCor(request.cor());
        entity.setIcone(request.icone());
    }
}
