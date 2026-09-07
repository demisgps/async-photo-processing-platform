FROM maven:3.9.12-eclipse-temurin-25
WORKDIR /workspace
COPY pom.xml mvnw mvnw.cmd ./
COPY .mvn .mvn
COPY photo-processor photo-processor
COPY photo-api/pom.xml photo-api/pom.xml
COPY photo-consumer/pom.xml photo-consumer/pom.xml
RUN ./mvnw -pl photo-processor -am -DskipTests package
EXPOSE 8080
ENTRYPOINT ["./mvnw", "-pl", "photo-processor", "function:run", "-Drun.functionTarget=com.example.photoprocessor.processamento.PhotoProcessorFunction", "-Drun.port=8080"]

