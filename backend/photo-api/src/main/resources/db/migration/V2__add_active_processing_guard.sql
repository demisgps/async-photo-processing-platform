ALTER TABLE PROCESSAMENTO_FOTO
    ADD COLUMN usuario_ativo_id BIGINT GENERATED ALWAYS AS (
        CASE
            WHEN status IN ('RECEBIDA', 'PROCESSANDO', 'PROCESSADA', 'PERSISTINDO') THEN usuario_id
            ELSE NULL
        END
    ) STORED,
    ADD CONSTRAINT uq_processamento_usuario_ativo UNIQUE (usuario_ativo_id);

