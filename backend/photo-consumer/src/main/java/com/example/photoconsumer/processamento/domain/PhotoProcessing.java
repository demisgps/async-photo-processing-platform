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
import java.time.LocalDateTime;

@Entity
@Table(name = "PROCESSAMENTO_FOTO")
public class PhotoProcessing {
    @Id @Column(name = "id", columnDefinition = "BINARY(16)") private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false) private User user;
    @Column(name = "sequencia_upload", nullable = false) private long uploadSequence;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false) private ProcessingStatus status;
    @Column(name = "bucket_original", nullable = false) private String originalBucket;
    @Column(name = "arquivo_original", nullable = false, length = 1024) private String originalObject;
    @Column(name = "geracao_original", nullable = false, length = 64) private String originalGeneration;
    @Column(name = "checksum_original", nullable = false, length = 128) private String originalChecksum;
    @Column(name = "nome_arquivo", nullable = false) private String fileName;
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
    @Column(name = "data_processamento") private LocalDateTime processedAt;
    @Column(name = "data_inicio_persistencia") private LocalDateTime persistenceStartedAt;
    @Column(name = "data_persistencia") private LocalDateTime persistedAt;
    @Column(name = "data_erro") private LocalDateTime errorAt;

    protected PhotoProcessing() {}
    public PhotoProcessing(UUID id, User user, long uploadSequence, ProcessingStatus status) {
        this.id=id; this.user=user; this.uploadSequence=uploadSequence; this.status=status;
        this.originalBucket="fotos-usuarios-original"; this.originalObject=user.getIdValue()+"/"+id+"/arquivo.jpg";
        this.originalGeneration="1"; this.originalChecksum="test"; this.fileName="arquivo.jpg"; this.contentType="image/jpeg";
    }
    public UUID getId() { return id; }
    public User getUser() { return user; }
    public long getUploadSequence() { return uploadSequence; }
    public ProcessingStatus getStatus() { return status; }
    public long getVersion() { return version; }
    public byte[] getProcessedImage() { return processedImage == null ? null : processedImage.clone(); }
    public String getProcessedBucket() { return processedBucket; }
    public String getProcessedObject() { return processedObject; }
    public String getProcessedGeneration() { return processedGeneration; }
    public String getProcessedChecksum() { return processedChecksum; }
    public String getContentType() { return contentType; }
    public Integer getWidth() { return width; }
    public Integer getHeight() { return height; }
    public void markProcessed() { require(ProcessingStatus.PROCESSANDO); status = ProcessingStatus.PROCESSADA; processedAt = LocalDateTime.now(); }
    public void startPersistence() { require(ProcessingStatus.PROCESSADA); status = ProcessingStatus.PERSISTINDO; persistenceStartedAt = LocalDateTime.now(); }
    public void persist(byte[] image, String bucket, String object, String generation, String checksum,
                        String contentType, int width, int height) {
        require(ProcessingStatus.PERSISTINDO);
        this.processedImage = image.clone(); this.processedBucket = bucket; this.processedObject = object;
        this.processedGeneration = generation; this.processedChecksum = checksum; this.contentType = contentType;
        this.width = width; this.height = height; this.status = ProcessingStatus.PERSISTIDA; this.persistedAt = LocalDateTime.now();
    }
    public boolean matchesProcessed(String bucket, String object, String generation, String checksum,
                                    String contentType, int width, int height) {
        if (processedObject == null) return true;
        return java.util.Objects.equals(processedBucket, bucket)
                && java.util.Objects.equals(processedObject, object)
                && java.util.Objects.equals(processedGeneration, generation)
                && java.util.Objects.equals(processedChecksum, checksum)
                && java.util.Objects.equals(this.contentType, contentType)
                && java.util.Objects.equals(this.width, width)
                && java.util.Objects.equals(this.height, height);
    }
    public void failPersistence(String code, String detail) {
        if (status != ProcessingStatus.PROCESSADA && status != ProcessingStatus.PERSISTINDO) {
            throw new IllegalStateException("Transição inválida: " + status);
        }
        status = ProcessingStatus.ERRO_PERSISTENCIA; errorCode = code; errorDetail = detail; errorAt = LocalDateTime.now();
    }
    public void failProcessing(String code, String detail) {
        require(ProcessingStatus.PROCESSANDO); status = ProcessingStatus.ERRO_PROCESSAMENTO;
        errorCode = code; errorDetail = detail; errorAt = LocalDateTime.now();
    }
    private void require(ProcessingStatus expected) { if (status != expected) throw new IllegalStateException("Transição inválida: " + status); }
}
