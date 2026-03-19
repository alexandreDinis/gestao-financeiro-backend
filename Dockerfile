FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder
WORKDIR /app
# Copiar pom.xml e baixar dependências (cache)
COPY pom.xml .
RUN mvn dependency:go-offline -B
# Copiar o source e fazer build
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
