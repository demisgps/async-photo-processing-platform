FROM maven:3.9.12-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml mvnw mvnw.cmd ./
COPY .mvn .mvn
COPY photo-consumer photo-consumer
COPY photo-api/pom.xml photo-api/pom.xml
COPY photo-processor/pom.xml photo-processor/pom.xml
RUN ./mvnw -pl photo-consumer -am -DskipTests package

FROM eclipse-temurin:25-jre
RUN groupadd --system app \
    && useradd --system --gid app --home-dir /app --no-create-home app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/photo-consumer/target/photo-consumer-1.0.0-SNAPSHOT.jar app.jar
USER app:app
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
