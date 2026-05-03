package com.certsender.controller;

import com.certsender.service.ExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * CONTRÔLEUR D'EXPORT
 *
 * GET /api/export/excel → télécharge l'historique en .xlsx
 * GET /api/export/csv   → télécharge l'historique en .csv
 *
 * Ces routes sont protégées : token JWT obligatoire.
 */
@RestController
@RequestMapping("/api/export")
@RequiredArgsConstructor
public class ExportController {

    private final ExportService exportService;

    private String timestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"));
    }

    // ── GET /api/export/excel ─────────────────────────────────────────────────
    @GetMapping("/excel")
    public ResponseEntity<byte[]> exportExcel() throws IOException {
        byte[] data = exportService.exportToExcel();
        String filename = "historique_certsender_" + timestamp() + ".xlsx";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(data.length)
                .body(data);
    }

    // ── GET /api/export/csv ───────────────────────────────────────────────────
    @GetMapping("/csv")
    public ResponseEntity<byte[]> exportCsv() throws IOException {
        byte[] data = exportService.exportToCsv();
        String filename = "historique_certsender_" + timestamp() + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .contentLength(data.length)
                .body(data);
    }
}
