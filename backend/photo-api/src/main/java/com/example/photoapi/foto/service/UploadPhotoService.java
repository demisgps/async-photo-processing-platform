package com.example.photoapi.foto.service;

import com.example.photoapi.exception.ApiException;
import com.example.photoapi.foto.validation.PhotoValidator;
import com.example.photoapi.processamento.domain.PhotoProcessing;
import com.example.photoapi.processamento.domain.ProcessingStatus;
import com.example.photoapi.processamento.repository.PhotoProcessingRepository;
import com.example.photoapi.storage.OriginalPhotoStorage;
import com.example.photoapi.storage.StoredObject;
import com.example.photoapi.usuario.repository.UserRepository;
import com.example.photoapi.usuario.web.contract.ProcessingAcceptedResponse;
import java.util.EnumSet;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UploadPhotoService {
    private static final EnumSet<ProcessingStatus> ACTIVE = EnumSet.of(ProcessingStatus.RECEBIDA,
            ProcessingStatus.PROCESSANDO, ProcessingStatus.PROCESSADA, ProcessingStatus.PERSISTINDO);
    private final UserRepository users;
    private final PhotoProcessingRepository processings;
    private final PhotoValidator validator;
    private final OriginalPhotoStorage storage;
    private final TransactionTemplate transactions;

    public UploadPhotoService(UserRepository users, PhotoProcessingRepository processings,
                              PhotoValidator validator, OriginalPhotoStorage storage, TransactionTemplate transactions) {
        this.users = users; this.processings = processings; this.validator = validator;
        this.storage = storage; this.transactions = transactions;
    }

    public ProcessingAcceptedResponse upload(long userId, MultipartFile file) {
        var photo = validator.validate(file);
        Allocation allocation = transactions.execute(tx -> {
            var user = users.findByIdForUpdate(userId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                    "USUARIO_NAO_ENCONTRADO", "Usuário não encontrado"));
            if (processings.existsByUserIdAndStatusIn(userId, ACTIVE)) throw active();
            return new Allocation(UUID.randomUUID(), user.allocateUploadSequence());
        });
        StoredObject stored = null;
        try {
            stored = storage.create(userId, allocation.processingId(), photo);
            StoredObject confirmed = stored;
            transactions.executeWithoutResult(tx -> {
                var user = users.findByIdForUpdate(userId).orElseThrow();
                if (processings.existsByUserIdAndStatusIn(userId, ACTIVE)) throw active();
                var processing = new PhotoProcessing(allocation.processingId(), user, allocation.sequence(),
                        confirmed.bucket(), confirmed.name(), confirmed.generation(), confirmed.checksum(),
                        photo.contentType(), "arquivo." + photo.extension());
                processing.startProcessing();
                processings.save(processing);
            });
            return new ProcessingAcceptedResponse(allocation.processingId(), userId, ProcessingStatus.PROCESSANDO.name());
        } catch (RuntimeException failure) {
            if (stored != null) {
                try { storage.deleteIdempotently(stored); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            }
            throw failure;
        }
    }

    private ApiException active() {
        return new ApiException(HttpStatus.CONFLICT, "PROCESSAMENTO_ATIVO", "Usuário possui processamento ativo");
    }
    private record Allocation(UUID processingId, long sequence) {}
}
