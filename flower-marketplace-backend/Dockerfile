# # ─────────────────────────────────────────────────────────────────────────────
# # Stage 1 – Dependency cache
# # ─────────────────────────────────────────────────────────────────────────────
# FROM maven:3.9.6-eclipse-temurin-21-alpine AS deps
# WORKDIR /build
# COPY pom.xml .
# RUN mvn dependency:go-offline -B -q

# # ─────────────────────────────────────────────────────────────────────────────
# # Stage 2 – Build
# # ─────────────────────────────────────────────────────────────────────────────
# FROM deps AS builder
# COPY src ./src
# RUN mvn clean package -DskipTests -B -q \
#     && mv target/flower-marketplace-backend-*.jar target/app.jar

# # ─────────────────────────────────────────────────────────────────────────────
# # Stage 3 – Runtime (minimal JRE)
# # ─────────────────────────────────────────────────────────────────────────────
# FROM eclipse-temurin:21-jre-alpine AS runtime

# LABEL maintainer="Flower Marketplace Team <dev@flowermarketplace.com>"
# LABEL org.opencontainers.image.title="flower-marketplace-backend"
# LABEL org.opencontainers.image.description="Spring Boot 3 backend for Flower Marketplace"

# WORKDIR /app

# # Non-root user
# RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# COPY --from=builder /build/target/app.jar app.jar

# RUN mkdir -p /app/uploads /app/logs \
#     && chown -R appuser:appgroup /app

# USER appuser

# EXPOSE 8080

# HEALTHCHECK --interval=30s --timeout=10s --start-period=90s --retries=3 \
#     CMD wget -qO- http://localhost:8080/api/actuator/health \
#     | grep -q '"status":"UP"' || exit 1

# ENTRYPOINT ["java", \
#     "-XX:+UseContainerSupport", \
#     "-XX:MaxRAMPercentage=50.0", \  
#     "-XX:+UseG1GC", \
#     "-XX:+HeapDumpOnOutOfMemoryError", \
#     "-XX:HeapDumpPath=/app/logs/heap-dump.hprof", \
#     "-Djava.security.egd=file:/dev/./urandom", \
#     "-Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:-prod}", \
#     "-jar", "app.jar"]
# ===== BUILD STAGE =====
FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /app
COPY pom.xml .
RUN mvn -q -e -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -q -DskipTests package


# ===== RUNTIME STAGE =====
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Tạo user non-root (best practice)
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy jar
COPY --from=build /app/target/*.jar app.jar

# JVM tối ưu RAM
ENTRYPOINT ["java","-XX:+UseContainerSupport","-XX:MaxRAMPercentage=75.0","-jar","/app/app.jar"]