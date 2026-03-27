package com.gestao.financeiro.config;

import com.gestao.financeiro.entity.Assinatura;
import com.gestao.financeiro.entity.Plano;
import com.gestao.financeiro.exception.PlanLimitExceededException;
import com.gestao.financeiro.repository.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class PlanLimitInterceptor implements HandlerInterceptor {

    private final AssinaturaRepository assinaturaRepository;
    private final ContaRepository contaRepository;
    private final CategoriaRepository categoriaRepository;
    private final CartaoCreditoRepository cartaoRepository;
    private final PessoaRepository pessoaRepository;
    private final MetaFinanceiraRepository metaRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!request.getMethod().equalsIgnoreCase("POST")) {
            return true; // Só verifica limites na criação
        }

        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            return true; // Rotas públicas ou sem tenant setado
        }

        Assinatura assinatura = assinaturaRepository.findByTenantId(tenantId).orElse(null);
        if (assinatura == null) {
            return true; // Se não tem assinatura, ignora ou lança erro (preferível ignorar e deixar Auth resolver)
        }

        Plano plano = assinatura.getPlano();
        String path = request.getRequestURI();

        // Contas
        if (path.matches("^/api/contas/?$")) {
            long total = contaRepository.count(); // A query já é filtrada por tenant via filter
            if (total >= plano.getMaxContas()) throw new PlanLimitExceededException("Contas", plano.getMaxContas());
        }
        
        // Categorias
        if (path.matches("^/api/categorias/?$")) {
            long total = categoriaRepository.count();
            if (total >= plano.getMaxCategorias()) throw new PlanLimitExceededException("Categorias", plano.getMaxCategorias());
        }

        // Cartões
        if (path.matches("^/api/cartoes/?$")) {
            long total = cartaoRepository.count();
            if (total >= plano.getMaxCartoes()) throw new PlanLimitExceededException("Cartões de Crédito", plano.getMaxCartoes());
        }

        // Metas
        if (path.matches("^/api/metas/?$")) {
            long total = metaRepository.count();
            if (total >= plano.getMaxMetas()) throw new PlanLimitExceededException("Metas Financeiras", plano.getMaxMetas());
        }
        
        // Pessoas (Para controle de dívidas)
        if (path.matches("^/api/pessoas/?$")) {
            long total = pessoaRepository.count();
            if (total >= plano.getMaxDividas()) throw new PlanLimitExceededException("Pessoas/Dívidas", plano.getMaxDividas());
        }

        return true;
    }
}
