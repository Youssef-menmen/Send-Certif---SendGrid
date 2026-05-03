package com.certsender.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * ENTITÉ Admin — table "admins" dans MySQL
 * Chaque ligne = un administrateur qui peut se connecter à l'application.
 */
@Entity
@Table(name = "admins")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Admin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, unique = true, length = 100)
    private String username;           // Nom d'utilisateur unique

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;       // Mot de passe hashé avec BCrypt (jamais en clair)

    @Column(name = "full_name", length = 150)
    private String fullName;           // Nom complet affiché dans l'interface

    @Column(name = "email", length = 255)
    private String email;              // Email de l'admin (optionnel)

    @Column(name = "role", length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private AdminRole role = AdminRole.ADMIN;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;     // false = compte désactivé

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;          // Qui a créé cet admin

    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum AdminRole {
        SUPER_ADMIN,   // Peut tout faire (créer/supprimer des admins)
        ADMIN          // Peut envoyer des certificats et voir l'historique
    }
}
