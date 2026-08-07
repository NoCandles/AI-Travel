package com.ai.travel.admin.dto;

import lombok.Data;
import java.io.Serializable;

/**
 * 统一API响应结构
 */
@Data
public class ApiResponse<T> implements Serializable {

    private boolean success;
    private T data;
    private String message;

    // ============ 成功 ============

    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = true;
        r.data = data;
        r.message = "操作成功";
        return r;
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = true;
        r.data = data;
        r.message = message;
        return r;
    }

    // ============ 错误 ============

    public static <T> ApiResponse<T> error(String message) {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = false;
        r.message = message;
        return r;
    }
}
