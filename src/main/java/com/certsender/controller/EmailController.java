package com.certsender.controller;

import com.certsender.dto.EmailDto;
import com.certsender.service.EmailService;
import com.sendgrid.*;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * CONTRÔLEUR EMAIL
 *
 * Routes :
 *   POST   /api/emails/send       → Envoie les certificats
 *   GET    /api/emails/logs       → Historique
 *   GET    /api/emails/stats      → Statistiques dashboard
 *   DELETE /api/emails/logs       → Supprimer l'historique
 *   GET    /api/emails/health     → Vérification backend (PUBLIC)
 *   GET    /api/emails/test-sendgrid → Test connexion SendGrid (DIAGNOSTIC)
 */
@RestController
@RequestMapping("/api/emails")
@RequiredArgsConstructor
@Slf4j
public class EmailController {

    private final EmailService emailService;

    @Value("${sendgrid.api.key}")
    private String sendGridApiKey;

    @Value("${sendgrid.from.email}")
    private String fromEmail;

    @Value("${sendgrid.from.name:Administration}")
    private String fromName;

    // ── POST /api/emails/send ─────────────────────────────────────────────────
    @PostMapping("/send")
    public ResponseEntity<EmailDto.SendSummary> sendCertificates(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam("emailSubject") String emailSubject,
            @RequestParam("emailBody") String emailBody) {

        log.info("Requête d'envoi reçue : {} fichiers", files.length);

        if (files == null || files.length == 0)
            return ResponseEntity.badRequest().build();
        if (emailSubject == null || emailSubject.isBlank())
            return ResponseEntity.badRequest().build();

        EmailDto.SendSummary summary = emailService.sendCertificates(
                files, emailSubject, emailBody);
        return ResponseEntity.ok(summary);
    }

    // ── GET /api/emails/logs ──────────────────────────────────────────────────
    @GetMapping("/logs")
    public ResponseEntity<List<EmailDto.LogResponse>> getLogs() {
        return ResponseEntity.ok(emailService.getAllLogs());
    }

    // ── GET /api/emails/stats ─────────────────────────────────────────────────
    @GetMapping("/stats")
    public ResponseEntity<EmailDto.DashboardStats> getStats() {
        return ResponseEntity.ok(emailService.getDashboardStats());
    }

    // ── DELETE /api/emails/logs ───────────────────────────────────────────────
    @DeleteMapping("/logs")
    public ResponseEntity<Map<String, String>> clearHistory() {
        emailService.clearHistory();
        return ResponseEntity.ok(Map.of("message", "Historique supprimé"));
    }

    // ── GET /api/emails/health (PUBLIC) ───────────────────────────────────────
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "message", "Cert Sender API is running"
        ));
    }

    // ── GET /api/emails/test-sendgrid (DIAGNOSTIC) ───────────────────────────
    /**
     * Teste directement la connexion SendGrid et envoie un email de test
     * à l'adresse passée en paramètre.
     *
     * Usage : GET /api/emails/test-sendgrid?to=votre@email.com
     *
     * Retourne les détails complets de la réponse SendGrid pour diagnostiquer
     * les problèmes de configuration.
     */
    @GetMapping("/test-sendgrid")
    public ResponseEntity<Map<String, Object>> testSendGrid(
            @RequestParam(defaultValue = "") String to) {

        log.info("=== TEST SENDGRID MANUEL ===");
        log.info("sendGridApiKey longueur : {}", sendGridApiKey != null ? sendGridApiKey.length() : "NULL");
        log.info("sendGridApiKey debut : {}", sendGridApiKey != null && sendGridApiKey.length() > 15
                ? sendGridApiKey.substring(0, 15) + "..." : "TROP COURTE OU NULL");
        log.info("fromEmail : {}", fromEmail);
        log.info("destinataire test : {}", to);

        if (to.isBlank()) {
            return ResponseEntity.ok(Map.of(
                    "error", "Ajoutez ?to=votre@email.com dans l'URL",
                    "exemple", "/api/emails/test-sendgrid?to=votreemail@gmail.com",
                    "sendGridApiKeyLength", sendGridApiKey != null ? sendGridApiKey.length() : 0,
                    "sendGridApiKeyDebut", sendGridApiKey != null && sendGridApiKey.length() > 15
                            ? sendGridApiKey.substring(0, 15) + "..." : "INVALIDE",
                    "fromEmail", fromEmail
            ));
        }

        try {
            Email from    = new Email(fromEmail, fromName);
            Email toEmail = new Email(to);
            Content content = new Content("text/plain",
                    "Email de test CertSender.\n\n"
                    + "Si vous recevez ce message, SendGrid est correctement configuré !\n\n"
                    + "From: " + fromEmail + "\n"
                    + "To: " + to + "\n"
                    + "API Key length: " + (sendGridApiKey != null ? sendGridApiKey.length() : 0));
            Mail mail = new Mail(from, "[TEST] CertSender - Vérification SendGrid", toEmail, content);

            SendGrid sg = new SendGrid(sendGridApiKey.trim());
            Request request = new Request();
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());

            log.info("Envoi requête test à SendGrid...");
            Response response = sg.api(request);

            log.info("Réponse SendGrid : code={}, body={}", response.getStatusCode(), response.getBody());

            boolean success = response.getStatusCode() == 202;

            return ResponseEntity.ok(Map.of(
                    "success", success,
                    "sendGridStatusCode", response.getStatusCode(),
                    "sendGridResponseBody", response.getBody() != null ? response.getBody() : "(vide)",
                    "sendGridHeaders", response.getHeaders() != null ? response.getHeaders().toString() : "(vide)",
                    "fromEmail", fromEmail,
                    "toEmail", to,
                    "apiKeyLength", sendGridApiKey.length(),
                    "apiKeyDebut", sendGridApiKey.substring(0, Math.min(15, sendGridApiKey.length())) + "...",
                    "message", success
                            ? "Email de test envoyé ! Vérifiez votre boîte (et les spams)."
                            : "ECHEC - SendGrid a rejeté l'email. Voir sendGridResponseBody pour le détail."
            ));

        } catch (Exception e) {
            log.error("Exception lors du test SendGrid : {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", e.getClass().getSimpleName() + ": " + e.getMessage(),
                    "fromEmail", fromEmail,
                    "apiKeyLength", sendGridApiKey != null ? sendGridApiKey.length() : 0
            ));
        }
    }
}
