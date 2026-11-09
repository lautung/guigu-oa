package com.lautung.oa.process.approval;

import com.lautung.oa.common.jwt.JwtHelper;
import com.lautung.oa.framework.config.exception.BusinessException;
import com.lautung.oa.model.process.Process;
import com.lautung.oa.model.process.ProcessTemplate;
import com.lautung.oa.model.system.SysUser;
import com.lautung.oa.process.ProcessApprovalSupport;
import com.lautung.oa.process.mapper.OaProcessMapper;
import com.lautung.oa.process.membership.ApprovalMembershipResolver;
import com.lautung.oa.process.read.ApprovalListReader;
import com.lautung.oa.process.service.Approval;
import com.lautung.oa.process.service.ApprovalTemplate;
import com.lautung.oa.process.service.OaProcessApproverRuleService;
import com.lautung.oa.vo.process.ApproverRuleRequest;
import com.lautung.oa.vo.process.ApprovalVo;
import com.lautung.oa.vo.process.ProcessFormVo;
import com.lautung.oa.vo.process.ProcessVo;
import org.flowable.engine.RuntimeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 动态审批人解析合同（ADR 0069，#285）：四来源语义、自审保留与自审回避、多候选人
 * 认领与并发后到拒绝、首节点/中途解析失败显式、旧模板无 sidecar no-op、
 * 解析只读部署里冻结的 sidecar 不读当前规则。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "lautung.oa.process-definition-dir=target/test-process-definitions")
@Sql({"/auth-contract-schema.sql", "/approval-contract-schema.sql", "/approver-contract-schema.sql"})
class ApproverResolutionContractTest {

    private static final int 审批中 = 1;
    private static final int 审批通过 = 2;
    private static final int 通过 = 1;

    @Autowired
    private Approval approval;

    @Autowired
    private ApprovalTemplate approvalTemplate;

    @Autowired
    private OaProcessApproverRuleService approverRuleService;

    @Autowired
    private ApprovalMembershipResolver membershipResolver;

    @Autowired
    private ApprovalListReader approvalListReader;

    @Autowired
    private ApproverTaskAssigner approverTaskAssigner;

    @Autowired
    private OaProcessMapper processMapper;

    @Autowired
    private ApprovalEngine approvalEngine;

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtHelper jwtHelper;

    @Autowired
    private MockMvc mockMvc;

    @TestConfiguration
    static class InMemoryRedisConfig {
        @Bean
        @Primary
        public RedisTemplate<String, String> redisTemplate() {
            return new com.lautung.oa.support.InMemoryRedisTemplate();
        }
    }

    @BeforeEach
    void resetOrganizationFixtures() {
        // 目标失效用例会改组织数据：每个用例前恢复夹具，用例之间互不污染。
        jdbcTemplate.update("UPDATE sys_post SET status = 1 WHERE id = 811");
        jdbcTemplate.update("UPDATE sys_role SET is_deleted = 0 WHERE id IN (821, 822, 823, 825)");
        jdbcTemplate.update("UPDATE sys_dept SET status = 1 WHERE id = 801");
        jdbcTemplate.update("UPDATE sys_user SET status = 1 WHERE id = 3");
    }

    @AfterEach
    void clearEngineInstances() {
        runtimeService.createProcessInstanceQuery().list()
                .forEach(instance -> runtimeService.deleteProcessInstance(instance.getId(), "contract-reset"));
    }

    @Test
    public void deptLeaderResolvesToTheInitiatorDepartmentLeader() {
        long templateId = publishTemplate("部门负责人模板",
                List.of(rule("first", "DEPT_LEADER", null), rule("second", "ROLE", 821L)));

        // lisi 在研发部（801），负责人是 zhangsan：首任务直接指派。
        approval.startUp(user(4L, "lisi", "李四"), form(templateId));

        assertTrue(canAct(3L, "zhangsan"), "部门负责人应能处理");
        assertFalse(canAct(4L, "lisi"), "申请人不是部门负责人不能处理");
        assertEquals("等待张三审批", startedDescription());

        approve(3L, "zhangsan");
        // 第二节点角色 821 {zhangsan,lisi} 剔除申请人 lisi → {zhangsan}。
        assertTrue(canAct(3L, "zhangsan"));
        approve(3L, "zhangsan");
        assertEquals(审批通过, startedStatus());
    }

