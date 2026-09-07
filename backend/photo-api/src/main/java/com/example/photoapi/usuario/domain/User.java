package com.example.photoapi.usuario.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "USUARIO")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nome", nullable = false, length = 150)
    private String name;

    @Column(name = "foto_atual_processamento_id", columnDefinition = "BINARY(16)")
    private UUID currentPhotoProcessingId;

    @Column(name = "proxima_sequencia_upload", nullable = false)
    private long nextUploadSequence = 1;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "data_cadastro", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "data_atualizacao", nullable = false)
    private LocalDateTime updatedAt;

    protected User() {}

    public User(String name) { this.name = name; }

    public Long getId() { return id; }
    public String getName() { return name; }
    public UUID getCurrentPhotoProcessingId() { return currentPhotoProcessingId; }
    public long getNextUploadSequence() { return nextUploadSequence; }
    public long getVersion() { return version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void rename(String name) { this.name = name; }
    public long allocateUploadSequence() { return nextUploadSequence++; }
    public void promote(UUID processingId) { this.currentPhotoProcessingId = processingId; }
}
