package com.example.photoapi.processamento.repository;

import com.example.photoapi.processamento.domain.PhotoProcessing;
import com.example.photoapi.processamento.domain.ProcessingStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PhotoProcessingRepository extends JpaRepository<PhotoProcessing, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PhotoProcessing p where p.id = :id")
    Optional<PhotoProcessing> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByUserIdAndStatusIn(long userId, Collection<ProcessingStatus> statuses);
    List<PhotoProcessing> findByUserId(long userId);
    Optional<PhotoProcessing> findFirstByUserIdOrderByUploadSequenceDesc(long userId);
    java.util.List<PhotoProcessing> findAllByUserId(long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update PhotoProcessing p set p.status = :next, p.version = p.version + 1 where p.id = :id and p.status = :expected and p.version = :version")
    int compareAndSetStatus(@Param("id") UUID id, @Param("expected") ProcessingStatus expected,
                            @Param("next") ProcessingStatus next, @Param("version") long version);
}
