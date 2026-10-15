package com.seatflow.common;

/**
 * Expected, domain level failure. Carries the HTTP status that should be
 * returned, so the exception handler does not have to guess.
 *
 * <p>中文批注：预期内的业务失败（例如活动不存在），自身携带要返回的 HTTP 状态码，
 * 避免"业务失败该返回 400 还是 404 还是 500"在各层之间各说各话。
 * 它与系统异常的区别体现在日志级别：业务异常用 warn、不需要堆栈；未知异常用 error、必须保留完整堆栈。
 */
public class BusinessException extends RuntimeException {

    private final int status;

    public BusinessException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }

    public static BusinessException notFound(String message) {
        return new BusinessException(404, message);
    }

    public static BusinessException conflict(String message) {
        return new BusinessException(409, message);
    }
}