    @Test
    public void deptLeaderWhoIsTheApplicantKeepsSelfReview() {
        long templateId = publishTemplate("自审保留模板",
                List.of(rule("first", "DEPT_LEADER", null), rule("second", "ROLE", 821L)));

        // alice 在自审部门（804，负责人是自己）：保留自审，硬失败会让负责人无法提交任何申请。
        approval.startUp(user(2L, "alice", "小艾"), form(templateId));

        assertTrue(canAct(2L, "alice"));
        approve(2L, "alice");
    }

    @Test
    public void unresolvableFirstNodeFailsTheStartExplicitlyAndLeavesNothingBehind() {
        long templateId = publishTemplate("无负责人模板",
                List.of(rule("first", "DEPT_LEADER", null), rule("second", "ROLE", 821L)));

        // eve 在无主部门（802，无负责人）：首节点解析不到 → 发起失败。
        BusinessException failure = assertThrows(BusinessException.class,
                () -> approval.startUp(user(5L, "eve", "小叶"), form(templateId)));
        assertEquals("第 1 步审批人无法解析（部门负责人）", failure.getMessage());

        // 发起失败=什么都没发生：不留审批行、不留账本记录。
        assertEquals(0L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM oa_process WHERE process_template_id = ?", Long.class, templateId));
        assertEquals(0L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM oa_process_record WHERE process_id IN "
                        + "(SELECT id FROM oa_process WHERE process_template_id = ?)",
                Long.class, templateId));
    }

