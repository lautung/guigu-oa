package com.lautung.oa.process;

import com.lautung.oa.framework.config.exception.BusinessException;
import com.lautung.oa.model.process.ProcessRecord;
import com.lautung.oa.model.process.ProcessTemplate;
import com.lautung.oa.model.system.SysUser;
import com.lautung.oa.process.read.ApprovalListReader;
import com.lautung.oa.process.service.Approval;
import com.lautung.oa.process.service.ApprovalTemplate;
import com.lautung.oa.security.custom.LoginUserInfoHelper;
import com.lautung.oa.vo.process.ApprovalVo;
import com.lautung.oa.vo.process.ProcessFormVo;
import com.lautung.oa.vo.process.ProcessVo;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jayway.jsonpath.JsonPath;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.identitylink.api.IdentityLinkType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 账本动作层底座合同（#287，ADR 0068）：action_type 迁移与历史回填重放、已处理白名单、
 * 撤回（状态档/保留面/不可恢复/权限与幂等 201）、催办（24h 频控/候选对象/不进已处理）。
 * 201 拒绝在 HTTP 面一律同链钉 $.message。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql({"/auth-contract-schema.sql", "/approval-contract-schema.sql"})
public class ApprovalActionContractTest {

    private static final int 审批中 = 1;
    private static final int 已撤回 = -2;

    @Autowired
    private Approval approval;

    @Autowired
    private ApprovalListReader approvalListReader;

    @Autowired
    private ApprovalTemplate approvalTemplate;

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private HistoryService historyService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    public void deployPublishedTemplateEngineDefinition() throws Exception {
        LoginUserInfoHelper.removeUserId();
        LoginUserInfoHelper.removeUsername();
        clearEngineInstances();
        repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey("approvalTwoStep")
                .list()
                .forEach(definition -> repositoryService.deleteDeployment(definition.getDeploymentId(), true));
        jdbcTemplate.update("UPDATE oa_process_template SET status = 0 WHERE id = 1");
        ProcessTemplate template = approvalTemplate.read(1L);
        var uploaded = approvalTemplate.uploadProcessDefinition(new MockMultipartFile(
                "file", "approvalTwoStep.zip", "application/zip", approvalTwoStepZip()));
        template.setProcessDefinitionKey(uploaded.processDefinitionKey());
        template.setProcessDefinitionPath(uploaded.processDefinitionPath());
        approvalTemplate.saveUnpublished(template);
        approvalTemplate.publish(template.getId());
    }

    @Test
    public void actionTypeMigrationBackfillIsExactAndReplayable() throws Exception {
        // 上下文启动时 Flyway 已执行 V136：迁移在案。
        assertEquals(1L, count("SELECT COUNT(*) FROM flyway_schema_history WHERE version = '136'"),
                "V136 迁移未执行");

        // 造历史形状：四类 description、一行空 description、一行回填前已有动作类型。
        jdbcTemplate.update(
                "INSERT INTO oa_process_record "
                        + "(process_id, description, status, operate_user_id, operate_user, action_type, is_deleted) VALUES "
                        + "(901, '发起申请', 1, 2, '小艾', NULL, 0), "
                        + "(901, '已通过', 1, 3, '张三', NULL, 0), "
                        + "(901, '已拒绝', -1, 3, '张三', NULL, 0), "
                        + "(901, '等待审批', 1, 3, '张三', NULL, 0), "
                        + "(901, NULL, 1, 3, '张三', NULL, 0), "
                        + "(901, '已通过', 1, 3, '张三', 'URGE', 0)");

        replayV136Backfill();
        assertBackfilled();
        replayV136Backfill();
        assertBackfilled();
    }

