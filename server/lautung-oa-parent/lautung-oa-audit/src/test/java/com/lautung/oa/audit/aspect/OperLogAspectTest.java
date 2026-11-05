package com.lautung.oa.audit.aspect;

import com.lautung.oa.audit.entity.SysOperLog;
import com.lautung.oa.audit.mapper.AuditOperatorMapper;
import com.lautung.oa.audit.mapper.OperatorSnapshot;
import com.lautung.oa.audit.mapper.SysOperLogMapper;
import com.lautung.oa.audit.service.OperLogWriter;
import com.lautung.oa.audit.support.OperParamSanitizer;
import com.lautung.oa.common.result.Result;
import com.lautung.oa.framework.operlog.BusinessType;
import com.lautung.oa.framework.operlog.OperLog;
import com.lautung.oa.security.address.TrustedProxyClientAddressResolver;
import com.lautung.oa.security.custom.LoginUserInfoHelper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.annotation.Order;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authorization.method.AuthorizationInterceptorsOrder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OperLogAspectTest {

    private SysOperLogMapper operLogMapper;

    private OperLogWriter writer;

    private OperLogAspect aspect;

    private ProceedingJoinPoint joinPoint;

    private OperLog annotation;

    @BeforeEach
    void setUp() throws Exception {
        operLogMapper = mock(SysOperLogMapper.class);
        AuditOperatorMapper operatorMapper = mock(AuditOperatorMapper.class);
        OperatorSnapshot snapshot = new OperatorSnapshot();
        snapshot.setUserName("管理员");
        snapshot.setDeptId(77L);
        snapshot.setDeptName("审计部");
        when(operatorMapper.findOperatorSnapshot(1L)).thenReturn(snapshot);

        writer = new OperLogWriter(operLogMapper, operatorMapper);
        // 对端 127.0.0.1 是受信代理，X-Real-IP 才是解析后的来源（#262）。
        aspect = new OperLogAspect(writer, new OperParamSanitizer(new ObjectMapper()),
                new TrustedProxyClientAddressResolver("127.0.0.1/32"));

        joinPoint = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);
        when(signature.getDeclaringTypeName()).thenReturn("com.example.SampleController");
        when(signature.getName()).thenReturn("save");
        when(joinPoint.getSignature()).thenReturn(signature);

        annotation = SampleController.class.getDeclaredMethod("save").getAnnotation(OperLog.class);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin/system/sysUser/save");
        request.addHeader("X-Real-IP", "10.1.2.3");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        LoginUserInfoHelper.setUserId(1L);
        LoginUserInfoHelper.setUsername("admin");
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        LoginUserInfoHelper.removeUserId();
        LoginUserInfoHelper.removeUsername();
    }

    @Test
    void recordsASuccessfulCallWithRequestAndOperatorSnapshot() throws Throwable {
        when(joinPoint.getArgs()).thenReturn(new Object[]{
                Map.of("username", "alice", "password", "111111", "phone", "13800131234")
        });
        Result<Object> ok = Result.ok();
        when(joinPoint.proceed()).thenReturn(ok);

        Object result = aspect.record(joinPoint, annotation);

        assertSame(ok, result);
        SysOperLog row = capturedRow();
        assertEquals("用户管理·新增用户", row.getTitle());
        assertEquals(BusinessType.CREATE.getCode(), row.getBusinessType());
        assertEquals("com.example.SampleController#save", row.getMethod());
        assertEquals("POST", row.getRequestMethod());
        assertEquals("/admin/system/sysUser/save", row.getOperUrl());
        assertEquals("10.1.2.3", row.getOperIp());
        assertEquals(1L, row.getOperId());
        assertEquals("管理员", row.getOperName());
        assertEquals(77L, row.getDeptId());
        assertEquals("审计部", row.getDeptName());
        assertEquals(200, row.getResultCode());
        assertEquals("成功", row.getResultMessage());
        assertEquals(SysOperLog.STATUS_SUCCESS, row.getStatus());
        assertNull(row.getErrorMsg());
        assertNotNull(row.getCostTime());
        assertTrue(row.getCostTime() >= 0);
        assertTrue(row.getOperParam().contains("\"password\":\"***\""), row.getOperParam());
        assertFalse(row.getOperParam().contains("111111"), row.getOperParam());
        assertTrue(row.getOperParam().contains("138****1234"), row.getOperParam());
    }

    @Test
    void recordsAFailureRowForANon200ResultAndKeepsTheAttemptParameter() throws Throwable {
        when(joinPoint.getArgs()).thenReturn(new Object[]{
                Map.of("username", "alice", "password", "111111")
        });
        Result<Object> failed = Result.fail().code(201).message("用户名已存在");
        when(joinPoint.proceed()).thenReturn(failed);

        Object result = aspect.record(joinPoint, annotation);

        assertSame(failed, result);
        SysOperLog row = capturedRow();
        assertEquals(SysOperLog.STATUS_FAILURE, row.getStatus());
        assertEquals(201, row.getResultCode());
        assertEquals("用户名已存在", row.getResultMessage());
        assertEquals("用户管理·新增用户", row.getTitle());
        assertTrue(row.getOperParam().contains("\"password\":\"***\""), row.getOperParam());
        assertFalse(row.getOperParam().contains("111111"), row.getOperParam());
    }

    @Test
    void recordsAFailureRowAndRethrowsWhenTheMethodThrows() throws Throwable {
        when(joinPoint.getArgs()).thenReturn(new Object[]{Map.of("password", "111111")});
        when(joinPoint.proceed()).thenThrow(new IllegalStateException("boom"));

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> aspect.record(joinPoint, annotation));

        assertEquals("boom", thrown.getMessage());
        SysOperLog row = capturedRow();
        assertEquals(SysOperLog.STATUS_FAILURE, row.getStatus());
        assertNull(row.getResultCode());
        assertTrue(row.getErrorMsg().contains("IllegalStateException"), row.getErrorMsg());
        assertTrue(row.getErrorMsg().contains("boom"), row.getErrorMsg());
        assertTrue(row.getOperParam().contains("\"password\":\"***\""), row.getOperParam());
    }

    @Test
    void aWriteFailureNeverChangesTheResponse() throws Throwable {
        OperLogWriter failingWriter = mock(OperLogWriter.class);
        doThrow(new RuntimeException("database down")).when(failingWriter).write(any());
        OperLogAspect failingAspect = new OperLogAspect(failingWriter,
                new OperParamSanitizer(new ObjectMapper()),
                new TrustedProxyClientAddressResolver("127.0.0.1/32"));
        Result<Object> ok = Result.ok();
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        when(joinPoint.proceed()).thenReturn(ok);

        Object result = failingAspect.record(joinPoint, annotation);

        assertSame(ok, result);
    }

    /**
     * 顺序即语义：{@code @PreAuthorize} 的 205 在 order 200 处拒绝，切面必须更靠内（值更大），
     * 否则被拒绝的调用也会留下审计行。合同测试另有一条真实 205 请求零行的用例。
     */
    @Test
    void runsInsideMethodSecurity() {
        assertTrue(OperLogAspect.ORDER > AuthorizationInterceptorsOrder.PRE_AUTHORIZE.getOrder(),
                "aspect order must be larger (inner) than PRE_AUTHORIZE");
        assertEquals(OperLogAspect.ORDER, OperLogAspect.class.getAnnotation(Order.class).value());
    }

    private SysOperLog capturedRow() {
        ArgumentCaptor<SysOperLog> captor = ArgumentCaptor.forClass(SysOperLog.class);
        verify(operLogMapper).insert(captor.capture());
        return captor.getValue();
    }

    static class SampleController {

        @OperLog(title = "用户管理·新增用户", businessType = BusinessType.CREATE)
        public void save() {
        }
    }
}