    @Test
    public void httpStartRejectsUnresolvableFirstNodeWithNamedMessage() throws Exception {
        long templateId = publishTemplate("HTTP失败模板",
                List.of(rule("first", "DEPT_LEADER", null), rule("second", "ROLE", 821L)));

        mockMvc.perform(post("/admin/process/startUp")
                        .header("token", token(5L, "eve"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"processTemplateId\":" + templateId
                                + ",\"processTypeId\":1,\"formValues\":\"{\\\"formData\\\":{\\\"days\\\":1}}\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("第 1 步审批人无法解析（部门负责人）"));
    }

    @Test
    public void roleResolvesToEnabledMembersMinusApplicantAsClaimableCandidates() {
        long templateId = publishTemplate("角色候选模板",
                List.of(rule("first", "ROLE", 823L), rule("second", "ROLE", 821L)));

        // 角色三人组 {zhangsan,lisi,eve}，申请人 alice 不在其中：三人都是候选，任一处理即认领。
        approval.startUp(user(2L, "alice", "小艾"), form(templateId));

        assertTrue(canAct(3L, "zhangsan"));
        assertTrue(canAct(4L, "lisi"));
        assertTrue(canAct(5L, "eve"));
        assertFalse(canAct(2L, "alice"));

        // 打开详情不认领：alice 只读，处理动作才是认领。
        approval.show(user(2L, "alice", "小艾"), latestProcessId());
        assertEquals(审批中, startedStatus());

        // zhangsan 处理即认领：首任务被处理，其余候选人对该任务不再能处理。
        approve(3L, "zhangsan");
        assertEquals(审批中, startedStatus());

        // 第二节点 {zhangsan,lisi}（alice 非成员）：lisi 处理。
        assertTrue(canAct(4L, "lisi"));
        approve(4L, "lisi");
        assertEquals(审批通过, startedStatus());
    }

    @Test
    public void postResolvesSameWayAsRole() {
        long templateId = publishTemplate("岗位候选模板",
                List.of(rule("first", "POST", 811L), rule("second", "POST", 811L)));

        // 岗位 {zhangsan,lisi}，申请人 alice 不在：两人都是候选。
        approval.startUp(user(2L, "alice", "小艾"), form(templateId));

        assertTrue(canAct(3L, "zhangsan"));
        assertTrue(canAct(4L, "lisi"));
        approve(3L, "zhangsan");
        approve(3L, "zhangsan");
        assertEquals(审批通过, startedStatus());
    }

    @Test
    public void roleOrPostResolvingToASingleMemberIsAssignedDirectly() {
        // ADR 0069 字面分叉：按解析人数——解析成一人 assignee 直派（不再标「候选人之一」），多人才候选下发。
        long roleTemplateId = publishTemplate("角色单人直派模板",
                List.of(rule("first", "ROLE", 821L), rule("second", "ROLE", 821L)));

        // 角色 821 {zhangsan,lisi}，申请人 lisi：剔除申请人后只剩 zhangsan 一人 → 直派。
        approval.startUp(user(4L, "lisi", "李四"), form(roleTemplateId));
        assertSingleAssignee("zhangsan");
        approve(3L, "zhangsan");
        approve(3L, "zhangsan");
        assertEquals(审批通过, startedStatus());

        long postTemplateId = publishTemplate("岗位单人直派模板",
                List.of(rule("first", "POST", 811L), rule("second", "POST", 811L)));

        // 岗位 811 {zhangsan,lisi}，申请人 lisi：同按解析人数分叉。
        approval.startUp(user(4L, "lisi", "李四"), form(postTemplateId));
        assertSingleAssignee("zhangsan");
        approve(3L, "zhangsan");
        approve(3L, "zhangsan");
        assertEquals(审批通过, startedStatus());
    }

    /** 解析成一人：引擎任务有 assignee、无候选，待处理行是 APPROVE 直派面。 */
    private void assertSingleAssignee(String expectedAssignee) {
        List<ApprovalEngine.CurrentTask> tasks = currentTasks(latestProcessId());
        assertEquals(expectedAssignee, tasks.get(0).assignee(), "解析成一人应直接指派 assignee");
        assertTrue(tasks.get(0).candidates().isEmpty(), "单人解析不得按候选下发");
        Process process = processMapper.selectById(latestProcessId());
        ProcessVo pendingRow = approvalListReader.pendingRow(user(3L, "zhangsan", "张三"), process);
        assertEquals(ProcessApprovalSupport.ACTION_KIND_APPROVE, pendingRow.getActionKind(),
                "单人直派的待处理行不得再标「候选人之一」");
    }

    @Test
    public void roleWithOnlyTheApplicantFailsTheStart() {
        long templateId = publishTemplate("自审回避模板",
                List.of(rule("first", "ROLE", 825L), rule("second", "ROLE", 821L)));

        // 角色 825 只有 alice 自己：剔除申请人后为空 → 解析失败（自审回避）。
        BusinessException failure = assertThrows(BusinessException.class,
                () -> approval.startUp(user(2L, "alice", "小艾"), form(templateId)));
        assertEquals("第 1 步审批人无法解析（角色）", failure.getMessage());
    }

    @Test
    public void midwayUnresolvableNodeFailsThatDecisionNamingTheNode() {
        // first=BPMN 写死 zhangsan（无规则），second=规则角色 825（只有 alice）。
        long templateId = publishMidwayTemplate("中途失败模板", List.of(rule("second", "ROLE", 825L)));

        approval.startUp(user(2L, "alice", "小艾"), form(templateId));
        assertTrue(canAct(3L, "zhangsan"));

        // 中途节点解析不到 → 该次处理失败并指明节点；处理不生效（事务回滚）。
        BusinessException failure = assertThrows(BusinessException.class, () -> approve(3L, "zhangsan"));
        assertEquals("第 2 步审批人无法解析（角色）", failure.getMessage());

        assertEquals(审批中, startedStatus());
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM oa_process_record WHERE process_id = ?", Long.class, latestProcessId()),
                "失败的处理不得留下账本记录");
        // 组织修好后重试：同一任务再处理即继续（second 节点按修好的组织解析出 zhangsan）。
        jdbcTemplate.update("INSERT INTO sys_user_role (role_id, user_id, is_deleted) VALUES (825, 3, 0)");
        approve(3L, "zhangsan");
        assertEquals(审批中, startedStatus());
        approve(3L, "zhangsan");
        assertEquals(审批通过, startedStatus());
    }

    @Test
    public void legacyPublishedTemplateWithoutSidecarKeepsBpmnAssigneeBehavior() {
        // 模板 1（approvalTwoStep）无规则发布 → 无 sidecar：即使规则行存在也 no-op。
        jdbcTemplate.update("UPDATE oa_process_template SET status = 0 WHERE id = 1");
        var uploaded = upload("approvalTwoStep", resourceZip("process/approval-two-step.bpmn20.xml"));
        ProcessTemplate template = approvalTemplate.read(1L);
        template.setProcessDefinitionKey(uploaded.processDefinitionKey());
        template.setProcessDefinitionPath(uploaded.processDefinitionPath());
        approvalTemplate.saveUnpublished(template);
        approvalTemplate.publish(1L);
        jdbcTemplate.update(
                "INSERT INTO oa_process_approver_rule (process_template_id, task_definition_key, source_type, target_id, is_deleted) "
                        + "VALUES (1, 'first', 'ROLE', 825, 0)");

        approval.startUp(user(2L, "alice", "小艾"), form(1L));

        // BPMN assignee 照旧：zhangsan 处理首节点，lisi 接第二节点，无候选语义介入。
        assertTrue(canAct(3L, "zhangsan"));
        assertFalse(canAct(4L, "lisi"));
        approve(3L, "zhangsan");
        assertTrue(canAct(4L, "lisi"));
    }

    @Test
    public void resolutionTreatsSoftDeletedProcessRowsAsMissingInitiation() {
        // ADR 0050：监听器的 oa_process 直读不吃 @TableLogic，存活过滤必须显式写进 SQL；
        // 已删行不得解析出发起人，按「缺少发起记录」命名失败。
        long templateId = publishTemplate("软删直读模板",
                List.of(rule("first", "ROLE", 821L), rule("second", "ROLE", 821L)));
        approval.startUp(user(2L, "alice", "小艾"), form(templateId));
        Long processId = latestProcessId();
        String taskId = currentTasks(processId).get(0).taskId();
        String instanceId = jdbcTemplate.queryForObject(
                "SELECT process_instance_id FROM oa_process WHERE id = ?", String.class, processId);
        String definitionId = runtimeService.createProcessInstanceQuery()
                .processInstanceId(instanceId).singleResult().getProcessDefinitionId();

        jdbcTemplate.update("UPDATE oa_process SET is_deleted = 1 WHERE id = ?", processId);
        BusinessException failure = assertThrows(BusinessException.class, () ->
                approverTaskAssigner.applyOnTaskCreated(new ApproverTaskAssigner.Trigger(
                        definitionId, "first", taskId, String.valueOf(processId))));
        assertEquals("审批人无法解析：审批缺少发起记录", failure.getMessage());
    }

    @Test
    public void resolutionReadsTheFrozenSidecarNotTheCurrentRules() {
        long templateId = publishTemplate("冻结解析模板",
                List.of(rule("first", "DEPT_LEADER", null), rule("second", "ROLE", 821L)));

        // 发布后改当前规则行：运行期解析必须仍按部署里冻结的 sidecar（不读当前模板）。
        jdbcTemplate.update(
                "UPDATE oa_process_approver_rule SET source_type = 'ROLE', target_id = 825 "
                        + "WHERE process_template_id = ? AND task_definition_key = 'first'",
                templateId);

        // 若读了当前规则（ROLE 825 只有 alice），首任务会被解析成 alice；
        // 冻结 sidecar（DEPT_LEADER）解析出的是 lisi 所在部门的负责人 zhangsan。
        approval.startUp(user(4L, "lisi", "李四"), form(templateId));
        assertTrue(canAct(3L, "zhangsan"));
        assertFalse(canAct(2L, "alice"));
    }

    @Test
    public void concurrentCandidateClaimsHaveExactlyOneCommittedWinner() throws Exception {
        long templateId = publishTemplate("并发候选模板",
                List.of(rule("first", "ROLE", 821L), rule("second", "ROLE", 821L)));

        for (int attempt = 0; attempt < 2; attempt++) {
            approval.startUp(user(2L, "alice", "小艾"), form(templateId));
            Long processId = latestProcessId();
            String taskId = currentTasks(processId).get(0).taskId();

            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                SysUser zhangsan = user(3L, "zhangsan", "张三");
                SysUser lisi = user(4L, "lisi", "李四");
                Future<Throwable> first = executor.submit(() -> decideCapturingFailure(zhangsan, processId, taskId));
                Future<Throwable> second = executor.submit(() -> decideCapturingFailure(lisi, processId, taskId));

                Throwable firstFailure = first.get(15, TimeUnit.SECONDS);
                Throwable secondFailure = second.get(15, TimeUnit.SECONDS);
                int successes = (firstFailure == null ? 1 : 0) + (secondFailure == null ? 1 : 0);
                assertEquals(1, successes, "同一候选任务只能有一个候选人提交成功");

                Throwable losingFailure = firstFailure == null ? secondFailure : firstFailure;
                assertTrue(losingFailure instanceof BusinessException,
                        "并发后到者按业务失败拒绝（已被其他人处理）");
                assertEquals(审批中, jdbcTemplate.queryForObject(
                        "SELECT status FROM oa_process WHERE id = ?", Integer.class, processId));
            } finally {
                executor.shutdownNow();
                assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
            }
        }
    }

    private Throwable decideCapturingFailure(SysUser user, Long processId, String taskId) {
        try {
            ApprovalVo approvalVo = new ApprovalVo();
            approvalVo.setProcessId(processId);
            approvalVo.setTaskId(taskId);
            approvalVo.setStatus(通过);
            approval.approve(user, approvalVo);
            return null;
        } catch (Throwable failure) {
            return failure;
        }
    }

    private List<ApprovalEngine.CurrentTask> currentTasks(Long processId) {
        String instanceId = jdbcTemplate.queryForObject(
                "SELECT process_instance_id FROM oa_process WHERE id = ?", String.class, processId);
        return approvalEngine.currentTasks(instanceId);
    }

    /** 当前轮到 username 处理（assignee 或候选用户之一，ADR 0069 处理动作本身认领）。 */
    private boolean canAct(long userId, String username) {
        Process process = processMapper.selectById(latestProcessId());
        if (process == null) {
            return false;
        }
        var membership = membershipResolver.resolve(user(userId, username, username), process);
        return currentTasks(process.getId()).stream().anyMatch(task -> membership.canAct(task.taskId()));
    }

    private void approve(long userId, String username) {
        Long processId = latestProcessId();
        String taskId = currentTasks(processId).get(0).taskId();
        ApprovalVo approvalVo = new ApprovalVo();
        approvalVo.setProcessId(processId);
        approvalVo.setTaskId(taskId);
        approvalVo.setStatus(通过);
        approval.approve(user(userId, username, username), approvalVo);
    }

    private int startedStatus() {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM oa_process WHERE id = ?", Integer.class, latestProcessId());
    }

    private String startedDescription() {
        return jdbcTemplate.queryForObject(
                "SELECT description FROM oa_process WHERE id = ?", String.class, latestProcessId());
    }

    private Long latestProcessId() {
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM oa_process", Long.class);
    }

    private SysUser user(long id, String username, String name) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        user.setName(name);
        return user;
    }

