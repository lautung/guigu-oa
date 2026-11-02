package com.lautung.oa.process;

import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Flowable 8 切库：硬前提闸门、引擎表 DROP 范围、已发布模板再部署清单。
 * 不改模板状态，不碰 oa_* / sys_*。
 */
public final class ProcessCutoverSupport {

    private ProcessCutoverSupport() {
    }

    public static void requireNoInProgress(long inProgressCount) {
        if (inProgressCount > 0) {
            throw new IllegalStateException(
                    "切库停止：oa_process 仍有 " + inProgressCount + " 条审批中（status=1）");
        }
    }

    public static List<String> engineTablesToDrop(List<String> tableNames) {
        if (tableNames == null || tableNames.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> engineTables = new ArrayList<>();
        for (String tableName : tableNames) {
            if (isEngineTable(tableName)) {
                engineTables.add(tableName);
            }
        }
        return engineTables;
    }

    public static List<String> dropStatements(List<String> tableNames) {
        List<String> statements = new ArrayList<>();
        for (String tableName : engineTablesToDrop(tableNames)) {
            statements.add("DROP TABLE IF EXISTS `" + tableName.replace("`", "") + "`");
        }
        return statements;
    }

    private static boolean isEngineTable(String tableName) {
        if (!StringUtils.hasText(tableName)) {
            return false;
        }
        String upper = tableName.toUpperCase(Locale.ROOT);
        return upper.startsWith("ACT_") || upper.startsWith("FLW_");
    }
}
