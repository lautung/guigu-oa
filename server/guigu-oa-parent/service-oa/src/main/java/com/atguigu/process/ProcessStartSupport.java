package com.atguigu.process;

import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

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
