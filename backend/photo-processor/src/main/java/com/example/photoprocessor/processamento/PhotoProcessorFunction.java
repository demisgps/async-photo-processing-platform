package com.example.photoprocessor.processamento;

import com.example.photoprocessor.config.ProcessorProperties;
import com.example.photoprocessor.event.ProcessingEventPublisher;
import com.example.photoprocessor.imagem.ImageTransformer;
import com.example.photoprocessor.imagem.InputPixelGuard;
import com.example.photoprocessor.storage.PhotoStorage;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.functions.CloudEventsFunction;
import io.cloudevents.CloudEvent;
import java.util.logging.Logger;
import org.slf4j.MDC;

public class PhotoProcessorFunction implements CloudEventsFunction {
    private static final Logger LOGGER = Logger.getLogger(PhotoProcessorFunction.class.getName());
    static final String FINALIZED = "google.cloud.storage.object.v1.finalized";
    private final ProcessorProperties properties;
    private final PhotoProcessingService service;
    private final ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public PhotoProcessorFunction() {
        this(ProcessorProperties.fromEnvironment());
    }
    private PhotoProcessorFunction(ProcessorProperties properties) {
        this(properties, new PhotoProcessingService(PhotoStorage.create(properties),
                new ImageTransformer(new InputPixelGuard(properties.maxPixels())), ProcessingEventPublisher.create(properties)));
    }
    public PhotoProcessorFunction(ProcessorProperties properties, PhotoProcessingService service) {
        this.properties = properties; this.service = service;
    }
    @Override public void accept(CloudEvent cloudEvent) throws Exception {
        if (!FINALIZED.equals(cloudEvent.getType()) || cloudEvent.getData() == null) {
            LOGGER.info(() -> "CloudEvent ignorado: tipo incompatível ou payload ausente"); return;
        }
        StorageFinalizedEvent event = mapper.readValue(cloudEvent.getData().toBytes(), StorageFinalizedEvent.class);
        if (!properties.originalBucket().equals(event.bucket())) {
            LOGGER.info(() -> "CloudEvent ignorado: bucket incompatível bucket=" + event.bucket()); return;
        }
        validate(event);
        MDC.put("usuarioId", Long.toString(event.usuarioId()));
        MDC.put("processamentoId", event.processamentoId().toString());
        try {
            service.process(event);
        } finally {
            MDC.remove("usuarioId");
            MDC.remove("processamentoId");
        }
    }
    private void validate(StorageFinalizedEvent event) {
        long userId = event.usuarioId();
        var processingId = event.processamentoId();
        String expectedPrefix = userId + "/" + processingId + "/arquivo.";
        if (event.generation() == null || event.generation().isBlank() || !event.name().startsWith(expectedPrefix))
            throw new IllegalArgumentException("CloudEvent de Storage estruturalmente inválido");
    }
}
