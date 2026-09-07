package com.example.photoapi.usuario.web.contract;

import java.time.Instant;

public record UserDetailResponse(long id, String nome, CurrentPhotoSummaryResponse foto,
                                 Instant dataCadastro, Instant dataAtualizacao) {}

