package com.certsender.repository;

import com.certsender.entity.Admin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AdminRepository extends JpaRepository<Admin, Long> {

    // Trouve un admin par son nom d'utilisateur (utilisé lors du login)
    Optional<Admin> findByUsername(String username);

    // Vérifie si un username existe déjà (pour éviter les doublons)
    boolean existsByUsername(String username);

    // Tous les admins actifs (pour la liste dans l'interface)
    List<Admin> findByActiveTrueOrderByCreatedAtDesc();
}
