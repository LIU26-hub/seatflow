package com.seatflow.registration;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RegistrationRepository extends JpaRepository<Registration, Long> {

    /**
     * The unique key on (activity_id, user_id, active_marker) guarantees at most
     * one row in these statuses per user and activity, so Optional is safe here.
     */
    Optional<Registration> findByActivityIdAndUserIdAndStatusIn(
            Long activityId, Long userId, Collection<RegistrationStatus> statuses);

    boolean existsByActivityIdAndUserIdAndStatusIn(
            Long activityId, Long userId, Collection<RegistrationStatus> statuses);

    List<Registration> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Registration> findByStatusAndConfirmDeadlineBefore(
            RegistrationStatus status, LocalDateTime cutoff, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
           update Registration r
              set r.status = :target, r.confirmedAt = :now
            where r.id = :id
              and r.status = :expected
              and r.confirmDeadline >= :now
           """)
    int confirm(@Param("id") Long id,
                @Param("target") RegistrationStatus target,
                @Param("expected") RegistrationStatus expected,
                @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Registration r set r.status = :target where r.id = :id and r.status in :sources")
    int transition(@Param("id") Long id,
                   @Param("target") RegistrationStatus target,
                   @Param("sources") Collection<RegistrationStatus> sources);
}
