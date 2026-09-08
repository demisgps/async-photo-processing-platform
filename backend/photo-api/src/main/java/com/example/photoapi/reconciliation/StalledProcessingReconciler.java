package com.example.photoapi.reconciliation;

import com.example.photoapi.config.ReconciliationProperties;
import com.example.photoapi.config.StorageProperties;
import com.example.photoapi.processamento.domain.ProcessingStatus;
import com.example.photoapi.processamento.repository.PhotoProcessingRepository;
import com.google.cloud.storage.Storage;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class StalledProcessingReconciler {
    private static final Logger LOG = LoggerFactory.getLogger(StalledProcessingReconciler.class);
    private final PhotoProcessingRepository processings;
    private final ReconciliationProperties properties;
    private final StorageProperties storageProperties;
    private final Storage storage;
    private final Set<UUID> observed = ConcurrentHashMap.newKeySet();

    public StalledProcessingReconciler(PhotoProcessingRepository processings, ReconciliationProperties properties,
                                       StorageProperties storageProperties, Storage storage) {
        this.processings = processings; this.properties = properties;
        this.storageProperties = storageProperties; this.storage = storage;
    }

    @Scheduled(fixedDelayString = "${photo.reconciliation.scan-interval:1m}")
    @Transactional
    public void reconcile() {
        var stale = processings.findByStatusAndProcessingStartedAtBefore(ProcessingStatus.PROCESSANDO,
                LocalDateTime.now().minus(properties.staleAfter()));
        Set<UUID> current = ConcurrentHashMap.newKeySet();
        for (var processing : stale) {
            if (processedObjectExists(processing.getUser().getId(), processing.getId())) {
                LOG.warn("event=PUBLICATION_PENDING processamentoId={} usuarioId={} status=PROCESSANDO",
                        processing.getId(), processing.getUser().getId());
                continue;
            }
            current.add(processing.getId());
            if (observed.contains(processing.getId())) {
                int changed = processings.markStalledAsProcessingError(processing.getId(), processing.getVersion(),
                        "PROCESSING_STALLED", "Processamento sem progresso confirmado em duas varreduras",
                        LocalDateTime.now());
                if (changed == 1) LOG.error("event=stalled_processing_failed processamentoId={}", processing.getId());
            }
        }
        observed.retainAll(current);
        observed.addAll(current);
    }

    private boolean processedObjectExists(long userId, UUID processingId) {
        String prefix = userId + "/" + processingId + "/arquivo.";
        return storage.get(storageProperties.processedBucket(), prefix + "jpg") != null
                || storage.get(storageProperties.processedBucket(), prefix + "png") != null;
    }
}