    @Test
    public void processedWhitelistCountsOnlyApproveAndRejectActions() {
        SysUser alice = user(2L, "alice", "小艾");
        SysUser zhangsan = user(3L, "zhangsan", "张三");
        SysUser lisi = user(4L, "lisi", "李四");

        approval.startUp(alice, publishedLeaveForm());
        ProcessVo pending = pending(zhangsan).getRecords().get(0);
        approval.approve(zhangsan, decision(pending, 1));

        // 同一用户名下的其它动作行：转办发起、催办、加签发起、其它（回填 OTHER）都不算处理。
        jdbcTemplate.update(
                "INSERT INTO oa_process_record "
                        + "(process_id, description, status, operate_user_id, operate_user, action_type, is_deleted) VALUES "
                        + "(?, '转办', 1, 3, '张三', 'TRANSFER', 0), "
                        + "(?, '已催办', 1, 3, '张三', 'URGE', 0), "
                        + "(?, '加签', 1, 3, '张三', 'ADD_SIGN', 0), "
                        + "(?, '等待审批', 1, 3, '张三', 'OTHER', 0)",
                pending.getId(), pending.getId(), pending.getId(), pending.getId());

        IPage<ProcessVo> processedZhangsan = processed(zhangsan);
        assertEquals(1, processedZhangsan.getTotal(), "白名单只认 APPROVE/REJECT，转办/催办/加签发起不算已处理");
        assertEquals(pending.getId(), processedZhangsan.getRecords().get(0).getId());

        // 发起记录与撤回记录都不是处理：发起人侧已处理保持为空（负匹配若回归，这里必红）。
        jdbcTemplate.update(
                "INSERT INTO oa_process_record "
                        + "(process_id, description, status, operate_user_id, operate_user, action_type, is_deleted) "
                        + "VALUES (?, '已撤回', -2, 2, '小艾', 'WITHDRAW', 0)",
                pending.getId());
        assertEquals(0, processed(alice).getTotal(), "发起申请/撤回/催办记录不算发起人的已处理");
    }

    @Test
    public void withdrawEndsApprovalAsWithdrawnAndPreservesLedgerAttachmentsCc() {
        SysUser alice = user(2L, "alice", "小艾");
        SysUser zhangsan = user(3L, "zhangsan", "张三");
        SysUser ccReader = user(6L, "cc-reader", "抄送阅读人");

        approval.startUp(alice, publishedLeaveForm());
        ProcessVo started = started(alice).getRecords().get(0);
        Long processId = started.getId();
        String instanceId = started.getProcessInstanceId();
        approval.approve(zhangsan, decision(pending(zhangsan).getRecords().get(0), 1));

        // 保留面种子：附件与抄送各一行（对象存储不在本票面，行存在性即合同）。
        jdbcTemplate.update(
                "INSERT INTO oa_attachment (process_id, owner_user_id, storage_id, object_key, file_name, "
                        + "content_type, size_bytes, status, is_deleted) "
                        + "VALUES (?, 2, 'storage-1', 'pending/storage-1', '说明.pdf', 'application/pdf', 31, 'ATTACHED', 0)",
                processId);
        jdbcTemplate.update(
                "INSERT INTO oa_process_cc (process_id, user_id, source_type, source_key, is_deleted) "
                        + "VALUES (?, 6, 'START', ?, 0)", processId, String.valueOf(processId));

        Approval.WithdrawOutcome outcome = approval.withdraw(alice, processId);

        assertEquals(已撤回, outcome.status());
        ProcessVo withdrawn = started(alice).getRecords().get(0);
        assertEquals(已撤回, withdrawn.getStatus(), "已发起列表照常包含已撤回审批");
        assertEquals("审批已撤回", withdrawn.getDescription());

        // 引擎：运行时实例结束，历史落 cancelled，删除原因命名撤回。
        assertEquals(0, runtimeService.createProcessInstanceQuery().processInstanceId(instanceId).count());
        HistoricProcessInstance historic = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(instanceId).singleResult();
        assertEquals("申请人撤回", historic.getDeleteReason());

        // 账本：WITHDRAW 行带动作类型与 -2 状态；时间线读账本自然出现撤回行。
        assertEquals(1L, count("SELECT COUNT(*) FROM oa_process_record WHERE process_id = " + processId
                + " AND action_type = 'WITHDRAW' AND description = '已撤回' AND status = -2 AND operate_user_id = 2"));
        List<String> timeline = recordDescriptions(alice, processId);
        assertEquals("已撤回", timeline.get(timeline.size() - 1));

        // 保留面：已处理行、附件、抄送全部保留；不再有待处理、不能再处理。
        assertEquals(1, processed(zhangsan).getTotal(), "已处理行保留");
        assertEquals(1L, count("SELECT COUNT(*) FROM oa_attachment WHERE process_id = " + processId
                + " AND status = 'ATTACHED' AND is_deleted = 0"), "附件保留");
        assertEquals(1L, count("SELECT COUNT(*) FROM oa_process_cc WHERE process_id = " + processId
                + " AND user_id = 6 AND is_deleted = 0"), "抄送保留");
        assertEquals(0, pending(zhangsan).getTotal());
        assertFalse(canApprove(zhangsan, processId));
        assertEquals(1, carbonCopied(ccReader).getTotal(), "抄送我的照常包含已撤回审批");

        // 不可恢复 + 重复撤回幂等 201：状态档不再变化。
        BusinessException replayed = assertThrows(BusinessException.class,
                () -> approval.withdraw(alice, processId));
        assertEquals(201, replayed.getCode());
        assertEquals("只有审批中的审批可以撤回", replayed.getMessage());
        assertEquals(已撤回, processStatus(processId));
    }

