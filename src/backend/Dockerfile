# ---- build stage ----
  FROM gradle:8.10.2-jdk21 AS build
  WORKDIR /app
  
  # Copy wrapper + gradle files first for caching
  COPY gradle gradle
  COPY gradlew ./
  COPY settings.gradle.kts build.gradle.kts ./
  # If you have gradle.properties, include it:
  # COPY gradle.properties ./
  
  # Pre-fetch deps
  RUN ./gradlew --no-daemon dependencies
  
  # Copy sources and build
  COPY src ./src
  RUN ./gradlew --no-daemon bootJar
  
  # ---- runtime stage ----
  FROM eclipse-temurin:21-jre
  WORKDIR /app
  # Copy the fat jar built by Spring Boot (adjust pattern if your name differs)
  COPY --from=build /app/build/libs/*-SNAPSHOT.jar /app/app.jar
  # Or more generic: COPY --from=build /app/build/libs/*.jar /app/app.jar
  EXPOSE 8080
  ENTRYPOINT ["java","-jar","/app/app.jar"]
  