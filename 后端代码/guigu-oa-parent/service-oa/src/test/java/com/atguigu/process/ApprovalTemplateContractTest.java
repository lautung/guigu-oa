package com.atguigu.process;

import com.atguigu.model.process.ProcessTemplate;
import com.atguigu.model.process.ProcessType;
import com.atguigu.model.system.SysUser;
import com.atguigu.process.service.Approval;
import com.atguigu.process.service.ApprovalTemplate;
import com.atguigu.security.custom.LoginUserInfoHelper;
import com.atguigu.vo.process.ProcessFormVo;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Sql({"/auth-contract-schema.sql", "/approval-contract-schema.sql"})
public class ApprovalTemplateContractTest {

    private static final int 审批中 = 1;

    @Autowired
    private ApprovalTemplate approvalTemplate;

    @Autowired
    private Approval approval;

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private HistoryService historyService;

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
    public void saveUnpublishedCannotInitiateAndStaysWritable() {
        ProcessTemplate saved = save病假申请();

        assertFalse(approvalTemplate.canInitiate(saved.getId()));
        assertTrue(approvalTemplate.canWrite(saved.getId()));
        assertEquals("病假申请", approvalTemplate.read(saved.getId()).getName());
    }

    @Test
    public void saveUnpublishedIgnoresCaller已发布() {
        ProcessTemplate template = 病假申请();
        template.setStatus(1);

        approvalTemplate.saveUnpublished(template);

        assertFalse(approvalTemplate.canInitiate(template.getId()));
        assertTrue(approvalTemplate.canWrite(template.getId()));
    }

    @Test
    public void findPageIncludes未发布And已发布() {
        ProcessTemplate unpublished = save病假申请();

        IPage<ProcessTemplate> page = approvalTemplate.findPage(new Page<>(1, 20));
        List<Long> ids = page.getRecords().stream().map(ProcessTemplate::getId).toList();

        assertTrue(ids.contains(1L));
        assertTrue(ids.contains(unpublished.getId()));
        assertTrue(approvalTemplate.canInitiate(1L));
        assertTrue(approvalTemplate.canWrite(unpublished.getId()));
        assertEquals("请假", page.getRecords().stream()
                .filter(item -> unpublished.getId().equals(item.getId()))
                .findFirst()
                .orElseThrow()
                .getProcessTypeName());
    }

    @Test
    public void findStartableOnly已发布GroupedBy审批类型() {
        List<ProcessType> groups = approvalTemplate.findStartable();

        assertTrue(groups.stream().noneMatch(group -> "加班".equals(group.getName())));
        ProcessType leave = groups.stream()
                .filter(group -> "请假".equals(group.getName()))
                .findFirst()
                .orElseThrow();
        List<Long> ids = leave.getProcessTemplateList().stream().map(ProcessTemplate::getId).toList();
        assertTrue(ids.contains(1L));
        assertTrue(ids.contains(2L));
        assertFalse(ids.contains(3L));
        assertFalse(ids.contains(4L));
    }

    @Test
    public void unpublishedCanBeSavedAgainAndDeleted() {
        ProcessTemplate saved = save病假申请();
        saved.setName("病假申请改");
        approvalTemplate.saveUnpublished(saved);

        assertEquals("病假申请改", approvalTemplate.read(saved.getId()).getName());
        assertFalse(approvalTemplate.canInitiate(saved.getId()));
        assertTrue(approvalTemplate.canWrite(saved.getId()));

        approvalTemplate.removeUnpublished(saved.getId());
        assertNull(approvalTemplate.read(saved.getId()));
        assertFalse(approvalTemplate.canWrite(saved.getId()));
        assertFalse(approvalTemplate.canInitiate(saved.getId()));
    }

    @Test
    public void publishMakes未发布已发布AndReadOnly() {
        ProcessTemplate saved = save病假申请();

        approvalTemplate.publish(saved.getId());

        assertTrue(approvalTemplate.canInitiate(saved.getId()));
        assertFalse(approvalTemplate.canWrite(saved.getId()));
        assertEquals("病假申请", approvalTemplate.read(saved.getId()).getName());
    }

    @Test
    public void 已发布CannotSaveDeleteOrRepublishButCanBeRead() {
        assertEquals("请假申请", approvalTemplate.read(1L).getName());
        assertTrue(approvalTemplate.canInitiate(1L));
        assertFalse(approvalTemplate.canWrite(1L));

        ProcessTemplate change = new ProcessTemplate();
        change.setId(1L);
        change.setName("改掉请假申请");
        change.setProcessTypeId(1L);
        assertThrows(RuntimeException.class, () -> approvalTemplate.saveUnpublished(change));
        assertEquals("请假申请", approvalTemplate.read(1L).getName());

        assertThrows(RuntimeException.class, () -> approvalTemplate.removeUnpublished(1L));
        assertEquals("请假申请", approvalTemplate.read(1L).getName());

        assertThrows(RuntimeException.class, () -> approvalTemplate.publish(1L));
        assertTrue(approvalTemplate.canInitiate(1L));
        assertFalse(approvalTemplate.canWrite(1L));
    }

    @Test
    public void 按未发布审批模板发起DoesNotLeave审批() {
        assertNull(LoginUserInfoHelper.getUserId());

        ProcessTemplate unpublished = save病假申请();
        SysUser alice = user(2L, "alice", "小艾");
        ProcessFormVo form = new ProcessFormVo();
        form.setProcessTemplateId(unpublished.getId());
        form.setProcessTypeId(1L);
        form.setFormValues("{\"formData\":{\"days\":1},\"formShowData\":{\"请假天数\":1}}");

        assertThrows(RuntimeException.class, () -> approval.startUp(alice, form));
        assertTrue(approval.findStarted(alice, new Page<>(1, 10)).getRecords().isEmpty());
        assertNull(LoginUserInfoHelper.getUserId());
    }

    @Test
    public void 已发布审批模板CanStartApproval() {
        SysUser alice = user(2L, "alice", "小艾");

        approval.startUp(alice, publishedLeaveForm());

        assertEquals(1, approval.findStarted(alice, new Page<>(1, 10)).getRecords().size());
        assertEquals(审批中, approval.findStarted(alice, new Page<>(1, 10)).getRecords().get(0).getStatus());
    }

    private ProcessTemplate save病假申请() {
        ProcessTemplate template = 病假申请();
        approvalTemplate.saveUnpublished(template);
        return template;
    }

    private static ProcessTemplate 病假申请() {
        ProcessTemplate template = new ProcessTemplate();
        template.setName("病假申请");
        template.setProcessTypeId(1L);
        template.setProcessDefinitionKey("approvalTwoStep");
        template.setProcessDefinitionPath("processes/qingjia.zip");
        template.setDescription("员工病假");
        return template;
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
