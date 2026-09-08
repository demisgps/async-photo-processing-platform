package com.example.photoconsumer.usuario.repository;

import com.example.photoconsumer.usuario.domain.User;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") long id);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE USUARIO u
            JOIN PROCESSAMENTO_FOTO candidata ON candidata.id = :processingId
            LEFT JOIN PROCESSAMENTO_FOTO atual ON atual.id = u.foto_atual_processamento_id
            SET u.foto_atual_processamento_id = :processingId, u.version = u.version + 1
            WHERE u.id = :userId
              AND candidata.usuario_id = u.id
              AND candidata.status = 'PERSISTINDO'
              AND (atual.id IS NULL OR candidata.sequencia_upload > atual.sequencia_upload)
            """, nativeQuery = true)
    int promoteIfEligibleAndNewer(@Param("userId") long userId,
                                  @Param("processingId") UUID processingId);
}
