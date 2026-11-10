package com.lautung.oa.process.publication;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * {@code oa-approvers.json} sidecar（ADR 0069，#285）：发布时把审批人规则与上传 zip
 * 合成一个 content-addressed 发布产物，TASK_CREATED 监听器按部署里冻结的这份 sidecar
 * 解析，不读「当前模板」。
 *
 * <p>纯函数 module。合成件条目时间戳固定：同一份上传 zip + 同一份规则永远得到同一个
 * 内容摘要（content-addressed 复用同一文件，不制造第二份产物）。
 */
public final class ApproverSidecar {

    public static final String SIDECAR_ENTRY = "oa-approvers.json";

    /** 冻结的单节点规则：节点 id、来源类型、目标 id 与 BPMN 里的步序（1 起始）。 */
    public record SidecarRule(String taskDefinitionKey, String sourceType, Long targetId, int order) {
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long FIXED_ENTRY_TIME = java.sql.Timestamp.valueOf("2026-01-01 00:00:00").getTime();

    private ApproverSidecar() {
    }

    /**
     * 合成发布产物：原 zip 的全部条目原样拷入（同名 sidecar 条目被 ours 取代），
     * 末尾追加 {@link #SIDECAR_ENTRY}。
     */
    public static byte[] synthesize(byte[] originalZip, String processDefinitionKey, List<SidecarRule> rules)
            throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipInputStream source = new ZipInputStream(new ByteArrayInputStream(originalZip), StandardCharsets.UTF_8);
             ZipOutputStream target = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = source.getNextEntry()) != null) {
                if (SIDECAR_ENTRY.equals(entry.getName())) {
                    continue;
                }
                ZipEntry copy = new ZipEntry(entry.getName());
                copy.setTime(FIXED_ENTRY_TIME);
                target.putNextEntry(copy);
                source.transferTo(target);
                target.closeEntry();
            }
            ZipEntry sidecar = new ZipEntry(SIDECAR_ENTRY);
            sidecar.setTime(FIXED_ENTRY_TIME);
            target.putNextEntry(sidecar);
            target.write(toJson(rules, processDefinitionKey));
            target.closeEntry();
        }
        return bytes.toByteArray();
    }

    public static byte[] toJson(List<SidecarRule> rules, String processDefinitionKey) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", 1);
        root.put("processDefinitionKey", processDefinitionKey);
        List<Map<String, Object>> lines = new ArrayList<>();
        for (SidecarRule rule : rules) {
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("taskDefinitionKey", rule.taskDefinitionKey());
            line.put("sourceType", rule.sourceType());
            line.put("targetId", rule.targetId());
            line.put("order", rule.order());
            lines.add(line);
        }
        root.put("rules", lines);
        return MAPPER.writeValueAsBytes(root);
    }

    /**
     * 读冻结的 sidecar。结构坏掉的 sidecar 属于发布产物被篡改：抛 IllegalArgumentException
     * 让监听器把它当解析失败处理（显式失败，不静默跳过）。
     */
    public static List<SidecarRule> parse(InputStream content) {
        try {
            JsonNode root = MAPPER.readTree(content);
            JsonNode rules = root == null ? null : root.get("rules");
            if (rules == null || !rules.isArray()) {
                throw new IllegalArgumentException("oa-approvers.json 缺少 rules 数组");
            }
            List<SidecarRule> parsed = new ArrayList<>();
            for (JsonNode line : rules) {
                JsonNode key = line.get("taskDefinitionKey");
                JsonNode sourceType = line.get("sourceType");
                if (key == null || !key.isString() || sourceType == null || !sourceType.isString()) {
                    throw new IllegalArgumentException("oa-approvers.json 规则行不完整");
                }
                JsonNode targetId = line.get("targetId");
                JsonNode order = line.get("order");
                parsed.add(new SidecarRule(
                        key.asText(),
                        sourceType.asText(),
                        targetId == null || targetId.isNull() ? null : targetId.asLong(),
                        order == null || order.isNull() ? 0 : order.asInt()));
            }
            return List.copyOf(parsed);
        } catch (Exception e) {
            throw new IllegalArgumentException("oa-approvers.json 无法读取", e);
        }
    }
}
