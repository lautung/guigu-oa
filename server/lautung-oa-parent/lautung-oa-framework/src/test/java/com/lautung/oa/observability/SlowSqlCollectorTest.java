package com.lautung.oa.observability;

import com.lautung.oa.framework.observability.SlowSqlCollector;
import com.lautung.oa.framework.observability.SlowSqlCollector.SlowSqlEntry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 慢 SQL 内存环形缓冲（ADR 0060）：容量 200、SQL 截断 1024、条目字段集合钉死为
 * {@code timestamp / statementId / elapsedMs / rows / sql}——缓冲带参数等于给 API
 * 留一条绕过 ADR 0039 手机号打码的路径，字段集合用反射断言，新增字段即红。
 */
class SlowSqlCollectorTest {

    @Test
    void ringKeepsOnlyTheNewest200EntriesWithNewestFirst() {
        SlowSqlCollector collector = new SlowSqlCollector();

        for (int i = 0; i < 300; i++) {
            collector.record("stmt-" + i, 10L * i, i, "SELECT " + i);
        }

        List<SlowSqlEntry> entries = collector.snapshot();
        assertEquals(200, entries.size(), "环形缓冲最多保留 200 条");
        assertEquals("stmt-299", entries.get(0).statementId(), "最新一条在最前");
        assertEquals("stmt-100", entries.get(entries.size() - 1).statementId(), "最旧保留的是第 100 条");
        assertEquals(299L * 10, entries.get(0).elapsedMs());
        assertEquals(299L, entries.get(0).rows());
        assertEquals("SELECT 299", entries.get(0).sql());
    }

    @Test
    void sqlIsHardTruncatedTo1024Characters() {
        SlowSqlCollector collector = new SlowSqlCollector();
        String longSql = "SELECT " + "c".repeat(2000);

        collector.record("stmt", 1L, 1L, longSql);

        List<SlowSqlEntry> entries = collector.snapshot();
        assertEquals(1, entries.size());
        assertEquals(1024, entries.get(0).sql().length());
        assertEquals(longSql.substring(0, 1024), entries.get(0).sql());
    }

    @Test
    void entryCarriesExactlyTheFiveDeclaredFieldsAndNeverParams() {
        Set<String> fields = Stream.of(SlowSqlEntry.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .collect(Collectors.toSet());

        assertEquals(Set.of("timestamp", "statementId", "elapsedMs", "rows", "sql"), fields,
                "缓冲条目只允许这五个字段，出现参数即违反 ADR 0060/0039");
    }

    @Test
    void snapshotIsANewListTheCallerMayKeep() {
        SlowSqlCollector collector = new SlowSqlCollector();
        collector.record("stmt-1", 1L, 1L, "SELECT 1");
        List<SlowSqlEntry> first = collector.snapshot();

        collector.record("stmt-2", 2L, 2L, "SELECT 2");

        assertEquals(1, first.size(), "先前取到的快照不随后续写入变化");
        assertEquals("stmt-1", first.get(0).statementId());
        assertEquals(2, collector.snapshot().size());
    }
}
