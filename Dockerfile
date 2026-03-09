####
# Dockerfile para build JVM - Microsserviço de Análise Inteligente
####

# ─── Stage 1: Build ──────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-21 AS build

WORKDIR /app

# Copia apenas o pom.xml primeiro para cache de dependências
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copia o código-fonte e compila
COPY src ./src
RUN mvn clean package -Dmaven.test.skip=true -q

# ─── Stage 2: Runtime ────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copia o runner gerado pelo Quarkus
COPY --from=build /app/target/quarkus-app/lib/ ./lib/
COPY --from=build /app/target/quarkus-app/*.jar ./
COPY --from=build /app/target/quarkus-app/app/ ./app/
COPY --from=build /app/target/quarkus-app/quarkus/ ./quarkus/

# Porta da aplicação
EXPOSE 8090

# Usuário não-root por segurança
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Define o perfil de produção
ENV QUARKUS_PROFILE=prod

ENTRYPOINT ["java", "-jar", "quarkus-run.jar"]