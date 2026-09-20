package com.example.photoconsumer.processamento.consumer;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = PubSubPushController.class)
public class PubSubPushExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(PubSubPushExceptionHandler.class);

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
    ResponseEntity<Void> invalidMessage(Exception failure) {
        LOGGER.warn("Mensagem Pub/Sub Push inválida; entrega não confirmada", failure);
        return ResponseEntity.badRequest().build();
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Void> retryableFailure(Exception failure) {
        LOGGER.error("Falha ao processar Pub/Sub Push; entrega não confirmada", failure);
        return ResponseEntity.internalServerError().build();
    }
}
