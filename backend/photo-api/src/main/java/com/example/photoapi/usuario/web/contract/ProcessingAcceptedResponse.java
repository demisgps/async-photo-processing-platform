package com.example.photoapi.usuario.web.contract;

import java.util.UUID;

public record ProcessingAcceptedResponse(UUID processamentoId, long usuarioId, String status) {}

