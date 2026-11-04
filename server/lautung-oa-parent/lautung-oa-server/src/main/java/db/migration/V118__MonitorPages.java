package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * 服务监控与数据监控页面种子（ADR 0060）：在 {@code /monitor} 目录下新增
 * {@code /monitor/server} 与 {@code /monitor/data} 两个只读页面并补授 {@code super_admin}，
 * 无按钮点（ADR 0056 读不设点）。
 *
 * <p>纯种子 DML 按仓库标准放 {@code db/seed/}；SQL 全部由 {@code NOT EXISTS} 守住，
 * 空库与既有库重复执行都不会多出行。
 */
public class V118__MonitorPages extends BaseJavaMigration {

    private static final String SEED_RESOURCE = "db/seed/monitor_pages_v118.sql";

    @Override
    public void migrate(Context context) throws Exception {
        ScriptUtils.executeSqlScript(context.getConnection(), new ClassPathResource(SEED_RESOURCE));
    }
}
