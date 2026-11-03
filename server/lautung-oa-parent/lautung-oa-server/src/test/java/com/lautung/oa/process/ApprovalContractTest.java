package com.lautung.oa.process;

import com.lautung.oa.model.process.Attachment;
import com.lautung.oa.model.process.Process;
import com.lautung.oa.model.process.ProcessRecord;
import com.lautung.oa.model.process.ProcessTemplate;
import com.lautung.oa.model.system.SysUser;
import com.lautung.oa.process.ProcessApprovalSupport;
import com.lautung.oa.process.approval.FlowableApprovalEngine;
import com.lautung.oa.process.approval.TestApprovalEngine;
import com.lautung.oa.process.attachment.AttachmentMapper;
import com.lautung.oa.process.attachment.TestAttachmentObjectStore;
import com.lautung.oa.process.cc.CarbonCopyTrigger;
import com.lautung.oa.process.cc.CarbonCopyWriter;
import com.lautung.oa.process.mapper.OaProcessMapper;
import com.lautung.oa.process.read.ApprovalListReader;
import com.lautung.oa.process.service.Approval;
import com.lautung.oa.process.service.ApprovalTemplate;
import com.lautung.oa.framework.config.exception.BusinessException;
import com.lautung.oa.security.custom.LoginUserInfoHelper;
import com.lautung.oa.vo.process.ApprovalVo;
import com.lautung.oa.vo.process.ProcessFormVo;
import com.lautung.oa.vo.process.ProcessVo;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.common.engine.api.delegate.event.AbstractFlowableEventListener;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.common.engine.api.delegate.event.FlowableEntityEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEventType;
import org.flowable.task.api.Task;
import org.springframework.jdbc.core.JdbcTemplate;
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
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.jayway.jsonpath.JsonPath;

import java.lang.reflect.Proxy;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql({"/auth-contract-schema.sql", "/approval-contract-schema.sql"})
public class ApprovalContractTest {

    private static final int 审批中 = 1;
    private static final int 审批通过 = 2;
    private static final int 审批拒绝 = -1;
    private static final int 通过 = 1;
    private static final int 拒绝 = -1;

    @Autowired
    private Approval approval;

    @Autowired
    private ApprovalListReader approvalListReader;

    @Autowired
    private ApprovalTemplate approvalTemplate;

    @Autowired
    private CarbonCopyWriter carbonCopyWriter;

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
    private AttachmentMapper attachmentMapper;

    @Autowired
    private OaProcessMapper processMapper;

    @Autowired
    private TestAttachmentObjectStore objectStore;

    @Autowired
    private TestApprovalEngine testApprovalEngine;

