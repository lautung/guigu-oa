package com.lautung.oa.audit;

import com.lautung.oa.framework.operlog.OperLog;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 采集守卫（ADR 0054）：{@code /admin/system/*} 与 {@code /admin/monitor/*} 下的后台 controller
 * 写方法必须挂 {@link OperLog}，漏挂即红。{@code IndexController} 是登录/续期/退出的传输端点，
 * 不是操作日志材料，显式排除。
 *
 * <p>扫描整个 {@code com.lautung.oa} 的 {@code @RestController}（含依赖 jar），不是维护一份
 * 名单：新后台模块落地时，漏注解会在本用例里指名到 controller#method。
 */
class OperLogCoverageTest {

    private static final List<String> BACKOFFICE_PREFIXES = List.of("/admin/system", "/admin/monitor");

    private static final String EXCLUDED_CONTROLLER = "IndexController";

    @Test
    void everyBackofficeWriteMethodDeclaresOperLog() throws ClassNotFoundException {
        List<String> missing = new ArrayList<>();
        int scannedWriteMethods = 0;

        for (Class<?> controller : backofficeControllers()) {
            if (EXCLUDED_CONTROLLER.equals(controller.getSimpleName())) {
                continue;
            }
            for (Method method : controller.getDeclaredMethods()) {
                if (!isWriteMapping(method)) {
                    continue;
                }
                scannedWriteMethods++;
                if (!method.isAnnotationPresent(OperLog.class)) {
                    missing.add(controller.getSimpleName() + "#" + method.getName());
                }
            }
        }

        assertTrue(scannedWriteMethods >= 14,
                "后台写方法扫描结果异常，只看到 " + scannedWriteMethods + " 个——守卫没有落在路径上");
        assertTrue(missing.isEmpty(), "后台写方法缺少 @OperLog: " + missing);
    }

    private static List<Class<?>> backofficeControllers() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class, true, true));
        Set<BeanDefinition> candidates = scanner.findCandidateComponents("com.lautung.oa");

        List<Class<?>> controllers = new ArrayList<>();
        for (BeanDefinition candidate : candidates) {
            Class<?> type = Class.forName(candidate.getBeanClassName());
            if (isBackofficeController(type)) {
                controllers.add(type);
            }
        }
        return controllers;
    }

    private static boolean isBackofficeController(Class<?> controller) {
        RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
        if (mapping == null) {
            return false;
        }
        return Arrays.stream(mapping.path())
                .anyMatch(path -> BACKOFFICE_PREFIXES.stream()
                        .anyMatch(prefix -> path.equals(prefix) || path.startsWith(prefix + "/")));
    }

    private static boolean isWriteMapping(Method method) {
        RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
        if (mapping == null) {
            return false;
        }
        return Arrays.stream(mapping.method()).anyMatch(requestMethod ->
                requestMethod == RequestMethod.POST
                        || requestMethod == RequestMethod.PUT
                        || requestMethod == RequestMethod.DELETE);
    }
}
