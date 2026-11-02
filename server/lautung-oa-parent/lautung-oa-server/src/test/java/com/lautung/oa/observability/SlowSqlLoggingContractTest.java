package com.lautung.oa.observability;

import com.lautung.oa.auth.mapper.SysUserMapper;
import com.lautung.oa.model.system.SysUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #151：端到端装配——Interceptor bean 被装进 SqlSessionFactory，真实 mapper 查询超过阈值时打 WARN。
 * H2 schema 用 auth-contract-script 种子；阈值 0 使每条语句都触发，与真实耗时无关。
 */
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(properties = {
        "lautung.oa.sql.slow-sql.enabled=true",
        "lautung.oa.sql.slow-sql.threshold-ms=0"})
@ActiveProfiles("test")
@Sql("/auth-contract-schema.sql")
class SlowSqlLoggingContractTest {

    @Autowired
    private SysUserMapper sysUserMapper;

    @Test
    void realMapperQueryIsLoggedWithStatementId(CapturedOutput output) {
        sysUserMapper.selectById(1L);

        String logs = output.toString();
        assertTrue(logs.contains("slow sql statement="), logs);
        assertTrue(logs.contains("SysUserMapper.selectById"), logs);
        assertTrue(logs.contains("rows="), logs);
    }

    /** update/insert/delete 与 query 同拦：慢的写语句也要进慢日志（#151，ADR 0041）。 */
    @Test
    void realMapperUpdateIsLoggedToo(CapturedOutput output) {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setName("admin");
        sysUserMapper.updateById(user);

        String logs = output.toString();
        assertTrue(logs.contains("slow sql statement="), logs);
        assertTrue(logs.contains("SysUserMapper.update"), logs);
    }

    /** traceId 关联靠 TraceIdFilter 写入的 MDC + 结构化输出自动携带，合同在此钉住。 */
    @Test
    void slowLogLineCarriesMdcTraceId(CapturedOutput output) {
        MDC.put("traceId", "slow-sql-contract-trace");
        try {
            sysUserMapper.selectById(1L);
        } finally {
            MDC.remove("traceId");
        }

        String logs = output.toString();
        assertTrue(logs.contains("slow sql statement="), logs);
        assertTrue(logs.contains("\"traceId\":\"slow-sql-contract-trace\""), logs);
    }
}
