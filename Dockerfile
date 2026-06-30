# Etapa 1: Construcción
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app

COPY pom.xml .
COPY chat-backend/pom.xml ./chat-backend/
COPY chat-backend/src ./chat-backend/src

# Compilamos forzando la generación del JAR ejecutable de Spring Boot
RUN mvn -f chat-backend/pom.xml clean package -DskipTests

# Etapa 2: Imagen de ejecución
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app


COPY --from=build /app/chat-backend/target/chat-backend-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]