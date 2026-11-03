package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * 系统监控目录与在线用户菜单种子（ADR 0060）：新增 {@code /monitor} 目录、{@code /monitor/online}
 * 页面与 {@code monitor:online:forceLogout} 按钮点，并显式补授 {@code super_admin}。
 *
 * <p>纯种子 DML 按仓库标准放 {@code db/seed/}；SQL 全部由 {@code NOT EXISTS} 守住，
 * 空库与既有库重复执行都不会多出行。
 */
public class V117__MonitorOnlineMenu extends BaseJavaMigration {

    private static final String SEED_RESOURCE = "db/seed/monitor_online_menu_v117.sql";

    @Override
    public void migrate(Context context) throws Exception {
        ScriptUtils.executeSqlScript(context.getConnection(), new ClassPathResource(SEED_RESOURCE));
    }
}
