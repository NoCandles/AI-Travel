package com.ai.travel.admin.config;

import java.lang.annotation.*;

/**
 * 操作日志注解 — 标记需要记录操作日志的方法
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AdminLog {

    /** 操作模块 */
    String module();

    /** 操作动作 */
    String action();

    /** 目标类型 */
    String targetType() default "";

    /** 目标ID表达式（SpEL支持，如 #id） */
    String targetIdExpr() default "";
}
