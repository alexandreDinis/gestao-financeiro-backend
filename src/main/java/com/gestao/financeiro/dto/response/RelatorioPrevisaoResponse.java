package com.gestao.financeiro.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record RelatorioPrevisaoResponse(
        BigDecimal saldoAtual,
        List<PrevisaoMesResponse> meses
) {}
