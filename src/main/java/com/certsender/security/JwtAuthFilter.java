package com.certsender.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * FILTRE JWT
 *
 * Ce filtre s'exécute automatiquement sur CHAQUE requête HTTP.
 * Son rôle : extraire et valider le token JWT du header Authorization.
 *
 * Fonctionnement :
 * 1. Reçoit la requête HTTP
 * 2. Cherche le header : "Authorization: Bearer xxxxx.yyyyy.zzzzz"
 * 3. Extrait le token (la partie après "Bearer ")
 * 4. Valide le token avec JwtUtils
 * 5. Si valide → place l'utilisateur dans le SecurityContext (Spring sait qu'il est connecté)
 * 6. Si invalide → Spring Security bloquera la requête avec 401 Unauthorized
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        try {
            // Extrait le token du header HTTP
            String token = extractToken(request);

            // Si un token est présent et valide
            if (token != null && jwtUtils.validateToken(token)) {
                String username = jwtUtils.getUsernameFromToken(token);
                String role     = jwtUtils.getRoleFromToken(token);

                // Crée l'objet d'authentification Spring Security
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                username,
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + role))
                        );

                // Enregistre l'authentification dans le contexte Spring
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception e) {
            log.error("Erreur lors de l'authentification JWT : {}", e.getMessage());
        }

        // Continue la chaîne de filtres (passe à la prochaine étape)
        filterChain.doFilter(request, response);
    }

    /**
     * Extrait le token JWT du header Authorization.
     * Format attendu : "Bearer eyJhbGci..."
     */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7); // Supprime "Bearer " (7 caractères)
        }
        return null;
    }
}
