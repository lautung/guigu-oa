package com.atguigu.demo;

import com.atguigu.auth.service.SysUserService;
import com.atguigu.model.process.Attachment;
import com.atguigu.model.process.Process;
import com.atguigu.model.process.ProcessTemplate;
import com.atguigu.model.system.SysUser;
import com.atguigu.process.attachment.AttachmentMapper;
import com.atguigu.process.service.Approval;
import com.atguigu.process.service.OaProcessTemplateService;
import com.atguigu.vo.process.ApprovalVo;
import com.atguigu.vo.process.ProcessFormVo;
import com.atguigu.vo.process.ProcessVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;
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
import com.atguigu.process.attachment.AttachmentS3Properties;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@Profile("demo")
public class DemoScenarioSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoScenarioSeeder.class);
    private static final String PROCESS_DEFINITION_KEY = "demoApproval";
    private static final String ATTACHMENT_CONTENT = "Compose 演示附件：请假申请说明。\n";

    private final Approval approval;
    private final OaProcessTemplateService templateService;
    private final SysUserService userService;
    private final RepositoryService repositoryService;
    private final AttachmentMapper attachmentMapper;
    private final JdbcTemplate jdbcTemplate;
    private final S3Client s3Client;
    private final AttachmentS3Properties s3Properties;

    public DemoScenarioSeeder(
            Approval approval,
            OaProcessTemplateService templateService,
            SysUserService userService,
            RepositoryService repositoryService,
            AttachmentMapper attachmentMapper,
            JdbcTemplate jdbcTemplate,
            S3Client attachmentS3Client,
            AttachmentS3Properties s3Properties) {
        this.approval = approval;
        this.templateService = templateService;
        this.userService = userService;
        this.repositoryService = repositoryService;
        this.attachmentMapper = attachmentMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.s3Client = attachmentS3Client;
        this.s3Properties = s3Properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureProcessDefinition();
        SysUser alice = requireUser("alice");
        SysUser bob = requireUser("bob");
        ProcessTemplate template = templateService.getOne(new LambdaQueryWrapper<ProcessTemplate>()
                .eq(ProcessTemplate::getProcessDefinitionKey, PROCESS_DEFINITION_KEY)
                .eq(ProcessTemplate::getStatus, 1)
                .eq(ProcessTemplate::getIsDeleted, 0)
                .last("LIMIT 1"));
        if (template == null) {
            throw new IllegalStateException("Compose 演示审批模板未由 Flyway 创建");
        }

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

        long pendingCount = approval.findPending(bob, new Page<>(1, 50)).getTotal();
        long processedCount = approval.findProcessed(bob, new Page<>(1, 50)).getTotal();
        Long copiedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT c.process_id) FROM oa_process_cc c JOIN sys_user u ON u.id = c.user_id "
                        + "WHERE u.username = 'carol' AND c.is_deleted = 0",
                Long.class);
        log.info("Compose 演示数据就绪 pending={} processed={} carolCopied={}",
                pendingCount, processedCount, copiedCount);
    }

    private void ensureProcessDefinition() {
        ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(PROCESS_DEFINITION_KEY)
                .latestVersion()
                .singleResult();
        if (definition == null) {
            repositoryService.createDeployment()
                    .name("Compose 演示审批")
                    .addClasspathResource("processes/demo-approval.bpmn20.xml")
                    .deploy();
        }
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
                        + "form_values, process_instance_id, current_auditor, status, create_time, update_time, is_deleted "
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
                    process.setCurrentAuditor(result.getString("current_auditor"));
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
        List<ProcessVo> pending = approval.findPending(bob, new Page<Process>(1, 100)).getRecords();
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