    @Test
    public void withdrawRefusalsAreNamedAndNeverWrite() {
        SysUser alice = user(2L, "alice", "小艾");
        SysUser zhangsan = user(3L, "zhangsan", "张三");

        approval.startUp(alice, publishedLeaveForm());
        ProcessVo started = started(alice).getRecords().get(0);
        Long processId = started.getId();

        BusinessException notApplicant = assertThrows(BusinessException.class,
                () -> approval.withdraw(zhangsan, processId));
        assertEquals(201, notApplicant.getCode());
        assertEquals("只有申请人可以撤回审批", notApplicant.getMessage());

        // 引擎实例已被并发对手结束（通过/拒绝）：撤回译成 201「确定未提交」，什么都没写。
        runtimeService.deleteProcessInstance(started.getProcessInstanceId(), "contract-simulate");
        BusinessException raced = assertThrows(BusinessException.class,
                () -> approval.withdraw(alice, processId));
        assertEquals(201, raced.getCode());
        assertEquals("该审批已被其他人处理", raced.getMessage());
        assertEquals(0L, count("SELECT COUNT(*) FROM oa_process_record WHERE process_id = " + processId
                + " AND action_type = 'WITHDRAW'"), "拒绝路径不写账本");
        assertEquals(审批中, processStatus(processId), "拒绝路径不写状态档");

        // 非审批中：终态（拒绝）之后整条不可撤回。
        approval.startUp(alice, publishedLeaveForm());
        ProcessVo second = started(alice).getRecords().stream()
                .filter(row -> !row.getId().equals(processId)).findFirst().orElseThrow();
        approval.approve(zhangsan, decision(pending(zhangsan).getRecords().get(0), -1));
        BusinessException notActive = assertThrows(BusinessException.class,
                () -> approval.withdraw(alice, second.getId()));
        assertEquals(201, notActive.getCode());
        assertEquals("只有审批中的审批可以撤回", notActive.getMessage());
    }

