package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * 缓存监控与缓存列表页面种子（ADR 0060，#237）：在 {@code /monitor} 目录下新增
 * {@code /monitor/cache}（只读页，无按钮点）与 {@code /monitor/cacheList}（挂
 * {@code monitor:cache:clean} 按钮点）两个页面并补授 {@code super_admin}。
 *
 * <p>纯种子 DML 按仓库标准放 {@code db/seed/}；SQL 全部由 {@code NOT EXISTS} 守住，
 * 空库与既有库重复执行都不会多出行。
 */
public class V119__MonitorCacheMenu extends BaseJavaMigration {

    private static final String SEED_RESOURCE = "db/seed/monitor_cache_menu_v119.sql";

    @Override
    public void migrate(Context context) throws Exception {
        ScriptUtils.executeSqlScript(context.getConnection(), new ClassPathResource(SEED_RESOURCE));
    }
}
