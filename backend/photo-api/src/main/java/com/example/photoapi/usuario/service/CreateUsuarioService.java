package com.example.photoapi.usuario.service;

import com.example.photoapi.foto.validation.PhotoValidator;
import com.example.photoapi.foto.validation.ValidatedPhoto;
import com.example.photoapi.processamento.domain.PhotoProcessing;
import com.example.photoapi.processamento.repository.PhotoProcessingRepository;
import com.example.photoapi.storage.OriginalPhotoStorage;
import com.example.photoapi.storage.StoredObject;
import com.example.photoapi.usuario.domain.User;
import com.example.photoapi.usuario.repository.UserRepository;
import com.example.photoapi.usuario.validation.NomeValidator;
import com.example.photoapi.usuario.web.contract.UserCreatedResponse;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CreateUsuarioService {
    private final NomeValidator nomeValidator;
    private final PhotoValidator photoValidator;
    private final UserRepository userRepository;
    private final PhotoProcessingRepository processingRepository;
    private final OriginalPhotoStorage storage;
    private final TransactionTemplate transactions;

    public CreateUsuarioService(NomeValidator nomeValidator, PhotoValidator photoValidator,
                                UserRepository userRepository, PhotoProcessingRepository processingRepository,
                                OriginalPhotoStorage storage, TransactionTemplate transactions) {
        this.nomeValidator = nomeValidator;
        this.photoValidator = photoValidator;
        this.userRepository = userRepository;
        this.processingRepository = processingRepository;
        this.storage = storage;
        this.transactions = transactions;
    }

    public UserCreatedResponse create(String rawName, MultipartFile file) {
        String name = nomeValidator.validate(rawName);
        ValidatedPhoto photo = photoValidator.validate(file);
        UUID processingId = UUID.randomUUID();
        User user = transactions.execute(status -> userRepository.saveAndFlush(new User(name)));
        StoredObject stored = null;
        try {
            stored = storage.create(user.getId(), processingId, photo);
            StoredObject confirmed = stored;
            PhotoProcessing processing = transactions.execute(status -> {
                User managedUser = userRepository.findByIdForUpdate(user.getId()).orElseThrow();
                long sequence = managedUser.allocateUploadSequence();
                PhotoProcessing created = new PhotoProcessing(processingId, managedUser, sequence,
                        confirmed.bucket(), confirmed.name(), confirmed.generation(),
                        confirmed.checksum() == null ? "" : confirmed.checksum(),
                        photo.contentType(), "arquivo." + photo.extension());
                created.startProcessing();
                return processingRepository.saveAndFlush(created);
            });
            return new UserCreatedResponse(user.getId(), name, processing.getId(), processing.getStatus().name());
        } catch (RuntimeException failure) {
            compensate(user.getId(), stored, failure);
            throw failure;
        }
    }

    private void compensate(long userId, StoredObject stored, RuntimeException originalFailure) {
        if (stored != null) {
            try { storage.deleteIdempotently(stored); }
            catch (RuntimeException cleanupFailure) { originalFailure.addSuppressed(cleanupFailure); }
        }
        try { transactions.execute(status -> { userRepository.deleteById(userId); return null; }); }
        catch (RuntimeException cleanupFailure) { originalFailure.addSuppressed(cleanupFailure); }
    }
}
