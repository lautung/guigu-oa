package com.lautung.oa.audit.aspect;

import com.lautung.oa.audit.entity.SysOperLog;
import com.lautung.oa.audit.service.OperLogWriter;
import com.lautung.oa.audit.support.AuditText;
import com.lautung.oa.audit.support.OperParamSanitizer;
import com.lautung.oa.common.result.Result;
import com.lautung.oa.framework.operlog.OperLog;
import com.lautung.oa.security.custom.LoginUserInfoHelper;
import com.lautung.oa.security.login.ClientAddress;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * {@link OperLog} 的采集切面（ADR 0054）。
 *
 * <p>只包住真正进入方法体的调用：{@code @Valid} 失败发生在 MVC 参数解析期、{@code @PreAuthorize}
 * 的 205 发生在方法安全拦截器，二者都在本切面之前。为此 {@code @Order} 必须大于
 * {@code AuthorizationInterceptorsOrder.PRE_AUTHORIZE.getOrder()}（Spring Security 7 为 200，
 * 值越小越外层），否则被拒绝的调用也会留下审计行。
 *
 * <p>成功判定看信封：正常返回且 {@code Result.code == 200} 为成功；非 200 的 Result 或抛异常
 * 为失败，失败行同样记 {@code oper_param}（排障要看到尝试了什么）。同步落库，写失败只 WARN。
 */
@Aspect
@Component
@Order(OperLogAspect.ORDER)
public class OperLogAspect {

    /** 大于方法安全的 200（PRE_AUTHORIZE），保证切面在 gate 之内。 */
    public static final int ORDER = 1000;

    private static final Logger log = LoggerFactory.getLogger(OperLogAspect.class);

    private static final int SUCCESS_CODE = 200;

    private static final int TITLE_LIMIT = 100;

    private static final int METHOD_LIMIT = 200;

    private static final int URL_LIMIT = 255;

    private final OperLogWriter writer;

    private final OperParamSanitizer sanitizer;

    public OperLogAspect(OperLogWriter writer, OperParamSanitizer sanitizer) {
        this.writer = writer;
        this.sanitizer = sanitizer;
    }

    @Around("@annotation(operLog)")
    public Object record(ProceedingJoinPoint joinPoint, OperLog operLog) throws Throwable {
        long startedAt = System.currentTimeMillis();
        SysOperLog entry = newEntry(joinPoint, operLog);

        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Throwable error) {
            entry.setStatus(SysOperLog.STATUS_FAILURE);
            entry.setErrorMsg(AuditText.truncate(describe(error), OperParamSanitizer.ERROR_MSG_LIMIT));
            entry.setCostTime(System.currentTimeMillis() - startedAt);
            persist(entry);
            throw error;
        }

        entry.setCostTime(System.currentTimeMillis() - startedAt);
        judge(entry, result);
        persist(entry);
        return result;
    }

    private SysOperLog newEntry(ProceedingJoinPoint joinPoint, OperLog operLog) {
        SysOperLog entry = new SysOperLog();
        entry.setTitle(AuditText.truncate(operLog.title(), TITLE_LIMIT));
        entry.setBusinessType(operLog.businessType().getCode());
        entry.setMethod(AuditText.truncate(
                joinPoint.getSignature().getDeclaringTypeName() + "#" + joinPoint.getSignature().getName(),
                METHOD_LIMIT));
        entry.setOperParam(sanitizer.sanitize(joinPoint.getArgs()));
        entry.setOperId(LoginUserInfoHelper.getUserId());
        entry.setOperName(LoginUserInfoHelper.getUsername());
        fillRequest(entry);
        return entry;
    }

    private void fillRequest(SysOperLog entry) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return;
        }
        HttpServletRequest request = attributes.getRequest();
        entry.setRequestMethod(request.getMethod());
        entry.setOperUrl(AuditText.truncate(request.getRequestURI(), URL_LIMIT));
        entry.setOperIp(ClientAddress.of(request));
    }

    private void judge(SysOperLog entry, Object result) {
        if (result instanceof Result<?> envelope) {
            Integer code = envelope.getCode();
            entry.setResultCode(code);
            entry.setResultMessage(AuditText.truncate(
                    envelope.getMessage(), OperParamSanitizer.RESULT_MESSAGE_LIMIT));
            entry.setStatus(code != null && code == SUCCESS_CODE
                    ? SysOperLog.STATUS_SUCCESS
                    : SysOperLog.STATUS_FAILURE);
            return;
        }
        // 带注解的方法目前都返回 Result；非 Result 的正常返回按成功记，不臆造业务码。
        entry.setStatus(SysOperLog.STATUS_SUCCESS);
    }

    private void persist(SysOperLog entry) {
        try {
            writer.write(entry);
        } catch (RuntimeException e) {
            // 落库是旁路：写失败不能把已成功的响应变成异常（ADR 0054）。
            log.warn("Operation log persistence skipped for {}", entry.getMethod(), e);
        }
    }

    private static String describe(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank()
                ? error.getClass().getSimpleName()
                : error.getClass().getSimpleName() + ": " + message;
    }
}