    @Test
    public void urgeWritesTargetsWithinCooldownAndChangesNothing() {
        SysUser alice = user(2L, "alice", "小艾");
        SysUser zhangsan = user(3L, "zhangsan", "张三");
        SysUser lisi = user(4L, "lisi", "李四");

        approval.startUp(alice, publishedLeaveForm());
        ProcessVo started = started(alice).getRecords().get(0);
        Long processId = started.getId();
        // 候选人来源（ADR 0069 机制）：当前任务补一个未认领候选人，催办对象必须包含他。
        taskService.addUserIdentityLink(
                pending(zhangsan).getRecords().get(0).getTaskId(), "lisi", IdentityLinkType.CANDIDATE);

        approval.urge(alice, processId);

        // URGE 行带对象：当前全部处理人（assignee + 未认领候选人），顿号连接。
        Map<String, Object> urge = jdbcTemplate.queryForMap(
                "SELECT description, status, target_user_name, operate_user_id FROM oa_process_record "
                        + "WHERE process_id = ? AND action_type = 'URGE'", processId);
        assertEquals("已催办", urge.get("description"));
        assertEquals(审批中, ((Number) urge.get("status")).intValue());
        assertEquals("张三、李四", urge.get("target_user_name"));
        assertEquals(2L, ((Number) urge.get("operate_user_id")).longValue());

        // 不改状态、不发通知、不进任何人的已处理；任务原样保留。
        ProcessVo after = started(alice).getRecords().get(0);
        assertEquals(审批中, after.getStatus());
        assertEquals("等待张三审批", after.getDescription());
        assertEquals(0, processed(alice).getTotal());
        assertEquals(0, processed(zhangsan).getTotal());
        assertEquals(1, pending(zhangsan).getTotal());

        // 非申请人 201。
        BusinessException notApplicant = assertThrows(BusinessException.class,
                () -> approval.urge(zhangsan, processId));
        assertEquals(201, notApplicant.getCode());
        assertEquals("只有申请人可以催办审批", notApplicant.getMessage());

        // 24 小时频控：重复催办命名拒绝，不追加 URGE 行。
        BusinessException cooldown = assertThrows(BusinessException.class,
                () -> approval.urge(alice, processId));
        assertEquals(201, cooldown.getCode());
        assertEquals("24 小时内已催办过，请稍后再试", cooldown.getMessage());
        assertEquals(1L, count("SELECT COUNT(*) FROM oa_process_record WHERE process_id = " + processId
                + " AND action_type = 'URGE'"), "频控内不得追加催办记录");
    }

    @Test
    public void urgeTargetsFallBackToUsernameWhenDisplayNameBlankOrUserMissing() {
        SysUser alice = user(2L, "alice", "小艾");
        SysUser zhangsan = user(3L, "zhangsan", "张三");

        approval.startUp(alice, publishedLeaveForm());
        ProcessVo started = pending(zhangsan).getRecords().get(0);
        Long processId = started.getId();
        try {
            jdbcTemplate.update("INSERT INTO sys_user "
                    + "(id, username, password, name, phone, status, is_deleted) "
                    + "VALUES (10, 'blankname', 'x', '', '13800000010', 1, 0)");
            // 当前处理人是空显示名的用户，另加一个查无此人的候选人：两者都退回 username。
            taskService.setAssignee(started.getTaskId(), "blankname");
            taskService.addUserIdentityLink(started.getTaskId(), "ghost", IdentityLinkType.CANDIDATE);

            approval.urge(alice, processId);

            String targets = jdbcTemplate.queryForObject(
                    "SELECT target_user_name FROM oa_process_record "
                            + "WHERE process_id = ? AND action_type = 'URGE'", String.class, processId);
            assertEquals("blankname、ghost", targets, "空显示名与缺用户都退回 username");
        } finally {
            jdbcTemplate.update("DELETE FROM sys_user WHERE id = 10");
        }
    }

