package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * 角色分配用户菜单种子（#256，ADR 0062 第 7 波）：在 {@code /system/sysRole} 页面下新增
 * {@code system:role:assignUser} 按钮点，并显式补授 {@code super_admin}。
 *
 * <p>页面本身已由 V110/V111 落地，本迁移只加按钮；纯种子 DML 按仓库标准放 {@code db/seed/}，
 * SQL 全部由 {@code NOT EXISTS} 守住，空库与既有库重复执行都不会多出行。
 */
public class V129__SysRoleAssignUserMenu extends BaseJavaMigration {

    private static final String SEED_RESOURCE = "db/seed/sys_role_assign_user_menu_v129.sql";

    @Override
    public void migrate(Context context) throws Exception {
        ScriptUtils.executeSqlScript(context.getConnection(), new ClassPathResource(SEED_RESOURCE));
    }
}
