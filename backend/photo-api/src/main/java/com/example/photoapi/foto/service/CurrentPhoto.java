package com.example.photoapi.foto.service;

public record CurrentPhoto(byte[] bytes, String contentType) {
    public CurrentPhoto { bytes = bytes.clone(); }
    @Override public byte[] bytes() { return bytes.clone(); }
}
