package com.gestao.financeiro.controller;

import com.gestao.financeiro.dto.request.UsuarioRequest;
import com.gestao.financeiro.dto.response.ApiResponse;
import com.gestao.financeiro.dto.response.UsuarioResponse;
import com.gestao.financeiro.service.UsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;

    @GetMapping
    public ApiResponse<List<UsuarioResponse>> listar(Pageable pageable) {
        return ApiResponse.ok(usuarioService.listar(pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<UsuarioResponse> buscarPorId(@PathVariable Long id) {
        return ApiResponse.ok(usuarioService.buscarPorId(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UsuarioResponse> criar(@Valid @RequestBody UsuarioRequest request) {
        return ApiResponse.created(usuarioService.criar(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<UsuarioResponse> atualizar(@PathVariable Long id, @Valid @RequestBody UsuarioRequest request) {
        return ApiResponse.ok(usuarioService.atualizar(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletar(@PathVariable Long id) {
        usuarioService.deletar(id);
    }
}
