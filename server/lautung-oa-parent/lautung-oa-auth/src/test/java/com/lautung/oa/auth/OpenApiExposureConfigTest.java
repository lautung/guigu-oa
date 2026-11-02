package com.lautung.oa.auth;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 接口文档只对本地 dev 开放（#140）。Compose 演示栈以 {@code dev,demo} 启动，
 * demo 必须覆盖 dev 的开启，否则匿名面又能枚举全部端点、参数与数据模型。
 *
 * <p>这里直接解析各 profile 的 YAML 并按加载顺序合并，断言的是「哪条生效路径打开文档」，
 * 而不是某个键是否写了字：springdoc 自己的默认值是开启，漏写就等于暴露。运行时是否真的
 * 不再服务文档由 {@code scripts/security-scan.mjs} 的 SEC-LEAK-03/04 与
 * {@code scripts/compose-baseline-check.sh} 在真实栈上验证。</p>
 */
public class OpenApiExposureConfigTest {

    static Stream<Arguments> profileCombinations() {
        return Stream.of(
                Arguments.of("基础配置（application.yml）关闭", false, List.of()),
                Arguments.of("dev 打开两份文档供本地开发", true, List.of("dev")),
                Arguments.of("演示栈 dev,demo：demo 覆盖 dev", false, List.of("dev", "demo")),
                Arguments.of("prod 单 profile 关闭", false, List.of("prod")),
                Arguments.of("dev,prod 以 prod 收尾时仍关闭", false, List.of("dev", "prod")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("profileCombinations")
    public void documentsFollowTheProfileContract(
            String caseName,
            boolean expected,
            List<String> profiles
    ) {
        String[] activeProfiles = profiles.toArray(String[]::new);
        assertEquals(expected, documentEnabled("api-docs", activeProfiles), "api-docs：" + caseName);
        assertEquals(expected, documentEnabled("swagger-ui", activeProfiles), "swagger-ui：" + caseName);
    }

    /**
     * 未配置时按 springdoc 的默认值视为开启：这样删掉 base 的关闭开关、或新增的 profile
     * 没继承到关闭，都会让上面的断言失败。
     */
    private boolean documentEnabled(String document, String... profiles) {
        Map<String, Object> springdoc = nestedMap(effectiveConfig(profiles), "springdoc");
        Object enabled = nestedMap(springdoc, document).get("enabled");
        return !(enabled instanceof Boolean flag) || flag;
    }

    private Map<String, Object> effectiveConfig(String... profiles) {
        Map<String, Object> merged = new LinkedHashMap<>();
        merge(merged, load("application.yml"));
        for (String profile : profiles) {
            merge(merged, load("application-" + profile + ".yml"));
        }
        return merged;
    }

    private Map<String, Object> load(String resource) {
        try (InputStream input = new ClassPathResource(resource).getInputStream()) {
            Map<String, Object> loaded = new Yaml().load(input);
            return loaded == null ? Map.of() : loaded;
        } catch (IOException exception) {
            throw new UncheckedIOException("读取配置失败：" + resource, exception);
        }
    }

    @SuppressWarnings("unchecked")
    private void merge(Map<String, Object> target, Map<String, Object> source) {
        source.forEach((key, value) -> {
            Object existing = target.get(key);
            if (existing instanceof Map && value instanceof Map) {
                merge((Map<String, Object>) existing, (Map<String, Object>) value);
            } else {
                target.put(key, value);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> nestedMap(Map<String, Object> config, String key) {
        Object value = config.get(key);
        return value instanceof Map ? (Map<String, Object>) value : Map.of();
    }
}
