package com.certsender.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

/**
 * UTILITAIRE JWT
 *
 * JWT = JSON Web Token. C'est un système d'authentification sans session :
 *
 * 1. L'admin se connecte avec username + password
 * 2. Le backend vérifie les credentials et génère un TOKEN (chaîne de caractères chiffrée)
 * 3. Le frontend stocke ce token dans localStorage
 * 4. Pour chaque requête suivante, le frontend envoie ce token dans le header HTTP
 * 5. Le backend vérifie que le token est valide avant d'autoriser l'accès
 *
 * Le token contient : username, role, date d'expiration — tout signé cryptographiquement.
 */
@Component
@Slf4j
public class JwtUtils {

    @Value("${app.jwt.secret:CertSenderSecretKeyPourJWTDoitEtre256BitsMinimum!!2024}")
    private String jwtSecret;

    @Value("${app.jwt.expiration-ms:86400000}") // 24 heures par défaut
    private int jwtExpirationMs;

    // Génère la clé de signature à partir du secret
    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    /**
     * Génère un token JWT pour un admin connecté.
     * @param username Le nom d'utilisateur de l'admin
     * @param role     Le rôle (ADMIN ou SUPER_ADMIN)
     * @return Le token JWT sous forme de String
     */
    public String generateToken(String username, String role) {
        return Jwts.builder()
                .setSubject(username)
                .claim("role", role)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Extrait le username depuis un token JWT.
     */
    public String getUsernameFromToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    /**
     * Extrait le rôle depuis un token JWT.
     */
    public String getRoleFromToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody()
                .get("role", String.class);
    }

    /**
     * Vérifie si un token est valide (signature correcte + non expiré).
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(getSigningKey()).build().parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Token JWT invalide : {}", e.getMessage());
            return false;
        }
    }
}
