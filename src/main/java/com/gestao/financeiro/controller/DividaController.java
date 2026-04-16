package com.gestao.financeiro.controller;

import com.gestao.financeiro.dto.request.DividaRequest;
import com.gestao.financeiro.dto.request.PagarMultiplasParcelasRequest;
import com.gestao.financeiro.dto.request.PagarParcelaDividaRequest;
import com.gestao.financeiro.dto.response.ApiResponse;
import com.gestao.financeiro.dto.response.DividaResponse;
import com.gestao.financeiro.dto.response.DividasResumoResponse;
import com.gestao.financeiro.dto.response.ParcelaDividaResponse;
import com.gestao.financeiro.entity.enums.StatusDivida;
import com.gestao.financeiro.entity.enums.TipoDivida;
import com.gestao.financeiro.repository.PessoaRepository;
import com.gestao.financeiro.service.DividaPdfService;
import com.gestao.financeiro.service.DividaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dividas")
@RequiredArgsConstructor
public class DividaController {

    private final DividaService dividaService;
    private final DividaPdfService dividaPdfService;
    private final PessoaRepository pessoaRepository;

    @GetMapping
    public ApiResponse<DividasResumoResponse> listar(
            @RequestParam(required = false) TipoDivida tipo,
            @RequestParam(required = false) Long pessoaId,
            @RequestParam(required = false) Integer ano,
            @RequestParam(required = false) Integer mes,
            @RequestParam(required = false) StatusDivida status,
            Pageable pageable) {
        return ApiResponse.ok(dividaService.listar(tipo, pessoaId, ano, mes, status, pageable));
    }

    @GetMapping("/exportar-pdf")
    public ResponseEntity<byte[]> exportarPdf(
            @RequestParam(required = false) TipoDivida tipo,
            @RequestParam(required = false) Long pessoaId,
            @RequestParam(required = false) Integer ano,
            @RequestParam(required = false) Integer mes,
            @RequestParam(required = false) StatusDivida status) {

        DividasResumoResponse resumo = dividaService.listarTodos(tipo, pessoaId, ano, mes, status);

        String pessoaNome = null;
        if (pessoaId != null) {
            pessoaNome = pessoaRepository.findById(pessoaId)
                    .map(p -> p.getNome())
                    .orElse(null);
        }

        byte[] pdf = dividaPdfService.gerarPdf(
                resumo.items(),
                resumo.totalGeral(),
                tipo != null ? tipo.name() : null,
                pessoaNome,
                ano, mes,
                status != null ? status.name() : null
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "relatorio-dividas.pdf");

        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ApiResponse<DividaResponse> buscarPorId(@PathVariable Long id) {
        return ApiResponse.ok(dividaService.buscarPorId(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DividaResponse> criar(@Valid @RequestBody DividaRequest request) {
        return ApiResponse.created(dividaService.criar(request));
    }

    @PutMapping("/parcelas/{parcelaId}/pagar")
    public ApiResponse<ParcelaDividaResponse> pagarParcela(
            @PathVariable Long parcelaId,
            @Valid @RequestBody PagarParcelaDividaRequest request) {
        return ApiResponse.ok(dividaService.pagarParcela(parcelaId, request));
    }

    @PutMapping("/parcelas/pagar-lote")
    public ApiResponse<List<ParcelaDividaResponse>> pagarLote(
            @Valid @RequestBody PagarMultiplasParcelasRequest request) {
        return ApiResponse.ok(dividaService.pagarMultiplasParcelas(request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletar(@PathVariable Long id) {
        dividaService.deletar(id);
    }

    @PutMapping("/{id}/cancelar-recorrencia")
    public ApiResponse<DividaResponse> cancelarRecorrencia(@PathVariable Long id) {
        return ApiResponse.ok(dividaService.cancelarRecorrencia(id));
    }

    @PostMapping("/processar-recorrencias")
    public ApiResponse<Void> processarRecorrencias() {
        dividaService.processarDividasRecorrentes();
        return ApiResponse.noContent();
    }
}
