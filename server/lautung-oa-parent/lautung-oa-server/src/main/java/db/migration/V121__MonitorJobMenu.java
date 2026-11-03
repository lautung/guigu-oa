package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * 定时任务页面种子（ADR 0060，#238）：在 {@code /monitor} 目录下新增
 * {@code /monitor/job} 页面与 {@code monitor:job:edit} / {@code monitor:job:run}
 * 两个按钮点，并补授 {@code super_admin}。
 *
 * <p>纯种子 DML 按仓库标准放 {@code db/seed/}；SQL 全部由 {@code NOT EXISTS} 守住，
 * 空库与既有库重复执行都不会多出行。表结构本身由 V120 承担。
 */
public class V121__MonitorJobMenu extends BaseJavaMigration {

    private static final String SEED_RESOURCE = "db/seed/monitor_job_menu_v121.sql";

    @Override
    public void migrate(Context context) throws Exception {
        ScriptUtils.executeSqlScript(context.getConnection(), new ClassPathResource(SEED_RESOURCE));
    }
}
