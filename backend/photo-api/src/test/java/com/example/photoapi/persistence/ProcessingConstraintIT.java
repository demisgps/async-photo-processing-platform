package com.example.photoapi.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class ProcessingConstraintIT extends MySqlIntegrationSupport {
    @Test
    void uniqueGeneratedColumnAllowsOnlyOneActiveProcessingPerUser() throws Exception {
        long userId = createUser();
        insertActive(userId, 1);
        try (Connection connection = connection()) {
            boolean rejected = false;
            try {
                insertActive(connection, userId, 2);
            } catch (SQLException exception) {
                rejected = "23000".equals(exception.getSQLState());
            }
            assertTrue(rejected);
        }
    }

    @Test
    void rowLockAllocatesDistinctUploadSequencesUnderConcurrency() throws Exception {
        long userId = createUser();
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Long> allocation = () -> allocateSequence(userId);
            var first = executor.submit(allocation);
            var second = executor.submit(allocation);
            assertTrue(java.util.Set.of(first.get(), second.get()).containsAll(java.util.Set.of(1L, 2L)));
        }
    }

    @Test
    void compareAndSetVersionAllowsOnlyExpectedVersion() throws Exception {
        long userId = createUser();
        try (Connection connection = connection(); PreparedStatement update = connection.prepareStatement(
                "UPDATE USUARIO SET nome = ?, version = version + 1 WHERE id = ? AND version = ?")) {
            update.setString(1, "Nome CAS"); update.setLong(2, userId); update.setLong(3, 0);
            assertEquals(1, update.executeUpdate());
            update.setString(1, "Nome obsoleto"); update.setLong(2, userId); update.setLong(3, 0);
            assertEquals(0, update.executeUpdate());
        }
    }

    private long createUser() throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO USUARIO(nome) VALUES ('Concorrência')", Statement.RETURN_GENERATED_KEYS);
            try (var keys = statement.getGeneratedKeys()) { keys.next(); return keys.getLong(1); }
        }
    }

    private void insertActive(long userId, long sequence) throws SQLException {
        try (Connection connection = connection()) { insertActive(connection, userId, sequence); }
    }

    private void insertActive(Connection connection, long userId, long sequence) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO PROCESSAMENTO_FOTO
                  (id, usuario_id, sequencia_upload, status, bucket_original, arquivo_original,
                   geracao_original, checksum_original, content_type, nome_arquivo)
                VALUES (?, ?, ?, 'PROCESSANDO', 'fotos-usuarios-original', ?, '1', 'checksum',
                        'image/jpeg', 'arquivo.jpg')
                """)) {
            statement.setBytes(1, MigrationIT.uuidBytes(id));
            statement.setLong(2, userId);
            statement.setLong(3, sequence);
            statement.setString(4, userId + "/" + id + "/arquivo.jpg");
            statement.executeUpdate();
        }
    }

    private long allocateSequence(long userId) throws SQLException {
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            long next;
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT proxima_sequencia_upload FROM USUARIO WHERE id = ? FOR UPDATE")) {
                select.setLong(1, userId);
                try (var result = select.executeQuery()) { result.next(); next = result.getLong(1); }
            }
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE USUARIO SET proxima_sequencia_upload = ? WHERE id = ?")) {
                update.setLong(1, next + 1); update.setLong(2, userId); update.executeUpdate();
            }
            connection.commit();
            return next;
        }
    }
}

