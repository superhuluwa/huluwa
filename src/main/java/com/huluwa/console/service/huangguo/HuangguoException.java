package com.huluwa.console.service.huangguo;

/** 黄果源站不可用、响应异常或加密播放地址解析失败；由全局异常处理转换成 502。 */
public class HuangguoException extends RuntimeException {
    public HuangguoException(String message, Throwable cause) {
        super(message, cause);
    }

    public HuangguoException(String message) {
        super(message);
    }
}
