# syntax=docker/dockerfile:1
# Build: Maven wrapper on a JDK. Tests are not run here (they need PostgreSQL/Docker); run them before building the image
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
# mvnw may come with CRLF line endings from a Windows checkout
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2,sharing=locked ./mvnw -B -q -DskipTests package && cp target/*.jar app.jar

# Run: JRE only, as an unprivileged user
FROM eclipse-temurin:25-jre
# curl is for the docker compose healthcheck; the base image no longer ships it
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
RUN groupadd --system app && useradd --system --gid app --no-create-home app
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
USER app
EXPOSE 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
