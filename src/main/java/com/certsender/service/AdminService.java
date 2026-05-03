package com.certsender.service;

import com.certsender.dto.AuthDto;
import com.certsender.entity.Admin;
import com.certsender.repository.AdminRepository;
import com.certsender.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // ─── LOGIN ───────────────────────────────────────────────────────────────

    /**
     * Vérifie les identifiants et retourne un token JWT si corrects.
     */
    public AuthDto.LoginResponse login(AuthDto.LoginRequest request) {

        // 1. Cherche l'admin par son username
        Admin admin = adminRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("Identifiants incorrects"));

        // 2. Vérifie que le compte est actif
        if (!admin.isActive()) {
            throw new RuntimeException("Ce compte est désactivé. Contactez un super-admin.");
        }

        // 3. Vérifie le mot de passe (compare le hash BCrypt)
        if (!passwordEncoder.matches(request.getPassword(), admin.getPasswordHash())) {
            throw new RuntimeException("Identifiants incorrects");
        }

        // 4. Met à jour la date de dernière connexion
        admin.setLastLogin(LocalDateTime.now());
        adminRepository.save(admin);

        // 5. Génère et retourne le token JWT
        String token = jwtUtils.generateToken(admin.getUsername(), admin.getRole().name());
        log.info("✅ Login réussi pour : {}", admin.getUsername());

        return AuthDto.LoginResponse.builder()
                .token(token)
                .username(admin.getUsername())
                .fullName(admin.getFullName())
                .role(admin.getRole().name())
                .message("Connexion réussie")
                .build();
    }

    // ─── GESTION DES ADMINS (SUPER_ADMIN seulement) ──────────────────────────

    /**
     * Crée un nouvel administrateur.
     */
    public AuthDto.AdminResponse createAdmin(
            AuthDto.CreateAdminRequest request, String createdByUsername) {

        if (adminRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException(
                    "Le nom d'utilisateur '" + request.getUsername() + "' existe déjà.");
        }

        Admin.AdminRole role;
        try {
            role = Admin.AdminRole.valueOf(
                    request.getRole() != null ? request.getRole().toUpperCase() : "ADMIN");
        } catch (IllegalArgumentException e) {
            role = Admin.AdminRole.ADMIN;
        }

        Admin newAdmin = Admin.builder()
                .username(request.getUsername())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .email(request.getEmail())
                .role(role)
                .active(true)
                .createdBy(createdByUsername)
                .build();

        Admin saved = adminRepository.save(newAdmin);
        log.info("✅ Nouvel admin créé : {} par {}", saved.getUsername(), createdByUsername);
        return toResponse(saved);
    }

    /**
     * Retourne la liste de tous les admins actifs.
     */
    public List<AuthDto.AdminResponse> getAllAdmins() {
        return adminRepository.findByActiveTrueOrderByCreatedAtDesc()
                .stream().map(this::toResponse).toList();
    }

    /**
     * Désactive un compte admin (soft delete — ne supprime pas de la BD).
     */
    public void deactivateAdmin(Long adminId, String requestedByUsername) {
        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("Admin introuvable"));

        if (admin.getUsername().equals(requestedByUsername)) {
            throw new RuntimeException("Vous ne pouvez pas désactiver votre propre compte.");
        }

        admin.setActive(false);
        adminRepository.save(admin);
        log.info("Admin {} désactivé par {}", admin.getUsername(), requestedByUsername);
    }

    /**
     * Réactive un compte admin précédemment désactivé.
     */
    public void reactivateAdmin(Long adminId) {
        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("Admin introuvable"));
        admin.setActive(true);
        adminRepository.save(admin);
    }

    /**
     * Change le mot de passe d'un admin.
     */
    public void changePassword(String username, AuthDto.ChangePasswordRequest request) {
        Admin admin = adminRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Admin introuvable"));

        if (!passwordEncoder.matches(request.getCurrentPassword(), admin.getPasswordHash())) {
            throw new RuntimeException("Mot de passe actuel incorrect.");
        }

        admin.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        adminRepository.save(admin);
        log.info("Mot de passe changé pour : {}", username);
    }

    /**
     * Retourne les infos de l'admin connecté (pour le profil).
     */
    public AuthDto.AdminResponse getProfile(String username) {
        Admin admin = adminRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Admin introuvable"));
        return toResponse(admin);
    }

    // ─── CONVERSION entité → DTO ─────────────────────────────────────────────

    private AuthDto.AdminResponse toResponse(Admin admin) {
        return AuthDto.AdminResponse.builder()
                .id(admin.getId())
                .username(admin.getUsername())
                .fullName(admin.getFullName())
                .email(admin.getEmail())
                .role(admin.getRole().name())
                .active(admin.isActive())
                .createdAt(admin.getCreatedAt() != null
                        ? admin.getCreatedAt().format(FMT) : "—")
                .createdBy(admin.getCreatedBy())
                .lastLogin(admin.getLastLogin() != null
                        ? admin.getLastLogin().format(FMT) : "Jamais")
                .build();
    }
}
