package com.gestao.financeiro.service;

import com.gestao.financeiro.dto.request.CategoriaRequest;
import com.gestao.financeiro.dto.response.CategoriaResponse;
import com.gestao.financeiro.entity.Categoria;
import com.gestao.financeiro.entity.enums.TipoCategoria;
import com.gestao.financeiro.exception.BusinessException;
import com.gestao.financeiro.exception.ResourceNotFoundException;
import com.gestao.financeiro.mapper.CategoriaMapper;
import com.gestao.financeiro.repository.CategoriaRepository;
import com.gestao.financeiro.config.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class CategoriaService {

    private final CategoriaRepository categoriaRepository;
    private final CategoriaMapper categoriaMapper;



    public Page<CategoriaResponse> listar(TipoCategoria tipo, Pageable pageable) {
        if (tipo != null) {
            return categoriaRepository.findByTipo(tipo, pageable)
                    .map(categoriaMapper::toResponse);
        }
        return categoriaRepository.findAll(pageable)
                .map(categoriaMapper::toResponse);
    }

    public CategoriaResponse buscarPorId(Long id) {
        return categoriaMapper.toResponse(findById(id));
    }

    public List<CategoriaResponse> listarSubcategorias(Long categoriaPaiId) {
        findById(categoriaPaiId); // valida existência
        return categoriaRepository.findByCategoriaPaiId(categoriaPaiId)
                .stream()
                .map(categoriaMapper::toResponse)
                .toList();
    }

    @Transactional
    public CategoriaResponse criar(CategoriaRequest request) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BusinessException("Tenant ID não encontrado no contexto");
        }

        if (categoriaRepository.existsByNomeAndTenantId(request.nome(), tenantId)) {
            throw new BusinessException("Já existe uma categoria com o nome: " + request.nome());
        }

        Categoria categoria = categoriaMapper.toEntity(request);
        categoria.setTenantId(tenantId);

        // Vincula categoria pai
        if (request.categoriaPaiId() != null) {
            Categoria pai = findById(request.categoriaPaiId());
            categoria.setCategoriaPai(pai);
        }

        categoria = categoriaRepository.save(categoria);
        log.info("[tenant={}] Categoria criada: id={} nome={} tipo={}", tenantId, categoria.getId(), categoria.getNome(), categoria.getTipo());

        return categoriaMapper.toResponse(categoria);
    }

    @Transactional
    public CategoriaResponse atualizar(Long id, CategoriaRequest request) {
        Categoria categoria = findById(id);

        if (!categoria.getNome().equals(request.nome()) && categoriaRepository.existsByNomeAndTenantId(request.nome(), categoria.getTenantId())) {
            throw new BusinessException("Já existe uma categoria com o nome: " + request.nome());
        }

        categoriaMapper.updateEntity(categoria, request);

        if (request.categoriaPaiId() != null) {
            if (request.categoriaPaiId().equals(id)) {
                throw new BusinessException("Uma categoria não pode ser pai de si mesma.");
            }
            Categoria pai = findById(request.categoriaPaiId());
            categoria.setCategoriaPai(pai);
        } else {
            categoria.setCategoriaPai(null);
        }

        categoria = categoriaRepository.save(categoria);
        log.info("[tenant={}] Categoria atualizada: id={}", categoria.getTenantId(), id);

        return categoriaMapper.toResponse(categoria);
    }

    @Transactional
    public void deletar(Long id) {
        Categoria categoria = findById(id);

        // Verifica se tem subcategorias
        List<Categoria> subcategorias = categoriaRepository.findByCategoriaPaiId(id);
        if (!subcategorias.isEmpty()) {
            throw new BusinessException("Não é possível remover categoria com subcategorias. Remova as subcategorias primeiro.");
        }

        categoria.softDelete();
        categoriaRepository.save(categoria);
        log.info("[tenant={}] Categoria desativada (soft delete): id={}", categoria.getTenantId(), id);
    }

    private Categoria findById(Long id) {
        com.gestao.financeiro.util.ValidationUtils.validateId(id, "Categoria");
        return categoriaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Categoria", id));
    }
}
