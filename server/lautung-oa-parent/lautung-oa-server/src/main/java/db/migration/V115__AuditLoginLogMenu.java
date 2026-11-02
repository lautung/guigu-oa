package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * 登录日志菜单种子（ADR 0054/0056，#216）：复用 V113 的「日志管理」目录，新增登录日志页
 * 与删除/清空/解锁按钮点，并显式补授 {@code super_admin}。
 *
 * <p>纯种子 DML 按仓库标准放 {@code db/seed/}（迁移目录只放 DDL 或需要 Java 判定的迁移）；
 * SQL 全部由 {@code NOT EXISTS} 守住，Java 迁移只负责执行文件，版本号保持不变（V115）。
 */
public class V115__AuditLoginLogMenu extends BaseJavaMigration {

    private static final String SEED_RESOURCE = "db/seed/audit_login_log_menu_v115.sql";

    @Override
    public void migrate(Context context) throws Exception {
        ScriptUtils.executeSqlScript(context.getConnection(), new ClassPathResource(SEED_RESOURCE));
    }
}
