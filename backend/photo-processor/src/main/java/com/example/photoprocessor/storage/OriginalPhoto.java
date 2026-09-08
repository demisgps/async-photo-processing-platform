package com.example.photoprocessor.storage;

import com.example.photoprocessor.event.StorageObjectReference;

public record OriginalPhoto(byte[] bytes, StorageObjectReference reference) {
    public OriginalPhoto { bytes = bytes.clone(); }
    @Override public byte[] bytes() { return bytes.clone(); }
}
