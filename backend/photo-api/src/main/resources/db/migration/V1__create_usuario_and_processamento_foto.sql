CREATE TABLE USUARIO (
    id BIGINT NOT NULL AUTO_INCREMENT,
    nome VARCHAR(150) NOT NULL,
    foto_atual_processamento_id BINARY(16) NULL,
    proxima_sequencia_upload BIGINT NOT NULL DEFAULT 1,
    version BIGINT NOT NULL DEFAULT 0,
    data_cadastro DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    data_atualizacao DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_usuario PRIMARY KEY (id),
    CONSTRAINT ck_usuario_nome CHECK (CHAR_LENGTH(TRIM(nome)) BETWEEN 2 AND 150),
    CONSTRAINT ck_usuario_proxima_sequencia CHECK (proxima_sequencia_upload >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE PROCESSAMENTO_FOTO (
    id BINARY(16) NOT NULL,
    usuario_id BIGINT NOT NULL,
    sequencia_upload BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    bucket_original VARCHAR(255) NOT NULL,
    arquivo_original VARCHAR(1024) NOT NULL,
    geracao_original VARCHAR(64) NOT NULL,
    checksum_original VARCHAR(128) NOT NULL,
    bucket_processado VARCHAR(255) NULL,
    arquivo_processado VARCHAR(1024) NULL,
    geracao_processada VARCHAR(64) NULL,
    checksum_processada VARCHAR(128) NULL,
    imagem_processada LONGBLOB NULL,
    content_type VARCHAR(64) NOT NULL,
    nome_arquivo VARCHAR(255) NOT NULL,
    largura INT NULL,
    altura INT NULL,
    erro_codigo VARCHAR(128) NULL,
    erro_detalhe TEXT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    data_recebimento DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    data_inicio_processamento DATETIME(6) NULL,
    data_processamento DATETIME(6) NULL,
    data_inicio_persistencia DATETIME(6) NULL,
    data_persistencia DATETIME(6) NULL,
    data_erro DATETIME(6) NULL,
    CONSTRAINT pk_processamento_foto PRIMARY KEY (id),
    CONSTRAINT fk_processamento_usuario FOREIGN KEY (usuario_id) REFERENCES USUARIO(id),
    CONSTRAINT uq_processamento_usuario_sequencia UNIQUE (usuario_id, sequencia_upload),
    CONSTRAINT ck_processamento_status CHECK (status IN (
        'RECEBIDA', 'PROCESSANDO', 'PROCESSADA', 'PERSISTINDO', 'PERSISTIDA',
        'ERRO_PROCESSAMENTO', 'ERRO_PERSISTENCIA'
    )),
    CONSTRAINT ck_processamento_sequencia CHECK (sequencia_upload >= 1),
    CONSTRAINT ck_processamento_content_type CHECK (content_type IN ('image/jpeg', 'image/png')),
    CONSTRAINT ck_processamento_dimensoes CHECK (
        (largura IS NULL AND altura IS NULL) OR
        (largura BETWEEN 1 AND 1024 AND altura BETWEEN 1 AND 1024)
    ),
    CONSTRAINT ck_processamento_erro CHECK (
        status NOT IN ('ERRO_PROCESSAMENTO', 'ERRO_PERSISTENCIA') OR
        (erro_codigo IS NOT NULL AND data_erro IS NOT NULL)
    ),
    INDEX ix_processamento_usuario_status (usuario_id, status),
    INDEX ix_processamento_status_inicio (status, data_inicio_processamento),
    INDEX ix_processamento_data_recebimento (data_recebimento)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE USUARIO
    ADD CONSTRAINT fk_usuario_foto_atual
    FOREIGN KEY (foto_atual_processamento_id) REFERENCES PROCESSAMENTO_FOTO(id);

CREATE INDEX ix_usuario_foto_atual ON USUARIO(foto_atual_processamento_id);

