package com.seatflow.activity;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

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
}
