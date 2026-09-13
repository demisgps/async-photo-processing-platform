package com.example.photoapi.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MigrationIT extends MySqlIntegrationSupport {
    private static final List<String> STATUSES = List.of(
            "RECEBIDA", "PROCESSANDO", "PROCESSADA", "PERSISTINDO", "PERSISTIDA",
            "ERRO_PROCESSAMENTO", "ERRO_PERSISTENCIA");

    @Test
    void migrationsCreateTablesForeignKeysChecksAndIndexes() throws Exception {
        try (var connection = connection()) {
            assertEquals(2, count(connection.prepareStatement(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name IN ('USUARIO','PROCESSAMENTO_FOTO')")));
            assertTrue(count(connection.prepareStatement(
                    "SELECT COUNT(*) FROM information_schema.referential_constraints WHERE constraint_schema = DATABASE()")) >= 2);
            assertTrue(count(connection.prepareStatement(
                    "SELECT COUNT(*) FROM information_schema.check_constraints WHERE constraint_schema = DATABASE()")) >= 2);
            assertTrue(count(connection.prepareStatement(
                    "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'PROCESSAMENTO_FOTO'")) >= 5);
        }
    }

    @Test
    void acceptsExactlyTheSevenStatesAndRejectsAnotherState() throws Exception {
        try (var connection = connection()) {
            for (String status : STATUSES) {
                long userId;
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("INSERT INTO USUARIO(nome) VALUES ('Usuário Teste')", Statement.RETURN_GENERATED_KEYS);
                    try (ResultSet keys = statement.getGeneratedKeys()) { keys.next(); userId = keys.getLong(1); }
                }
                insertProcessing(connection, UUID.randomUUID(), userId, 1, status);
            }
            long invalidUserId;
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO USUARIO(nome) VALUES ('Usuário Inválido')", Statement.RETURN_GENERATED_KEYS);
                try (ResultSet keys = statement.getGeneratedKeys()) { keys.next(); invalidUserId = keys.getLong(1); }
            }
            assertThrows(SQLException.class,
                    () -> insertProcessing(connection, UUID.randomUUID(), invalidUserId, 1, "INVALID_STATUS"));
        }
    }

    private static void insertProcessing(java.sql.Connection connection, UUID id, long userId,
                                         long sequence, String status) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO PROCESSAMENTO_FOTO
                  (id, usuario_id, sequencia_upload, status, bucket_original, arquivo_original,
                   geracao_original, checksum_original, content_type, nome_arquivo,
                   erro_codigo, erro_detalhe, data_erro)
                VALUES (?, ?, ?, ?, 'fotos-usuarios-original', ?, '1', 'checksum', 'image/jpeg',
                        'arquivo.jpg', ?, ?, ?)
                """)) {
            statement.setBytes(1, uuidBytes(id));
            statement.setLong(2, userId);
            statement.setLong(3, sequence);
            statement.setString(4, status);
            statement.setString(5, userId + "/" + id + "/arquivo.jpg");
            boolean error = status.startsWith("ERRO_");
            statement.setString(6, error ? "TEST_ERROR" : null);
            statement.setString(7, error ? "Erro de teste" : null);
            statement.setObject(8, error ? java.time.LocalDateTime.now() : null);
            statement.executeUpdate();
        }
    }

    static byte[] uuidBytes(UUID uuid) {
        return ByteBuffer.allocate(16).putLong(uuid.getMostSignificantBits()).putLong(uuid.getLeastSignificantBits()).array();
    }

    private static int count(PreparedStatement statement) throws SQLException {
        try (statement; ResultSet result = statement.executeQuery()) {
            result.next();
            return result.getInt(1);
        }
    }
}
