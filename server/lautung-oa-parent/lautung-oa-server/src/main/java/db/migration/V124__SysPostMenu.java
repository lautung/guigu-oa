package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * 岗位管理菜单种子（#248）：在 {@code /system} 目录下新增 {@code /system/sysPost}
 * 页面与 {@code system:post:add|edit|remove} 三个按钮点，并补授 {@code super_admin}。
 *
 * <p>表结构本身由 V110 建立；纯种子 DML 按仓库标准放 {@code db/seed/}，
 * SQL 全部由 {@code NOT EXISTS} 守住，空库与既有库重复执行都不会多出行。
 */
public class V124__SysPostMenu extends BaseJavaMigration {

    private static final String SEED_RESOURCE = "db/seed/sys_post_menu_v124.sql";

    @Override
    public void migrate(Context context) throws Exception {
        ScriptUtils.executeSqlScript(context.getConnection(), new ClassPathResource(SEED_RESOURCE));
    }
}
