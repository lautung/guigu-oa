package com.lautung.oa.audit.support;

import com.lautung.oa.framework.observability.PhoneNumberMasker;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.springframework.validation.BindingResult;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 落库参数的脱敏与截断（ADR 0054）。
 *
 * <p>脱敏是字段级的，与 ADR 0039 的 stdout 接缝（{@link PhoneNumberMasker} 的结构化日志
 * customizer）互不影响：这里先按字段名黑名单（大小写不敏感）递归替换为 {@code "***"}，
 * 再把整串 JSON 过一遍 {@link PhoneNumberMasker}，最后截断到
 * {@link #OPER_PARAM_LIMIT}。传输层容器（request/response/MultipartFile/BindingResult/Principal）
 * 不参与序列化——它们既不是请求体，也会把 header 或临时文件带进日志。
 */
public class OperParamSanitizer {

    public static final int OPER_PARAM_LIMIT = 2000;

    public static final int RESULT_MESSAGE_LIMIT = 500;

    public static final int ERROR_MSG_LIMIT = 2000;

    static final String MASKED = "***";

    static final Set<String> SENSITIVE_FIELD_NAMES = Set.of(
            "password",
            "oldpassword",
            "newpassword",
            "confirmpassword",
            "initialpassword",
            "token",
            "refreshtoken",
            "secret",
            "authorization");

    private static final String UNSERIALIZABLE = "<参数无法序列化>";

    private final ObjectMapper objectMapper;

    public OperParamSanitizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 把控制器实参序列化成脱敏、打码并截断后的 JSON 数组。 */
    public String sanitize(Object[] args) {
        return sanitize(args, null);
    }

    /**
     * 带参数名时，裸标量（如 multipart 的 {@code initialPassword}）按参数名打码：
     * 字段级黑名单只覆盖对象字段，独立 String 参数需要这一层才不落明文。
     */
    public String sanitize(Object[] args, String[] parameterNames) {
        ArrayNode serialized = objectMapper.createArrayNode();
        if (args != null) {
            for (int index = 0; index < args.length; index++) {
                Object arg = args[index];
                if (skip(arg)) {
                    continue;
                }
                if (isSensitiveParameter(parameterNames, index)) {
                    serialized.add(MASKED);
                    continue;
                }
                try {
                    serialized.add(objectMapper.valueToTree(arg));
                } catch (RuntimeException e) {
                    // 单个参数序列化失败不拖垮整条日志；不落它的 toString（可能含密码）。
                    serialized.add(UNSERIALIZABLE);
                }
            }
        }
        maskSensitiveFields(serialized);
        String json;
        try {
            json = objectMapper.writeValueAsString(serialized);
        } catch (RuntimeException e) {
            return UNSERIALIZABLE;
        }
        return AuditText.truncate(PhoneNumberMasker.mask(json), OPER_PARAM_LIMIT);
    }

    private static boolean isSensitiveParameter(String[] parameterNames, int index) {
        return parameterNames != null
                && index < parameterNames.length
                && parameterNames[index] != null
                && SENSITIVE_FIELD_NAMES.contains(parameterNames[index].toLowerCase(Locale.ROOT));
    }

    private static boolean skip(Object arg) {
        return arg == null
                || arg instanceof ServletRequest
                || arg instanceof ServletResponse
                || arg instanceof MultipartFile
                || arg instanceof BindingResult
                || arg instanceof Principal;
    }

    private void maskSensitiveFields(JsonNode node) {
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            List<String> fieldNames = new ArrayList<>();
            object.properties().forEach(entry -> fieldNames.add(entry.getKey()));
            for (String fieldName : fieldNames) {
                if (SENSITIVE_FIELD_NAMES.contains(fieldName.toLowerCase(Locale.ROOT))) {
                    object.put(fieldName, MASKED);
                } else {
                    maskSensitiveFields(object.get(fieldName));
                }
            }
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                maskSensitiveFields(item);
            }
        }
    }
}
