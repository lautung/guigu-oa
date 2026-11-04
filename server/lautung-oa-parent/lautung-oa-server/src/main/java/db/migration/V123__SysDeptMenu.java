package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * 部门管理菜单种子（#247）：新增 {@code /system/sysDept} 页面与三个按钮点，
 * 并显式补授 {@code super_admin}（ADR 0056：管理员不做身份旁路）。
 *
 * <p>纯种子 DML 按仓库标准放 {@code db/seed/}，SQL 全部由 {@code NOT EXISTS} 守住，
 * 空库与既有库重复执行都不会多出行。
 */
public class V123__SysDeptMenu extends BaseJavaMigration {

    private static final String SEED_RESOURCE = "db/seed/sys_dept_menu_v123.sql";

    @Override
    public void migrate(Context context) throws Exception {
        ScriptUtils.executeSqlScript(context.getConnection(), new ClassPathResource(SEED_RESOURCE));
    }
}
