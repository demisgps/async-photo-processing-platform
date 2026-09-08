package com.example.photoprocessor.imagem;

public record TransformedImage(byte[] bytes, String contentType, String extension, int width, int height,
                               String checksum) {
    public TransformedImage { bytes = bytes.clone(); }
    @Override public byte[] bytes() { return bytes.clone(); }
}
