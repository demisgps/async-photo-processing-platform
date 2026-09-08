package com.example.photoapi.foto.validation;

public record ValidatedPhoto(byte[] bytes, String contentType, String extension) {
    public ValidatedPhoto { bytes = bytes.clone(); }
    @Override public byte[] bytes() { return bytes.clone(); }
}
