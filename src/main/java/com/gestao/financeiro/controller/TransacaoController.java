package com.gestao.financeiro.controller;

import com.gestao.financeiro.dto.request.TransacaoRequest;
import com.gestao.financeiro.dto.response.ApiResponse;
import com.gestao.financeiro.dto.response.TransacaoResponse;
import com.gestao.financeiro.entity.enums.StatusTransacao;
import com.gestao.financeiro.entity.enums.TipoTransacao;
import com.gestao.financeiro.entity.enums.TipoDespesa;
import com.gestao.financeiro.service.TransacaoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/transacoes")
@RequiredArgsConstructor
public class TransacaoController {

    private final TransacaoService transacaoService;

    @GetMapping
    public ApiResponse<List<TransacaoResponse>> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) Long contaId,
            @RequestParam(required = false) TipoTransacao tipo,
            @RequestParam(required = false) TipoDespesa tipoDespesa,
            @RequestParam(required = false) StatusTransacao status,
            @RequestParam(required = false) String origem,
            @RequestParam(required = false) String busca,
            Pageable pageable) {
        Boolean geradoAutomaticamente = null;
        if ("AUTOMATICA".equalsIgnoreCase(origem)) geradoAutomaticamente = true;
        else if ("MANUAL".equalsIgnoreCase(origem)) geradoAutomaticamente = false;

        return ApiResponse.ok(transacaoService.listar(
                dataInicio, dataFim, categoriaId, contaId, tipo, tipoDespesa, status, geradoAutomaticamente, busca, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<TransacaoResponse> buscarPorId(@PathVariable Long id) {
        return ApiResponse.ok(transacaoService.buscarPorId(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TransacaoResponse> criar(@Valid @RequestBody TransacaoRequest request) {
        return ApiResponse.created(transacaoService.criar(request));
    }

    @PutMapping("/{id}/pagar")
    public ApiResponse<TransacaoResponse> pagar(@PathVariable Long id) {
        return ApiResponse.ok(transacaoService.pagar(id));
    }

    @PutMapping("/{id}/cancelar")
    public ApiResponse<TransacaoResponse> cancelar(@PathVariable Long id) {
        return ApiResponse.ok(transacaoService.cancelar(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletar(@PathVariable Long id) {
        transacaoService.deletar(id);
    }

    @PatchMapping("/{id}/tornar-manual")
    public ApiResponse<TransacaoResponse> tornarManual(@PathVariable Long id) {
        return ApiResponse.ok(transacaoService.tornarManual(id));
    }
}
