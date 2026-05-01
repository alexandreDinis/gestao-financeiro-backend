package com.gestao.financeiro.controller;

import com.gestao.financeiro.dto.request.PrevisaoAjusteRequest;
import com.gestao.financeiro.dto.response.RelatorioPrevisaoResponse;
import com.gestao.financeiro.service.PrevisaoCaixaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/previsao")
@RequiredArgsConstructor
public class PrevisaoCaixaController {

    private final PrevisaoCaixaService previsaoCaixaService;

    @GetMapping
    public ResponseEntity<RelatorioPrevisaoResponse> getPrevisao(@RequestParam(defaultValue = "12") int meses) {
        RelatorioPrevisaoResponse response = previsaoCaixaService.gerarPrevisao(meses);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/ajustes")
    public ResponseEntity<Void> salvarAjuste(@Valid @RequestBody PrevisaoAjusteRequest request) {
        previsaoCaixaService.salvarAjuste(request);
        return ResponseEntity.ok().build();
    }
}
