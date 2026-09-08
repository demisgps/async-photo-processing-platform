package com.example.photoapi.usuario.service;

import com.example.photoapi.exception.ApiException;
import com.example.photoapi.usuario.repository.UserRepository;
import com.example.photoapi.usuario.validation.NomeValidator;
import com.example.photoapi.usuario.web.contract.UserDetailResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateUsuarioService {
    private final UserRepository users;
    private final NomeValidator names;
    private final UsuarioQueryService queries;

    public UpdateUsuarioService(UserRepository users, NomeValidator names, UsuarioQueryService queries) {
        this.users = users;
        this.names = names;
        this.queries = queries;
    }

    @Transactional
    public UserDetailResponse update(long id, String rawName) {
        var user = users.findByIdForUpdate(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                "USUARIO_NAO_ENCONTRADO", "Usuário " + id + " não encontrado"));
        user.rename(names.validate(rawName));
        users.flush();
        return queries.find(id);
    }
}
