package com.example.photoconsumer.processamento.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.example.photoconsumer.processamento.repository.PhotoProcessingRepository;
import com.example.photoconsumer.processamento.service.DeadLetterService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeadLetterMessageTest {
    @Test void recoversProcessingIdWhenPresentAndNeverInventsOneForMalformedPayload() {
        var repository = mock(PhotoProcessingRepository.class);
        var service = new DeadLetterService(repository);
        UUID id = UUID.randomUUID();
        when(repository.findByIdForUpdate(id)).thenReturn(java.util.Optional.empty());
        service.handle("message-valid", ("{\"processamentoId\":\"" + id + "\",\"eventId\":\"event\"}").getBytes(),
                Map.of("eventType", "PhotoProcessingResult"));
        verify(repository).findByIdForUpdate(id);
        clearInvocations(repository);

        service.handle("message-malformed", "{".getBytes(), Map.of("source", "raw"));
        verifyNoInteractions(repository);
    }

    @Test void brokerBootstrapDefinesFiniteRedeliveryDltAndRetention() throws Exception {
        String bootstrap = Files.readString(Path.of("../../docker/pubsub/bootstrap.sh"));
        assertThat(bootstrap).contains("foto-processada-dlq", "\\\"maxDeliveryAttempts\\\": 8",
                "\\\"minimumBackoff\\\": \\\"10s", "\\\"maximumBackoff\\\": \\\"300s", "604800s");
    }
}
