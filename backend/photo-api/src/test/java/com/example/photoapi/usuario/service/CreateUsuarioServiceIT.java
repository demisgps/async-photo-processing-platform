package com.example.photoapi.usuario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

import com.example.photoapi.foto.validation.PhotoValidator;
import com.example.photoapi.foto.validation.ValidatedPhoto;
import com.example.photoapi.processamento.domain.PhotoProcessing;
import com.example.photoapi.processamento.repository.PhotoProcessingRepository;
import com.example.photoapi.storage.OriginalPhotoStorage;
import com.example.photoapi.storage.StoredObject;
import com.example.photoapi.usuario.domain.User;
import com.example.photoapi.usuario.repository.UserRepository;
import com.example.photoapi.usuario.validation.NomeValidator;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

class CreateUsuarioServiceIT {
    NomeValidator names = mock(NomeValidator.class);
    PhotoValidator photos = mock(PhotoValidator.class);
    UserRepository users = mock(UserRepository.class);
    PhotoProcessingRepository processings = mock(PhotoProcessingRepository.class);
    OriginalPhotoStorage storage = mock(OriginalPhotoStorage.class);
    TransactionTemplate transactions = mock(TransactionTemplate.class);
    CreateUsuarioService service = new CreateUsuarioService(names, photos, users, processings, storage, transactions);
    MultipartFile file = mock(MultipartFile.class);
    User user = mock(User.class);
    ValidatedPhoto photo = new ValidatedPhoto(new byte[]{1}, "image/png", "png");
    StoredObject object = new StoredObject("original", "42/id/arquivo.png", "9", "crc");

    @BeforeEach void transactionsExecuteCallbacks() {
        when(transactions.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        doAnswer(invocation -> {
            java.util.function.Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactions).executeWithoutResult(any());
        when(names.validate(" Ana ")).thenReturn("Ana");
        when(photos.validate(file)).thenReturn(photo);
        when(users.saveAndFlush(any(User.class))).thenReturn(user);
        when(user.getId()).thenReturn(42L);
        when(user.allocateUploadSequence()).thenReturn(1L);
        when(users.findByIdForUpdate(42L)).thenReturn(Optional.of(user));
        when(storage.create(anyLong(), any(), any())).thenReturn(object);
        when(processings.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test void confirmsStorageAndPersistsInitialSequenceInProcessingState() {
        var response = service.create(" Ana ", file);
        assertThat(response.id()).isEqualTo(42);
        assertThat(response.nome()).isEqualTo("Ana");
        assertThat(response.status()).isEqualTo("PROCESSANDO");
        verify(storage).create(org.mockito.ArgumentMatchers.eq(42L), any(), org.mockito.ArgumentMatchers.eq(photo));
        verify(processings).saveAndFlush(org.mockito.ArgumentMatchers.argThat(p ->
                p.getUploadSequence() == 1 && p.getStatus().name().equals("PROCESSANDO")));
    }

    @Test void removesUserWhenStorageFailsBeforeAcceptance() {
        when(storage.create(anyLong(), any(), any())).thenThrow(new IllegalStateException("storage"));
        assertThatThrownBy(() -> service.create(" Ana ", file)).isInstanceOf(IllegalStateException.class);
        verify(users).deleteById(42L);
    }

    @Test void removesStoredObjectAndUserWhenFinalDatabaseWriteFails() {
        when(processings.saveAndFlush(any())).thenThrow(new IllegalStateException("database"));
        assertThatThrownBy(() -> service.create(" Ana ", file)).isInstanceOf(IllegalStateException.class);
        verify(storage).deleteIdempotently(object);
        verify(users).deleteById(42L);
    }
}
