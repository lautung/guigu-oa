package com.lautung.oa.framework.observability;

import java.util.ArrayList;
import java.util.List;

/**
 * 慢 SQL 内存环形缓冲（ADR 0060）：只保留最近 {@value #CAPACITY} 条，线程安全。
 * 条目只存时间、语句 id、elapsedMs、rows 与截断到 {@value #MAX_SQL_LENGTH} 字符的 SQL，
 * <b>不存参数</b>——ADR 0039 的手机号打码在日志输出层，缓冲带参数等于给 API
 * 留一条绕过打码的路径。缓冲由 {@link SlowSqlLoggingInterceptor} 在打 WARN 的同点写入，
 * 随 {@code lautung.oa.sql.slow-sql.enabled} 同生同灭，重启即清，正好匹配「看当下」。
 */
public final class SlowSqlCollector {

    /** 单条目字段集合钉死：无参数字段是安全边界，不是实现细节。 */
    public record SlowSqlEntry(long timestamp, String statementId, long elapsedMs, long rows, String sql) {
    }

    static final int CAPACITY = 200;
    static final int MAX_SQL_LENGTH = 1024;

    private final SlowSqlEntry[] ring = new SlowSqlEntry[CAPACITY];
    private long writes;

    /**
     * 在慢 SQL 日志的同一判定点写入；SQL 文本在此截断，调用方给原文即可。
     */
    public synchronized void record(String statementId, long elapsedMs, long rows, String sql) {
        ring[(int) (writes % CAPACITY)] = new SlowSqlEntry(
                System.currentTimeMillis(), statementId, elapsedMs, rows, truncate(sql));
        writes++;
    }

    /** 最新在前的快照，返回的列表与后续写入无关。 */
    public synchronized List<SlowSqlEntry> snapshot() {
        int count = (int) Math.min(writes, CAPACITY);
        List<SlowSqlEntry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entries.add(ring[(int) ((writes - 1 - i) % CAPACITY)]);
        }
        return List.copyOf(entries);
    }

    private static String truncate(String sql) {
        if (sql == null || sql.length() <= MAX_SQL_LENGTH) {
            return sql == null ? "" : sql;
        }
        return sql.substring(0, MAX_SQL_LENGTH);
    }
}
