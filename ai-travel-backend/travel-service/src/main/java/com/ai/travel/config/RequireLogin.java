package com.ai.travel.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要登录才能访问的接口。
 * <p>
 * 加在 Controller 方法上，未登录用户（anonymous_user）访问时返回 401。
 * 加在 Controller 类上，该控制器所有方法都需要登录。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireLogin {
}
