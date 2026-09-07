package com.example.photoapi.usuario.web.contract;

import java.util.UUID;

public record CurrentPhotoSummaryResponse(boolean disponivel, UUID processamentoId, String status) {}

