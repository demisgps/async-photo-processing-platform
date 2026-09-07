package com.example.photoconsumer.processamento.repository;

import com.example.photoconsumer.processamento.domain.PhotoProcessing;
import com.example.photoconsumer.processamento.domain.ProcessingStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PhotoProcessingRepository extends JpaRepository<PhotoProcessing, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PhotoProcessing p join fetch p.user where p.id = :id")
    Optional<PhotoProcessing> findByIdForUpdate(@Param("id") UUID id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update PhotoProcessing p set p.status = :next, p.version = p.version + 1 where p.id = :id and p.status = :expected and p.version = :version")
    int compareAndSetStatus(@Param("id") UUID id, @Param("expected") ProcessingStatus expected,
                            @Param("next") ProcessingStatus next, @Param("version") long version);
}
