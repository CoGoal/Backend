# ---------- Сборка ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Сначала только pom.xml — чтобы кэшировать зависимости отдельным слоем
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q package -DskipTests

# ---------- Запуск ----------
FROM eclipse-temurin:17-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
