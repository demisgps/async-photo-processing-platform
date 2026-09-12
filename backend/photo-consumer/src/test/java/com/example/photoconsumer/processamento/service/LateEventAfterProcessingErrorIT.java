package com.example.photoconsumer.processamento.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.example.photoconsumer.persistence.MySqlIntegrationSupport;
import com.example.photoconsumer.processamento.domain.*;
import com.example.photoconsumer.processamento.repository.PhotoProcessingRepository;
import com.example.photoconsumer.storage.ProcessedPhotoStorage;
import com.example.photoconsumer.usuario.domain.User;
import com.example.photoconsumer.usuario.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LateEventAfterProcessingErrorIT extends MySqlIntegrationSupport {
    @Test void resultAfterProcessingErrorIsNoOpWithoutResurrection() {
        var service = context.getBean(FinalizeProcessingService.class);
        var processings = context.getBean(PhotoProcessingRepository.class);
        var users = context.getBean(UserRepository.class);
        var storage = context.getBean(ProcessedPhotoStorage.class);
        clearInvocations(storage);
        long userId = uniqueUserId();
        User user = users.saveAndFlush(new User(userId, "Terminal"));
        UUID id = UUID.randomUUID();
        PhotoProcessing processing = new PhotoProcessing(id, user, 1, ProcessingStatus.PROCESSANDO);
        processing.failProcessing("STALLED", "confirmed");
        processings.saveAndFlush(processing);

        assertThat(service.handle(FinalizeProcessingIT.result(id, userId)))
                .isEqualTo(FinalizeProcessingService.Outcome.NO_OP);
        assertThat(processings.findById(id).orElseThrow().getStatus()).isEqualTo(ProcessingStatus.ERRO_PROCESSAMENTO);
        verifyNoInteractions(storage);
    }
}
