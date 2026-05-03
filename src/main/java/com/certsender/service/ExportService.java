package com.certsender.service;

import com.certsender.entity.EmailLog;
import com.certsender.repository.EmailLogRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * SERVICE D'EXPORT
 *
 * Génère l'historique des envois sous deux formats :
 *  - Excel (.xlsx) via Apache POI
 *  - CSV  (.csv)  via écriture manuelle
 */
@Service
@RequiredArgsConstructor
public class ExportService {

    private final EmailLogRepository emailLogRepository;

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    // ─── EXPORT EXCEL ────────────────────────────────────────────────────────

    /**
     * Génère un fichier Excel (.xlsx) contenant tout l'historique.
     * @return tableau d'octets du fichier Excel
     */
    public byte[] exportToExcel() throws IOException {

        List<EmailLog> logs = emailLogRepository.findAllByOrderBySentAtDesc();

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Historique des envois");

            // ── Style entête : fond bleu foncé, texte blanc, gras ──
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            Font headerFont = workbook.createFont();
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 11);
            headerStyle.setFont(headerFont);

            // ── Style succès : fond vert clair ──
            CellStyle successStyle = workbook.createCellStyle();
            successStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
            successStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // ── Style échec : fond rouge clair ──
            CellStyle errorStyle = workbook.createCellStyle();
            errorStyle.setFillForegroundColor(IndexedColors.ROSE.getIndex());
            errorStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // ── Ligne d'entête ──
            String[] headers = {"#", "Email Étudiant", "Fichier PDF",
                                 "Session", "Date d'envoi", "Statut", "Message d'erreur"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // ── Lignes de données ──
            int rowNum = 1;
            for (EmailLog log : logs) {
                Row row = sheet.createRow(rowNum++);

                row.createCell(0).setCellValue(log.getId());
                row.createCell(1).setCellValue(nvl(log.getStudentEmail()));
                row.createCell(2).setCellValue(nvl(log.getFileName()));
                row.createCell(3).setCellValue(nvl(log.getSessionId()));
                row.createCell(4).setCellValue(
                        log.getSentAt() != null ? log.getSentAt().format(FMT) : "—");

                Cell statusCell = row.createCell(5);
                statusCell.setCellValue(log.getStatus().name());
                if (log.getStatus() == EmailLog.EmailStatus.SUCCESS) {
                    statusCell.setCellStyle(successStyle);
                } else if (log.getStatus() == EmailLog.EmailStatus.FAILED) {
                    statusCell.setCellStyle(errorStyle);
                }

                row.createCell(6).setCellValue(nvl(log.getErrorMessage()));
            }

            // ── Ajuste la largeur des colonnes automatiquement ──
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                // Minimum 3000 unités pour les petites colonnes
                if (sheet.getColumnWidth(i) < 3000) sheet.setColumnWidth(i, 3000);
            }

            // ── Ajoute une ligne de résumé en bas ──
            Row summaryRow = sheet.createRow(rowNum + 1);
            long success = logs.stream()
                    .filter(l -> l.getStatus() == EmailLog.EmailStatus.SUCCESS).count();
            long failed  = logs.stream()
                    .filter(l -> l.getStatus() == EmailLog.EmailStatus.FAILED).count();
            summaryRow.createCell(0).setCellValue(
                    "Total: " + logs.size() + " | Succès: " + success + " | Échecs: " + failed);

            workbook.write(out);
            return out.toByteArray();
        }
    }

    // ─── EXPORT CSV ──────────────────────────────────────────────────────────

    /**
     * Génère une chaîne de caractères au format CSV.
     * @return contenu CSV encodé en UTF-8
     */
    public byte[] exportToCsv() throws IOException {

        List<EmailLog> logs = emailLogRepository.findAllByOrderBySentAtDesc();

        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             PrintWriter writer = new PrintWriter(out)) {

            // BOM UTF-8 : permet à Excel d'ouvrir correctement les CSV avec accents
            out.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});

            // Entête CSV
            writer.println("ID,Email Etudiant,Fichier PDF,Session,Date Envoi,Statut,Erreur");

            // Données
            for (EmailLog log : logs) {
                writer.printf("%d,%s,%s,%s,%s,%s,%s%n",
                        log.getId(),
                        csvEscape(log.getStudentEmail()),
                        csvEscape(log.getFileName()),
                        csvEscape(log.getSessionId()),
                        log.getSentAt() != null ? log.getSentAt().format(FMT) : "",
                        log.getStatus().name(),
                        csvEscape(log.getErrorMessage())
                );
            }

            writer.flush();
            return out.toByteArray();
        }
    }

    // ─── Utilitaires ─────────────────────────────────────────────────────────

    private String nvl(String s) {
        return s != null ? s : "—";
    }

    /** Encapsule une valeur CSV dans des guillemets si elle contient une virgule */
    private String csvEscape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
