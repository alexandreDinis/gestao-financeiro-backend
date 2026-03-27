package com.gestao.financeiro.service;

import com.gestao.financeiro.dto.request.UsuarioRequest;
import com.gestao.financeiro.dto.response.UsuarioResponse;
import com.gestao.financeiro.entity.Usuario;
import com.gestao.financeiro.exception.BusinessException;
import com.gestao.financeiro.exception.ResourceNotFoundException;
import com.gestao.financeiro.mapper.UsuarioMapper;
import com.gestao.financeiro.repository.UsuarioRepository;
import com.gestao.financeiro.config.TenantContext;
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



    public Page<UsuarioResponse> listar(Pageable pageable) {
        return usuarioRepository.findByAtivoTrue(pageable)
                .map(usuarioMapper::toResponse);
    }

    public UsuarioResponse buscarPorId(Long id) {
        return usuarioMapper.toResponse(findById(id));
    }

    @Transactional
    public UsuarioResponse criar(UsuarioRequest request) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            // Se for criação de usuário e não tiver tenant no contexto, 
            // talvez devesse ser 1L (superadmin criando) ou vir no request.
            // Para manter consistência com os outros, exigiremos contexto ou usaremos 1L como fallback seguro para Usuários.
            tenantId = 1L; 
        }

        if (usuarioRepository.existsByEmail(request.email())) {
            throw new BusinessException("Já existe um usuário com o email: " + request.email());
        }

        Usuario usuario = usuarioMapper.toEntity(request);
        usuario.setTenantId(tenantId);

        usuario = usuarioRepository.save(usuario);
        log.info("[tenant={}] Usuário criado: id={} email={}", tenantId, usuario.getId(), usuario.getEmail());

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
