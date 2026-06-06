# ============================================
# Stage 1: Build the application
# ============================================
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /app

# Copy dependency definitions first (for Docker layer caching)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build
COPY src ./src
RUN mvn clean package -DskipTests

# ============================================
# Stage 2: Create the runtime image
# ============================================
FROM eclipse-temurin:21-jre

WORKDIR /app

# Copy the built JAR from the builder stage
COPY --from=builder /app/target/*.jar app.jar

# Render assigns a dynamic port via the PORT env var
ENV PORT=8080
EXPOSE ${PORT}

ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT} -jar app.jar"]
