FROM gradle:8.10.2-jdk21 AS build
WORKDIR /app

COPY gradle gradle
COPY gradlew ./
RUN chmod +x gradlew

COPY settings.gradle.kts build.gradle.kts ./

COPY domain/build.gradle.kts domain/
COPY repositories/build.gradle.kts repositories/
COPY http/build.gradle.kts http/
COPY services/build.gradle.kts services/

RUN ./gradlew --no-daemon dependencies

COPY src ./src
COPY domain/src ./domain/src
COPY repositories/src ./repositories/src
COPY http/src ./http/src
COPY services/src ./services/src

RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
