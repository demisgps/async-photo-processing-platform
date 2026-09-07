package com.example.photoconsumer.processamento.domain;

public enum ProcessingStatus {
    RECEBIDA,
    PROCESSANDO,
    PROCESSADA,
    PERSISTINDO,
    PERSISTIDA,
    ERRO_PROCESSAMENTO,
    ERRO_PERSISTENCIA
}