    @Autowired
    private ApprovalTransactionCcProbe approvalTransactionCcProbe;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    public void deployPublishedTemplateEngineDefinition() throws IOException {
        LoginUserInfoHelper.removeUserId();
        LoginUserInfoHelper.removeUsername();
        clearEngineInstances();
        objectStore.clear();
        testApprovalEngine.reset();
        runtimeService.removeEventListener(approvalTransactionCcProbe);
        approvalTransactionCcProbe.reset();
        runtimeService.addEventListener(approvalTransactionCcProbe, FlowableEngineEventType.TASK_CREATED);
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

    @AfterEach
    public void clearLoginThreadLocal() {
        runtimeService.removeEventListener(approvalTransactionCcProbe);
        testApprovalEngine.reset();
        LoginUserInfoHelper.removeUserId();
        LoginUserInfoHelper.removeUsername();
    }

    @Test
    public void afterStartApprovalAppearsIn已发起As审批中AndIn待处理WithCurrentTaskHandle() {
        assertNull(LoginUserInfoHelper.getUserId());
        assertNull(LoginUserInfoHelper.getUsername());

        SysUser alice = user(2L, "alice", "小艾");
        SysUser zhangsan = user(3L, "zhangsan", "张三");

        approval.startUp(alice, publishedLeaveForm());

        List<ProcessVo> startedByAlice = started(alice).getRecords();
        assertEquals(1, startedByAlice.size());
        ProcessVo started = startedByAlice.get(0);
        assertEquals(审批中, started.getStatus());
        assertEquals(alice.getId(), started.getUserId());
        assertEquals("小艾发起请假申请申请", started.getTitle());
        assertEquals("等待张三审批", started.getDescription());

        List<ProcessVo> pendingForZhangsan = pending(zhangsan).getRecords();
        assertEquals(1, pendingForZhangsan.size());
        ProcessVo pending = pendingForZhangsan.get(0);
        assertEquals(started.getId(), pending.getId());
        assertTrue(pending.getTaskId() != null && !pending.getTaskId().isBlank());
        assertNotEquals("0", pending.getTaskId());

        assertTrue(pending(alice).getRecords().isEmpty());
        assertTrue(started(zhangsan).getRecords().isEmpty());
        assertNull(LoginUserInfoHelper.getUserId());
        assertNull(LoginUserInfoHelper.getUsername());
    }

    @Test
    public void afterCurrentHandlerApprovesItAppearsIn已处理AndStays审批中WhenNext处理人Remains() {
        assertNull(LoginUserInfoHelper.getUserId());

        SysUser alice = user(2L, "alice", "小艾");
        SysUser zhangsan = user(3L, "zhangsan", "张三");
        SysUser lisi = user(4L, "lisi", "李四");

        approval.startUp(alice, publishedLeaveForm());
        ProcessVo pending = pending(zhangsan).getRecords().get(0);

        assertTrue(canApprove(zhangsan, pending.getId()));
        assertFalse(canApprove(alice, pending.getId()));
        assertFalse(canApprove(lisi, pending.getId()));

        approve(zhangsan, pending);

        List<ProcessVo> processedByZhangsan = processed(zhangsan).getRecords();
        assertEquals(1, processedByZhangsan.size());
        assertEquals(pending.getId(), processedByZhangsan.get(0).getId());
        assertEquals(审批中, processedByZhangsan.get(0).getStatus());
        assertTrue(processed(alice).getRecords().isEmpty());
        assertTrue(pending(zhangsan).getRecords().isEmpty());
        assertEquals(1, pending(lisi).getRecords().size());
        assertEquals(审批中, started(alice).getRecords().get(0).getStatus());
        assertEquals("等待李四审批", started(alice).getRecords().get(0).getDescription());

        assertEquals(List.of("发起申请", "已通过"), recordDescriptions(zhangsan, pending.getId()));
        assertFalse(canApprove(zhangsan, pending.getId()));
        assertTrue(canApprove(lisi, pending.getId()));
        assertNull(LoginUserInfoHelper.getUserId());
    }

    @Test
    public void afterLastHandlerApprovesApprovalIs审批通过AndAppearsIn已处理() {
        SysUser alice = user(2L, "alice", "小艾");
        SysUser zhangsan = user(3L, "zhangsan", "张三");
        SysUser lisi = user(4L, "lisi", "李四");

        approval.startUp(alice, publishedLeaveForm());
        approve(zhangsan, pending(zhangsan).getRecords().get(0));

        ProcessVo pendingForLisi = pending(lisi).getRecords().get(0);
        approve(lisi, pendingForLisi);

        List<ProcessVo> processedByLisi = processed(lisi).getRecords();
        assertEquals(1, processedByLisi.size());
        assertEquals(pendingForLisi.getId(), processedByLisi.get(0).getId());
        assertEquals(审批通过, processedByLisi.get(0).getStatus());
        assertEquals(1, processed(zhangsan).getRecords().size());
        assertEquals(审批通过, processed(zhangsan).getRecords().get(0).getStatus());
        assertEquals(审批通过, started(alice).getRecords().get(0).getStatus());
        assertTrue(pending(lisi).getRecords().isEmpty());
        assertFalse(canApprove(lisi, pendingForLisi.getId()));
        assertFalse(canApprove(zhangsan, pendingForLisi.getId()));
        assertEquals(List.of("发起申请", "已通过", "已通过"), recordDescriptions(lisi, pendingForLisi.getId()));
        assertNull(LoginUserInfoHelper.getUserId());
    }

    @Test
    public void afterCurrentHandlerRejectsApprovalIs审批拒绝AndAppearsIn已处理() {
        SysUser alice = user(2L, "alice", "小艾");
        SysUser zhangsan = user(3L, "zhangsan", "张三");
        SysUser lisi = user(4L, "lisi", "李四");

        approval.startUp(alice, publishedLeaveForm());
        ProcessVo pending = pending(zhangsan).getRecords().get(0);

        reject(zhangsan, pending);

        List<ProcessVo> processedByZhangsan = processed(zhangsan).getRecords();
        assertEquals(1, processedByZhangsan.size());
        assertEquals(pending.getId(), processedByZhangsan.get(0).getId());
        assertEquals(审批拒绝, processedByZhangsan.get(0).getStatus());
        assertEquals(审批拒绝, started(alice).getRecords().get(0).getStatus());
        assertTrue(processed(alice).getRecords().isEmpty());
        assertTrue(pending(zhangsan).getRecords().isEmpty());
        assertTrue(pending(lisi).getRecords().isEmpty());
        assertEquals(List.of("发起申请", "已拒绝"), recordDescriptions(zhangsan, pending.getId()));
        assertFalse(canApprove(zhangsan, pending.getId()));
        assertFalse(canApprove(lisi, pending.getId()));
        assertFalse(canApprove(alice, pending.getId()));
        assertNull(LoginUserInfoHelper.getUserId());
    }

    @Test
    public void 已处理And已发起Identify审批WithoutFakeTaskHandle() {
        assertNull(LoginUserInfoHelper.getUserId());

        SysUser alice = user(2L, "alice", "小艾");
        SysUser zhangsan = user(3L, "zhangsan", "张三");

        approval.startUp(alice, publishedLeaveForm());

        ProcessVo started = started(alice).getRecords().get(0);
        assertNoFakeTaskHandle(started);
        assertFalse(approval.show(alice, started.getId()).containsKey("taskId"));

        ProcessVo pending = pending(zhangsan).getRecords().get(0);
        assertTrue(pending.getTaskId() != null && !pending.getTaskId().isBlank());
        assertNotEquals("0", pending.getTaskId());

        approve(zhangsan, pending);

        ProcessVo processed = processed(zhangsan).getRecords().get(0);
        assertEquals(started.getId(), processed.getId());
        assertNoFakeTaskHandle(processed);
        assertNoFakeTaskHandle(started(alice).getRecords().get(0));
        assertFalse(approval.show(zhangsan, processed.getId()).containsKey("taskId"));
        assertNull(LoginUserInfoHelper.getUserId());
    }

    @Test
    public void 详情ShowsTitleStatusFormAnd流转记录() {
        SysUser alice = user(2L, "alice", "小艾");
        SysUser zhangsan = user(3L, "zhangsan", "张三");

        approval.startUp(alice, publishedLeaveForm());
        ProcessVo started = started(alice).getRecords().get(0);
        Map<String, Object> detail = approval.show(zhangsan, started.getId());

        Process process = (Process) detail.get("process");
        assertEquals("小艾发起请假申请申请", process.getTitle());
        assertEquals(审批中, process.getStatus());
        assertTrue(process.getFormValues().contains("\"days\":1"));
        assertEquals(List.of("发起申请"), recordDescriptions(zhangsan, started.getId()));
        assertTrue(canApprove(zhangsan, started.getId()));
        assertFalse(canApprove(alice, started.getId()));
        assertFalse(detail.containsKey("taskId"));
    }

    @Test
    public void approvalMemberGateIsSharedByApplicantCurrentAndPastApprovers() {
        SysUser alice = user(2L, "alice", "小艾");
        SysUser zhangsan = user(3L, "zhangsan", "张三");
        SysUser eve = user(5L, "eve", "小叶");

        approval.startUp(alice, publishedLeaveForm());
        Long processId = started(alice).getRecords().get(0).getId();

        assertEquals(processId, approval.requireApprovalMember(processId, alice).getId());
        assertEquals(processId, approval.requireApprovalMember(processId, zhangsan).getId());
        assertThrows(BusinessException.class, () -> approval.requireApprovalMember(processId, eve));

        approve(zhangsan, pending(zhangsan).getRecords().get(0));
        assertEquals(processId, approval.requireApprovalMember(processId, zhangsan).getId());
    }

    @Test
    public void 已发布审批模板DoesNotStartWhen流程zipNotInEngine() {
        repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey("qingjia")
                .list()
                .forEach((ProcessDefinition definition) ->
                        repositoryService.deleteDeployment(definition.getDeploymentId(), true));

        SysUser alice = user(2L, "alice", "小艾");
        ProcessFormVo form = publishedLeaveForm();
        form.setProcessTemplateId(2L);

        assertThrows(RuntimeException.class, () -> approval.startUp(alice, form));
        assertTrue(started(alice).getRecords().isEmpty());
    }

    @Test
    public void 已发起与配置实例查询显式按创建时间倒序最新在前() throws Exception {
        SysUser alice = user(2L, "alice", "小艾");
        String aliceToken = loginToken("alice");
        String adminToken = loginToken("admin");

        Approval.StartOutcome first = approval.startUp(alice, publishedLeaveForm());
        Approval.StartOutcome second = approval.startUp(alice, publishedLeaveForm());

        // 已发起（办事）：最新发起的审批落在第一页最上（#179，与抄送我的对齐）。
        mockMvc.perform(get("/admin/process/findStarted/1/10").header("token", aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].id").value(second.processId().intValue()))
                .andExpect(jsonPath("$.data.records[1].id").value(first.processId().intValue()));

        // 配置侧审批实例查询：同一排序契约，顺序确定可复现。
        mockMvc.perform(get("/admin/process/1/10").header("token", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].id").value(second.processId().intValue()))
                .andExpect(jsonPath("$.data.records[1].id").value(first.processId().intValue()));

        // 同刻发起：create_time 打平后由审批 id 决胜，最新发起的仍在前。
        jdbcTemplate.update(
                "UPDATE oa_process SET create_time = CURRENT_TIMESTAMP WHERE id IN (?, ?)",
                first.processId(), second.processId());
        mockMvc.perform(get("/admin/process/findStarted/1/10").header("token", aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].id").value(second.processId().intValue()))
                .andExpect(jsonPath("$.data.records[1].id").value(first.processId().intValue()));
    }

    @Test
    public void 五种列表行形状一致且回查行带发起人与审批模板() throws Exception {
        String aliceToken = loginToken("alice");
        String zhangsanToken = loginToken("zhangsan");
        String ccReaderToken = loginToken("cc-reader");
        String adminToken = loginToken("admin");

        // 同一审批给 cc-reader 两条抄送授权（发起抄送 + 任务规则抄送）：DISTINCT total 仍只计一次。
        jdbcTemplate.update(
                "INSERT INTO oa_process_cc_rule (process_definition_key, task_definition_key, user_id, is_deleted) "
                        + "VALUES ('approvalTwoStep', 'first', 6, 0)");
        mockMvc.perform(post("/admin/process/startUp")
                        .header("token", aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"processTemplateId\":1,\"processTypeId\":1,"
                                + "\"formValues\":\"{\\\"formData\\\":{\\\"days\\\":2},"
                                + "\\\"formShowData\\\":{\\\"请假天数\\\":2}}\","
                                + "\"ccUserIds\":[6]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 待处理：回查行补发起人姓名、审批模板名、审批类型名（#181 additive），死字段不再出现。
        expectApprovalRowShapeOnFirstRecord("/admin/process/findPending/1/10", zhangsanToken);

        String pendingBody = mockMvc.perform(get("/admin/process/findPending/1/10")
                        .header("token", zhangsanToken))
                .andReturn().getResponse().getContentAsString();
        String taskId = JsonPath.read(pendingBody, "$.data.records[0].taskId");
        Object processId = JsonPath.read(pendingBody, "$.data.records[0].id");
        mockMvc.perform(post("/admin/process/approve")
                        .header("token", zhangsanToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"processId\":" + processId + ",\"taskId\":\"" + taskId + "\",\"status\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        String lisiPending = mockMvc.perform(get("/admin/process/findPending/1/10")
                        .header("token", loginToken("lisi")))
                .andReturn().getResponse().getContentAsString();
        String nextTaskId = JsonPath.read(lisiPending, "$.data.records[0].taskId");
        mockMvc.perform(post("/admin/process/approve")
                        .header("token", loginToken("lisi"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"processId\":" + processId + ",\"taskId\":\"" + nextTaskId + "\",\"status\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 已处理（逐任务回查）、已发起、抄送我的、配置实例查询：同一行形状。
        expectApprovalRowShapeOnFirstRecord("/admin/process/findProcessed/1/10", zhangsanToken);
        expectApprovalRowShapeOnFirstRecord("/admin/process/findStarted/1/10", aliceToken);
        expectApprovalRowShapeOnFirstRecord("/admin/process/findCarbonCopied/1/10", ccReaderToken);
        expectApprovalRowShapeOnFirstRecord("/admin/process/1/10", adminToken);

        // 抄送行：双授权只出一条审批（DISTINCT total=去重审批数），并补 update_time（公共字段口径）。
        mockMvc.perform(get("/admin/process/findCarbonCopied/1/10").header("token", ccReaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].updateTime").isNotEmpty());
    }

    private void expectApprovalRowShapeOnFirstRecord(String url, String token) throws Exception {
        mockMvc.perform(get(url).header("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].name").value("小艾"))
                .andExpect(jsonPath("$.data.records[0].processTemplateName").value("请假申请"))
                .andExpect(jsonPath("$.data.records[0].processTypeName").value("请假"))
                .andExpect(jsonPath("$.data.records[0].formProps").doesNotExist())
                .andExpect(jsonPath("$.data.records[0].formOptions").doesNotExist());
    }

    @Test
    public void 已处理行单位按审批去重且锚定最近一次处理() throws Exception {
        jdbcTemplate.update(
                "INSERT INTO oa_process_template (id, name, process_type_id, status, is_deleted) "
                        + "VALUES (210, '同人两步请假', 1, 0, 0)");
        var uploaded = approvalTemplate.uploadProcessDefinition(new MockMultipartFile(
                "file", "approvalSameApprover.zip", "application/zip", approvalSameApproverZip()));
        ProcessTemplate template = approvalTemplate.read(210L);
        template.setProcessDefinitionKey(uploaded.processDefinitionKey());
        template.setProcessDefinitionPath(uploaded.processDefinitionPath());
        approvalTemplate.saveUnpublished(template);
        approvalTemplate.publish(210L);

        SysUser alice = user(2L, "alice", "小艾");
        SysUser zhangsan = user(3L, "zhangsan", "张三");

        ProcessFormVo form = publishedLeaveForm();
        form.setProcessTemplateId(210L);
        Approval.StartOutcome twiceProcessed = approval.startUp(alice, form);
        Approval.StartOutcome onceProcessed = approval.startUp(alice, form);

        // 同一用户先后处理同一审批的两个节点：行=审批（GLOSSARY「已处理」），只出一行，
        // total=去重审批数（#186）；重复行不是另一份审批（GLOSSARY「审批」Avoid 口径）。
        approve(zhangsan, pendingRow(zhangsan, twiceProcessed.processId()));
        approve(zhangsan, pendingRow(zhangsan, onceProcessed.processId()));
        approve(zhangsan, pendingRow(zhangsan, twiceProcessed.processId()));

        // 排序锚点=最近一次处理（MAX）：twiceProcessed 两条记录分别锚在 01-01/06-01，
        // onceProcessed 唯一记录锚在 03-01。MAX 语义下 twiceProcessed（06-01）排前；
        // 若实现静默退化成最早一次（MIN，01-01），顺序反转为 [once, twice]——本断言钉死 MAX。
        stampProcessingRecords(twiceProcessed.processId(), "2026-01-01 08:00:00");
        stampProcessingRecords(onceProcessed.processId(), "2026-03-01 08:00:00");
        stampLatestProcessingRecord(twiceProcessed.processId(), "2026-06-01 08:00:00");

        IPage<ProcessVo> processed = approvalListReader.processed(new Page<>(1, 10), zhangsan);
        assertEquals(2, processed.getTotal());
        assertEquals(2, processed.getRecords().size());
        assertEquals(twiceProcessed.processId(), processed.getRecords().get(0).getId());
        assertEquals(onceProcessed.processId(), processed.getRecords().get(1).getId());
    }

    @Test
    public void 已处理按最近一次处理时间倒序同刻由审批id决胜() throws Exception {
        SysUser alice = user(2L, "alice", "小艾");
        SysUser zhangsan = user(3L, "zhangsan", "张三");

        Approval.StartOutcome older = approval.startUp(alice, publishedLeaveForm());
        Approval.StartOutcome newer = approval.startUp(alice, publishedLeaveForm());

        approve(zhangsan, pending(zhangsan).getRecords().stream()
                .filter(row -> row.getId().equals(older.processId())).findFirst().orElseThrow());
        approve(zhangsan, pending(zhangsan).getRecords().stream()
                .filter(row -> row.getId().equals(newer.processId())).findFirst().orElseThrow());

        // 排序锚点=该审批最近一次处理时间（#186）：older 的处理时间更晚，时间压过 id 决胜、older 排前。
        stampProcessingRecords(older.processId(), "2026-06-01 08:00:00");
        stampProcessingRecords(newer.processId(), "2026-01-01 08:00:00");

        IPage<ProcessVo> processed = approvalListReader.processed(new Page<>(1, 10), zhangsan);
        assertEquals(2, processed.getTotal());
        assertEquals(older.processId(), processed.getRecords().get(0).getId());
        assertEquals(newer.processId(), processed.getRecords().get(1).getId());

        // 同刻：两条审批最近一次处理时间相同，由审批 id 决胜（id 大在前，#179 口径）——顺序反转。
        stampProcessingRecords(older.processId(), "2026-03-01 08:00:00");
        stampProcessingRecords(newer.processId(), "2026-03-01 08:00:00");

        IPage<ProcessVo> tied = approvalListReader.processed(new Page<>(1, 10), zhangsan);
        assertEquals(newer.processId(), tied.getRecords().get(0).getId());
        assertEquals(older.processId(), tied.getRecords().get(1).getId());
    }

    @Test
    public void 配置实例查询按关键字状态与时间范围过滤() throws Exception {
        SysUser alice = user(2L, "alice", "小艾");
        String adminToken = loginToken("admin");

        Approval.StartOutcome first = approval.startUp(alice, publishedLeaveForm());
        Approval.StartOutcome second = approval.startUp(alice, publishedLeaveForm());

        jdbcTemplate.update("UPDATE oa_process SET title = '机房抢修特例' WHERE id = ?", first.processId());
        jdbcTemplate.update(
                "UPDATE oa_process SET create_time = TIMESTAMP '2026-01-01 08:00:00' WHERE id = ?",
                first.processId());
        jdbcTemplate.update(
                "UPDATE oa_process SET create_time = TIMESTAMP '2026-06-01 08:00:00' WHERE id = ?",
                second.processId());

        // keyword 命中标题；未命中返回空。
        mockMvc.perform(get("/admin/process/1/10").header("token", adminToken).param("keyword", "特例"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(first.processId().intValue()));
        mockMvc.perform(get("/admin/process/1/10").header("token", adminToken).param("keyword", "不存在的标题"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        // 审批掉 second：status 过滤两侧各剩一条。
        approve(user(3L, "zhangsan", "张三"), pending(user(3L, "zhangsan", "张三")).getRecords()
                .stream().filter(row -> row.getId().equals(second.processId())).findFirst().orElseThrow());
        approve(user(4L, "lisi", "李四"), pending(user(4L, "lisi", "李四")).getRecords().get(0));

        mockMvc.perform(get("/admin/process/1/10").header("token", adminToken).param("status", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(second.processId().intValue()));
        mockMvc.perform(get("/admin/process/1/10").header("token", adminToken).param("status", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(first.processId().intValue()));

        // 时间范围：begin 只含 second，end 只含 first；组合过滤同时生效。
        mockMvc.perform(get("/admin/process/1/10")
                        .header("token", adminToken).param("createTimeBegin", "2026-03-01 00:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(second.processId().intValue()));
        mockMvc.perform(get("/admin/process/1/10")
                        .header("token", adminToken).param("createTimeEnd", "2026-03-01 00:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(first.processId().intValue()));
        mockMvc.perform(get("/admin/process/1/10")
                        .header("token", adminToken)
                        .param("keyword", "特例")
                        .param("status", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    public void 已删审批行不在已发起与配置实例查询里且total同口径() throws Exception {
        SysUser alice = user(2L, "alice", "小艾");
        String aliceToken = loginToken("alice");
        String adminToken = loginToken("admin");

        Approval.StartOutcome kept = approval.startUp(alice, publishedLeaveForm());
        Approval.StartOutcome deleted = approval.startUp(alice, publishedLeaveForm());
        jdbcTemplate.update("UPDATE oa_process SET title = '已作废的机房抢修' WHERE id = ?", deleted.processId());
        jdbcTemplate.update("UPDATE oa_process SET is_deleted = 1 WHERE id = ?", deleted.processId());

        // 已发起：records 与 total 同口径。total 由分页插件从同一条 SQL 派生 count（#198），
        // 只断 records 会漏掉「列表干净但页码还在」这半边。
        mockMvc.perform(get("/admin/process/findStarted/1/10").header("token", aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(kept.processId().intValue()));

        // 配置实例查询：同一个 SQL 的另一个入口。
        mockMvc.perform(get("/admin/process/1/10").header("token", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(kept.processId().intValue()));

        // 存活过滤与既有条件叠加、不是把 where 短路：keyword 单独命中已删行时结果仍是空。
        mockMvc.perform(get("/admin/process/1/10")
                        .header("token", adminToken)
                        .param("keyword", "已作废的机房抢修"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.records.length()").value(0));

        // 列表与详情口径一致：已删行按 id 也读不到（MyBatis-Plus 生成的 SQL 自动带 @TableLogic）。
        assertNull(processMapper.selectById(deleted.processId()));
    }

    @Test
    public void 待处理已处理已发起KeepPagination() {
        SysUser alice = user(2L, "alice", "小艾");
        SysUser zhangsan = user(3L, "zhangsan", "张三");

        approval.startUp(alice, publishedLeaveForm());
        approval.startUp(alice, publishedLeaveForm());

        IPage<ProcessVo> startedPage = approvalListReader.started(new Page<>(1, 1), alice);
        assertEquals(1, startedPage.getRecords().size());
        assertEquals(2, startedPage.getTotal());
        IPage<ProcessVo> startedPageTwo = approvalListReader.started(new Page<>(2, 1), alice);
        assertEquals(1, startedPageTwo.getRecords().size());
        assertNotEquals(startedPage.getRecords().get(0).getId(), startedPageTwo.getRecords().get(0).getId());

        IPage<ProcessVo> pendingPage = approvalListReader.pending(new Page<>(1, 1), zhangsan);
        assertEquals(1, pendingPage.getRecords().size());
        assertEquals(2, pendingPage.getTotal());

        approve(zhangsan, pending(zhangsan).getRecords().get(0));
        approve(zhangsan, pending(zhangsan).getRecords().get(0));

        // zhangsan 处理的是两个不同审批的各一个节点：行=审批（#186），total=去重审批数=2；
        // 页大小 1 时 records=1，已处理供给的窗口语义由这一对断言钉住。
        IPage<ProcessVo> processedPage = approvalListReader.processed(new Page<>(1, 1), zhangsan);
        assertEquals(1, processedPage.getRecords().size());
        assertEquals(2, processedPage.getTotal());
    }

    @Test
    public void http发起待处理已发起StillWorkWithToken() throws Exception {
        String aliceToken = loginToken("alice");
        String zhangsanToken = loginToken("zhangsan");
        mockMvc.perform(post("/admin/process/startUp")
                        .header("token", aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"processTemplateId\":1,\"processTypeId\":1,\"formValues\":\"{\\\"formData\\\":{\\\"days\\\":1}}\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        String startedBody = mockMvc.perform(get("/admin/process/findStarted/1/10")
                        .header("token", aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].status").value(审批中))
                .andExpect(jsonPath("$.data.records[0].taskId").value(nullValue()))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Object processId = JsonPath.read(startedBody, "$.data.records[0].id");

        mockMvc.perform(get("/admin/process/show/" + processId).header("token", aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.isApprove").value(false))
                .andExpect(jsonPath("$.data.processRecordList.length()").value(1));

        String pendingBody = mockMvc.perform(get("/admin/process/findPending/1/10")
                        .header("token", zhangsanToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].taskId").isNotEmpty())
                .andExpect(jsonPath("$.data.records[0].taskId").value(not("0")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String taskId = JsonPath.read(pendingBody, "$.data.records[0].taskId");

        mockMvc.perform(get("/admin/process/show/" + processId)
                        .header("token", zhangsanToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.isApprove").value(true))
                .andExpect(jsonPath("$.data.process.title").value("小艾发起请假申请申请"))
                .andExpect(jsonPath("$.data.taskId").doesNotExist());

        mockMvc.perform(post("/admin/process/approve")
                        .header("token", zhangsanToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"processId\":" + processId + ",\"taskId\":\"" + taskId + "\",\"status\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/admin/process/findProcessed/1/10")
                        .header("token", zhangsanToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].id").value(processId))
                .andExpect(jsonPath("$.data.records[0].taskId").value(nullValue()));

        mockMvc.perform(get("/admin/process/1/10")
                        .header("token", loginToken("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].id").value(processId))
                .andExpect(jsonPath("$.data.records[0].title").value("小艾发起请假申请申请"));
    }

    @Test
    public void httpRejectsNonMemberReadsAndNonCurrentApprovalActions() throws Exception {
        String aliceToken = loginToken("alice");
        String zhangsanToken = loginToken("zhangsan");
        String lisiToken = loginToken("lisi");
        String eveToken = loginToken("eve");
        String adminToken = loginToken("admin");

        mockMvc.perform(post("/admin/process/startUp")
                        .header("token", aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"processTemplateId\":1,\"processTypeId\":1,\"formValues\":\"{\\\"formData\\\":{\\\"days\\\":1}}\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        String startedBody = mockMvc.perform(get("/admin/process/findStarted/1/10")
                        .header("token", aliceToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Object processId = JsonPath.read(startedBody, "$.data.records[0].id");
        String pendingBody = mockMvc.perform(get("/admin/process/findPending/1/10")
                        .header("token", zhangsanToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String taskId = JsonPath.read(pendingBody, "$.data.records[0].taskId");

        mockMvc.perform(get("/admin/system/index/info").header("token", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.menus.length()").value(greaterThan(0)));

        mockMvc.perform(get("/admin/process/show/" + processId)
                        .header("token", eveToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));

        // admin has the configuration menu grant, but configuration access is not process membership.
        mockMvc.perform(get("/admin/process/show/" + processId)
                        .header("token", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(post("/admin/process/approve")
                        .header("token", lisiToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"processId\":" + processId + ",\"taskId\":\"" + taskId + "\",\"status\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(post("/admin/process/approve")
                        .header("token", aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"processId\":" + processId + ",\"taskId\":\"" + taskId + "\",\"status\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(post("/admin/process/approve")
                        .header("token", zhangsanToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"processId\":" + processId + ",\"taskId\":\"" + taskId + "\",\"status\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    public void httpCarbonCopyMemberCanReadFullApprovalButCannotProcessIt() throws Exception {
        String aliceToken = loginToken("alice");
        String zhangsanToken = loginToken("zhangsan");
        String ccReaderToken = loginToken("cc-reader");

        mockMvc.perform(post("/admin/process/startUp")
                        .header("token", aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"processTemplateId\":1,\"processTypeId\":1,"
                                + "\"formValues\":\"{\\\"formData\\\":{\\\"days\\\":2},"
                                + "\\\"formShowData\\\":{\\\"请假天数\\\":2}}\","
                                + "\"ccUserIds\":[6]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        String startedBody = mockMvc.perform(get("/admin/process/findStarted/1/10")
                        .header("token", aliceToken))
                .andReturn().getResponse().getContentAsString();
        Object processId = JsonPath.read(startedBody, "$.data.records[0].id");
        String pendingBody = mockMvc.perform(get("/admin/process/findPending/1/10")
                        .header("token", zhangsanToken))
                .andReturn().getResponse().getContentAsString();
        String taskId = JsonPath.read(pendingBody, "$.data.records[0].taskId");

        mockMvc.perform(get("/admin/process/findCarbonCopied/1/10")
                        .header("token", ccReaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(processId));

        mockMvc.perform(get("/admin/process/show/" + processId)
                        .header("token", ccReaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.isApprove").value(false))
                .andExpect(jsonPath("$.data.process.formValues").value(org.hamcrest.Matchers.containsString("\"days\":2")))
                .andExpect(jsonPath("$.data.processRecordList[0].description").value("发起申请"));

        mockMvc.perform(get("/admin/attachments/processes/" + processId)
                        .header("token", ccReaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canUpload").value(false));

        mockMvc.perform(get("/admin/process/findPending/1/10")
                        .header("token", ccReaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        mockMvc.perform(post("/admin/process/approve")
                        .header("token", ccReaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"processId\":" + processId + ",\"taskId\":\"" + taskId + "\",\"status\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(post("/admin/process/approve")
                        .header("token", zhangsanToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"processId\":" + processId + ",\"taskId\":\"" + taskId + "\",\"status\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        String lisiPendingBody = mockMvc.perform(get("/admin/process/findPending/1/10")
                        .header("token", loginToken("lisi")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String nextTaskId = JsonPath.read(lisiPendingBody, "$.data.records[0].taskId");
        mockMvc.perform(post("/admin/process/approve")
                        .header("token", loginToken("lisi"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"processId\":" + processId + ",\"taskId\":\"" + nextTaskId + "\",\"status\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/admin/process/show/" + processId).header("token", ccReaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.processRecordList.length()").value(3));
    }

    @Test
    public void httpCarbonCopyRecipientsExposeOnlyIdAndDisplayName() throws Exception {
        String body = mockMvc.perform(get("/admin/process/carbonCopyRecipients")
                        .header("token", loginToken("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(greaterThan(0)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // 抄送人选项只用于按显示名选择；返回登录名会给出可枚举的爆破入口（#136）。
        List<Map<String, Object>> recipients = JsonPath.read(body, "$.data");
        assertFalse(recipients.isEmpty(), body);
        assertTrue(recipients.stream().allMatch(row -> row.keySet().equals(Set.of("id", "name"))), body);
    }

    @Test
    public void httpTaskCreatedRuleGrantsLiveReadAccessThroughApprovalHistory() throws Exception {
        String aliceToken = loginToken("alice");
        String zhangsanToken = loginToken("zhangsan");
        String lisiToken = loginToken("lisi");
        String eveToken = loginToken("eve");

        jdbcTemplate.update(
                "INSERT INTO oa_process_cc_rule (process_definition_key, task_definition_key, user_id, is_deleted) "
                        + "VALUES ('approvalTwoStep', 'first', 5, 0)");

        mockMvc.perform(post("/admin/process/startUp")
                        .header("token", aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"processTemplateId\":1,\"processTypeId\":1,"
                                + "\"formValues\":\"{\\\"formData\\\":{\\\"days\\\":3}}\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        String startedBody = mockMvc.perform(get("/admin/process/findStarted/1/10")
                        .header("token", aliceToken))
                .andReturn().getResponse().getContentAsString();
        Object processId = JsonPath.read(startedBody, "$.data.records[0].id");

        mockMvc.perform(get("/admin/process/findCarbonCopied/1/10")
                        .header("token", eveToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(processId));
        mockMvc.perform(get("/admin/process/show/" + processId)
                        .header("token", eveToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isApprove").value(false))
                .andExpect(jsonPath("$.data.processRecordList[0].description").value("发起申请"));

        String zhangsanPending = mockMvc.perform(get("/admin/process/findPending/1/10")
                        .header("token", zhangsanToken))
                .andReturn().getResponse().getContentAsString();
        String firstTaskId = JsonPath.read(zhangsanPending, "$.data.records[0].taskId");
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM oa_process_cc WHERE process_id = ? AND user_id = 5 AND task_id = ?",
                Long.class,
                processId,
                firstTaskId));
        carbonCopyWriter.copyOnTaskCreated(new CarbonCopyTrigger(
                "approvalTwoStep", "first", firstTaskId, processId.toString()));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM oa_process_cc WHERE process_id = ? AND user_id = 5 AND task_id = ?",
                Long.class,
                processId,
                firstTaskId), "重复处理同一 TASK_CREATED task_id 不得重复授权");
        mockMvc.perform(post("/admin/process/approve")
                        .header("token", zhangsanToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"processId\":" + processId + ",\"taskId\":\"" + firstTaskId
                                + "\",\"status\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/admin/process/show/" + processId)
                        .header("token", zhangsanToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isApprove").value(false))
                .andExpect(jsonPath("$.data.processRecordList.length()").value(2));

        mockMvc.perform(post("/admin/process/approve")
                        .header("token", zhangsanToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"processId\":" + processId + ",\"taskId\":\"" + firstTaskId
                                + "\",\"status\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));

        String lisiPending = mockMvc.perform(get("/admin/process/findPending/1/10")
                        .header("token", lisiToken))
                .andReturn().getResponse().getContentAsString();
        String secondTaskId = JsonPath.read(lisiPending, "$.data.records[0].taskId");
        mockMvc.perform(post("/admin/process/approve")
                        .header("token", lisiToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"processId\":" + processId + ",\"taskId\":\"" + secondTaskId
                                + "\",\"status\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/admin/process/show/" + processId)
                        .header("token", eveToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isApprove").value(false))
                .andExpect(jsonPath("$.data.process.status").value(审批通过))
                .andExpect(jsonPath("$.data.processRecordList.length()").value(3));
    }

    @Test
    public void concurrentApprovalRequestsForSameTaskHaveExactlyOneCommittedWinner() throws Exception {
        SysUser alice = user(2L, "alice", "小艾");
        SysUser zhangsan = user(3L, "zhangsan", "张三");
        SysUser lisi = user(4L, "lisi", "李四");

        for (int attempt = 0; attempt < 3; attempt++) {
            approval.startUp(alice, publishedLeaveForm());
            ProcessVo startedProcess = started(alice).getRecords().stream()
                    .filter(process -> process.getStatus() == 审批中)
                    .findFirst()
                    .orElseThrow();
            ProcessVo pendingTask = pending(zhangsan).getRecords().get(0);
            ApprovalVo approvalVo = new ApprovalVo();
            approvalVo.setProcessId(pendingTask.getId());
            approvalVo.setTaskId(pendingTask.getTaskId());
            approvalVo.setStatus(通过);

            testApprovalEngine.armCompleteBarrier();
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<Throwable> first = executor.submit(() -> approveCapturingFailure(zhangsan, approvalVo));
                Future<Throwable> second = executor.submit(() -> approveCapturingFailure(zhangsan, approvalVo));

                assertTrue(testApprovalEngine.awaitCompleteBarrier(),
                        "两个审批请求应在各自的事务中同时进入 Flowable complete");
                Throwable firstFailure = first.get(15, TimeUnit.SECONDS);
                Throwable secondFailure = second.get(15, TimeUnit.SECONDS);
                assertEquals(2, testApprovalEngine.activeTransactionsAtComplete(),
                        "两个 Flowable 调用都应处于 Spring 事务中");

                int successes = (firstFailure == null ? 1 : 0) + (secondFailure == null ? 1 : 0);
                assertEquals(1, successes, "同一任务只能有一个审批请求提交成功");
                assertEquals(1, (firstFailure == null ? 0 : 1) + (secondFailure == null ? 0 : 1));
                Throwable losingFailure = firstFailure == null ? secondFailure : firstFailure;
                assertTrue(losingFailure instanceof FlowableException,
                        "失败请求应由 Flowable 对同一任务的并发提交拒绝");

                ProcessVo persistedProcess = started(alice).getRecords().stream()
                        .filter(process -> startedProcess.getId().equals(process.getId()))
                        .findFirst()
                        .orElseThrow();
                assertEquals(审批中, persistedProcess.getStatus());
                assertEquals(List.of("发起申请", "已通过"),
                        recordDescriptions(zhangsan, pendingTask.getId()));
                assertTrue(pending(zhangsan).getRecords().isEmpty());
                assertEquals(1, pending(lisi).getRecords().stream()
                        .filter(process -> startedProcess.getId().equals(process.getId()))
                        .count());
                assertEquals(1, historyService.createHistoricTaskInstanceQuery()
                        .processInstanceId(startedProcess.getProcessInstanceId())
                        .taskAssignee("zhangsan")
                        .finished()
                        .count());
            } finally {
                executor.shutdownNow();
                assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    public void startUpReturnsProcessIdentityAndCurrentTaskIds() {
        SysUser alice = user(2L, "alice", "小艾");
        SysUser zhangsan = user(3L, "zhangsan", "张三");

        Approval.StartOutcome outcome = approval.startUp(alice, publishedLeaveForm());

        ProcessVo started = started(alice).getRecords().get(0);
        assertEquals(started.getId(), outcome.processId());
        assertEquals(started.getProcessInstanceId(), outcome.processInstanceId());
        ProcessVo pending = pending(zhangsan).getRecords().get(0);
        assertEquals(List.of(pending.getTaskId()), outcome.currentTaskIds());
    }

    @Test
    public void startUpWithSameIdempotencyKeyReturnsTheOriginalApprovalAndKeepsOneRow() {
        SysUser alice = user(2L, "alice", "小艾");
        SysUser zhangsan = user(3L, "zhangsan", "张三");

        Approval.StartOutcome first = approval.startUp(alice, publishedLeaveForm("submit-once"));
        Approval.StartOutcome replayed = approval.startUp(alice, publishedLeaveForm("submit-once"));

        assertEquals(first.processId(), replayed.processId());
        assertEquals(first.processInstanceId(), replayed.processInstanceId());
        assertEquals(first.currentTaskIds(), replayed.currentTaskIds());
        assertEquals(1, processCount());
        assertEquals(1, count("SELECT COUNT(*) FROM oa_process WHERE idempotency_key = 'submit-once'"));
        assertEquals(1, count("SELECT COUNT(*) FROM oa_process_record"));
        assertEquals(1, historyService.createHistoricProcessInstanceQuery().count());
        assertEquals(first.processId(), pending(zhangsan).getRecords().get(0).getId());
    }

    @Test
    public void replayedIdempotencyKeyKeepsTheFirstSnapshotWhenContentDiffers() {
        SysUser alice = user(2L, "alice", "小艾");

        Approval.StartOutcome first = approval.startUp(alice, publishedLeaveForm("submit-once"));
        ProcessFormVo changed = publishedLeaveForm("submit-once");
        changed.setFormValues("{\"formData\":{\"days\":9},\"formShowData\":{\"请假天数\":9}}");
        Approval.StartOutcome replayed = approval.startUp(alice, changed);

        assertEquals(first.processId(), replayed.processId());
        assertEquals(1, processCount());
        assertEquals("{\"formData\":{\"days\":1},\"formShowData\":{\"请假天数\":1}}",
                jdbcTemplate.queryForObject(
                        "SELECT form_values FROM oa_process WHERE id = ?", String.class, first.processId()));
    }

    @Test
    public void concurrentStartUpsWithSameIdempotencyKeyCreateExactlyOneApproval() throws Exception {
        SysUser alice = user(2L, "alice", "小艾");
        CountDownLatch firstInserted = new CountDownLatch(1);
        CountDownLatch allowCommit = new CountDownLatch(1);
        CountDownLatch replayStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        Future<Approval.StartOutcome> winner = executor.submit(() -> transaction.execute(status -> {
            Approval.StartOutcome outcome = approval.startUp(alice, publishedLeaveForm("race-key"));
            firstInserted.countDown();
            await(allowCommit);
            return outcome;
        }));
        try {
            assertTrue(firstInserted.await(10, TimeUnit.SECONDS));
            Future<Approval.StartOutcome> replay = executor.submit(() -> {
                replayStarted.countDown();
                return approval.startUp(alice, publishedLeaveForm("race-key"));
            });
            assertTrue(replayStarted.await(10, TimeUnit.SECONDS));
            Thread.sleep(150);
            assertFalse(replay.isDone(), "重复提交应等第一次提交落定后的唯一键结果");
            allowCommit.countDown();
            Approval.StartOutcome committed = winner.get(10, TimeUnit.SECONDS);
            Approval.StartOutcome replayed = replay.get(10, TimeUnit.SECONDS);

            assertEquals(committed.processId(), replayed.processId());
            assertEquals(committed.processInstanceId(), replayed.processInstanceId());
            assertEquals(1, processCount());
            assertEquals(1, count("SELECT COUNT(*) FROM oa_process WHERE idempotency_key = 'race-key'"));
            assertEquals(1, count("SELECT COUNT(*) FROM oa_process_record"));
            assertEquals(1, historyService.createHistoricProcessInstanceQuery().count());
        } finally {
            allowCommit.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    public void sameIdempotencyKeyFromDifferentUsersCreatesSeparateApprovals() {
        SysUser alice = user(2L, "alice", "小艾");
        SysUser lisi = user(4L, "lisi", "李四");

        Approval.StartOutcome first = approval.startUp(alice, publishedLeaveForm("shared-key"));
        Approval.StartOutcome second = approval.startUp(lisi, publishedLeaveForm("shared-key"));

        assertNotEquals(first.processId(), second.processId());
        assertEquals(2, processCount());
    }

    @Test
    public void differentIdempotencyKeysCreateSeparateApprovals() {
        SysUser alice = user(2L, "alice", "小艾");

        Approval.StartOutcome first = approval.startUp(alice, publishedLeaveForm("attempt-1"));
        Approval.StartOutcome second = approval.startUp(alice, publishedLeaveForm("attempt-2"));

        assertNotEquals(first.processId(), second.processId());
        assertEquals(2, processCount());
    }

    @Test
    public void invalidIdempotencyKeyIsRejectedWithoutCreatingAnApproval() {
        SysUser alice = user(2L, "alice", "小艾");
        ProcessFormVo blank = publishedLeaveForm();
        blank.setIdempotencyKey("   ");
        ProcessFormVo tooLong = publishedLeaveForm();
        tooLong.setIdempotencyKey("x".repeat(65));

        assertEquals(201, assertThrows(BusinessException.class, () -> approval.startUp(alice, blank)).getCode());
        assertEquals(201, assertThrows(BusinessException.class, () -> approval.startUp(alice, tooLong)).getCode());
        assertEquals(0, processCount());
    }

    @Test
    public void aReplayWithMalformedFormValuesStillReturnsTheFirstApproval() {
        SysUser alice = user(2L, "alice", "小艾");

        Approval.StartOutcome first = approval.startUp(alice, publishedLeaveForm("attempt-1"));
        ProcessFormVo replay = publishedLeaveForm("attempt-1");
        replay.setFormValues("{not-json");

        Approval.StartOutcome replayed = approval.startUp(alice, replay);

        assertEquals(first.processId(), replayed.processId());
        assertEquals(1, processCount());
    }

    @Test
    public void approveReturnsNextState() {
        SysUser alice = user(2L, "alice", "小艾");
        SysUser zhangsan = user(3L, "zhangsan", "张三");
        SysUser lisi = user(4L, "lisi", "李四");

        approval.startUp(alice, publishedLeaveForm());
        ProcessVo pending = pending(zhangsan).getRecords().get(0);

        Approval.DecisionOutcome outcome = approval.approve(zhangsan, decision(pending, 通过));

        assertEquals(pending.getId(), outcome.processId());
        assertEquals(审批中, outcome.status());
        ProcessVo next = pending(lisi).getRecords().get(0);
        assertEquals(List.of(next.getTaskId()), outcome.nextTaskIds());
    }

    @Test
    public void startUpWritesLedgerAndBindsAttachmentsBeforeEngineStart() {
        SysUser alice = user(2L, "alice", "小艾");
        String draftId = UUID.randomUUID().toString();
        Attachment attachment = pendingAttachment(alice, draftId);

        ProcessFormVo form = publishedLeaveForm();
        form.setAttachmentDraftId(draftId);
        form.setAttachmentIds(List.of(attachment.getId()));
        testApprovalEngine.expectLedgerAndAttachmentsBeforeStart(1);

        approval.startUp(alice, form);

        Attachment bound = attachmentMapper.selectById(attachment.getId());
        assertEquals(Attachment.ATTACHED, bound.getStatus());
        assertEquals(started(alice).getRecords().get(0).getId(), bound.getProcessId());
    }

    @Test
    public void approveRecordsDecisionBeforeReadingNextTasksAndUpdatingProcess() {
        SysUser alice = user(2L, "alice", "小艾");
        SysUser zhangsan = user(3L, "zhangsan", "张三");

        approval.startUp(alice, publishedLeaveForm());
        ProcessVo pending = pending(zhangsan).getRecords().get(0);

        testApprovalEngine.expectNoDecisionRecordBeforeComplete(pending.getId());
        testApprovalEngine.expectDecisionRecordBeforeCurrentTasks();
        Approval.DecisionOutcome outcome = approval.approve(zhangsan, decision(pending, 通过));

        assertEquals(审批中, outcome.status());
        assertEquals("等待李四审批", started(alice).getRecords().get(0).getDescription());
    }

    @Test
    public void startUpEngineFailureDeletesBoundCopyAndKeepsPendingUploadRetryable() {
        SysUser alice = user(2L, "alice", "小艾");
        String draftId = UUID.randomUUID().toString();
        Attachment attachment = pendingAttachment(alice, draftId);
        String pendingKey = attachment.getObjectKey();
        String permanentKey = "attachments/" + attachment.getStorageId();

        ProcessFormVo form = publishedLeaveForm();
        form.setAttachmentDraftId(draftId);
        form.setAttachmentIds(List.of(attachment.getId()));
        testApprovalEngine.failNextStart();

        assertThrows(RuntimeException.class, () -> approval.startUp(alice, form));

        assertEquals(0L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM oa_process WHERE user_id = 2", Long.class));
        Attachment failed = attachmentMapper.selectById(attachment.getId());
        assertEquals(Attachment.PENDING, failed.getStatus());
        assertNull(failed.getProcessId());
        assertEquals(pendingKey, failed.getObjectKey());
        assertTrue(objectStore.contains(pendingKey));
        assertFalse(objectStore.contains(permanentKey));

        approval.startUp(alice, form);

        Attachment retried = attachmentMapper.selectById(attachment.getId());
        assertEquals(Attachment.ATTACHED, retried.getStatus());
        assertEquals(permanentKey, retried.getObjectKey());
        assertFalse(objectStore.contains(pendingKey));
        assertTrue(objectStore.contains(permanentKey));
    }

    @Test
    public void startUpAttachmentBatchMidFailureCompensatesEarlierCopiesAndRetries() {
        SysUser alice = user(2L, "alice", "小艾");
        String draftId = UUID.randomUUID().toString();
        Attachment first = pendingAttachment(alice, draftId);
        Attachment second = pendingAttachment(alice, draftId);
        String firstPending = first.getObjectKey();
        String secondPending = second.getObjectKey();
        String firstPermanent = "attachments/" + first.getStorageId();
        String secondPermanent = "attachments/" + second.getStorageId();

        ProcessFormVo form = publishedLeaveForm();
        form.setAttachmentDraftId(draftId);
        form.setAttachmentIds(List.of(first.getId(), second.getId()));
        objectStore.failOnCopyAttempt(2);

        assertThrows(RuntimeException.class, () -> approval.startUp(alice, form));

        assertEquals(Attachment.PENDING, attachmentMapper.selectById(first.getId()).getStatus());
        assertEquals(Attachment.PENDING, attachmentMapper.selectById(second.getId()).getStatus());
        assertFalse(objectStore.contains(firstPermanent));
        assertFalse(objectStore.contains(secondPermanent));
        assertTrue(objectStore.contains(firstPending));
        assertTrue(objectStore.contains(secondPending));

        approval.startUp(alice, form);

        assertEquals(Attachment.ATTACHED, attachmentMapper.selectById(first.getId()).getStatus());
        assertEquals(Attachment.ATTACHED, attachmentMapper.selectById(second.getId()).getStatus());
        assertFalse(objectStore.contains(firstPending));
        assertFalse(objectStore.contains(secondPending));
        assertTrue(objectStore.contains(firstPermanent));
        assertTrue(objectStore.contains(secondPermanent));
    }

    @Test
    public void approveEngineCompleteFailureKeepsTaskAndWritesNothing() {
        SysUser alice = user(2L, "alice", "小艾");
        SysUser zhangsan = user(3L, "zhangsan", "张三");

        approval.startUp(alice, publishedLeaveForm());
        ProcessVo pending = pending(zhangsan).getRecords().get(0);
        testApprovalEngine.failNextComplete();

        assertThrows(RuntimeException.class, () -> approval.approve(zhangsan, decision(pending, 通过)));

        assertEquals(1L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM oa_process_record", Long.class));
        assertEquals(审批中, started(alice).getRecords().get(0).getStatus());
        assertEquals(1, pending(zhangsan).getRecords().size());
        assertEquals(List.of("发起申请"), recordDescriptions(zhangsan, pending.getId()));
    }

    @Test
    public void startUpRollsBackBusinessFlowableAndSynchronousCarbonCopyWritesTogether() {
        jdbcTemplate.execute("ALTER TABLE oa_process_record ADD CONSTRAINT reject_test_operator "
                + "CHECK (operate_user_id IS NULL OR operate_user_id <> 99)");
        try {
            assertThrows(RuntimeException.class,
                    () -> approval.startUp(user(99L, "rollback", "回滚验证"), publishedLeaveForm()));

            assertEquals(1, approvalTransactionCcProbe.taskCreatedCount(),
                    "测试监听器应已在 Flowable 启动命令中写入抄送探针");
            assertEquals(0L, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM oa_process WHERE user_id = 99", Long.class));
            assertEquals(0L, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM oa_process_record", Long.class));
            assertEquals(0L, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM approval_transaction_cc_probe", Long.class));
            assertEquals(0, runtimeService.createProcessInstanceQuery().count());
            assertEquals(0, historyService.createHistoricProcessInstanceQuery().count());
        } finally {
            jdbcTemplate.execute("ALTER TABLE oa_process_record DROP CONSTRAINT reject_test_operator");
        }
    }

    @Test
    public void approveRollsBackFlowableCompletionAndBusinessWritesTogether() {
        SysUser alice = user(2L, "alice", "小艾");
        SysUser zhangsan = user(3L, "zhangsan", "张三");
        approval.startUp(alice, publishedLeaveForm());
        ProcessVo pendingTask = pending(zhangsan).getRecords().get(0);

        jdbcTemplate.execute("ALTER TABLE oa_process_record ADD CONSTRAINT reject_approval_operator "
                + "CHECK (operate_user_id IS NULL OR operate_user_id <> 3)");
        try {
            ApprovalVo approvalVo = new ApprovalVo();
            approvalVo.setProcessId(pendingTask.getId());
            approvalVo.setTaskId(pendingTask.getTaskId());
            approvalVo.setStatus(通过);
            assertThrows(RuntimeException.class, () -> approval.approve(zhangsan, approvalVo));

            assertEquals(1L, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM oa_process_record", Long.class), "只保留已提交的发起记录");
            assertEquals(1L, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM approval_transaction_cc_probe", Long.class),
                    "审批事务回滚时只保留发起时的抄送记录");
            assertEquals(1, taskService.createTaskQuery().taskId(pendingTask.getTaskId()).count());
            assertEquals(0, taskService.createTaskQuery()
                    .processInstanceId(pendingTask.getProcessInstanceId())
                    .taskAssignee("lisi")
                    .count());
            assertEquals(0, historyService.createHistoricTaskInstanceQuery()
                    .taskId(pendingTask.getTaskId())
                    .finished()
                    .count());
            assertEquals(List.of("发起申请"), recordDescriptions(zhangsan, pendingTask.getId()));
        } finally {
            jdbcTemplate.execute("ALTER TABLE oa_process_record DROP CONSTRAINT reject_approval_operator");
        }
    }

    private Throwable approveCapturingFailure(SysUser user, ApprovalVo approvalVo) {
        try {
            approval.approve(user, approvalVo);
            return null;
        } catch (RuntimeException failure) {
            return failure;
        }
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

    private ProcessVo pendingRow(SysUser user, Long processId) {
        return pending(user).getRecords().stream()
                .filter(row -> row.getId().equals(processId)).findFirst().orElseThrow();
    }

    /** 该审批全部处理动作记录的处理时间（fixture 用；「发起申请」流转记录不是处理动作，不碰）。 */
    private void stampProcessingRecords(Long processId, String timestamp) {
        jdbcTemplate.update("UPDATE oa_process_record SET create_time = TIMESTAMP '" + timestamp + "' "
                + "WHERE process_id = ? AND description <> '" + ProcessApprovalSupport.INITIATION_DESCRIPTION + "'",
                processId);
    }

    /** 只锚该审批最新一条处理动作记录（MAX(id)）：给同人两节点的两批记录设可区分时间用。 */
    private void stampLatestProcessingRecord(Long processId, String timestamp) {
        jdbcTemplate.update("UPDATE oa_process_record SET create_time = TIMESTAMP '" + timestamp + "' "
                + "WHERE id = (SELECT MAX(id) FROM oa_process_record "
                + "WHERE process_id = ? AND description <> '" + ProcessApprovalSupport.INITIATION_DESCRIPTION + "')",
                processId);
    }

    private void approve(SysUser user, ProcessVo pending) {
        decide(user, pending, 通过);
    }

    private void reject(SysUser user, ProcessVo pending) {
        decide(user, pending, 拒绝);
    }

    private ApprovalVo decision(ProcessVo pending, int status) {
        ApprovalVo approvalVo = new ApprovalVo();
        approvalVo.setProcessId(pending.getId());
        approvalVo.setTaskId(pending.getTaskId());
        approvalVo.setStatus(status);
        return approvalVo;
    }

    private void decide(SysUser user, ProcessVo pending, int status) {
        approval.approve(user, decision(pending, status));
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

    private static void assertNoFakeTaskHandle(ProcessVo vo) {
        assertNull(vo.getTaskId());
    }

    private Attachment pendingAttachment(SysUser owner, String draftId) {
        Attachment attachment = new Attachment();
        attachment.setOwnerUserId(owner.getId());
        attachment.setDraftId(draftId);
        attachment.setStorageId(UUID.randomUUID().toString());
        attachment.setObjectKey("pending/" + attachment.getStorageId());
        attachment.setFileName("receipt.pdf");
        attachment.setContentType("application/pdf");
        attachment.setSizeBytes(31L);
        attachment.setStatus(Attachment.PENDING);
        attachment.setUploadExpiresAt(Date.from(Instant.now().plus(Duration.ofMinutes(5))));
        attachmentMapper.insert(attachment);
        objectStore.put(attachment.getObjectKey(), 31L, "application/pdf");
        return attachment;
    }

    private static ProcessFormVo publishedLeaveForm() {
        ProcessFormVo form = new ProcessFormVo();
        form.setProcessTemplateId(1L);
        form.setProcessTypeId(1L);
        form.setFormValues("{\"formData\":{\"days\":1},\"formShowData\":{\"请假天数\":1}}");
        return form;
    }

    private static ProcessFormVo publishedLeaveForm(String idempotencyKey) {
        ProcessFormVo form = publishedLeaveForm();
        form.setIdempotencyKey(idempotencyKey);
        return form;
    }

    private int processCount() {
        return count("SELECT COUNT(*) FROM oa_process");
    }

    private int count(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("并发合同等待超时");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("并发合同被中断", e);
        }
    }

    private static byte[] approvalTwoStepZip() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (InputStream bpmn = ApprovalContractTest.class.getClassLoader()
                     .getResourceAsStream("process/approval-two-step.bpmn20.xml");
             ZipOutputStream zip = new ZipOutputStream(bytes)) {
            if (bpmn == null) throw new IOException("审批合同 BPMN 不存在");
            zip.putNextEntry(new ZipEntry("approval-two-step.bpmn20.xml"));
            bpmn.transferTo(zip);
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    private static byte[] approvalSameApproverZip() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (InputStream bpmn = ApprovalContractTest.class.getClassLoader()
                     .getResourceAsStream("process/approval-same-approver-two-step.bpmn20.xml");
             ZipOutputStream zip = new ZipOutputStream(bytes)) {
            if (bpmn == null) throw new IOException("同人两步合同 BPMN 不存在");
            zip.putNextEntry(new ZipEntry("approval-same-approver-two-step.bpmn20.xml"));
            bpmn.transferTo(zip);
            zip.closeEntry();
        }
        return bytes.toByteArray();
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

    @TestConfiguration
    static class InMemoryRedisConfig {
        @Bean
        public ApprovalTransactionCcProbe approvalTransactionCcProbe(JdbcTemplate jdbcTemplate) {
            return new ApprovalTransactionCcProbe(jdbcTemplate);
        }

        @Bean
        @Primary
        public TestAttachmentObjectStore testAttachmentObjectStore() {
            return new TestAttachmentObjectStore();
        }

        @Bean
        @Primary
        public TestApprovalEngine testApprovalEngine(
                FlowableApprovalEngine delegate, JdbcTemplate jdbcTemplate) {
            return new TestApprovalEngine(delegate, jdbcTemplate);
        }

        @Bean
        @Primary
        public RedisTemplate<String, String> redisTemplate() {
            ConcurrentHashMap<String, String> store = new ConcurrentHashMap<>();
            RedisTemplate<String, String> template = new RedisTemplate<String, String>() {
                @Override
                @SuppressWarnings("unchecked")
                public ValueOperations<String, String> opsForValue() {
                    return (ValueOperations<String, String>) Proxy.newProxyInstance(
                            ValueOperations.class.getClassLoader(),
                            new Class<?>[]{ValueOperations.class},
                            (proxy, method, args) -> {
                                if ("set".equals(method.getName()) && args != null && args.length >= 2) {
                                    store.put(String.valueOf(args[0]), String.valueOf(args[1]));
                                    return null;
                                }
                                if ("get".equals(method.getName()) && args != null && args.length >= 1) {
                                    return store.get(args[0]);
                                }
                                if ("toString".equals(method.getName())) {
                                    return "InMemoryValueOperations";
                                }
                                throw new UnsupportedOperationException(method.getName());
                            });
                }

                @Override
                public Boolean delete(String key) {
                    return store.remove(key) != null;
                }

                @Override
                public void afterPropertiesSet() {
                }
            };
            return template;
        }
    }

    static class ApprovalTransactionCcProbe extends AbstractFlowableEventListener {
        private final JdbcTemplate jdbcTemplate;
        private final AtomicInteger taskCreatedCount = new AtomicInteger();

        ApprovalTransactionCcProbe(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }

        @Override
        public void onEvent(FlowableEvent event) {
            if (event.getType() != FlowableEngineEventType.TASK_CREATED) {
                return;
            }
            if (!(event instanceof FlowableEntityEvent entityEvent)
                    || !(entityEvent.getEntity() instanceof Task task)) {
                throw new IllegalStateException("TASK_CREATED 事件不包含 Flowable task 实体");
            }
            taskCreatedCount.incrementAndGet();
            jdbcTemplate.update(
                    "INSERT INTO approval_transaction_cc_probe (task_id, process_instance_id) VALUES (?, ?)",
                    task.getId(),
                    task.getProcessInstanceId());
        }

        @Override
        public boolean isFailOnException() {
            return false;
        }

        @Override
        public java.util.Collection<? extends FlowableEventType> getTypes() {
            return Set.of(FlowableEngineEventType.TASK_CREATED);
        }

        void reset() {
            taskCreatedCount.set(0);
        }

        int taskCreatedCount() {
            return taskCreatedCount.get();
        }
    }
}
