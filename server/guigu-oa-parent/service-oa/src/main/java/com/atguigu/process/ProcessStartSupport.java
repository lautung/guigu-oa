package com.atguigu.process;

import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 办事发起：把 formValues JSON 转成 Flowable 变量，并生成 oa_process 标题/等待描述。
 */
public final class ProcessStartSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<Map<String, Object>>() {};

    private ProcessStartSupport() {
    }

    public static Map<String, Object> formDataVariables(String formValues) {
        JsonNode formData = formDataNode(formValues);
        if (formData == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> parsed = OBJECT_MAPPER.convertValue(formData, MAP_TYPE);
        return parsed == null ? Collections.emptyMap() : parsed;
    }

    /**
     * 空值仍允许（无表单的模板）；有内容时必须是带 {@code formData} 对象的合法 JSON。
     */
    public static boolean isValidFormValues(String formValues) {
        return !StringUtils.hasText(formValues) || formDataNode(formValues) != null;
    }

    /**
     * 已发布模板的动态必填（ADR 0035）：按 form-create 语义解释 formProps，
     * 返回为空必填字段的消息，规则顺序即消息顺序。没有就返回空列表。
     *
     * <p>只校验静态可判定的必填（server ⊆ client）：客户端不渲染的字段不校验，
     * 条件字段在服务端无法求值也跳过，保证服务端不会拒绝一个客户端同样放行的提交。
     * 空值定义与 form-create 的 is.empty 完全一致：缺失、null、空串、空数组算空，
     * 纯空白串、0、false、空对象不算空。formProps 解析失败按无约束处理（fail-open）。
     */
    public static List<String> missingRequiredMessages(String formProps, String formValues) {
        List<String> messages = new ArrayList<>();
        collectMissingRequired(rulesRoot(formProps), formDataNode(formValues), messages);
        return messages;
    }

    private static JsonNode rulesRoot(String formProps) {
        if (!StringUtils.hasText(formProps)) {
            return null;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(formProps);
            return root.isArray() ? root : null;
        } catch (Exception ignored) {
            // formProps 坏了按无约束处理：这种模板客户端也渲染不出来，用户到不了提交
            return null;
        }
    }

    private static void collectMissingRequired(JsonNode rules, JsonNode formData, List<String> messages) {
        if (rules == null || !rules.isArray()) {
            return;
        }
        for (JsonNode rule : rules) {
            if (!rule.isObject() || isSkippedRule(rule)) {
                continue;
            }
            String field = textOf(rule.get("field"));
            if (field != null && isRequired(rule)
                    && isEmptyValue(formData == null ? null : formData.get(field))) {
                messages.add(requiredMessage(rule, field));
            }
            collectMissingRequired(rule.get("children"), formData, messages);
        }
    }

    private static boolean isRequired(JsonNode rule) {
        JsonNode required = rule.get("$required");
        if (required != null) {
            if (required.isBoolean()) {
                return required.booleanValue();
            }
            // form-create：$required 为字符串时是必填，字符串本身作为校验消息
            return required.isTextual() && !required.asText().isEmpty();
        }
        JsonNode effect = rule.get("effect");
        return effect != null && effect.isObject()
                && effect.has("required") && effect.get("required").isBoolean()
                && effect.get("required").booleanValue();
    }

    /**
     * hidden 与 type=hidden 客户端不渲染、自然不校验；control 的条件在服务端无法求值，
     * 整体跳过——否则客户端按条件藏起来的字段会把用户死锁在发起页。display=false 客户端仍校验，这里也校验。
     */
    private static boolean isSkippedRule(JsonNode rule) {
        JsonNode hidden = rule.get("hidden");
        if (hidden != null && hidden.isBoolean() && hidden.booleanValue()) {
            return true;
        }
        if ("hidden".equals(textOf(rule.get("type")))) {
            return true;
        }
        JsonNode control = rule.get("control");
        return control != null && !control.isNull();
    }

    private static boolean isEmptyValue(JsonNode value) {
        if (value == null || value.isNull()) {
            return true;
        }
        if (value.isTextual()) {
            return value.asText().isEmpty();
        }
        return value.isArray() && value.isEmpty();
    }

    private static String requiredMessage(JsonNode rule, String field) {
        JsonNode required = rule.get("$required");
        if (required != null && required.isTextual() && !required.asText().isEmpty()) {
            return required.asText();
        }
        String title = textOf(rule.get("title"));
        return title != null ? title + "不能为空" : field + "不能为空";
    }

    private static String textOf(JsonNode node) {
        return node != null && node.isTextual() ? node.asText() : null;
    }

    private static JsonNode formDataNode(String formValues) {
        if (!StringUtils.hasText(formValues)) {
            return null;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(formValues);
            JsonNode formData = root.get("formData");
            return formData != null && formData.isObject() ? formData : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static Map<String, Object> engineVariables(String formValues) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("data", formDataVariables(formValues));
        return variables;
    }

    public static String title(String userName, String templateName) {
        return userName + "发起" + templateName + "申请";
    }

    public static String waitingDescription(List<String> approverNames) {
        if (CollectionUtils.isEmpty(approverNames)) {
            return "等待审批";
        }
        return "等待" + String.join(",", approverNames) + "审批";
    }
}
