package com.lautung.oa.process.publication;

import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.UserTask;
import org.springframework.util.StringUtils;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 上传流程 zip 里的 BPMN userTask 列表解析（ADR 0069，#285）。纯函数 module：
 * 给配置页的节点清单与发布校验「每节点至少一个来源（规则或 BPMN assignee）」共用。
 *
 * <p>与部署器同一套识别口径（.bpmn20.xml / .bpmn / .xml 条目，Flowable 转换器）：
 * 一个 zip 多个 process 时优先取 key 命中的那个，没有命中取第一个可解析的。
 * 解析不出 BPMN 抛 {@link IllegalArgumentException}——上传可以保存，发布时据此拒绝。
 */
public final class BpmnUserTasks {

    public static final String NO_BPMN_MESSAGE = "流程 zip 里没有可解析的流程图";

    private BpmnUserTasks() {
    }

    /** BPMN userTask 快照：节点 id、节点名与写死的 assignee（可空）。 */
    public record UserTaskRef(String key, String name, String assignee) {
    }

    public static List<UserTaskRef> parse(byte[] zipBytes, String processDefinitionKey) {
        if (zipBytes == null || zipBytes.length == 0) {
            throw new IllegalArgumentException(NO_BPMN_MESSAGE);
        }
        List<UserTaskRef> fallback = null;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!looksLikeBpmn(entry.getName())) {
                    continue;
                }
                List<Process> processes;
                try {
                    processes = parseProcesses(zip.readAllBytes());
                } catch (IllegalArgumentException unparsableEntry) {
                    continue;
                }
                if (processes == null) {
                    continue;
                }
                Process matched = processForKey(processes, processDefinitionKey);
                List<UserTaskRef> tasks = userTasksOf(matched);
                if (matched != processes.get(0)) {
                    return tasks;
                }
                if (fallback == null) {
                    fallback = tasks;
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException(NO_BPMN_MESSAGE, e);
        }
        if (fallback == null) {
            throw new IllegalArgumentException(NO_BPMN_MESSAGE);
        }
        return fallback;
    }

    /** key 命中的 process；key 为空或未命中时取文档顺序里的第一个。 */
    private static Process processForKey(List<Process> processes, String processDefinitionKey) {
        if (!StringUtils.hasText(processDefinitionKey)) {
            return processes.get(0);
        }
        return processes.stream()
                .filter(process -> processDefinitionKey.equals(process.getId()))
                .findFirst()
                .orElse(processes.get(0));
    }

    private static boolean looksLikeBpmn(String entryName) {
        String lower = entryName.toLowerCase();
        return lower.endsWith(".bpmn20.xml") || lower.endsWith(".bpmn") || lower.endsWith(".xml");
    }

    private static List<Process> parseProcesses(byte[] xmlBytes) {
        try {
            XMLInputFactory factory = XMLInputFactory.newInstance();
            // 上传件是外部输入：禁 DTD 与外部实体，不给 XXE 留口。
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
            XMLStreamReader reader = factory.createXMLStreamReader(new ByteArrayInputStream(xmlBytes));
            try {
                BpmnModel model = new BpmnXMLConverter().convertToBpmnModel(reader);
                List<Process> processes = model.getProcesses();
                return processes == null || processes.isEmpty() ? null : processes;
            } finally {
                reader.close();
            }
        } catch (XMLStreamException e) {
            throw new IllegalArgumentException(NO_BPMN_MESSAGE, e);
        } catch (org.flowable.bpmn.exceptions.XMLException e) {
            throw new IllegalArgumentException(NO_BPMN_MESSAGE, e);
        }
    }

    private static List<UserTaskRef> userTasksOf(Process process) {
        List<UserTaskRef> refs = new ArrayList<>();
        for (UserTask task : process.findFlowElementsOfType(UserTask.class)) {
            refs.add(new UserTaskRef(task.getId(), task.getName(), emptyToNull(task.getAssignee())));
        }
        return List.copyOf(refs);
    }

    private static String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }
}
