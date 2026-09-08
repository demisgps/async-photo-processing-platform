package com.example.photoapi.usuario.service;

import com.example.photoapi.exception.ApiException;
import com.example.photoapi.processamento.domain.PhotoProcessing;
import com.example.photoapi.processamento.repository.PhotoProcessingRepository;
import com.example.photoapi.usuario.domain.User;
import com.example.photoapi.usuario.repository.UserRepository;
import com.example.photoapi.usuario.web.contract.CurrentPhotoSummaryResponse;
import com.example.photoapi.usuario.web.contract.UserDetailResponse;
import com.example.photoapi.usuario.web.contract.UserSummaryResponse;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsuarioQueryService {
    private final UserRepository users;
    private final PhotoProcessingRepository processings;

    public UsuarioQueryService(UserRepository users, PhotoProcessingRepository processings) {
        this.users = users;
        this.processings = processings;
    }

    @Transactional(readOnly = true)
    public List<UserSummaryResponse> list() {
        return users.findAll().stream().map(this::summary).toList();
    }

    @Transactional(readOnly = true)
    public UserDetailResponse find(long id) {
        User user = users.findById(id).orElseThrow(() -> notFound(id));
        return new UserDetailResponse(user.getId(), user.getName(), photo(user),
                user.getCreatedAt().toInstant(ZoneOffset.UTC), user.getUpdatedAt().toInstant(ZoneOffset.UTC));
    }

    private UserSummaryResponse summary(User user) {
        return new UserSummaryResponse(user.getId(), user.getName(), photo(user));
    }

    private CurrentPhotoSummaryResponse photo(User user) {
        if (user.getCurrentPhotoProcessingId() != null) {
            return processings.findById(user.getCurrentPhotoProcessingId())
                    .map(p -> new CurrentPhotoSummaryResponse(true, p.getId(), p.getStatus().name()))
                    .orElse(new CurrentPhotoSummaryResponse(false, null, null));
        }
        return processings.findFirstByUserIdOrderByUploadSequenceDesc(user.getId())
                .map(this::unavailablePhoto)
                .orElse(new CurrentPhotoSummaryResponse(false, null, null));
    }

    private CurrentPhotoSummaryResponse unavailablePhoto(PhotoProcessing processing) {
        return new CurrentPhotoSummaryResponse(false, processing.getId(), processing.getStatus().name());
    }

    private ApiException notFound(long id) {
        return new ApiException(HttpStatus.NOT_FOUND, "USUARIO_NAO_ENCONTRADO", "Usuário " + id + " não encontrado");
    }
}
