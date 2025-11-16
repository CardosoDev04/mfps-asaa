# ---- build stage ----
FROM gradle:8.10.2-jdk21 AS build
WORKDIR /app

# Copy wrapper + gradle files first for caching
COPY gradle gradle
COPY gradlew ./
COPY settings.gradle.kts build.gradle.kts ./
# If you have gradle.properties, include it:
# COPY gradle.properties ./

# Also copy subproject build files for dependency resolution and better caching
COPY domain/build.gradle.kts domain/
COPY repositories/build.gradle.kts repositories/
COPY http/build.gradle.kts http/
COPY services/build.gradle.kts services/

# Pre-fetch deps (configures all projects)
RUN ./gradlew --no-daemon dependencies

# Copy sources for all modules and build
COPY src ./src
COPY domain/src ./domain/src
COPY repositories/src ./repositories/src
COPY http/src ./http/src
COPY services/src ./services/src
RUN ./gradlew --no-daemon bootJar

# ---- runtime stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app
# Copy the fat jar built by Spring Boot (adjust pattern if your name differs)
COPY --from=build /app/build/libs/*-SNAPSHOT.jar /app/app.jar
# Or more generic: COPY --from=build /app/build/libs/*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
