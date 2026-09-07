package com.example.photoapi.usuario.web.contract;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserNameRequest(@NotBlank @Size(min = 2, max = 150) String nome) {}

