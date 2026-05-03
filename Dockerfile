# =====================================================
# DOCKERFILE - CertSender Backend
# =====================================================
# Ce fichier permet à Render de construire et lancer
# le backend Spring Boot via Docker.
#
# Pourquoi Docker sur Render ?
# Render ne détecte pas automatiquement les projets Java/Maven.
# En ajoutant ce Dockerfile, on dit à Render :
# "Utilise Docker pour construire et lancer ce projet."
#
# Sur Render → Settings :
#   - Environment : Docker
#   - Dockerfile Path : ./Dockerfile  (ou ./backend/Dockerfile)
#
# Variables d'environnement à ajouter sur Render :
#   SPRING_PROFILES_ACTIVE = prod   ← OBLIGATOIRE
#   DB_HOST, DB_PORT, DB_NAME, DB_USERNAME, DB_PASSWORD
#   SENDGRID_API_KEY, SENDGRID_FROM_EMAIL, SENDGRID_FROM_NAME
#   JWT_SECRET, FRONTEND_URL
# =====================================================

# ── ÉTAPE 1 : Build ──────────────────────────────────
# On utilise une image Maven + Java 17 pour compiler le projet
FROM maven:3.9.6-eclipse-temurin-17 AS build

WORKDIR /app

# Copie d'abord le pom.xml seul pour bénéficier du cache Docker
# (si le pom.xml n'a pas changé, Maven ne re-télécharge pas les dépendances)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copie ensuite tout le code source
COPY src ./src

# Compile et crée le fichier .jar (sans exécuter les tests)
RUN mvn clean package -DskipTests -B

# ── ÉTAPE 2 : Run ────────────────────────────────────
# Image légère Java 17 uniquement pour l'exécution
# (pas besoin de Maven en production)
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Copie uniquement le .jar depuis l'étape de build
COPY --from=build /app/target/cert-sender-backend-1.0.0.jar app.jar

# Port exposé (Render injecte la variable PORT automatiquement)
EXPOSE 8080

# Démarrage de l'application
# --spring.profiles.active=prod force l'utilisation de application-prod.properties
# Mais on utilise aussi la variable d'env SPRING_PROFILES_ACTIVE définie sur Render
ENTRYPOINT ["java", "-jar", "app.jar"]
