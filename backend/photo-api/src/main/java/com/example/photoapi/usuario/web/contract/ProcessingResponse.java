package com.example.photoapi.usuario.web.contract;

import java.util.UUID;

public record ProcessingResponse(UUID processamentoId, long usuarioId, String status,
                                 String erroCodigo, String erroDetalhe) {}

