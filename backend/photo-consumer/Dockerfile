FROM maven:3.9.12-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml mvnw mvnw.cmd ./
COPY .mvn .mvn
COPY photo-consumer photo-consumer
COPY photo-api/pom.xml photo-api/pom.xml
COPY photo-processor/pom.xml photo-processor/pom.xml
RUN ./mvnw -pl photo-consumer -am -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /workspace/photo-consumer/target/photo-consumer-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar", "--server.port=8081"]
