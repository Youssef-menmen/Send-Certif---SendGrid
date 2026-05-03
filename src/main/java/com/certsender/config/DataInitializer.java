package com.certsender.config;

import com.certsender.entity.Admin;
import com.certsender.repository.AdminRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * INITIALISEUR DE DONNÉES
 *
 * S'exécute automatiquement au démarrage de Spring Boot.
 * Crée un SUPER_ADMIN par défaut si aucun admin n'existe en base.
 *
 * Identifiants par défaut :
 *   username : admin
 *   password : Admin@2024
 *
 * ⚠️ CHANGEZ CES IDENTIFIANTS DÈS LE PREMIER LOGIN !
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        // Si aucun admin n'existe, on crée le compte par défaut
        if (adminRepository.count() == 0) {
            Admin superAdmin = Admin.builder()
                    .username("admin")
                    .passwordHash(passwordEncoder.encode("Admin@2024"))
                    .fullName("Super Administrateur")
                    .email("admin@certsender.com")
                    .role(Admin.AdminRole.SUPER_ADMIN)
                    .active(true)
                    .createdBy("system")
                    .build();

            adminRepository.save(superAdmin);

            log.info("========================================");
            log.info("✅ Compte admin par défaut créé :");
            log.info("   Username : admin");
            log.info("   Password : Admin@2024");
            log.info("   ⚠️  Changez ce mot de passe après connexion !");
            log.info("========================================");
        }
    }
}
