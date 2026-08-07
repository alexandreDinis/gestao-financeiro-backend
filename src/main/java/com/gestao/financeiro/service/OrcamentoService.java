package com.gestao.financeiro.service;

import com.gestao.financeiro.dto.request.GerarOrcamentoLoteRequest;
import com.gestao.financeiro.dto.request.OrcamentoItemLoteRequest;
import com.gestao.financeiro.dto.request.OrcamentoRequest;
import com.gestao.financeiro.dto.response.OrcamentoResponse;
import com.gestao.financeiro.dto.response.OrcamentoResumoResponse;
import com.gestao.financeiro.dto.response.OrcamentoSugestaoResponse;
import com.gestao.financeiro.entity.Categoria;
import com.gestao.financeiro.entity.Orcamento;
import com.gestao.financeiro.entity.enums.TipoCategoria;
import com.gestao.financeiro.exception.BusinessException;
import com.gestao.financeiro.exception.ResourceNotFoundException;
import com.gestao.financeiro.mapper.OrcamentoMapper;
import com.gestao.financeiro.repository.CategoriaRepository;
import com.gestao.financeiro.repository.OrcamentoRepository;
import com.gestao.financeiro.config.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class OrcamentoService {

    private final OrcamentoRepository orcamentoRepository;
    private final CategoriaRepository categoriaRepository;
    private final OrcamentoMapper orcamentoMapper;
    private final EntityManager entityManager;



    public List<OrcamentoResponse> listar(Integer mes, Integer ano) {
        return orcamentoRepository.findByMesAndAno(mes, ano).stream()
                .map(orcamentoMapper::toResponse)
                .toList();
    }

    /**
     * Resumo: limite vs gasto real por categoria.
     * Gasto real vem de DUAS fontes:
     * 1. Transações DESPESA com status PAGO (gastos diretos de débito)
     * 2. Parcelas de cartão de crédito que vencem no período (impacto mensal)
     * 
     * Isso garante que compras no cartão de crédito também sejam
     * contabilizadas no orçamento.
     * 
     * Suporta hierarquia: se a categoria do orçamento tem filhas, agrega
     * os gastos das filhas e retorna o breakdown.
     */
    @SuppressWarnings("unchecked")
    public List<OrcamentoResumoResponse> resumo(Integer mes, Integer ano) {
        List<Orcamento> orcamentos = orcamentoRepository.findByMesAndAno(mes, ano);

        LocalDate inicio = LocalDate.of(ano, mes, 1);
        LocalDate fim = inicio.with(TemporalAdjusters.lastDayOfMonth());

        return orcamentos.stream().map(orc -> {
            Long catId = orc.getCategoria().getId();
            
            // Buscar subcategorias (filhas)
            List<Categoria> subcategorias = categoriaRepository.findByCategoriaPaiId(catId);
            
            // Calcula gasto da categoria pai
            BigDecimal gastoPai = calcularGastoCategoria(catId, inicio, fim);
            
            // Calcula gasto das filhas e monta breakdown
            BigDecimal gastoFilhasTotal = BigDecimal.ZERO;
            List<OrcamentoResumoResponse.SubcategoriaGasto> breakdown = new java.util.ArrayList<>();
            
            for (Categoria subcat : subcategorias) {
                BigDecimal gastoSub = calcularGastoCategoria(subcat.getId(), inicio, fim);
                if (gastoSub.compareTo(BigDecimal.ZERO) > 0) {
                    gastoFilhasTotal = gastoFilhasTotal.add(gastoSub);
                    breakdown.add(new OrcamentoResumoResponse.SubcategoriaGasto(
                            subcat.getId(), subcat.getNome(), subcat.getCor(), gastoSub
                    ));
                }
            }

            BigDecimal gastoTotal = gastoPai.add(gastoFilhasTotal);

            BigDecimal restante = orc.getLimite().subtract(gastoTotal);
            double percentual = orc.getLimite().compareTo(BigDecimal.ZERO) > 0
                    ? gastoTotal.multiply(BigDecimal.valueOf(100))
                            .divide(orc.getLimite(), 1, RoundingMode.HALF_UP)
                            .doubleValue()
                    : 0;

            return new OrcamentoResumoResponse(
                    orc.getId(),
                    orc.getCategoria().getId(),
                    orc.getCategoria().getNome(),
                    orc.getCategoria().getCor(),
                    orc.getLimite(),
                    gastoTotal,
                    restante,
                    percentual,
                    breakdown.isEmpty() ? null : breakdown
            );
        }).toList();
    }

    public List<OrcamentoSugestaoResponse> gerarSugestoes(Integer mes, Integer ano, Integer mesesHistorico) {
        int window = (mesesHistorico == null || mesesHistorico <= 0) ? 3 : mesesHistorico;

        LocalDate targetMonthStart = LocalDate.of(ano, mes, 1);
        LocalDate inicioHistorico = targetMonthStart.minusMonths(window);
        LocalDate fimHistorico = targetMonthStart.minusDays(1);

        // Fetch all DESPESA categories for the tenant
        List<Categoria> categoriasDespesa = categoriaRepository.findByTipo(TipoCategoria.DESPESA, Pageable.unpaged()).getContent()
                .stream().filter(c -> c.getDeletedAt() == null).toList();

        // Existing orcamentos for the target month
        List<Orcamento> orcamentosExistentes = orcamentoRepository.findByMesAndAno(mes, ano);

        return categoriasDespesa.stream().map(cat -> {
            Long catId = cat.getId();

            // Calculate total expenses over historical window (including subcategories)
            List<Categoria> subcats = categoriaRepository.findByCategoriaPaiId(catId);
            BigDecimal totalHistoricoPai = calcularGastoCategoria(catId, inicioHistorico, fimHistorico);
            BigDecimal totalHistoricoSub = BigDecimal.ZERO;
            for (Categoria sub : subcats) {
                totalHistoricoSub = totalHistoricoSub.add(calcularGastoCategoria(sub.getId(), inicioHistorico, fimHistorico));
            }
            BigDecimal totalHistorico = totalHistoricoPai.add(totalHistoricoSub);

            BigDecimal mediaHistorica = totalHistorico.divide(BigDecimal.valueOf(window), 2, RoundingMode.HALF_UP);

            Orcamento orcExistente = orcamentosExistentes.stream()
                    .filter(o -> o.getCategoria().getId().equals(catId))
                    .findFirst()
                    .orElse(null);

            BigDecimal limiteAtual = orcExistente != null ? orcExistente.getLimite() : null;
            BigDecimal limiteSugerido = limiteAtual != null ? limiteAtual : mediaHistorica;

            return new OrcamentoSugestaoResponse(
                    cat.getId(),
                    cat.getNome(),
                    cat.getCor(),
                    mediaHistorica,
                    limiteAtual,
                    limiteSugerido
            );
        }).toList();
    }

    @Transactional
    public List<OrcamentoResponse> salvarLote(GerarOrcamentoLoteRequest request) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BusinessException("Tenant ID não encontrado no contexto");
        }

        List<Orcamento> orcamentosExistentes = orcamentoRepository.findByMesAndAno(request.mes(), request.ano());
        List<OrcamentoResponse> resultado = new ArrayList<>();

        for (OrcamentoItemLoteRequest item : request.orcamentos()) {
            if (item.limite() == null) continue;

            Orcamento orcamento = orcamentosExistentes.stream()
                    .filter(o -> o.getCategoria().getId().equals(item.categoriaId()))
                    .findFirst()
                    .orElse(null);

            if (orcamento != null) {
                orcamento.setLimite(item.limite());
                orcamento = orcamentoRepository.save(orcamento);
                resultado.add(orcamentoMapper.toResponse(orcamento));
            } else if (item.limite().compareTo(BigDecimal.ZERO) > 0) {
                Categoria categoria = categoriaRepository.findById(item.categoriaId())
                        .orElseThrow(() -> new ResourceNotFoundException("Categoria", item.categoriaId()));

                Orcamento novo = new Orcamento();
                novo.setTenantId(tenantId);
                novo.setCategoria(categoria);
                novo.setMes(request.mes());
                novo.setAno(request.ano());
                novo.setLimite(item.limite());

                novo = orcamentoRepository.save(novo);
                resultado.add(orcamentoMapper.toResponse(novo));
            }
        }

        log.info("[tenant={}] Salvos {} orçamentos em lote para {}/{}", tenantId, resultado.size(), request.mes(), request.ano());
        return resultado;
    }

    private BigDecimal calcularGastoCategoria(Long catId, LocalDate inicio, LocalDate fim) {
        // 1. Gastos DIRETOS (transação no débito, status PAGO)
        String jpql = """
            SELECT COALESCE(SUM(t.valor), 0)
            FROM Transacao t
            WHERE t.categoria.id = :catId
              AND t.tipo = 'DESPESA'
              AND t.status = 'PAGO'
              AND t.data BETWEEN :inicio AND :fim
              AND t.deletedAt IS NULL
        """;

        BigDecimal gastoTransacao = (BigDecimal) entityManager.createQuery(jpql)
                .setParameter("catId", catId)
                .setParameter("inicio", inicio)
                .setParameter("fim", fim)
                .getSingleResult();

        // 2. Parcelas de CARTÃO DE CRÉDITO que vencem no período (impacto mensal)
        String jpqlParcela = """
            SELECT COALESCE(SUM(p.valorParcela), 0)
            FROM Parcela p
            JOIN p.transacao t
            WHERE t.categoria.id = :catId
              AND p.dataVencimento BETWEEN :inicio AND :fim
              AND t.deletedAt IS NULL
              AND t.status <> 'CANCELADO'
        """;

        BigDecimal gastoParcela = (BigDecimal) entityManager.createQuery(jpqlParcela)
                .setParameter("catId", catId)
                .setParameter("inicio", inicio)
                .setParameter("fim", fim)
                .getSingleResult();

        return gastoTransacao.add(gastoParcela);
    }

    @Transactional
    public OrcamentoResponse criar(OrcamentoRequest request) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BusinessException("Tenant ID não encontrado no contexto");
        }

        if (orcamentoRepository.existsByCategoriaIdAndMesAndAnoAndTenantId(
                request.categoriaId(), request.mes(), request.ano(), tenantId)) {
            throw new BusinessException("Já existe orçamento para esta categoria neste mês.");
        }

        Categoria categoria = categoriaRepository.findById(request.categoriaId())
                .orElseThrow(() -> new ResourceNotFoundException("Categoria", request.categoriaId()));

        Orcamento orcamento = orcamentoMapper.toEntity(request);
        orcamento.setTenantId(tenantId);
        orcamento.setCategoria(categoria);

        orcamento = orcamentoRepository.save(orcamento);
        log.info("[tenant={}] Orçamento criado: id={} categoria={} limite={} {}/{}",
                tenantId, orcamento.getId(), categoria.getNome(), request.limite(), request.mes(), request.ano());

        return orcamentoMapper.toResponse(orcamento);
    }

    @Transactional
    public OrcamentoResponse atualizar(Long id, OrcamentoRequest request) {
        Orcamento orcamento = findById(id);
        orcamento.setLimite(request.limite());
        orcamento = orcamentoRepository.save(orcamento);
        log.info("[tenant={}] Orçamento atualizado: id={} novoLimite={}", orcamento.getTenantId(), id, request.limite());
        return orcamentoMapper.toResponse(orcamento);
    }

    @Transactional
    public void deletar(Long id) {
        Orcamento orcamento = findById(id);
        orcamento.softDelete();
        orcamentoRepository.save(orcamento);
        log.info("[tenant={}] Orçamento removido: id={}", orcamento.getTenantId(), id);
    }

    private Orcamento findById(Long id) {
        return orcamentoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Orçamento", id));
    }
}
