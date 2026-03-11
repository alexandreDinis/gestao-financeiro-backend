package com.gestao.financeiro.controller;

import com.gestao.financeiro.dto.request.CategoriaRequest;
import com.gestao.financeiro.dto.response.ApiResponse;
import com.gestao.financeiro.dto.response.CategoriaResponse;
import com.gestao.financeiro.entity.enums.TipoCategoria;
import com.gestao.financeiro.service.CategoriaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categorias")
@RequiredArgsConstructor
public class CategoriaController {

    private final CategoriaService categoriaService;

    @GetMapping
    public ApiResponse<List<CategoriaResponse>> listar(
            @RequestParam(required = false) TipoCategoria tipo,
            Pageable pageable) {
        return ApiResponse.ok(categoriaService.listar(tipo, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<CategoriaResponse> buscarPorId(@PathVariable Long id) {
        return ApiResponse.ok(categoriaService.buscarPorId(id));
    }

    @GetMapping("/{id}/subcategorias")
    public ApiResponse<List<CategoriaResponse>> subcategorias(@PathVariable Long id) {
        return ApiResponse.ok(categoriaService.listarSubcategorias(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CategoriaResponse> criar(@Valid @RequestBody CategoriaRequest request) {
        return ApiResponse.created(categoriaService.criar(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<CategoriaResponse> atualizar(@PathVariable Long id, @Valid @RequestBody CategoriaRequest request) {
        return ApiResponse.ok(categoriaService.atualizar(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletar(@PathVariable Long id) {
        categoriaService.deletar(id);
    }
}
