package com.gestao.financeiro.service;

import com.gestao.financeiro.config.TenantContext;
import com.gestao.financeiro.dto.response.AssinaturaResponse;
import com.gestao.financeiro.dto.response.UsoPlanoResponse;
import com.gestao.financeiro.entity.Assinatura;
import com.gestao.financeiro.entity.Plano;
import com.gestao.financeiro.exception.BusinessException;
import com.gestao.financeiro.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssinaturaService {

    private final AssinaturaRepository assinaturaRepository;
    private final ContaRepository contaRepository;
    private final CategoriaRepository categoriaRepository;
    private final CartaoCreditoRepository cartaoRepository;
    private final PessoaRepository pessoaRepository;
    private final MetaFinanceiraRepository metaRepository;

    public AssinaturaResponse buscarAssinaturaAtual() {
        Assinatura assinatura = getAssinaturaAtiva();
        return toResponse(assinatura);
    }

    public UsoPlanoResponse buscarUsoAtual() {
        Assinatura assinatura = getAssinaturaAtiva();
        Plano plano = assinatura.getPlano();
        
        Map<String, UsoPlanoResponse.LimiteUso> uso = new HashMap<>();
        
        long contas = contaRepository.count();
        uso.put("contas", new UsoPlanoResponse.LimiteUso(contas, plano.getMaxContas(), contas >= plano.getMaxContas()));
        
        long categorias = categoriaRepository.count();
        uso.put("categorias", new UsoPlanoResponse.LimiteUso(categorias, plano.getMaxCategorias(), categorias >= plano.getMaxCategorias()));
        
        long cartoes = cartaoRepository.count();
        uso.put("cartoes", new UsoPlanoResponse.LimiteUso(cartoes, plano.getMaxCartoes(), cartoes >= plano.getMaxCartoes()));
        
        long metas = metaRepository.count();
        uso.put("metas", new UsoPlanoResponse.LimiteUso(metas, plano.getMaxMetas(), metas >= plano.getMaxMetas()));
        
        long pessoas = pessoaRepository.count(); // Approximate for dividas limit
        uso.put("pessoas", new UsoPlanoResponse.LimiteUso(pessoas, (long) plano.getMaxDividas(), pessoas >= plano.getMaxDividas()));

        return new UsoPlanoResponse(plano.getNome(), uso);
    }

    private Assinatura getAssinaturaAtiva() {
        Long tenantId = TenantContext.getTenantId();
        return assinaturaRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new BusinessException("Assinatura não encontrada para o tenant."));
    }

    private AssinaturaResponse toResponse(Assinatura a) {
        return new AssinaturaResponse(a.getId(), a.getTenantId(), a.getPlano().getNome(),
                a.getPlano().getTipo().name(), a.getValorMensal(), a.getDataInicio(),
                a.getDataFim(), a.getStatus());
    }
}
