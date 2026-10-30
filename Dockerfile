# Multi-stage Dockerfile for SmartDialer (Java 21)
FROM maven:3.9.8-eclipse-temurin-21 AS build
WORKDIR /app

# Copy pom.xml and pre-fetch dependencies for layer caching
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and package application
COPY src ./src
RUN mvn clean package -DskipTests

# Runtime Stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Add unprivileged user for security
RUN addgroup -S smartdialer && adduser -S smartdialer -G smartdialer
USER smartdialer

COPY --from=build /app/target/smartdialer-1.0.0.jar app.jar

ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080

ENTRYPOINT ["java", "-XX:+EnableDynamicAgentLoading", "-XX:+UseG1GC", "-jar", "app.jar"]
