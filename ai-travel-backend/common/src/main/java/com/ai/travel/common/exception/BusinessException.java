package com.ai.travel.common.exception;

/**
 * 业务异常基类
 * <p>
 * 所有业务异常均应继承此类。
 * GlobalExceptionHandler 据此区分业务错误（返回友好提示）和系统错误（返回通用错误消息）。
 */
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(String message) {
        this(400, message);
    }

    public int getCode() {
        return code;
    }
}
