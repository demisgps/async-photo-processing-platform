package com.example.photoapi.foto.service;

import com.example.photoapi.exception.ApiException;
import com.example.photoapi.processamento.domain.ProcessingStatus;
import com.example.photoapi.processamento.repository.PhotoProcessingRepository;
import com.example.photoapi.usuario.repository.UserRepository;
import java.util.EnumSet;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CurrentPhotoService {
    private static final EnumSet<ProcessingStatus> ACTIVE = EnumSet.of(ProcessingStatus.RECEBIDA,
            ProcessingStatus.PROCESSANDO, ProcessingStatus.PROCESSADA, ProcessingStatus.PERSISTINDO);
    private final UserRepository users; private final PhotoProcessingRepository processings;
    public CurrentPhotoService(UserRepository users, PhotoProcessingRepository processings) {
        this.users=users; this.processings=processings;
    }
    @Transactional(readOnly = true)
    public CurrentPhoto find(long userId) {
        var user = users.findById(userId).orElseThrow(() -> notFound("Usuário não encontrado"));
        if (user.getCurrentPhotoProcessingId() != null) {
            var current = processings.findById(user.getCurrentPhotoProcessingId())
                    .filter(p -> p.getStatus() == ProcessingStatus.PERSISTIDA && p.getProcessedImage() != null)
                    .orElseThrow(() -> notFound("Foto atual não encontrada"));
            return new CurrentPhoto(current.getProcessedImage(), current.getContentType());
        }
        if (processings.existsByUserIdAndStatusIn(userId, ACTIVE))
            throw new ApiException(HttpStatus.CONFLICT, "FOTO_TEMPORARIAMENTE_INDISPONIVEL",
                    "A foto ainda está sendo processada");
        throw notFound("Foto atual não encontrada");
    }
    private ApiException notFound(String message) { return new ApiException(HttpStatus.NOT_FOUND, "FOTO_NAO_ENCONTRADA", message); }
}
