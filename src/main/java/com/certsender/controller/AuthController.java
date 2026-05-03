package com.certsender.controller;

import com.certsender.dto.AuthDto;
import com.certsender.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * CONTRÔLEUR AUTH + GESTION ADMINS
 *
 * Routes publiques :
 *   POST /api/auth/login          → connexion
 *
 * Routes protégées (token requis) :
 *   GET  /api/auth/me             → profil de l'admin connecté
 *   POST /api/auth/change-password → changer son mot de passe
 *
 * Routes SUPER_ADMIN uniquement (configuré dans SecurityConfig) :
 *   GET    /api/admins            → liste des admins
 *   POST   /api/admins            → créer un admin
 *   PUT    /api/admins/{id}/deactivate → désactiver
 *   PUT    /api/admins/{id}/reactivate → réactiver
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AdminService adminService;

    // ── POST /api/auth/login ─────────────────────────────────────────────────
    @PostMapping("/api/auth/login")
    public ResponseEntity<?> login(@Valid @RequestBody AuthDto.LoginRequest request) {
        try {
            AuthDto.LoginResponse response = adminService.login(request);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // ── GET /api/auth/me ─────────────────────────────────────────────────────
    @GetMapping("/api/auth/me")
    public ResponseEntity<AuthDto.AdminResponse> getProfile(Authentication auth) {
        return ResponseEntity.ok(adminService.getProfile(auth.getName()));
    }

    // ── POST /api/auth/change-password ───────────────────────────────────────
    @PostMapping("/api/auth/change-password")
    public ResponseEntity<Map<String, String>> changePassword(
            @RequestBody AuthDto.ChangePasswordRequest request,
            Authentication auth) {
        try {
            adminService.changePassword(auth.getName(), request);
            return ResponseEntity.ok(Map.of("message", "Mot de passe modifié avec succès"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── GET /api/admins ──────────────────────────────────────────────────────
    @GetMapping("/api/admins")
    public ResponseEntity<List<AuthDto.AdminResponse>> getAllAdmins() {
        return ResponseEntity.ok(adminService.getAllAdmins());
    }

    // ── POST /api/admins ─────────────────────────────────────────────────────
    @PostMapping("/api/admins")
    public ResponseEntity<?> createAdmin(
            @Valid @RequestBody AuthDto.CreateAdminRequest request,
            Authentication auth) {
        try {
            AuthDto.AdminResponse created = adminService.createAdmin(request, auth.getName());
            return ResponseEntity.ok(created);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── PUT /api/admins/{id}/deactivate ──────────────────────────────────────
    @PutMapping("/api/admins/{id}/deactivate")
    public ResponseEntity<Map<String, String>> deactivate(
            @PathVariable Long id, Authentication auth) {
        try {
            adminService.deactivateAdmin(id, auth.getName());
            return ResponseEntity.ok(Map.of("message", "Admin désactivé"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── PUT /api/admins/{id}/reactivate ──────────────────────────────────────
    @PutMapping("/api/admins/{id}/reactivate")
    public ResponseEntity<Map<String, String>> reactivate(@PathVariable Long id) {
        adminService.reactivateAdmin(id);
        return ResponseEntity.ok(Map.of("message", "Admin réactivé"));
    }
}
