package com.example.photoprocessor.imagem;

public class FunctionalProcessingException extends RuntimeException {
    private final String code;
    public FunctionalProcessingException(String code, String message) { super(message); this.code = code; }
    public FunctionalProcessingException(String code, String message, Throwable cause) { super(message, cause); this.code = code; }
    public String code() { return code; }
}
