package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * 用户导出按钮点种子（#259）：在 {@code /system/sysUser} 下新增
 * {@code system:user:export}，并补授 {@code super_admin}。
 *
 * <p>纯种子 DML 按仓库标准放 {@code db/seed/}，SQL 全部由 {@code NOT EXISTS} 守住，
 * 空库与既有库重复执行都不会多出行。
 */
public class V132__SysUserExportMenu extends BaseJavaMigration {

    private static final String SEED_RESOURCE = "db/seed/sys_user_export_menu_v132.sql";

    @Override
    public void migrate(Context context) throws Exception {
        ScriptUtils.executeSqlScript(context.getConnection(), new ClassPathResource(SEED_RESOURCE));
    }
}
