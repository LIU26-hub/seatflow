package com.seatflow.infra.scheduling;

/**
 * The timeout scanner depends on this seam, not on a concrete service.
 *
 * <p>中文批注：定时任务只负责"什么时候扫"，不负责"怎么回收"。
 * 回收涉及状态机与名额回加，属于业务逻辑，由报名模块实现这个接口即可。
 * 还有一个实际好处：任务先写好、业务后补，只要实现类还没注册，
 * 任务检测不到 bean 就安静跳过，应用照常启动。
 */
public interface RegistrationExpiryHandler {

    /**
     * Expires registrations whose confirmation deadline has passed and
     * releases their seats idempotently.
     *
     * @return how many registrations were expired by this call
     */
    int expireOverdue(int batchSize);
}
