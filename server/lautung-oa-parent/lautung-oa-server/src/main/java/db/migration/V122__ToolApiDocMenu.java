package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * 系统工具菜单种子（ADR 0061，#246）：新增 {@code /tool} 目录与
 * {@code /tool/api-doc} 页面，并补授 {@code super_admin}。
 *
 * <p>系统工具本期只有接口文档一张页面，不种按钮点（只读页无写操作）；
 * 纯种子 DML 按仓库标准放 {@code db/seed/}，SQL 全部由 {@code NOT EXISTS} 守住，
 * 空库与既有库重复执行都不会多出行。
 */
public class V122__ToolApiDocMenu extends BaseJavaMigration {

    private static final String SEED_RESOURCE = "db/seed/tool_api_doc_menu_v122.sql";

    @Override
    public void migrate(Context context) throws Exception {
        ScriptUtils.executeSqlScript(context.getConnection(), new ClassPathResource(SEED_RESOURCE));
    }
}
