package com.lautung.oa.process.cc;

import com.lautung.oa.framework.config.exception.BusinessException;
import com.lautung.oa.model.process.Process;
import com.lautung.oa.model.system.SysUser;
import com.lautung.oa.process.mapper.OaProcessMapper;
import com.lautung.oa.process.membership.ProcessMembershipExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CarbonCopyService implements CarbonCopyWriter, ProcessMembershipExtension {

    private final JdbcTemplate jdbcTemplate;
    private final OaProcessMapper processMapper;

    public CarbonCopyService(JdbcTemplate jdbcTemplate, OaProcessMapper processMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.processMapper = processMapper;
    }

    @Transactional
    public void copyAtStart(SysUser starter, Long processId, List<Long> recipientIds) {
        if (recipientIds == null || recipientIds.isEmpty()) {
            return;
        }
        if (starter == null || starter.getId() == null || processId == null) {
            throw denied();
        }
        Set<Long> recipients = new LinkedHashSet<>(recipientIds);
        if (recipients.contains(null)) {
            throw new BusinessException(201, "抄送人无效");
        }
        recipients.remove(starter.getId());
        if (recipients.isEmpty()) {
            return;
        }
        String placeholders = recipients.stream().map(ignored -> "?").collect(Collectors.joining(","));
        List<Long> activeIds = jdbcTemplate.queryForList(
                "SELECT id FROM sys_user WHERE id IN (" + placeholders + ") AND status = 1 AND is_deleted = 0",
                Long.class,
                recipients.toArray());
        if (activeIds.size() != recipients.size()) {
            throw new BusinessException(201, "抄送人不存在或已停用");
        }
        for (Long recipientId : activeIds) {
            insertGrant(processId, recipientId, "START", String.valueOf(processId), null);
        }
    }

    public List<CarbonCopyRecipient> findRecipients(Long excludedUserId) {
        return jdbcTemplate.query(
                "SELECT id, name FROM sys_user WHERE status = 1 AND is_deleted = 0 AND id <> ? ORDER BY name, id",
                (result, row) -> new CarbonCopyRecipient(result.getLong("id"), result.getString("name")),
                excludedUserId == null ? -1L : excludedUserId);
    }

    @Override
    public boolean isMember(SysUser user, Process process) {
        if (user == null || user.getId() == null || process == null || process.getId() == null) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM oa_process_cc WHERE process_id = ? AND user_id = ? AND is_deleted = 0",
                Integer.class,
                process.getId(),
                user.getId());
        return count != null && count > 0;
    }

    @Override
    @Transactional
    public void copyOnTaskCreated(CarbonCopyTrigger trigger) {
        if (trigger == null || !StringUtils.hasText(trigger.businessKey())
                || !StringUtils.hasText(trigger.taskId())) {
            return;
        }
        long processId;
        try {
            processId = Long.parseLong(trigger.businessKey());
        } catch (NumberFormatException ignored) {
            return;
        }
        Process process = processMapper.selectById(processId);
        if (process == null) {
            return;
        }
        List<Long> recipientIds = jdbcTemplate.queryForList(
                "SELECT r.user_id FROM oa_process_cc_rule r JOIN sys_user u ON u.id = r.user_id "
                        + "WHERE r.process_definition_key = ? AND r.task_definition_key = ? "
                        + "AND r.is_deleted = 0 AND u.status = 1 AND u.is_deleted = 0",
                Long.class,
                trigger.processDefinitionKey(),
                trigger.taskDefinitionKey());
        for (Long recipientId : recipientIds) {
            if (recipientId.equals(process.getUserId())) {
                continue;
            }
            insertGrant(processId, recipientId, "TASK", trigger.taskId(), trigger.taskId());
        }
    }

    private void insertGrant(Long processId, Long userId, String sourceType, String sourceKey, String taskId) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO oa_process_cc (process_id, user_id, source_type, source_key, task_id, is_deleted) "
                            + "VALUES (?, ?, ?, ?, ?, 0)",
                    processId,
                    userId,
                    sourceType,
                    sourceKey,
                    taskId);
        } catch (DuplicateKeyException ignored) {
            // Flowable may deliver the same task event again. Unique source/task keys make retries harmless.
        }
    }

    private BusinessException denied() {
        return new BusinessException(205, "没有操作权限");
    }
}
