package com.atguigu.process;

import com.atguigu.model.process.Process;
import com.atguigu.model.process.ProcessRecord;
import com.atguigu.model.system.SysUser;
import com.atguigu.process.service.Approval;
import com.atguigu.common.config.exception.GuiguException;
import com.atguigu.security.custom.LoginUserInfoHelper;
import com.atguigu.vo.process.ApprovalVo;
import com.atguigu.vo.process.ProcessFormVo;
import com.atguigu.vo.process.ProcessVo;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.repository.ProcessDefinition;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
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
    private RepositoryService repositoryService;

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private HistoryService historyService;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    public void deployPublishedTemplateEngineDefinition() {
        LoginUserInfoHelper.removeUserId();
        LoginUserInfoHelper.removeUsername();
        clearEngineInstances();
        if (repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey("approvalTwoStep")
                .count() == 0) {
            repositoryService.createDeployment()
                    .addClasspathResource("process/approval-two-step.bpmn20.xml")
                    .deploy();
        }
    }

    @AfterEach
    public void clearLoginThreadLocal() {
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
        assertEquals(List.of("发起申请", "驳回"), recordDescriptions(zhangsan, pending.getId()));
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
        assertThrows(GuiguException.class, () -> approval.requireApprovalMember(processId, eve));

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
    public void 待处理已处理已发起KeepPagination() {
        SysUser alice = user(2L, "alice", "小艾");
        SysUser zhangsan = user(3L, "zhangsan", "张三");

        approval.startUp(alice, publishedLeaveForm());
        approval.startUp(alice, publishedLeaveForm());

        IPage<ProcessVo> startedPage = approval.findStarted(alice, new Page<>(1, 1));
        assertEquals(1, startedPage.getRecords().size());
        assertEquals(2, startedPage.getTotal());
        IPage<ProcessVo> startedPageTwo = approval.findStarted(alice, new Page<>(2, 1));
        assertEquals(1, startedPageTwo.getRecords().size());
        assertNotEquals(startedPage.getRecords().get(0).getId(), startedPageTwo.getRecords().get(0).getId());

        IPage<ProcessVo> pendingPage = approval.findPending(zhangsan, new Page<Process>(1, 1));
        assertEquals(1, pendingPage.getRecords().size());
        assertEquals(2, pendingPage.getTotal());

        approve(zhangsan, pending(zhangsan).getRecords().get(0));
        approve(zhangsan, pending(zhangsan).getRecords().get(0));

        IPage<ProcessVo> processedPage = approval.findProcessed(zhangsan, new Page<Process>(1, 1));
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

        mockMvc.perform(get("/admin/process/show/" + processId)
                        .header("token", eveToken))
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
        return approval.findStarted(user, new Page<>(1, 10));
    }

    private IPage<ProcessVo> pending(SysUser user) {
        return approval.findPending(user, new Page<Process>(1, 10));
    }

    private IPage<ProcessVo> processed(SysUser user) {
        return approval.findProcessed(user, new Page<Process>(1, 10));
    }

    private void approve(SysUser user, ProcessVo pending) {
        decide(user, pending, 通过);
    }

    private void reject(SysUser user, ProcessVo pending) {
        decide(user, pending, 拒绝);
    }

    private void decide(SysUser user, ProcessVo pending, int status) {
        ApprovalVo approvalVo = new ApprovalVo();
        approvalVo.setProcessId(pending.getId());
        approvalVo.setTaskId(pending.getTaskId());
        approvalVo.setStatus(status);
        approval.approve(user, approvalVo);
    }

    private boolean canApprove(SysUser user, Long processId) {
        try {
            return Boolean.TRUE.equals(approval.show(user, processId).get("isApprove"));
        } catch (GuiguException e) {
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

    @TestConfiguration
    static class InMemoryRedisConfig {
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
                public void afterPropertiesSet() {
                }
            };
            return template;
        }
    }
}
