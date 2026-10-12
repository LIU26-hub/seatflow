package com.seatflow.activity;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA derives the query from the method name, so no SQL is needed
 * for this simple case. Custom queries belong in {@code @Query} methods here.
 */
public interface ActivityRepository extends JpaRepository<Activity, Long> {

    List<Activity> findByStatusOrderByRegistrationStartAsc(ActivityStatus status);
}
