package com.example.photoapi.usuario.validation;

import com.example.photoapi.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class NomeValidator {
    public String validate(String value) {
        if (value == null) {
            throw invalid();
        }
        String normalized = value.strip();
        long length = normalized.codePoints().count();
        if (length < 2 || length > 150) {
            throw invalid();
        }
        return normalized;
    }

    private ApiException invalid() {
        return new ApiException(HttpStatus.BAD_REQUEST, "NOME_INVALIDO",
                "Nome deve possuir de 2 a 150 caracteres Unicode após trim");
    }
}
