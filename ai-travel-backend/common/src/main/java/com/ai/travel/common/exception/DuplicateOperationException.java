package com.ai.travel.common.exception;

/**
 * 重复操作异常（如重复签到、重复点赞等）
 */
public class DuplicateOperationException extends BusinessException {
    public DuplicateOperationException(String message) {
        super(4002, message);
    }
}
