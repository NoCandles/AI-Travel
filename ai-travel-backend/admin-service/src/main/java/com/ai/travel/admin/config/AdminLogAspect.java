package com.ai.travel.admin.config;

import com.ai.travel.admin.repository.AdminLogRepository;
import com.alibaba.fastjson2.JSON;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AdminLogAspect {

    private final AdminLogRepository adminLogRepository;

    @Around("@annotation(com.ai.travel.admin.config.AdminLog)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        Object result = null;
        Exception error = null;

        try {
            result = joinPoint.proceed();
            return result;
        } catch (Exception e) {
            error = e;
            throw e;
        } finally {
            try {
                saveLog(joinPoint, start, result, error);
            } catch (Exception e) {
                log.error("记录操作日志失败", e);
            }
        }
    }

    private void saveLog(ProceedingJoinPoint joinPoint, long start, Object result, Exception error) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        AdminLog ann = method.getAnnotation(AdminLog.class);

        com.ai.travel.admin.entity.AdminLog logEntry = new com.ai.travel.admin.entity.AdminLog();
        logEntry.setAdminId(AdminContext.getId());
        logEntry.setAdminName(AdminContext.getUsername());
        logEntry.setModule(ann.module());
        logEntry.setAction(ann.action());

        // 记录参数
        String[] paramNames = signature.getParameterNames();
        Object[] paramValues = joinPoint.getArgs();
        Map<String, Object> params = new HashMap<>();
        if (paramNames != null) {
            for (int i = 0; i < paramNames.length; i++) {
                params.put(paramNames[i], paramValues[i]);
            }
        }

        // 提取 targetType 和 targetId
        if (!ann.targetType().isEmpty()) {
            logEntry.setTargetType(ann.targetType());
        }
        if (paramValues.length > 0 && params.containsKey("id")) {
            logEntry.setTargetId(String.valueOf(params.get("id")));
        }

        // 构建详情 JSON
        Map<String, Object> detail = new HashMap<>();
        detail.put("params", params);
        detail.put("costMs", System.currentTimeMillis() - start);
        if (error != null) {
            detail.put("error", error.getMessage());
        }
        logEntry.setDetail(JSON.toJSONString(detail));

        // 记录 IP
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                String ip = request.getHeader("X-Forwarded-For");
                if (ip == null || ip.isEmpty()) {
                    ip = request.getRemoteAddr();
                }
                logEntry.setIp(ip);
            }
        } catch (Exception ignored) {}

        adminLogRepository.insert(logEntry);
    }
}
