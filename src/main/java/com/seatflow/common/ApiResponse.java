package com.seatflow.common;

/**
 * Single response envelope for every endpoint.
 *
 * <p>A record is used on purpose: it is immutable, it removes getter and setter
 * boilerplate, and it makes the shape of the payload explicit at the type level.
 *
 * <p>中文批注：全局统一响应信封。所有接口都返回 code + message + data 这一种结构，
 * 客户端只需要解析一种格式。用 record 实现：不可变，且自动获得构造器、equals、hashCode、toString；
 * 注意它生成的是访问器 code()/message()/data()，不是 JavaBean 风格的 getCode()——
 * Jackson 序列化出来的 JSON 字段名也正是这两个单词本身。
 */
public record ApiResponse<T>(int code, String message, T data) {

    private static final int OK = 0;

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(OK, "ok", data);
    }

    public static ApiResponse<Void> success() {
        return new ApiResponse<>(OK, "ok", null);
    }

    public static <T> ApiResponse<T> failure(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
