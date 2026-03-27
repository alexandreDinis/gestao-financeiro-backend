package com.gestao.financeiro.controller;

import com.gestao.financeiro.dto.response.ApiResponse;
import com.gestao.financeiro.entity.Plano;
import com.gestao.financeiro.repository.PlanoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/planos")
@RequiredArgsConstructor
public class PlanoController {

    private final PlanoRepository planoRepository;

    @GetMapping
    public ApiResponse<List<Plano>> listarPlanos() {
        // Endpoint público definido no SecurityConfig
        return ApiResponse.ok(planoRepository.findAll());
    }
}
