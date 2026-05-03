package com.certsender.service;

import com.certsender.dto.EmailDto;
import com.certsender.entity.EmailLog;
import com.certsender.entity.EmailLog.EmailStatus;
import com.certsender.repository.EmailLogRepository;
import com.sendgrid.*;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final EmailLogRepository emailLogRepository;

    @Value("${sendgrid.api.key}")
    private String sendGridApiKey;

    @Value("${sendgrid.from.email}")
    private String fromEmail;

    @Value("${sendgrid.from.name:Administration}")
    private String fromName;

    @Value("${app.mail.delay-between-emails:500}")
    private long delayBetweenEmails;

    // ─── Méthode principale ──────────────────────────────────────────────────

    public EmailDto.SendSummary sendCertificates(
            MultipartFile[] files, String emailSubject, String emailBody) {

        String sessionId = UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // LOG DE DIAGNOSTIC — visible dans les logs Spring Boot (console)
        log.info("=== DEBUT SESSION [{}] - {} fichiers ===", sessionId, files.length);
        log.info(">>> sendGridApiKey chargée : longueur={}, debut={}",
                sendGridApiKey != null ? sendGridApiKey.length() : "NULL",
                sendGridApiKey != null && sendGridApiKey.length() > 10
                        ? sendGridApiKey.substring(0, 10) + "..." : "INVALIDE");
        log.info(">>> fromEmail = {}", fromEmail);

        LocalDateTime startedAt = LocalDateTime.now();
        List<EmailDto.SendResult> results = new ArrayList<>();
        int successCount = 0, failureCount = 0;

        for (MultipartFile file : files) {
            EmailDto.SendResult result = processSinglePdf(
                    file, emailSubject, emailBody, sessionId);
            results.add(result);
            if (result.isSuccess()) successCount++;
            else failureCount++;

            if (delayBetweenEmails > 0) {
                try { Thread.sleep(delayBetweenEmails); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        }

        log.info("=== FIN SESSION [{}] : {} succes, {} echecs ===",
                sessionId, successCount, failureCount);

        return EmailDto.SendSummary.builder()
                .totalFiles(files.length).successCount(successCount)
                .failureCount(failureCount).results(results)
                .sessionId(sessionId).startedAt(startedAt)
                .finishedAt(LocalDateTime.now()).build();
    }

    // ─── Traite un seul PDF ──────────────────────────────────────────────────

    private EmailDto.SendResult processSinglePdf(
            MultipartFile file, String emailSubject, String emailBody, String sessionId) {

        String fileName     = file.getOriginalFilename();
        String studentEmail = extractEmailFromFileName(fileName);

        log.info(">>> Traitement fichier : [{}] → email extrait : [{}]", fileName, studentEmail);

        if (studentEmail == null) {
            String errorMsg = "Nom de fichier invalide (doit etre email@domaine.ext.pdf) : " + fileName;
            log.warn(errorMsg);
            saveLog(null, fileName, EmailStatus.FAILED, errorMsg, sessionId);
            return EmailDto.SendResult.builder()
                    .email("inconnu").fileName(fileName)
                    .success(false).message(errorMsg)
                    .sentAt(LocalDateTime.now()).build();
        }

        try {
            log.info(">>> Tentative d'envoi SendGrid vers : {}", studentEmail);
            int statusCode = sendViaSendGrid(
                    studentEmail, emailSubject,
                    personalizeBody(emailBody, studentEmail), file);

            log.info(">>> SendGrid status code recu : {}", statusCode);

            if (statusCode == 202) {
                log.info(">>> SUCCESS : email accepte par SendGrid pour {}", studentEmail);
                saveLog(studentEmail, fileName, EmailStatus.SUCCESS, null, sessionId);
                return EmailDto.SendResult.builder()
                        .email(studentEmail).fileName(fileName)
                        .success(true).message("Email envoye avec succes (SendGrid 202)")
                        .sentAt(LocalDateTime.now()).build();
            } else {
                String errorMsg = "SendGrid a refuse l'email. Code HTTP : " + statusCode;
                log.error(">>> ECHEC SendGrid code {} pour {}", statusCode, studentEmail);
                saveLog(studentEmail, fileName, EmailStatus.FAILED, errorMsg, sessionId);
                return EmailDto.SendResult.builder()
                        .email(studentEmail).fileName(fileName)
                        .success(false).message(errorMsg)
                        .sentAt(LocalDateTime.now()).build();
            }

        } catch (Exception e) {
            String errorMsg = "Exception lors de l'appel SendGrid : " + e.getClass().getSimpleName()
                    + " - " + e.getMessage();
            log.error(">>> EXCEPTION pour {} : {}", studentEmail, e.getMessage(), e);
            saveLog(studentEmail, fileName, EmailStatus.FAILED, errorMsg, sessionId);
            return EmailDto.SendResult.builder()
                    .email(studentEmail).fileName(fileName)
                    .success(false).message(errorMsg)
                    .sentAt(LocalDateTime.now()).build();
        }
    }

    // ─── Appel API SendGrid — retourne le code HTTP ──────────────────────────

    /**
     * Retourne le code HTTP de la réponse SendGrid.
     * 202 = email accepté et mis en file de livraison.
     * Tout autre code = erreur.
     */
    private int sendViaSendGrid(String to, String subject, String body, MultipartFile attachment)
            throws IOException {

        // Vérification défensive de la clé API
        if (sendGridApiKey == null || sendGridApiKey.isBlank() || sendGridApiKey.equals("SG.VOTRE_CLE_API_ICI")) {
            throw new IOException("Clé API SendGrid non configurée dans application.properties !");
        }

        Email from    = new Email(fromEmail, fromName);
        Email toEmail = new Email(to);
        Content content = new Content("text/plain", body);
        Mail mail = new Mail(from, subject, toEmail, content);

        // PDF en pièce jointe encodé Base64
        Attachments pdfAttachment = new Attachments();
        pdfAttachment.setContent(Base64.getEncoder().encodeToString(attachment.getBytes()));
        pdfAttachment.setType("application/pdf");
        pdfAttachment.setFilename(attachment.getOriginalFilename());
        pdfAttachment.setDisposition("attachment");
        mail.addAttachments(pdfAttachment);

        // Construction de la requête HTTP vers api.sendgrid.com
        SendGrid sg = new SendGrid(sendGridApiKey.trim()); // trim() évite les espaces accidentels
        Request request = new Request();
        request.setMethod(Method.POST);
        request.setEndpoint("mail/send");
        request.setBody(mail.build());

        log.info(">>> Envoi requête HTTPS à api.sendgrid.com/v3/mail/send ...");
        Response response = sg.api(request);

        log.info(">>> Réponse SendGrid : code={}, body={}",
                response.getStatusCode(),
                response.getBody() != null && !response.getBody().isBlank()
                        ? response.getBody() : "(vide - normal si 202)");

        return response.getStatusCode();
    }

    // ─── Utilitaires ─────────────────────────────────────────────────────────

    private String extractEmailFromFileName(String fileName) {
        if (fileName == null || !fileName.toLowerCase().endsWith(".pdf")) return null;
        String withoutExt = fileName.substring(0, fileName.length() - 4);
        if (withoutExt.contains("@") && withoutExt.contains(".")) {
            String[] parts = withoutExt.split("@");
            if (parts.length == 2 && !parts[0].isEmpty() && parts[1].contains("."))
                return withoutExt.toLowerCase().trim();
        }
        return null;
    }

    private String personalizeBody(String template, String studentEmail) {
        if (template == null) return "";
        return template.replace("{email}", studentEmail)
                       .replace("{date}", LocalDateTime.now().toLocalDate().toString());
    }

    private void saveLog(String studentEmail, String fileName,
                         EmailStatus status, String errorMessage, String sessionId) {
        emailLogRepository.save(EmailLog.builder()
                .studentEmail(studentEmail).fileName(fileName)
                .sentAt(LocalDateTime.now()).status(status)
                .errorMessage(errorMessage).sessionId(sessionId).build());
    }

    // ─── Dashboard & Historique ──────────────────────────────────────────────

    public List<EmailDto.LogResponse> getAllLogs() {
        return emailLogRepository.findAllByOrderBySentAtDesc()
                .stream().map(EmailDto.LogResponse::fromEntity).toList();
    }

    public EmailDto.DashboardStats getDashboardStats() {
        long total   = emailLogRepository.count();
        long success = emailLogRepository.countByStatus(EmailStatus.SUCCESS);
        long failed  = emailLogRepository.countByStatus(EmailStatus.FAILED);
        long pending = emailLogRepository.countByStatus(EmailStatus.PENDING);
        double rate  = total > 0 ? Math.round((double) success / total * 1000.0) / 10.0 : 0;
        return EmailDto.DashboardStats.builder()
                .totalSent(total).successCount(success)
                .failureCount(failed).pendingCount(pending)
                .successRate(rate).build();
    }

    public void clearHistory() {
        emailLogRepository.deleteAll();
    }
}
