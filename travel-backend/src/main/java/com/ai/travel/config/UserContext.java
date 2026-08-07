package com.ai.travel.config;

/**
 * 用户上下文，用于存储当前请求的用户 ID
 */
public class UserContext {
    
    private static final ThreadLocal<String> CURRENT_USER_ID = new ThreadLocal<>();

    public static void setCurrentUserId(String userId) {
        CURRENT_USER_ID.set(userId);
    }

    public static String getCurrentUserId() {
        return CURRENT_USER_ID.get();
    }

    public static void clear() {
        CURRENT_USER_ID.remove();
    }
}
