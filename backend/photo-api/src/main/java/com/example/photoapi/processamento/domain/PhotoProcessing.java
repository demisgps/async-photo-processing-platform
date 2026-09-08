package com.example.photoapi.processamento.domain;

import com.example.photoapi.usuario.domain.User;
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
import java.time.LocalDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "PROCESSAMENTO_FOTO")
public class PhotoProcessing {
    @Id
    @Column(name = "id", columnDefinition = "BINARY(16)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private User user;

    @Column(name = "sequencia_upload", nullable = false)
    private long uploadSequence;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ProcessingStatus status;

    @Column(name = "usuario_ativo_id", insertable = false, updatable = false)
    private Long activeUserId;

    @Column(name = "bucket_original", nullable = false)
    private String originalBucket;
    @Column(name = "arquivo_original", nullable = false, length = 1024)
    private String originalObject;
    @Column(name = "geracao_original", nullable = false, length = 64)
    private String originalGeneration;
    @Column(name = "checksum_original", nullable = false, length = 128)
    private String originalChecksum;
    @Column(name = "bucket_processado")
    private String processedBucket;
    @Column(name = "arquivo_processado", length = 1024)
    private String processedObject;
    @Column(name = "geracao_processada", length = 64)
    private String processedGeneration;
    @Column(name = "checksum_processada", length = 128)
    private String processedChecksum;
    @Lob @Basic(fetch = FetchType.LAZY)
    @Column(name = "imagem_processada", columnDefinition = "LONGBLOB")
    private byte[] processedImage;
    @Column(name = "content_type", nullable = false, length = 64)
    private String contentType;
    @Column(name = "nome_arquivo", nullable = false)
    private String fileName;
    @Column(name = "largura") private Integer width;
    @Column(name = "altura") private Integer height;
    @Column(name = "erro_codigo", length = 128) private String errorCode;
    @Column(name = "erro_detalhe", columnDefinition = "TEXT") private String errorDetail;
    @Version @Column(name = "version", nullable = false) private long version;
    @CreationTimestamp @Column(name = "data_recebimento", nullable = false, updatable = false)
    private LocalDateTime receivedAt;
    @Column(name = "data_inicio_processamento") private LocalDateTime processingStartedAt;
    @Column(name = "data_processamento") private LocalDateTime processedAt;
    @Column(name = "data_inicio_persistencia") private LocalDateTime persistenceStartedAt;
    @Column(name = "data_persistencia") private LocalDateTime persistedAt;
    @Column(name = "data_erro") private LocalDateTime errorAt;

    protected PhotoProcessing() {}

    public PhotoProcessing(UUID id, User user, long uploadSequence, String originalBucket,
                           String originalObject, String originalGeneration, String originalChecksum,
                           String contentType, String fileName) {
        this.id = id;
        this.user = user;
        this.uploadSequence = uploadSequence;
        this.status = ProcessingStatus.RECEBIDA;
        this.originalBucket = originalBucket;
        this.originalObject = originalObject;
        this.originalGeneration = originalGeneration;
        this.originalChecksum = originalChecksum;
        this.contentType = contentType;
        this.fileName = fileName;
    }

    public void startProcessing() {
        if (status != ProcessingStatus.RECEBIDA) {
            throw new IllegalStateException("Somente processamento RECEBIDA pode ser iniciado");
        }
        status = ProcessingStatus.PROCESSANDO;
        processingStartedAt = LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public long getUploadSequence() { return uploadSequence; }
    public ProcessingStatus getStatus() { return status; }
    public long getVersion() { return version; }
    public String getErrorCode() { return errorCode; }
    public String getErrorDetail() { return errorDetail; }
    public byte[] getProcessedImage() { return processedImage == null ? null : processedImage.clone(); }
    public String getContentType() { return contentType; }
    public String getOriginalBucket() { return originalBucket; }
    public String getOriginalObject() { return originalObject; }
    public String getProcessedBucket() { return processedBucket; }
    public String getProcessedObject() { return processedObject; }
    public LocalDateTime getProcessingStartedAt() { return processingStartedAt; }
    public void failProcessing(String code, String detail) {
        if (status != ProcessingStatus.PROCESSANDO) throw new IllegalStateException("Transição inválida: " + status);
        status = ProcessingStatus.ERRO_PROCESSAMENTO; errorCode = code; errorDetail = detail; errorAt = LocalDateTime.now();
    }
}
