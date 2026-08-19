package com.atguigu.process;

import com.atguigu.model.process.Process;
import com.atguigu.model.process.ProcessRecord;
import com.atguigu.model.process.ProcessTemplate;
import com.atguigu.vo.process.ProcessVo;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProcessApprovalSupportTest {

    @Test
    public void pendingVoCopiesProcessFieldsAndKeepsEngineTaskId() {
        Process process = new Process();
        process.setId(12L);
        process.setTitle("张三发起请假申请申请");
        process.setStatus(1);
        process.setFormValues("{\"formShowData\":{\"请假天数\":\"3\"}}");
        process.setProcessInstanceId("proc-1");
        Date createTime = new Date(1_724_000_000_000L);
        process.setCreateTime(createTime);

        ProcessVo vo = ProcessApprovalSupport.toPendingVo(process, "task-9");

        assertEquals(12L, vo.getId());
        assertEquals("张三发起请假申请申请", vo.getTitle());
        assertEquals(1, vo.getStatus());
        assertEquals("{\"formShowData\":{\"请假天数\":\"3\"}}", vo.getFormValues());
        assertEquals("proc-1", vo.getProcessInstanceId());
        assertEquals(createTime, vo.getCreateTime());
        assertEquals("task-9", vo.getTaskId());
    }

    @Test
    public void processedVoCopiesProcessFieldsAndForcesTaskIdZero() {
        Process process = new Process();
        process.setId(12L);
        process.setTitle("张三发起请假申请申请");
        process.setStatus(2);
        process.setFormValues("{\"formShowData\":{\"请假天数\":\"3\"}}");
        process.setProcessInstanceId("proc-1");

        ProcessVo vo = ProcessApprovalSupport.toProcessedVo(process);

        assertEquals(12L, vo.getId());
        assertEquals("张三发起请假申请申请", vo.getTitle());
        assertEquals(2, vo.getStatus());
        assertEquals("{\"formShowData\":{\"请假天数\":\"3\"}}", vo.getFormValues());
        assertEquals("proc-1", vo.getProcessInstanceId());
        assertEquals("0", vo.getTaskId());
    }

    @Test
    public void recordDescriptionUsesPassedOrRejectedWording() {
        assertEquals("已通过", ProcessApprovalSupport.recordDescription(1));
        assertEquals("驳回", ProcessApprovalSupport.recordDescription(-1));
    }

    @Test
    public void applyOutcomeKeepsApprovingWhenNextAssigneesRemain() {
        Process process = new Process();
        process.setStatus(1);

        ProcessApprovalSupport.applyOutcome(process, 1, Arrays.asList("李四"), true);

        assertEquals(1, process.getStatus());
        assertEquals("等待李四审批", process.getDescription());
    }

    @Test
    public void applyOutcomeKeepsApprovingWhenNextTaskHasNoNamedAssignee() {
        Process process = new Process();

        ProcessApprovalSupport.applyOutcome(process, 1, Collections.emptyList(), true);

        assertEquals(1, process.getStatus());
        assertEquals("等待审批", process.getDescription());
    }

    @Test
    public void applyOutcomeMarksPassedWhenApproveHasNoNextTask() {
        Process process = new Process();

        ProcessApprovalSupport.applyOutcome(process, 1, Collections.emptyList(), false);

        assertEquals(2, process.getStatus());
        assertEquals("审批完成（通过）", process.getDescription());
    }

    @Test
    public void applyOutcomeMarksRejectedWhenRejectHasNoNextTask() {
        Process process = new Process();

        ProcessApprovalSupport.applyOutcome(process, -1, Collections.emptyList(), false);

        assertEquals(-1, process.getStatus());
        assertEquals("审批完成（驳回）", process.getDescription());
    }

    @Test
    public void currentApproverMatchesAssigneeUsername() {
        assertTrue(ProcessApprovalSupport.isCurrentApprover(Arrays.asList("zhangsan", "lisi"), "zhangsan"));
        assertFalse(ProcessApprovalSupport.isCurrentApprover(Arrays.asList("lisi"), "zhangsan"));
        assertFalse(ProcessApprovalSupport.isCurrentApprover(Collections.emptyList(), "zhangsan"));
        assertFalse(ProcessApprovalSupport.isCurrentApprover(Arrays.asList("zhangsan"), null));
    }

    @Test
    public void showPayloadKeepsProcessRecordsTemplateAndApproveFlag() {
        Process process = new Process();
        process.setId(12L);
        process.setFormValues("{\"formShowData\":{\"请假天数\":\"3\"}}");
        ProcessRecord record = new ProcessRecord();
        record.setDescription("发起申请");
        ProcessTemplate template = new ProcessTemplate();
        template.setName("请假申请");

        Map<String, Object> payload = ProcessApprovalSupport.showPayload(
                process,
                Collections.singletonList(record),
                template,
                true
        );

        assertSame(process, payload.get("process"));
        assertEquals(1, ((List<?>) payload.get("processRecordList")).size());
        assertSame(template, payload.get("processTemplate"));
        assertEquals(true, payload.get("isApprove"));
    }
}
