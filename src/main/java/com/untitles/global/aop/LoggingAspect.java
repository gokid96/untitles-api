package com.untitles.global.aop;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
@Slf4j
public class LoggingAspect {

    // 컨트롤러 로깅
    @Around("execution(* com.untitles.domain.*.controller..*(..))")
    public Object logController(ProceedingJoinPoint joinPoint) throws Throwable {

        HttpServletRequest request = getCurrentRequest();
        if (request == null) {
            return joinPoint.proceed();
        }
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String controllerMethod = joinPoint.getSignature().toShortString();

        long start = System.currentTimeMillis();

        try {
            Object result = joinPoint.proceed();
            long elapsed = System.currentTimeMillis() - start;

            log.info("[{}] {} - {} - {}ms", method, uri, controllerMethod, elapsed);
            return result;

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;

            log.warn("[{}] {} - {} - {}ms - {}: {}",
                    method, uri, controllerMethod, elapsed,
                    e.getClass().getSimpleName(), e.getMessage());
            throw e;
        }
    }

    // 서비스 레이어 성능 측정 (100ms 이상만 로깅)
    @Around("execution(* com.untitles.domain.*.service..*(..))")
    public Object logSlowService(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        
        try {
            return joinPoint.proceed();
        } finally {
            long elapsed = System.currentTimeMillis() - start;
            if (elapsed > 100) {  // 100ms 이상만 경고
                log.warn("[SLOW SERVICE] {}.{} - {}ms",
                        joinPoint.getTarget().getClass().getSimpleName(),
                        joinPoint.getSignature().getName(),
                        elapsed);
            }
        }
    }

    // Repository 레이어 성능 측정 (50ms 이상만 로깅)
    @Around("execution(* com.untitles.domain.*.repository..*(..))")
    public Object logSlowRepository(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        
        try {
            return joinPoint.proceed();
        } finally {
            long elapsed = System.currentTimeMillis() - start;
            if (elapsed > 50) {  // 50ms 이상만 경고 (DB 쿼리는 더 민감하게)
                log.warn("[SLOW QUERY] {}.{} - {}ms",
                        joinPoint.getTarget().getClass().getSimpleName(),
                        joinPoint.getSignature().getName(),
                        elapsed);
            }
        }
    }

    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return null;
        }
        return attrs.getRequest();
    }
}