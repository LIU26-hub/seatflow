package com.seatflow.activity;

import java.util.Collection;
import java.util.List;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JPA derives the query from the method name, so no SQL is needed
 * for this simple case. Custom queries belong in {@code @Query} methods here.
 *
 * <p>中文批注：数据访问层，不需要写实现类——方法名本身就是查询语义：
 * findByStatusOrderByRegistrationStartAsc 表示"按状态过滤、再按报名开始时间升序"。
 * 只有复杂报表类查询，才需要在这里用 @Query 手写 SQL。
 */
public interface ActivityRepository extends JpaRepository<Activity, Long> {

    List<Activity> findByStatusOrderByRegistrationStartAsc(ActivityStatus status);

    List<Activity> findByOrganizerIdAndStatusNotOrderByCreatedAtDesc(Long organizerId, ActivityStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Activity a SET a.registeredCount = a.registeredCount + 1 WHERE a.id = :activityId and a.registeredCount<a.capacity")
    int incrementRegisteredCount(@Param("activityId") Long activityId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Activity a SET a.registeredCount = a.registeredCount - 1 WHERE a.id = :activityId and a.registeredCount>0")
    int releaseRegisteredCount(@Param("activityId") Long activityId);
}
