package com.ai.travel.admin.config;

import lombok.Data;

/**
 * 管理员上下文（ThreadLocal）
 */
public class AdminContext {

    private static final ThreadLocal<AdminInfo> HOLDER = new ThreadLocal<>();

    public static void set(String id, String username, String role) {
        HOLDER.set(new AdminInfo(id, username, role));
    }

    public static AdminInfo get() {
        return HOLDER.get();
    }

    public static String getId() {
        AdminInfo info = HOLDER.get();
        return info != null ? info.getId() : null;
    }

    public static String getUsername() {
        AdminInfo info = HOLDER.get();
        return info != null ? info.getUsername() : null;
    }

    public static String getRole() {
        AdminInfo info = HOLDER.get();
        return info != null ? info.getRole() : null;
    }

    public static void clear() {
        HOLDER.remove();
    }

    @Data
    public static class AdminInfo {
        private final String id;
        private final String username;
        private final String role;
    }
}
