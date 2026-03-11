package com.gestao.financeiro.service;

import com.gestao.financeiro.dto.request.UsuarioRequest;
import com.gestao.financeiro.dto.response.UsuarioResponse;
import com.gestao.financeiro.entity.Usuario;
import com.gestao.financeiro.exception.BusinessException;
import com.gestao.financeiro.exception.ResourceNotFoundException;
import com.gestao.financeiro.mapper.UsuarioMapper;
import com.gestao.financeiro.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final UsuarioMapper usuarioMapper;

    // MVP: tenant fixo = 1
    private static final Long DEFAULT_TENANT_ID = 1L;

    public Page<UsuarioResponse> listar(Pageable pageable) {
        return usuarioRepository.findByAtivoTrue(pageable)
                .map(usuarioMapper::toResponse);
    }

    public UsuarioResponse buscarPorId(Long id) {
        return usuarioMapper.toResponse(findById(id));
    }

    @Transactional
    public UsuarioResponse criar(UsuarioRequest request) {
        if (usuarioRepository.existsByEmail(request.email())) {
            throw new BusinessException("Já existe um usuário com o email: " + request.email());
        }

        Usuario usuario = usuarioMapper.toEntity(request);
        usuario.setTenantId(DEFAULT_TENANT_ID);

        usuario = usuarioRepository.save(usuario);
        log.info("[tenant={}] Usuário criado: id={} email={}", DEFAULT_TENANT_ID, usuario.getId(), usuario.getEmail());

        return usuarioMapper.toResponse(usuario);
    }

    @Transactional
    public UsuarioResponse atualizar(Long id, UsuarioRequest request) {
        Usuario usuario = findById(id);

        // Verifica email duplicado se mudou
        if (!usuario.getEmail().equals(request.email()) && usuarioRepository.existsByEmail(request.email())) {
            throw new BusinessException("Já existe um usuário com o email: " + request.email());
        }

        usuarioMapper.updateEntity(usuario, request);
        usuario = usuarioRepository.save(usuario);
        log.info("[tenant={}] Usuário atualizado: id={}", usuario.getTenantId(), id);

        return usuarioMapper.toResponse(usuario);
    }

    @Transactional
    public void deletar(Long id) {
        Usuario usuario = findById(id);
        usuario.softDelete();
        usuarioRepository.save(usuario);
        log.info("[tenant={}] Usuário desativado (soft delete): id={}", usuario.getTenantId(), id);
    }

    private Usuario findById(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário", id));
    }
}
