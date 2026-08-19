package com.atguigu.process;

import com.atguigu.model.process.ProcessTemplate;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProcessCutoverSupportTest {

    @Test
    public void requireNoInProgressAllowsCutoverWhenApprovingCountIsZero() {
        ProcessCutoverSupport.requireNoInProgress(0L);
    }

    @Test
    public void requireNoInProgressStopsCutoverWhenApprovingRowsRemain() {
        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> ProcessCutoverSupport.requireNoInProgress(2L));
        assertTrue(thrown.getMessage().contains("status=1"));
        assertTrue(thrown.getMessage().contains("2"));
    }

    @Test
    public void engineTablesToDropKeepsActivitiAndFlowableTablesAndLeavesBusinessTables() {
        List<String> dropped = ProcessCutoverSupport.engineTablesToDrop(Arrays.asList(
                "oa_process",
                "oa_process_record",
                "sys_user",
                "ACT_RU_TASK",
                "ACT_RU_INTEGRATION",
                "ACT_GE_PROPERTY",
                "FLW_RU_BATCH",
                "FLW_RU_BATCH_PART"
        ));

        assertEquals(Arrays.asList(
                "ACT_RU_TASK",
                "ACT_RU_INTEGRATION",
                "ACT_GE_PROPERTY",
                "FLW_RU_BATCH",
                "FLW_RU_BATCH_PART"
        ), dropped);
    }

    @Test
    public void dropStatementsQuoteOnlyEngineTables() {
        List<String> statements = ProcessCutoverSupport.dropStatements(Arrays.asList(
                "oa_process",
                "ACT_RU_TASK",
                "FLW_RU_BATCH"
        ));

        assertEquals(Arrays.asList(
                "DROP TABLE IF EXISTS `ACT_RU_TASK`",
                "DROP TABLE IF EXISTS `FLW_RU_BATCH`"
        ), statements);
    }

    @Test
    public void publishedToRedeployKeepsPublishedZipPathsAndDoesNotChangeStatus() {
        ProcessTemplate published = new ProcessTemplate();
        published.setName("请假申请");
        published.setStatus(1);
        published.setProcessDefinitionPath("processes/qingjia.zip");

        ProcessTemplate unpublished = new ProcessTemplate();
        unpublished.setName("病假草稿");
        unpublished.setStatus(0);
        unpublished.setProcessDefinitionPath("processes/bingjia.zip");

        ProcessTemplate publishedWithoutZip = new ProcessTemplate();
        publishedWithoutZip.setName("空路径");
        publishedWithoutZip.setStatus(1);
        publishedWithoutZip.setProcessDefinitionPath("");

        List<ProcessTemplate> toRedeploy = ProcessCutoverSupport.publishedToRedeploy(
                Arrays.asList(published, unpublished, publishedWithoutZip));

        assertEquals(1, toRedeploy.size());
        assertSame(published, toRedeploy.get(0));
        assertEquals(1, published.getStatus());
        assertEquals("processes/qingjia.zip", published.getProcessDefinitionPath());
        assertEquals(0, unpublished.getStatus());
    }

    @Test
    public void engineTablesToDropMatchesActAndFlwPrefixesCaseInsensitively() {
        assertEquals(
                Arrays.asList("act_ru_task", "flw_ru_batch"),
                ProcessCutoverSupport.engineTablesToDrop(Arrays.asList("act_ru_task", "flw_ru_batch", "oa_process")));
    }
}
