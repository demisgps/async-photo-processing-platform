package com.example.photoapi.usuario.web.contract;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ApiErrorResponse(int status, String codigo, String mensagem, Instant timestamp,
                               UUID processamentoId, List<FieldErrorResponse> campos) {
    public ApiErrorResponse {
        campos = campos == null ? List.of() : List.copyOf(campos);
    }
}

