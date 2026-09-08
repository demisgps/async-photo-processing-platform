package com.example.photoapi.processamento.service;

import com.example.photoapi.exception.ApiException;
import com.example.photoapi.processamento.repository.PhotoProcessingRepository;
import com.example.photoapi.usuario.web.contract.ProcessingResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProcessingQueryService {
    private final PhotoProcessingRepository repository;
    public ProcessingQueryService(PhotoProcessingRepository repository) { this.repository = repository; }
    @Transactional(readOnly = true)
    public ProcessingResponse find(UUID id) {
        var processing = repository.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                "PROCESSAMENTO_NAO_ENCONTRADO", "Processamento não encontrado"));
        return new ProcessingResponse(processing.getId(), processing.getUser().getId(), processing.getStatus().name(),
                processing.getErrorCode(), processing.getErrorDetail());
    }
}
