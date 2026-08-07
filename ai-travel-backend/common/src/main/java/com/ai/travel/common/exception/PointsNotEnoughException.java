package com.ai.travel.common.exception;

/**
 * 积分不足异常
 */
public class PointsNotEnoughException extends BusinessException {
    public PointsNotEnoughException() {
        super(4001, "积分不足");
    }
}
