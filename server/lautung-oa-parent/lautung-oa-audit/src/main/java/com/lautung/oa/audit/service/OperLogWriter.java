package com.lautung.oa.audit.service;

import com.lautung.oa.audit.entity.SysOperLog;
import com.lautung.oa.audit.mapper.AuditOperatorMapper;
import com.lautung.oa.audit.mapper.OperatorSnapshot;
import com.lautung.oa.audit.mapper.SysOperLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * 操作日志的同步落库（ADR 0054）：控制器返回时就写，响应成功即日志在库。
 *
 * <p>写失败只 WARN，绝不改变响应——审计是旁路，不能把一次已成功的业务写变成 500。
 */
@Component
public class OperLogWriter {

    private static final Logger log = LoggerFactory.getLogger(OperLogWriter.class);

    private final SysOperLogMapper operLogMapper;

    private final AuditOperatorMapper operatorMapper;

    public OperLogWriter(SysOperLogMapper operLogMapper, AuditOperatorMapper operatorMapper) {
        this.operLogMapper = operLogMapper;
        this.operatorMapper = operatorMapper;
    }

    public void write(SysOperLog entry) {
        try {
            snapshotOperator(entry);
            // ADR 0031 边界：实体不继承 BaseEntity，但 create_time 声明了 FieldFill.INSERT，
            // 公共字段处理器按字段名仍会填。兜底只防 handler 未装配/未生效，时间取应用时钟。
            if (entry.getCreateTime() == null) {
                entry.setCreateTime(new Date());
            }
            operLogMapper.insert(entry);
        } catch (RuntimeException e) {
            log.warn("Operation log write failed for {}", entry.getMethod(), e);
        }
    }

    private void snapshotOperator(SysOperLog entry) {
        Long operId = entry.getOperId();
        if (operId == null) {
            return;
        }
        OperatorSnapshot snapshot = operatorMapper.findOperatorSnapshot(operId);
        if (snapshot == null) {
            return;
        }
        if (snapshot.getUserName() != null && !snapshot.getUserName().isBlank()) {
            entry.setOperName(snapshot.getUserName());
        }
        entry.setDeptId(snapshot.getDeptId());
        entry.setDeptName(snapshot.getDeptName());
    }
}
