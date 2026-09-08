package com.example.photoapi.usuario.service;

import com.example.photoapi.exception.ApiException;
import com.example.photoapi.processamento.domain.ProcessingStatus;
import com.example.photoapi.processamento.repository.PhotoProcessingRepository;
import com.example.photoapi.storage.UserPhotoStorageCleaner;
import com.example.photoapi.usuario.repository.UserRepository;
import java.util.EnumSet;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class DeleteUsuarioService {
    private static final EnumSet<ProcessingStatus> ACTIVE = EnumSet.of(ProcessingStatus.RECEBIDA,
            ProcessingStatus.PROCESSANDO, ProcessingStatus.PROCESSADA, ProcessingStatus.PERSISTINDO);
    private final UserRepository users;
    private final PhotoProcessingRepository processings;
    private final UserPhotoStorageCleaner storage;
    private final TransactionTemplate transactions;

    public DeleteUsuarioService(UserRepository users, PhotoProcessingRepository processings,
                                UserPhotoStorageCleaner storage, TransactionTemplate transactions) {
        this.users = users; this.processings = processings; this.storage = storage; this.transactions = transactions;
    }

    public void delete(long userId) {
        var references = transactions.execute(tx -> {
            users.findByIdForUpdate(userId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                    "USUARIO_NAO_ENCONTRADO", "Usuário não encontrado"));
            if (processings.existsByUserIdAndStatusIn(userId, ACTIVE)) {
                throw new ApiException(HttpStatus.CONFLICT, "PROCESSAMENTO_ATIVO", "Usuário possui processamento ativo");
            }
            return processings.findByUserId(userId);
        });
        references.forEach(storage::delete);
        transactions.executeWithoutResult(tx -> {
            var user = users.findByIdForUpdate(userId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                    "USUARIO_NAO_ENCONTRADO", "Usuário não encontrado"));
            if (processings.existsByUserIdAndStatusIn(userId, ACTIVE)) {
                throw new ApiException(HttpStatus.CONFLICT, "PROCESSAMENTO_ATIVO", "Usuário possui processamento ativo");
            }
            user.promote(null);
            users.flush();
            processings.deleteAll(processings.findByUserId(userId));
            processings.flush();
            users.delete(user);
        });
    }
}
