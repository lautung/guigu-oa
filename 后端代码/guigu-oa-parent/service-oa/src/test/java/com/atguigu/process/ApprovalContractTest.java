package com.atguigu.process;

import com.atguigu.model.process.ProcessRecord;
import com.atguigu.model.system.SysUser;
import com.atguigu.process.service.Approval;
import com.atguigu.security.custom.LoginUserInfoHelper;
import com.atguigu.vo.process.ApprovalVo;
import com.atguigu.vo.process.ProcessFormVo;
import com.atguigu.vo.process.ProcessVo;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.atguigu.model.process.Process;
import org.flowable.engine.RepositoryService;
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
import java.util.concurrent.ConcurrentHashMap;

import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    private MockMvc mockMvc;

    @BeforeEach
    public void deployPublishedTemplateEngineDefinition() {
        LoginUserInfoHelper.removeUserId();
        LoginUserInfoHelper.removeUsername();
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
    public void http发起待处理已发起StillWorkWithToken() throws Exception {
        String aliceToken = loginToken("alice");
        mockMvc.perform(post("/admin/process/startUp")
                        .header("token", aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"processTemplateId\":1,\"processTypeId\":1,\"formValues\":\"{\\\"formData\\\":{\\\"days\\\":1}}\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/admin/process/findStarted/1/10")
                        .header("token", aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].status").value(审批中));

        mockMvc.perform(get("/admin/process/findPending/1/10")
                        .header("token", loginToken("zhangsan")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].taskId").isNotEmpty())
                .andExpect(jsonPath("$.data.records[0].taskId").value(not("0")));
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
        return approval.findfindPending(user, new Page<Process>(1, 10));
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
        return Boolean.TRUE.equals(approval.show(user, processId).get("isApprove"));
    }

    private List<String> recordDescriptions(SysUser user, Long processId) {
        @SuppressWarnings("unchecked")
        List<ProcessRecord> records =
                (List<ProcessRecord>) approval.show(user, processId).get("processRecordList");
        return records.stream().map(ProcessRecord::getDescription).toList();
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
