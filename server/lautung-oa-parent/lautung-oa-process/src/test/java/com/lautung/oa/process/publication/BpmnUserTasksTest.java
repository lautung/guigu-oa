package com.lautung.oa.process.publication;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 上传模板的 BPMN userTask 列表解析（ADR 0069，#285）：配置页的节点清单与
 * 发布校验「每节点至少一个来源」都靠它；解析不出 BPMN 的 zip 允许保存、发布时拒绝。
 */
class BpmnUserTasksTest {

    @Test
    void parsesUserTasksInDocumentOrderWithAssignee() throws IOException {
        byte[] zip = zip("two-step.bpmn20.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:activiti="http://activiti.org/bpmn"
                             targetNamespace="http://bpmn.io/schema/bpmn">
                  <process id="approvalTwoStep" isExecutable="true">
                    <startEvent id="start"/>
                    <userTask id="first" name="一审" activiti:assignee="zhangsan"/>
                    <userTask id="second" name="二审"/>
                    <endEvent id="end"/>
                    <sequenceFlow id="flow1" sourceRef="start" targetRef="first"/>
                    <sequenceFlow id="flow2" sourceRef="first" targetRef="second"/>
                    <sequenceFlow id="flow3" sourceRef="second" targetRef="end"/>
                  </process>
                </definitions>
                """);

        List<BpmnUserTasks.UserTaskRef> tasks = BpmnUserTasks.parse(zip, "approvalTwoStep");

        assertEquals(2, tasks.size());
        assertEquals(new BpmnUserTasks.UserTaskRef("first", "一审", "zhangsan"), tasks.get(0));
        assertEquals(new BpmnUserTasks.UserTaskRef("second", "二审", null), tasks.get(1));
    }

    @Test
    void parsesFlowableNamespaceAssigneeAndPrefersTheProcessMatchingTheKey() throws IOException {
        byte[] zip = zip("mixed.bpmn20.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn"
                             targetNamespace="http://bpmn.io/schema/bpmn">
                  <process id="someOther" isExecutable="true">
                    <userTask id="noise" name="噪声节点"/>
                  </process>
                  <process id="target" isExecutable="true">
                    <userTask id="bobReview" name="鲍勃审批" flowable:assignee="bob"/>
                  </process>
                </definitions>
                """);

        List<BpmnUserTasks.UserTaskRef> tasks = BpmnUserTasks.parse(zip, "target");

        assertEquals(1, tasks.size());
        assertEquals("bobReview", tasks.get(0).key());
        assertEquals("bob", tasks.get(0).assignee());
    }

    @Test
    void zipWithoutBpmnEntriesThrows() throws IOException {
        byte[] zip = zip("readme.txt", "不是流程图");

        IllegalArgumentException thrown =
                assertThrows(IllegalArgumentException.class, () -> BpmnUserTasks.parse(zip, "anyKey"));
        assertEquals("流程 zip 里没有可解析的流程图", thrown.getMessage());
    }

    @Test
    void corruptBpmnEntryThrows() throws IOException {
        byte[] zip = zip("broken.bpmn20.xml", "<definitions>不完整");

        assertThrows(IllegalArgumentException.class, () -> BpmnUserTasks.parse(zip, "anyKey"));
    }

    @Test
    void nullOrEmptyZipThrows() {
        assertThrows(IllegalArgumentException.class, () -> BpmnUserTasks.parse(null, "anyKey"));
        assertThrows(IllegalArgumentException.class, () -> BpmnUserTasks.parse(new byte[0], "anyKey"));
        assertTrue(true);
    }

    private static byte[] zip(String entryName, String content) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry(entryName));
            zip.write(content.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }
}