    private ProcessFormVo form(long templateId) {
        ProcessFormVo form = new ProcessFormVo();
        form.setProcessTemplateId(templateId);
        form.setProcessTypeId(1L);
        form.setFormValues("{\"formData\":{\"days\":1}}");
        return form;
    }

    private static ApproverRuleRequest rule(String taskDefinitionKey, String sourceType, Long targetId) {
        ApproverRuleRequest request = new ApproverRuleRequest();
        request.setTaskDefinitionKey(taskDefinitionKey);
        request.setSourceType(sourceType);
        request.setTargetId(targetId);
        return request;
    }

    private long publishTemplate(String name, List<ApproverRuleRequest> rules) {
        return saveRulesAndPublish(name, upload("approverRuleFlow", flowZip()), rules);
    }

    /** first=BPMN 写死 zhangsan、second 无 assignee 的两步流程（中途失败用例专用）。 */
    private long publishMidwayTemplate(String name, List<ApproverRuleRequest> rules) {
        return saveRulesAndPublish(name,
                upload("approverMidwayFlow", resourceZip("process/approver-midway-flow.bpmn20.xml")), rules);
    }

    private ApprovalTemplate.UploadedProcessDefinition upload(String key, byte[] zipBytes) {
        try {
            return approvalTemplate.uploadProcessDefinition(
                    new MockMultipartFile("file", key + ".zip", "application/zip", zipBytes));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private long saveRulesAndPublish(
            String name, ApprovalTemplate.UploadedProcessDefinition uploaded, List<ApproverRuleRequest> rules) {
        ProcessTemplate template = new ProcessTemplate();
        template.setName(name);
        template.setProcessTypeId(1L);
        template.setProcessDefinitionKey(uploaded.processDefinitionKey());
        template.setProcessDefinitionPath(uploaded.processDefinitionPath());
        approvalTemplate.saveUnpublished(template);
        Long templateId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM oa_process_template WHERE name = ?", Long.class, name);
        approverRuleService.replaceRules(templateId, rules);
        approvalTemplate.publish(templateId);
        return templateId;
    }

    private static byte[] flowZip() {
        return resourceZip("process/approver-rule-flow.bpmn20.xml");
    }

    private static byte[] resourceZip(String resource) {
        try (InputStream bpmn = ApproverResolutionContractTest.class.getClassLoader().getResourceAsStream(resource)) {
            if (bpmn == null) {
                throw new IllegalStateException("缺少测试流程定义 " + resource);
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
                zip.putNextEntry(new ZipEntry(resource.substring(resource.lastIndexOf('/') + 1)));
                bpmn.transferTo(zip);
                zip.closeEntry();
            }
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private String token(long userId, String username) {
        return jwtHelper.createTokenPair(userId, username, 0L).token();
    }
}
