package com.huluwa.console.common;

/**
 * 统一响应信封：code 沿用 HTTP 语义（200 成功 / 400 参数 / 401 未认证 / 403 无权限 / 500 服务异常）。
 */
public record ApiResult<T>(int code, String message, T data) {

    public static <T> ApiResult<T> ok(T data) {
        return new ApiResult<>(200, "ok", data);
    }

    public static ApiResult<Void> ok() {
        return new ApiResult<>(200, "ok", null);
    }

    public static <T> ApiResult<T> fail(int code, String message) {
        return new ApiResult<>(code, message, null);
    }
}