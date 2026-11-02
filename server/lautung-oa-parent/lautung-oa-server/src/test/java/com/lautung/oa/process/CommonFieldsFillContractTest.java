package com.lautung.oa.process;

import com.lautung.oa.model.process.Process;
import com.lautung.oa.process.mapper.OaProcessMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 公共字段由应用填（ADR 0031）。
 *
 * <p>测试 schema 里 {@code create_time} / {@code update_time} 没有数据库默认值，所以断言通过
 * 就说明值来自 {@code CommonFieldsMetaObjectHandler}，而不是数据库时钟。
 */
@SpringBootTest
@ActiveProfiles("test")
@Sql({"/auth-contract-schema.sql", "/approval-contract-schema.sql"})
class CommonFieldsFillContractTest {

    @Autowired
    private OaProcessMapper processMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void insertFillsBothCommonFieldsBeforeTheRowIsReadable() {
        Process process = insertProcess("公共字段插入");

        assertNotNull(process.getCreateTime(), "插入后 create_time 必须由应用写入，不能留空");
        assertNotNull(process.getUpdateTime(), "插入后 update_time 必须由应用写入，不能留空");
        assertEquals(process.getCreateTime(), process.getUpdateTime(),
                "同一次插入的两个公共字段取同一个时刻");

        Process stored = processMapper.selectById(process.getId());
        assertNotNull(stored.getCreateTime());
        assertNotNull(stored.getUpdateTime());
    }

    @Test
    void updateFillsUpdateTimeWhenTheSubmittedEntityLeavesItEmpty() throws InterruptedException {
        Process process = insertProcess("公共字段更新");
        Date created = process.getCreateTime();
        Thread.sleep(20);

        Process patch = new Process();
        patch.setId(process.getId());
        patch.setTitle("公共字段更新后");
        processMapper.updateById(patch);

        Process stored = processMapper.selectById(process.getId());
        assertEquals("公共字段更新后", stored.getTitle());
        assertEquals(created, stored.getCreateTime(), "更新不得改写 create_time");
        assertNotNull(stored.getUpdateTime());
        assertTrue(stored.getUpdateTime().after(created), "更新后 update_time 应晚于 create_time");
    }

    @Test
    void logicalDeleteStillWritesTheRow() {
        Process process = insertProcess("公共字段删除");

        processMapper.deleteById(process.getId());

        assertNull(processMapper.selectById(process.getId()), "逻辑删除后按 id 读不到");
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT is_deleted FROM oa_process WHERE id = ?", Integer.class, process.getId()),
                "删除写的是逻辑标记，行还在");
    }

    private Process insertProcess(String title) {
        Process process = new Process();
        process.setProcessCode(String.valueOf(System.currentTimeMillis()));
        process.setUserId(1L);
        process.setTitle(title);
        process.setStatus(1);
        processMapper.insert(process);
        return process;
    }
}
