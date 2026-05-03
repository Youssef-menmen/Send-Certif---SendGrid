package com.certsender.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

public class AuthDto {

    /** Reçu du frontend lors du login */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginRequest {
        @NotBlank(message = "Le nom d'utilisateur est obligatoire")
        private String username;

        @NotBlank(message = "Le mot de passe est obligatoire")
        private String password;
    }

    /** Renvoyé au frontend après un login réussi */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginResponse {
        private String token;        // Le JWT à stocker dans localStorage
        private String username;
        private String fullName;
        private String role;
        private String message;
    }

    /** DTO pour créer un nouvel admin */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateAdminRequest {
        @NotBlank(message = "Le nom d'utilisateur est obligatoire")
        private String username;

        @NotBlank(message = "Le mot de passe est obligatoire")
        private String password;

        private String fullName;
        private String email;
        private String role; // "ADMIN" ou "SUPER_ADMIN"
    }

    /** DTO pour afficher un admin dans la liste */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminResponse {
        private Long id;
        private String username;
        private String fullName;
        private String email;
        private String role;
        private boolean active;
        private String createdAt;
        private String createdBy;
        private String lastLogin;
    }

    /** DTO pour changer le mot de passe */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChangePasswordRequest {
        @NotBlank
        private String currentPassword;
        @NotBlank
        private String newPassword;
    }
}
