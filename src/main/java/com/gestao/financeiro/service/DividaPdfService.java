package com.gestao.financeiro.service;

import com.gestao.financeiro.dto.response.DividaResponse;
import com.gestao.financeiro.dto.response.ParcelaDividaResponse;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DividaPdfService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Color PRIMARY_COLOR = new Color(33, 37, 41);
    private static final Color HEADER_BG = new Color(52, 58, 64);
    private static final Color PERSON_BG = new Color(233, 236, 239);
    private static final Color SUBTOTAL_BG = new Color(248, 249, 250);
    private static final Color TOTAL_BG = new Color(52, 58, 64);

    private static final Font TITLE_FONT = new Font(Font.HELVETICA, 18, Font.BOLD, PRIMARY_COLOR);
    private static final Font SUBTITLE_FONT = new Font(Font.HELVETICA, 10, Font.NORMAL, new Color(108, 117, 125));
    private static final Font TABLE_HEADER_FONT = new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE);
    private static final Font PERSON_FONT = new Font(Font.HELVETICA, 11, Font.BOLD, PRIMARY_COLOR);
    private static final Font CELL_FONT = new Font(Font.HELVETICA, 9, Font.NORMAL, PRIMARY_COLOR);
    private static final Font CELL_BOLD_FONT = new Font(Font.HELVETICA, 9, Font.BOLD, PRIMARY_COLOR);
    private static final Font SUBTOTAL_FONT = new Font(Font.HELVETICA, 10, Font.BOLD, PRIMARY_COLOR);
    private static final Font TOTAL_FONT = new Font(Font.HELVETICA, 11, Font.BOLD, Color.WHITE);

    public byte[] gerarPdf(List<DividaResponse> dividas, BigDecimal totalGeral,
                           String tipoFiltro, String pessoaFiltro, Integer ano, Integer mes, String statusFiltro) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 36, 36, 50, 36);
            PdfWriter.getInstance(document, baos);
            document.open();

            addHeader(document, tipoFiltro, pessoaFiltro, ano, mes, statusFiltro);
            document.add(new Paragraph(" "));

            // Agrupar por pessoa
            Map<String, List<DividaResponse>> porPessoa = dividas.stream()
                    .collect(Collectors.groupingBy(
                            DividaResponse::pessoaNome,
                            LinkedHashMap::new,
                            Collectors.toList()));

            for (Map.Entry<String, List<DividaResponse>> entry : porPessoa.entrySet()) {
                addPessoaSection(document, entry.getKey(), entry.getValue());
                document.add(new Paragraph(" "));
            }

            // Total Geral
            addTotalGeral(document, totalGeral);

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Erro ao gerar PDF de dívidas", e);
            throw new RuntimeException("Erro ao gerar PDF de dívidas", e);
        }
    }

    private void addHeader(Document document, String tipo, String pessoa, Integer ano, Integer mes, String status) throws DocumentException {
        Paragraph title = new Paragraph("Extrato Detalhado de Lançamentos", TITLE_FONT);
        title.setAlignment(Element.ALIGN_CENTER);
        document.add(title);

        String dataEmissao = "Emitido em: " + LocalDate.now().format(DATE_FMT);
        Paragraph dataParagraph = new Paragraph(dataEmissao, SUBTITLE_FONT);
        dataParagraph.setAlignment(Element.ALIGN_CENTER);
        document.add(dataParagraph);

        // Filtros aplicados
        List<String> filtros = new ArrayList<>();
        if (tipo != null) filtros.add("Tipo: " + formatTipo(tipo));
        if (pessoa != null) filtros.add("Pessoa: " + pessoa);
        if (ano != null) {
            String periodo = mes != null ? String.format("%02d/%d", mes, ano) : String.valueOf(ano);
            filtros.add("Período: " + periodo);
        }
        if (status != null) filtros.add("Status: " + formatStatus(status));

        if (!filtros.isEmpty()) {
            Paragraph filtrosParagraph = new Paragraph("Filtros: " + String.join(" | ", filtros), SUBTITLE_FONT);
            filtrosParagraph.setAlignment(Element.ALIGN_CENTER);
            filtrosParagraph.setSpacingBefore(4);
            document.add(filtrosParagraph);
        }

        // Linha separadora
        PdfPTable line = new PdfPTable(1);
        line.setWidthPercentage(100);
        line.setSpacingBefore(10);
        PdfPCell lineCell = new PdfPCell();
        lineCell.setBorderWidthBottom(1.5f);
        lineCell.setBorderColorBottom(HEADER_BG);
        lineCell.setBorderWidthTop(0);
        lineCell.setBorderWidthLeft(0);
        lineCell.setBorderWidthRight(0);
        lineCell.setFixedHeight(1);
        line.addCell(lineCell);
        document.add(line);
    }

    private void addPessoaSection(Document document, String pessoaNome, List<DividaResponse> dividas) throws DocumentException {
        // Header da pessoa
        PdfPTable personHeader = new PdfPTable(1);
        personHeader.setWidthPercentage(100);
        PdfPCell personCell = new PdfPCell(new Phrase(pessoaNome, PERSON_FONT));
        personCell.setBackgroundColor(PERSON_BG);
        personCell.setPadding(8);
        personCell.setBorder(0);
        personHeader.addCell(personCell);
        document.add(personHeader);

        // Tabela de parcelas
        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{35f, 12f, 18f, 18f, 17f});

        // Header da tabela
        addTableHeaderCell(table, "Descrição");
        addTableHeaderCell(table, "Parcela");
        addTableHeaderCell(table, "Vencimento");
        addTableHeaderCell(table, "Valor");
        addTableHeaderCell(table, "Status");

        BigDecimal subtotal = BigDecimal.ZERO;
        boolean alternate = false;

        for (DividaResponse divida : dividas) {
            if (divida.parcelas() == null || divida.parcelas().isEmpty()) {
                // Dívida sem parcelas filtradas – mostrar linha resumo
                PdfPCell descCell = createCell(divida.descricao(), CELL_BOLD_FONT, alternate);
                table.addCell(descCell);
                table.addCell(createCell("-", CELL_FONT, alternate));
                table.addCell(createCell("-", CELL_FONT, alternate));
                table.addCell(createCellRight(formatCurrency(divida.valorRestante()), CELL_FONT, alternate));
                table.addCell(createCell(formatStatus(divida.status().name()), CELL_FONT, alternate));
                subtotal = subtotal.add(divida.valorRestante() != null ? divida.valorRestante() : BigDecimal.ZERO);
                alternate = !alternate;
            } else {
                for (ParcelaDividaResponse parcela : divida.parcelas()) {
                    String parcelaStr = parcela.numeroParcela() + "/" + divida.totalParcelas();
                    PdfPCell descCell = createCell(divida.descricao(), CELL_BOLD_FONT, alternate);
                    table.addCell(descCell);
                    table.addCell(createCell(parcelaStr, CELL_FONT, alternate));
                    table.addCell(createCell(parcela.dataVencimento() != null ? parcela.dataVencimento().format(DATE_FMT) : "-", CELL_FONT, alternate));
                    table.addCell(createCellRight(formatCurrency(parcela.valor()), CELL_FONT, alternate));
                    table.addCell(createCell(formatStatus(parcela.status().name()), CELL_FONT, alternate));
                    subtotal = subtotal.add(parcela.valor() != null ? parcela.valor() : BigDecimal.ZERO);
                    alternate = !alternate;
                }
            }
        }

        // Subtotal da pessoa
        PdfPCell emptyCell = new PdfPCell(new Phrase("", CELL_FONT));
        emptyCell.setBackgroundColor(SUBTOTAL_BG);
        emptyCell.setBorder(0);
        emptyCell.setColspan(3);
        table.addCell(emptyCell);

        PdfPCell subtotalLabel = new PdfPCell(new Phrase("Subtotal:", SUBTOTAL_FONT));
        subtotalLabel.setBackgroundColor(SUBTOTAL_BG);
        subtotalLabel.setBorder(0);
        subtotalLabel.setPadding(6);
        subtotalLabel.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(subtotalLabel);

        PdfPCell subtotalValue = new PdfPCell(new Phrase(formatCurrency(subtotal), SUBTOTAL_FONT));
        subtotalValue.setBackgroundColor(SUBTOTAL_BG);
        subtotalValue.setBorder(0);
        subtotalValue.setPadding(6);
        subtotalValue.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(subtotalValue);

        document.add(table);
    }

    private void addTotalGeral(Document document, BigDecimal totalGeral) throws DocumentException {
        PdfPTable totalTable = new PdfPTable(2);
        totalTable.setWidthPercentage(100);
        totalTable.setWidths(new float[]{70f, 30f});
        totalTable.setSpacingBefore(10);

        PdfPCell labelCell = new PdfPCell(new Phrase("TOTAL GERAL", TOTAL_FONT));
        labelCell.setBackgroundColor(TOTAL_BG);
        labelCell.setPadding(10);
        labelCell.setBorder(0);
        labelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totalTable.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(formatCurrency(totalGeral), TOTAL_FONT));
        valueCell.setBackgroundColor(TOTAL_BG);
        valueCell.setPadding(10);
        valueCell.setBorder(0);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totalTable.addCell(valueCell);

        document.add(totalTable);
    }

    // ─── Helpers ─────────────────────────────────────────────────────

    private void addTableHeaderCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, TABLE_HEADER_FONT));
        cell.setBackgroundColor(HEADER_BG);
        cell.setPadding(6);
        cell.setBorder(0);
        table.addCell(cell);
    }

    private PdfPCell createCell(String text, Font font, boolean alternate) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "-", font));
        if (alternate) cell.setBackgroundColor(new Color(248, 249, 250));
        cell.setPadding(5);
        cell.setBorder(0);
        return cell;
    }

    private PdfPCell createCellRight(String text, Font font, boolean alternate) {
        PdfPCell cell = createCell(text, font, alternate);
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        return cell;
    }

    private String formatCurrency(BigDecimal value) {
        if (value == null) return "R$ 0,00";
        return String.format("R$ %,.2f", value).replace(",", "X").replace(".", ",").replace("X", ".");
    }

    private String formatTipo(String tipo) {
        return switch (tipo) {
            case "A_RECEBER" -> "A Receber";
            case "A_PAGAR" -> "A Pagar";
            default -> tipo;
        };
    }

    private String formatStatus(String status) {
        return switch (status) {
            case "PENDENTE" -> "Pendente";
            case "PAGA", "PAGO" -> "Pago";
            case "ATRASADA", "ATRASADO" -> "Atrasado";
            case "CANCELADO" -> "Cancelado";
            default -> status;
        };
    }
}
