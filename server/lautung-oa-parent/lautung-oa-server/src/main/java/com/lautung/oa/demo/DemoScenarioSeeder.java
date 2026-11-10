package com.lautung.oa.demo;

import com.lautung.oa.auth.service.SysUserService;
import com.lautung.oa.model.process.Attachment;
import com.lautung.oa.model.process.Process;
import com.lautung.oa.model.process.ProcessTemplate;
import com.lautung.oa.model.system.SysUser;
import com.lautung.oa.process.attachment.AttachmentMapper;
import com.lautung.oa.process.read.ApprovalListReader;
import com.lautung.oa.process.service.Approval;
import com.lautung.oa.vo.process.ApprovalVo;
import com.lautung.oa.vo.process.ProcessFormVo;
import com.lautung.oa.vo.process.ProcessVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import com.lautung.oa.storage.StorageS3Properties;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@Profile("demo")
public class DemoScenarioSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoScenarioSeeder.class);
    private static final String ATTACHMENT_CONTENT = "Compose 演示附件：请假申请说明。\n";

    /** 演示定时任务（#238）：行由启动登记产生（disabled），这里只做改名与备注的化妆。 */
    public static final String DEMO_JOB_BEAN = "demoLogMonitorJob";
    public static final String DEMO_JOB_NAME = "演示：示例日志任务";
    public static final String DEMO_JOB_REMARK = "演示用，默认停用；在页面上手动执行一次即可看到执行记录。";

    private final Approval approval;
    private final ApprovalListReader approvalListReader;
    private final DemoApprovalPublisher demoApprovalPublisher;
    private final SysUserService userService;
    private final AttachmentMapper attachmentMapper;
    private final JdbcTemplate jdbcTemplate;
    private final S3Client s3Client;
    private final StorageS3Properties s3Properties;

    public DemoScenarioSeeder(
            Approval approval,
            ApprovalListReader approvalListReader,
            DemoApprovalPublisher demoApprovalPublisher,
            SysUserService userService,
            AttachmentMapper attachmentMapper,
            JdbcTemplate jdbcTemplate,
            S3Client attachmentS3Client,
            StorageS3Properties s3Properties) {
        this.approval = approval;
        this.approvalListReader = approvalListReader;
        this.demoApprovalPublisher = demoApprovalPublisher;
        this.userService = userService;
        this.attachmentMapper = attachmentMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.s3Client = attachmentS3Client;
        this.s3Properties = s3Properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureDemoJobRow();
        demoApprovalPublisher.ensureAttendancePublished();
        ProcessTemplate template = demoApprovalPublisher.ensurePublished();
        if (template == null) {
            return;
        }
        SysUser alice = requireUser("alice");
        SysUser bob = requireUser("bob");

        Process pending = findScenario(alice.getId(), template.getId(), "demo-pending");
        if (pending == null) {
            approval.startUp(alice, form(template, "demo-pending", "待处理演示：请假两天", 2));
            pending = requireScenario(alice.getId(), template.getId(), "demo-pending");
        }
        ensureAttachment(alice, pending);

        Process ended = findScenario(alice.getId(), template.getId(), "demo-ended");
        if (ended == null) {
            approval.startUp(alice, form(template, "demo-ended", "已结束演示：请假一天", 1));
            ended = requireScenario(alice.getId(), template.getId(), "demo-ended");
        }
        completeForBob(bob, ended);

        long pendingCount = approvalListReader.pending(new Page<>(1, 50), bob).getTotal();
        long processedCount = approvalListReader.processed(new Page<>(1, 50), bob).getTotal();
        Long copiedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT c.process_id) FROM oa_process_cc c JOIN sys_user u ON u.id = c.user_id "
                        + "WHERE u.username = 'carol' AND c.is_deleted = 0",
                Long.class);
        log.info("Compose 演示数据就绪 pending={} processed={} carolCopied={}",
                pendingCount, processedCount, copiedCount);
    }

    /**
     * 演示定时任务的行化妆：登记（{@code @Order} 取 {@code Ordered.HIGHEST_PRECEDENCE} 的调度器，先于本 seeder）已把
     * {@code demoLogMonitorJob} 种成 disabled 行，这里补上可读的名字与备注。
     * 只 UPDATE，不碰 enabled / cron / 并发。
     */
    public void ensureDemoJobRow() {
        jdbcTemplate.update(
                "UPDATE sys_job SET name = ?, remark = ? WHERE bean_name = ? AND is_deleted = 0",
                DEMO_JOB_NAME, DEMO_JOB_REMARK, DEMO_JOB_BEAN);
    }

    private SysUser requireUser(String username) {
        SysUser user = userService.getUserByUserName(username);
        if (user == null || user.getId() == null || !Integer.valueOf(1).equals(user.getStatus())) {
            throw new IllegalStateException("Compose 演示用户不可用: " + username);
        }
        return user;
    }

    private ProcessFormVo form(ProcessTemplate template, String scenario, String reason, int days) {
        ProcessFormVo form = new ProcessFormVo();
        form.setProcessTemplateId(template.getId());
        form.setProcessTypeId(template.getProcessTypeId());
        form.setFormValues("{\"formData\":{\"reason\":\"" + reason + "\",\"days\":" + days
                + ",\"demoScenario\":\"" + scenario + "\"},\"formShowData\":{\"请假原因\":\""
                + reason + "\",\"请假天数\":" + days + "}}");
        return form;
    }

    private Process findScenario(Long userId, Long templateId, String scenario) {
        return jdbcTemplate.query(
                "SELECT id, process_code, user_id, process_template_id, process_type_id, title, description, "
                        + "form_values, process_instance_id, status, create_time, update_time, is_deleted "
                        + "FROM oa_process WHERE user_id = ? AND process_template_id = ? AND form_values LIKE ? "
                        + "AND is_deleted = 0 ORDER BY id LIMIT 1",
                (result, row) -> {
                    Process process = new Process();
                    process.setId(result.getLong("id"));
                    process.setProcessCode(result.getString("process_code"));
                    process.setUserId(result.getLong("user_id"));
                    process.setProcessTemplateId(result.getLong("process_template_id"));
                    process.setProcessTypeId(result.getLong("process_type_id"));
                    process.setTitle(result.getString("title"));
                    process.setDescription(result.getString("description"));
                    process.setFormValues(result.getString("form_values"));
                    process.setProcessInstanceId(result.getString("process_instance_id"));
                    process.setStatus(result.getInt("status"));
                    process.setCreateTime(result.getTimestamp("create_time"));
                    process.setUpdateTime(result.getTimestamp("update_time"));
                    process.setIsDeleted(result.getInt("is_deleted"));
                    return process;
                },
                userId,
                templateId,
                "%\"demoScenario\":\"" + scenario + "\"%")
                .stream().findFirst().orElse(null);
    }

    private Process requireScenario(Long userId, Long templateId, String scenario) {
        Process process = findScenario(userId, templateId, scenario);
        if (process == null) {
            throw new IllegalStateException("Compose 演示审批实例创建失败: " + scenario);
        }
        return process;
    }

    private void completeForBob(SysUser bob, Process process) {
        if (!Integer.valueOf(1).equals(process.getStatus())) {
            return;
        }
        List<ProcessVo> pending = approvalListReader.pending(new Page<>(1, 100), bob).getRecords();
        ProcessVo task = pending.stream()
                .filter(item -> process.getId().equals(item.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Bob 未收到演示审批任务"));
        ApprovalVo decision = new ApprovalVo();
        decision.setProcessId(process.getId());
        decision.setTaskId(task.getTaskId());
        decision.setStatus(1);
        approval.approve(bob, decision);
    }

    private void ensureAttachment(SysUser alice, Process process) {
        Long existing = attachmentMapper.selectCount(new LambdaQueryWrapper<Attachment>()
                .eq(Attachment::getProcessId, process.getId())
                .eq(Attachment::getStatus, Attachment.ATTACHED));
        if (existing != null && existing > 0) {
            return;
        }
        String objectKey = "attachments/demo-" + process.getId() + ".txt";
        byte[] content = ATTACHMENT_CONTENT.getBytes(StandardCharsets.UTF_8);
        s3Client.putObject(PutObjectRequest.builder()
                        .bucket(s3Properties.getBucket())
                        .key(objectKey)
                        .contentType("text/plain; charset=utf-8")
                        .build(),
                RequestBody.fromBytes(content));

        Attachment attachment = new Attachment();
        attachment.setProcessId(process.getId());
        attachment.setOwnerUserId(alice.getId());
        attachment.setStorageId("demo-" + process.getId());
        attachment.setObjectKey(objectKey);
        attachment.setFileName("请假说明.txt");
        attachment.setContentType("text/plain; charset=utf-8");
        attachment.setSizeBytes((long) content.length);
        attachment.setStatus(Attachment.ATTACHED);
        attachmentMapper.insert(attachment);
    }
}
