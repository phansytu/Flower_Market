package com.flowermarketplace.admin.repository;

import com.flowermarketplace.admin.entity.BanRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BanRecordRepository extends JpaRepository<BanRecord, Long> {

    Page<BanRecord> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Optional<BanRecord> findByUserIdAndActiveTrue(Long userId);

    boolean existsByUserIdAndActiveTrue(Long userId);

    /** Find active temporary bans that have already passed their expiry — for a scheduled cleanup job. */
    @Query("SELECT b FROM BanRecord b WHERE b.active = true AND b.banType = 'TEMPORARY' AND b.expiresAt < CURRENT_TIMESTAMP")
    java.util.List<BanRecord> findExpiredActiveBans();

    Page<BanRecord> findByActiveTrueOrderByCreatedAtDesc(Pageable pageable);
}
