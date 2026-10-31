package com.atguigu.process.cc;

import com.atguigu.common.config.exception.GuiguException;
import com.atguigu.common.page.PageWindow;
import com.atguigu.model.process.Process;
import com.atguigu.model.system.SysUser;
import com.atguigu.process.mapper.OaProcessMapper;
import com.atguigu.process.membership.ProcessMembershipExtension;
import com.atguigu.vo.process.ProcessVo;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
            throw new GuiguException(201, "抄送人无效");
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
            throw new GuiguException(201, "抄送人不存在或已停用");
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

    public IPage<ProcessVo> findMine(Page<ProcessVo> page, Long userId) {
        long offset = PageWindow.offset(page);
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT c.process_id) FROM oa_process_cc c "
                        + "JOIN oa_process p ON p.id = c.process_id "
                        + "WHERE c.user_id = ? AND c.is_deleted = 0 AND p.is_deleted = 0",
                Long.class,
                userId);
        List<ProcessVo> records = jdbcTemplate.query(
                "SELECT DISTINCT p.id, p.create_time, p.process_code, p.user_id, u.name, "
                        + "p.process_template_id, t.name AS process_template_name, "
                        + "p.process_type_id, pt.name AS process_type_name, p.title, p.description, "
                        + "p.form_values, p.process_instance_id, p.current_auditor, p.status "
                        + "FROM oa_process_cc c JOIN oa_process p ON p.id = c.process_id "
                        + "LEFT JOIN sys_user u ON u.id = p.user_id "
                        + "LEFT JOIN oa_process_template t ON t.id = p.process_template_id "
                        + "LEFT JOIN oa_process_type pt ON pt.id = p.process_type_id "
                        + "WHERE c.user_id = ? AND c.is_deleted = 0 AND p.is_deleted = 0 "
                        + "ORDER BY p.create_time DESC, p.id DESC LIMIT ? OFFSET ?",
                (result, row) -> {
                    ProcessVo process = new ProcessVo();
                    process.setId(result.getLong("id"));
                    process.setCreateTime(result.getTimestamp("create_time"));
                    process.setProcessCode(result.getString("process_code"));
                    process.setUserId(result.getLong("user_id"));
                    process.setName(result.getString("name"));
                    process.setProcessTemplateId(result.getLong("process_template_id"));
                    process.setProcessTemplateName(result.getString("process_template_name"));
                    process.setProcessTypeId(result.getLong("process_type_id"));
                    process.setProcessTypeName(result.getString("process_type_name"));
                    process.setTitle(result.getString("title"));
                    process.setDescription(result.getString("description"));
                    process.setFormValues(result.getString("form_values"));
                    process.setProcessInstanceId(result.getString("process_instance_id"));
                    process.setCurrentAuditor(result.getString("current_auditor"));
                    process.setStatus(result.getInt("status"));
                    return process;
                },
                userId,
                page.getSize(),
                offset);
        IPage<ProcessVo> result = new Page<>(page.getCurrent(), page.getSize(), total == null ? 0 : total);
        result.setRecords(records);
        return result;
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

    private GuiguException denied() {
        return new GuiguException(205, "没有操作权限");
    }
}
