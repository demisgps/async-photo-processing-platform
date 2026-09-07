package com.example.photoapi.usuario.web.contract;

import java.util.UUID;

public record UserCreatedResponse(long id, String nome, UUID processamentoId, String status) {}

