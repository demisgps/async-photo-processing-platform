package com.example.photoapi.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

class HappyPathIT {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void reachesPersistidaAndServesTheProcessedPhotoWithoutPrestartedCompose() throws Exception {
        File composeFile = new File("../../compose.yaml").getCanonicalFile();
        try (DockerComposeContainer<?> environment = new DockerComposeContainer<>(
                DockerImageName.parse("docker/compose:1.29.2"), composeFile)
                .withBuild(true).withRemoveVolumes(true).withStartupTimeout(Duration.ofMinutes(6))
                .withExposedService("photo-api", 8080, Wait.forHttp("/actuator/health/readiness").forStatusCode(200))) {
            environment.start();
            String baseUrl = "http://" + environment.getServiceHost("photo-api", 8080) + ":"
                    + environment.getServicePort("photo-api", 8080);
            HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            HttpResponse<String> creation = http.send(createRequest(baseUrl, jpeg()), HttpResponse.BodyHandlers.ofString());
            assertThat(creation.statusCode()).isEqualTo(201);
            JsonNode accepted = mapper.readTree(creation.body());
            assertThat(accepted.path("status").asText()).isEqualTo("PROCESSANDO");
            long userId = accepted.path("id").asLong();
            String processingId = accepted.path("processamentoId").asText();

            JsonNode processing = awaitPersisted(http, baseUrl, processingId);
            assertThat(processing.path("status").asText()).isEqualTo("PERSISTIDA");
            HttpResponse<byte[]> photo = http.send(HttpRequest.newBuilder(
                    URI.create(baseUrl + "/api/v1/usuarios/" + userId + "/foto")).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            assertThat(photo.statusCode()).isEqualTo(200);
            assertThat(photo.headers().firstValue("Content-Type")).hasValueSatisfying(
                    value -> assertThat(value).isIn("image/jpeg", "image/png"));
            assertThat(photo.body()).isNotEmpty();
        }
    }

    private JsonNode awaitPersisted(HttpClient http, String baseUrl, String processingId) throws Exception {
        long deadline = System.nanoTime() + Duration.ofMinutes(2).toNanos(); JsonNode last = null;
        while (System.nanoTime() < deadline) {
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(
                    URI.create(baseUrl + "/api/v1/processamentos/" + processingId)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                last = mapper.readTree(response.body());
                if ("PERSISTIDA".equals(last.path("status").asText())) return last;
                if (last.path("status").asText().startsWith("ERRO_")) break;
            }
            Thread.sleep(500);
        }
        throw new AssertionError("Processamento não chegou a PERSISTIDA; último estado=" + last);
    }

    private HttpRequest createRequest(String baseUrl, byte[] image) {
        String boundary = "codex-boundary";
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        try {
            body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"nome\"\r\n\r\nAna\r\n"
                    + "--" + boundary + "\r\nContent-Disposition: form-data; name=\"foto\"; filename=\"foto.jpg\"\r\n"
                    + "Content-Type: image/jpeg\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            body.write(image); body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        } catch (java.io.IOException impossible) { throw new IllegalStateException(impossible); }
        return HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/usuarios"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build();
    }

    private byte[] jpeg() throws Exception {
        BufferedImage image = new BufferedImage(20, 10, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics(); graphics.setColor(Color.BLUE); graphics.fillRect(0, 0, 20, 10); graphics.dispose();
        ByteArrayOutputStream output = new ByteArrayOutputStream(); ImageIO.write(image, "jpg", output); return output.toByteArray();
    }
}
