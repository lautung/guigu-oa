package com.lautung.oa.process;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    public void engineTablesToDropMatchesActAndFlwPrefixesCaseInsensitively() {
        assertEquals(
                Arrays.asList("act_ru_task", "flw_ru_batch"),
                ProcessCutoverSupport.engineTablesToDrop(Arrays.asList("act_ru_task", "flw_ru_batch", "oa_process")));
    }
}
