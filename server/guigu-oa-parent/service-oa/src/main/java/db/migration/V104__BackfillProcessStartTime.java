package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * 用引擎历史回填 {@code oa_process.create_time}（ADR 0031）。
 *
 * <p>为什么是 Java 迁移而不是 SQL：空库首次启动时 {@code ACT_*} 由 Flowable 在 Flyway 之后创建，
 * 直接写 {@code UPDATE ... ACT_HI_PROCINST ...} 会在这种库上因表不存在而失败。这里先查元数据，
 * 引擎历史不存在就跳过——那时也没有可证明的发起时刻，回填本就没有对象。
 *
 * <p>回填范围只限可证的行：{@code ACT_HI_PROCINST.START_TIME_} 是这次审批真正被发起的那一刻。
 * 其余表（{@code oa_process_record}、{@code sys_user} 等）在引擎历史里没有稳定对应关系，
 * 保持 NULL，不编造历史（与 V4 对已发布模板的立场一致）。
 */
public class V104__BackfillProcessStartTime extends BaseJavaMigration {

    private static final String BACKFILL =
            "UPDATE oa_process SET create_time = ("
                    + "SELECT h.START_TIME_ FROM ACT_HI_PROCINST h "
                    + "WHERE h.PROC_INST_ID_ = oa_process.process_instance_id) "
                    + "WHERE create_time IS NULL "
                    + "AND process_instance_id IS NOT NULL "
                    + "AND EXISTS (SELECT 1 FROM ACT_HI_PROCINST h2 "
                    + "WHERE h2.PROC_INST_ID_ = oa_process.process_instance_id)";

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        if (!engineHistoryExists(connection)) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(BACKFILL);
        }
    }

    private boolean engineHistoryExists(Connection connection) throws Exception {
        DatabaseMetaData metaData = connection.getMetaData();
        String catalog = connection.getCatalog();
        for (String candidate : new String[]{"ACT_HI_PROCINST", "act_hi_procinst"}) {
            try (ResultSet tables = metaData.getTables(catalog, null, candidate, new String[]{"TABLE"})) {
                if (tables.next()) {
                    return true;
                }
            }
        }
        return false;
    }
}
