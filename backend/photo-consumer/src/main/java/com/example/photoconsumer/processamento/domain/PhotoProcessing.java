package com.example.photoconsumer.processamento.domain;

import com.example.photoconsumer.usuario.domain.User;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;

@Entity
@Table(name = "PROCESSAMENTO_FOTO")
public class PhotoProcessing {
    @Id @Column(name = "id", columnDefinition = "BINARY(16)") private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false) private User user;
    @Column(name = "sequencia_upload", nullable = false) private long uploadSequence;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false) private ProcessingStatus status;
    @Column(name = "bucket_processado") private String processedBucket;
    @Column(name = "arquivo_processado", length = 1024) private String processedObject;
    @Column(name = "geracao_processada", length = 64) private String processedGeneration;
    @Column(name = "checksum_processada", length = 128) private String processedChecksum;
    @Lob @Basic(fetch = FetchType.LAZY)
    @Column(name = "imagem_processada", columnDefinition = "LONGBLOB") private byte[] processedImage;
    @Column(name = "content_type", nullable = false) private String contentType;
    @Column(name = "largura") private Integer width;
    @Column(name = "altura") private Integer height;
    @Column(name = "erro_codigo") private String errorCode;
    @Column(name = "erro_detalhe", columnDefinition = "TEXT") private String errorDetail;
    @Version @Column(name = "version", nullable = false) private long version;

    protected PhotoProcessing() {}
    public UUID getId() { return id; }
    public User getUser() { return user; }
    public long getUploadSequence() { return uploadSequence; }
    public ProcessingStatus getStatus() { return status; }
    public long getVersion() { return version; }
}

