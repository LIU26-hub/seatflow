package com.seatflow.infra.gate;

/**
 * Pre-reservation gate in front of the database row lock.
 *
 * <p>中文批注：它只负责"要不要让这个请求去抢数据库那一行"，不负责最终正确性。
 * 数据库的条件更新仍然是唯一真相：即使闸门放行过多，数据库也会拒绝超出容量的请求。
 * 反过来，闸门不可用时必须放行（fail-open），把压力交回数据库，而不是让报名不可用。
 */
public interface SeatGate {

    /**
     * Attempts to reserve one seat.
     *
     * @param seatsLeftSnapshot current seats left read from the database; used
     *                          only to initialise the Redis key
     * @return true when the caller may continue to the database, false when the
     *         gate already knows the activity is sold out
     */
    boolean tryReserve(Long activityId, int seatsLeftSnapshot);

    /** Gives a previously reserved seat back (cancel, expiry, rollback). */
    void release(Long activityId);
}