    @Test
    public void httpWithdrawAndUrgeEnvelopesPinRefusalMessages() throws Exception {
        String aliceToken = loginToken("alice");
        String zhangsanToken = loginToken("zhangsan");

        approval.startUp(user(2L, "alice", "小艾"), publishedLeaveForm());
        Long processId = started(user(2L, "alice", "小艾")).getRecords().get(0).getId();

        mockMvc.perform(post("/admin/process/urge/" + processId).header("token", aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post("/admin/process/urge/" + processId).header("token", aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("24 小时内已催办过，请稍后再试"));
        mockMvc.perform(post("/admin/process/withdraw/" + processId).header("token", zhangsanToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("只有申请人可以撤回审批"));
        mockMvc.perform(post("/admin/process/withdraw/" + processId).header("token", aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post("/admin/process/withdraw/" + processId).header("token", aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("只有审批中的审批可以撤回"));
    }

    @TestConfiguration
    static class InMemoryRedisConfig {
        @Bean
        @Primary
        public org.springframework.data.redis.core.RedisTemplate<String, String> redisTemplate() {
            // MockMvc 登录链的限流/会话读写在进程内完成（与 LoginAuthContractTest 同款夹具）。
            return new com.lautung.oa.support.InMemoryRedisTemplate();
        }
    }

    // ---- 夹具与 helpers（与 ApprovalContractTest 同款形状） ----

    /** 只重放 V136 的回填 UPDATE（回填幂等可重放；ALTER 只在 Flyway 首跑执行）。 */
    private void replayV136Backfill() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream(
                "/db/migration/V136__process_record_action_type.sql")) {
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            String body = Arrays.stream(sql.split("\n"))
                    .filter(line -> !line.strip().startsWith("--"))
                    .collect(Collectors.joining("\n"));
            for (String statement : body.split(";")) {
                String trimmed = statement.strip();
                if (trimmed.toUpperCase().startsWith("UPDATE")) {
                    jdbcTemplate.execute(trimmed);
                }
            }
        }
    }

    private void assertBackfilled() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT description, action_type FROM oa_process_record WHERE process_id = 901 "
                        + "ORDER BY id");
        assertEquals(6, rows.size());
        assertEquals("INITIATE", rows.get(0).get("action_type"));
        assertEquals("APPROVE", rows.get(1).get("action_type"));
        assertEquals("REJECT", rows.get(2).get("action_type"));
        assertEquals("OTHER", rows.get(3).get("action_type"));
        assertEquals("OTHER", rows.get(4).get("action_type"));
        assertEquals("URGE", rows.get(5).get("action_type"), "已有动作类型的行不得被回填覆盖");
    }

    private boolean canApprove(SysUser user, Long processId) {
        try {
            return Boolean.TRUE.equals(approval.show(user, processId).get("isApprove"));
        } catch (BusinessException e) {
            if (e.getCode() == 403) {
                return false;
            }
            throw e;
        }
    }

    private List<String> recordDescriptions(SysUser user, Long processId) {
        @SuppressWarnings("unchecked")
        List<ProcessRecord> records =
                (List<ProcessRecord>) approval.show(user, processId).get("processRecordList");
        return records.stream().map(ProcessRecord::getDescription).toList();
    }

    private int processStatus(Long processId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM oa_process WHERE id = ?", Integer.class, processId);
    }

    private long count(String sql) {
        Long result = jdbcTemplate.queryForObject(sql, Long.class);
        return result == null ? 0L : result;
    }

    private String loginToken(String username) throws Exception {
        String loginBody = mockMvc.perform(post("/admin/system/index/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"111111\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(loginBody, "$.data.token");
    }

    private IPage<ProcessVo> started(SysUser user) {
        return approvalListReader.started(new Page<>(1, 10), user);
    }

    private IPage<ProcessVo> pending(SysUser user) {
        return approvalListReader.pending(new Page<>(1, 10), user);
    }

    private IPage<ProcessVo> processed(SysUser user) {
        return approvalListReader.processed(new Page<>(1, 10), user);
    }

    private IPage<ProcessVo> carbonCopied(SysUser user) {
        return approvalListReader.carbonCopied(new Page<>(1, 10), user);
    }

    private ApprovalVo decision(ProcessVo pending, int status) {
        ApprovalVo approvalVo = new ApprovalVo();
        approvalVo.setProcessId(pending.getId());
        approvalVo.setTaskId(pending.getTaskId());
        approvalVo.setStatus(status);
        return approvalVo;
    }

    private static ProcessFormVo publishedLeaveForm() {
        ProcessFormVo form = new ProcessFormVo();
        form.setProcessTemplateId(1L);
        form.setProcessTypeId(1L);
        form.setFormValues("{\"formData\":{\"days\":1},\"formShowData\":{\"请假天数\":1}}");
        return form;
    }

    private static SysUser user(Long id, String username, String name) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        user.setName(name);
        return user;
    }

    private void clearEngineInstances() {
        runtimeService.createProcessInstanceQuery().list()
                .forEach(instance -> runtimeService.deleteProcessInstance(instance.getId(), "contract-reset"));
        historyService.createHistoricProcessInstanceQuery().list()
                .forEach(instance -> historyService.deleteHistoricProcessInstance(instance.getId()));
    }

    private static byte[] approvalTwoStepZip() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (InputStream bpmn = ApprovalActionContractTest.class.getClassLoader()
                     .getResourceAsStream("process/approval-two-step.bpmn20.xml");
             java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(bytes)) {
            if (bpmn == null) throw new IllegalStateException("审批合同 BPMN 不存在");
            zip.putNextEntry(new java.util.zip.ZipEntry("approval-two-step.bpmn20.xml"));
            bpmn.transferTo(zip);
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }
}
