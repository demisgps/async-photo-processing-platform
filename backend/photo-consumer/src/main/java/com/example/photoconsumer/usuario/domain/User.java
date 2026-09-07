package com.example.photoconsumer.usuario.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;

@Entity
@Table(name = "USUARIO")
public class User {
    @Id private Long id;
    @Column(name = "nome", nullable = false) private String name;
    @Column(name = "foto_atual_processamento_id", columnDefinition = "BINARY(16)")
    private UUID currentPhotoProcessingId;
    @Column(name = "proxima_sequencia_upload", nullable = false) private long nextUploadSequence;
    @Version @Column(name = "version", nullable = false) private long version;

    protected User() {}
    public Long getId() { return id; }
    public UUID getCurrentPhotoProcessingId() { return currentPhotoProcessingId; }
    public void promote(UUID processingId) { currentPhotoProcessingId = processingId; }
}

