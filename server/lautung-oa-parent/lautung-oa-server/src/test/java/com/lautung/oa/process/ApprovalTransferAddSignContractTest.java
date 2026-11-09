package com.lautung.oa.process;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jayway.jsonpath.JsonPath;
import com.lautung.oa.framework.config.exception.BusinessException;
import com.lautung.oa.model.process.ProcessRecord;
import com.lautung.oa.model.process.ProcessTemplate;
import com.lautung.oa.model.system.SysUser;
import com.lautung.oa.process.ProcessApprovalSupport.ApprovalActionType;
import com.lautung.oa.process.attachment.AttachmentService;
import com.lautung.oa.process.membership.ApprovalMembership;
import com.lautung.oa.process.membership.ApprovalMembershipResolver;
import com.lautung.oa.process.read.ApprovalListReader;
import com.lautung.oa.process.service.Approval;
import com.lautung.oa.process.service.ApprovalTemplate;
import com.lautung.oa.security.custom.LoginUserInfoHelper;
import com.lautung.oa.vo.process.AddSignVo;
import com.lautung.oa.vo.process.ApprovalVo;
import com.lautung.oa.vo.process.ProcessFormVo;
import com.lautung.oa.vo.process.ProcessVo;
import com.lautung.oa.vo.process.TransferVo;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
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
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 转办与加签合同（#288，ADR 0068）：前/后加签全流程（多人全通过、任一拒绝整条拒绝、
 * 前签回原处理人、后签通过记录保留、批次后再加签、加签人不能再加签）、转办可见性与
 * 再转办、成员资格五关系读/补传/动作三档矩阵、待处理三源合成与 total、候选认领并发
 * 后到者拒绝、挂起行与候选人行呈现。201 拒绝一律钉 $.message。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql({"/auth-contract-schema.sql", "/approval-contract-schema.sql"})
public class ApprovalTransferAddSignContractTest {

    private static final int 审批中 = 1;
    private static final int 已拒绝 = -1;
    private static final int 已通过 = 2;
    private static final String ADD_SIGN = "ADD_SIGN";
    private static final String APPROVE = "APPROVE";
    private static final String CANDIDATE = "CANDIDATE";

    @Autowired
    private Approval approval;

    @Autowired
    private ApprovalListReader approvalListReader;

    @Autowired
    private ApprovalTemplate approvalTemplate;

    @Autowired
    private ApprovalMembershipResolver membershipResolver;

    @Autowired
    private AttachmentService attachmentService;

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private TaskService taskService;

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
    public void beforeAddSignFreezesHandlerUntilBatchPassesAndAllowsAnotherBatch() {
        SysUser alice = user(2L, "alice", "小艾");
        SysUser zhangsan = user(3L, "zhangsan", "张三");
        SysUser lisi = user(4L, "lisi", "李四");
        SysUser eve = user(5L, "eve", "小叶");

        approval.startUp(alice, publishedLeaveForm());
        ProcessVo engineRow = pending(zhangsan).getRecords().get(0);
        Long processId = engineRow.getId();
        assertEquals(APPROVE, engineRow.getActionKind());
        assertFalse(engineRow.getSuspended());

        // 前加签：1..N 人（会签口径），原任务保持活动但冻结。
        Approval.AddSignOutcome batch =
                approval.addSign(zhangsan, processId, addSignVo(1, List.of(4L, 5L)));
        assertEquals(1, batch.batchNo());
        assertEquals(2, batch.signTaskIds().size());

        // 账本：ADD_SIGN 行（operator=发起人、target=加签人清单）；等待文案指向加签人。
        assertEquals(1L, count("SELECT COUNT(*) FROM oa_process_record WHERE process_id = " + processId
                + " AND action_type = 'ADD_SIGN' AND operate_user_id = 3 AND target_user_name = '李四、小叶'"));
        assertEquals("等待李四,小叶加签", processDescription(processId));

        // 原处理人挂起：仍在待处理、可读可补传、不可动作；详情隐藏动作面并带挂起标记。
        ProcessVo suspendedRow = pending(zhangsan).getRecords().get(0);
        assertEquals(1, pending(zhangsan).getTotal(), "挂起行保留在待处理");
        assertEquals(APPROVE, suspendedRow.getActionKind());
        assertTrue(suspendedRow.getSuspended(), "挂起标记必须下发");
        assertEquals(engineRow.getTaskId(), suspendedRow.getTaskId(), "挂起行仍是配对引擎任务");
        assertEquals("等待加签人处理", pendingStateLabel(zhangsan, processId));
        ApprovalMembership suspended = membership(zhangsan, processId);
        assertTrue(suspended.canRead());
        assertTrue(suspended.canUpload(), "挂起中的原处理人可补传");
        assertFalse(suspended.canAct(suspendedRow.getTaskId()));
        assertFalse(suspended.hasActionableTask());
        assertEquals(Boolean.FALSE, show(zhangsan, processId).get("isApprove"), "挂起行详情无动作面");
        BusinessException frozenAct = assertThrows(BusinessException.class,
                () -> approval.approve(zhangsan, decision(suspendedRow, 1)));
        assertEquals(403, frozenAct.getCode());
        BusinessException frozenTransfer = assertThrows(BusinessException.class,
                () -> approval.transfer(zhangsan, processId, transferVo(5L)));
        assertEquals(201, frozenTransfer.getCode());
        assertEquals("加签处理中，暂不能转办", frozenTransfer.getMessage());

        // 加签人行：actionKind=ADD_SIGN，taskId 是加签任务 id，正常可动作。
        ProcessVo lisiRow = pending(lisi).getRecords().get(0);
        assertEquals(ADD_SIGN, lisiRow.getActionKind());
        assertEquals(String.valueOf(batch.signTaskIds().get(0)), lisiRow.getTaskId());
        assertFalse(lisiRow.getSuspended());
        // 加签人不能再加签（没有可动作引擎任务）。
        BusinessException signerAddSign = assertThrows(BusinessException.class,
                () -> approval.addSign(lisi, processId, addSignVo(1, List.of(5L))));
        assertEquals(201, signerAddSign.getCode());
        assertEquals("只有当前处理人可以加签", signerAddSign.getMessage());
        // 同一批次进行中不能再加签（发起人被挂起挡住；批次判定显式断言：1..N 个未决行并行）。
        assertEquals(2L, count("SELECT COUNT(*) FROM oa_process_add_sign_task "
                + "WHERE engine_task_id = '" + engineRow.getTaskId() + "' AND status = 'PENDING'"));

        // 多人全通过前：批次未收口（李四通过后小叶仍在）。
        approval.approve(lisi, decision(lisiRow, 1));
        assertEquals(1, processed(lisi).getTotal(), "加签通过是处理，进已处理白名单");
        assertTrue(pending(zhangsan).getRecords().get(0).getSuspended(), "批次未收口前原处理人仍挂起");

        // 小叶通过 → 批次收口：解挂回原处理人，等待文案复原。
        approval.approve(eve, decision(pending(eve).getRecords().get(0), 1));
        ProcessVo restoredRow = pending(zhangsan).getRecords().get(0);
        assertFalse(restoredRow.getSuspended(), "加签人全部通过后原任务解挂");
        assertEquals("等待张三审批", processDescription(processId));
        assertTrue(membership(zhangsan, processId).canAct(restoredRow.getTaskId()));

        // 回到原处理人后可再来一批（批次号递增），再走一遍全通过。
        Approval.AddSignOutcome secondBatch =
                approval.addSign(zhangsan, processId, addSignVo(1, List.of(4L)));
        assertEquals(2, secondBatch.batchNo(), "前加签回原处理人后可再来一批");
        approval.approve(lisi, decision(pending(lisi).getRecords().get(0), 1));
        assertFalse(pending(zhangsan).getRecords().get(0).getSuspended());

        // 原处理人回待处理并可动作：通过进二审，二审通过整条完成。
        approval.approve(zhangsan, decision(pending(zhangsan).getRecords().get(0), 1));
        approval.approve(lisi, decision(pending(lisi).getRecords().get(0), 1));
        assertEquals(已通过, processStatus(processId));
        assertEquals(0, pending(zhangsan).getTotal());
        assertEquals(0, pending(lisi).getTotal());
    }

    @Test
    public void afterAddSignRecordsApprovalAndCompletesTheNodeOnlyAfterBatchPasses() {
        SysUser alice = user(2L, "alice", "小艾");
        SysUser zhangsan = user(3L, "zhangsan", "张三");
        SysUser lisi = user(4L, "lisi", "李四");
        SysUser eve = user(5L, "eve", "小叶");

        approval.startUp(alice, publishedLeaveForm());
        ProcessVo engineRow = pending(zhangsan).getRecords().get(0);
        Long processId = engineRow.getId();
        String instanceId = engineRow.getProcessInstanceId();

        approval.addSign(zhangsan, processId, addSignVo(2, List.of(4L, 5L)));

        // 后加签：原处理人的通过先记账（APPROVE 账本行进已处理白名单），引擎任务不完成。
        assertEquals(1L, count("SELECT COUNT(*) FROM oa_process_record WHERE process_id = " + processId
                + " AND action_type = 'APPROVE' AND operate_user_id = 3 AND description = '已通过'"));
        assertEquals(1, processed(zhangsan).getTotal(), "已记账的通过进已处理");
        assertEquals(审批中, processStatus(processId));
        assertEquals(0, approvalListReader.pending(new Page<>(1, 10), zhangsan).getTotal(),
                "后加签原处理人通过后不再进待处理");
        assertEquals(1, taskService.createTaskQuery()
                .processInstanceId(instanceId).taskAssignee("zhangsan").count(),
                "引擎任务未完成，仍在原处理人名下");
        assertEquals("等待李四,小叶加签", processDescription(processId));

        // 批次未收口：加签人逐个通过，节点不前移。
        approval.approve(lisi, decision(pending(lisi).getRecords().get(0), 1));
        approval.approve(eve, decision(pending(eve).getRecords().get(0), 1));
        // 加签人全部通过 → 用已记账的通过完成当前节点进入下一节点（二审 lisi）。
        assertEquals("等待李四审批", processDescription(processId));
        assertEquals(1, pending(lisi).getTotal());
        assertEquals(0, pending(zhangsan).getTotal());

        approval.approve(lisi, decision(pending(lisi).getRecords().get(0), 1));
        assertEquals(已通过, processStatus(processId));
    }

    @Test
    public void anySignerRejectionRejectsWholeApprovalAndDestroysRemainingSignTasks() {
        SysUser alice = user(2L, "alice", "小艾");
        SysUser zhangsan = user(3L, "zhangsan", "张三");
        SysUser lisi = user(4L, "lisi", "李四");
        SysUser eve = user(5L, "eve", "小叶");

        // 后加签 + 拒绝：原处理人已记账的通过保留。
        approval.startUp(alice, publishedLeaveForm());
        ProcessVo first = pending(zhangsan).getRecords().get(0);
        Long firstProcessId = first.getId();
        approval.addSign(zhangsan, firstProcessId, addSignVo(2, List.of(4L, 5L)));
        approval.approve(lisi, decision(pending(lisi).getRecords().get(0), -1));

        assertEquals(已拒绝, processStatus(firstProcessId), "任一加签人拒绝即整条审批拒绝");
        assertEquals("审批完成（拒绝）", processDescription(firstProcessId));
        assertEquals(1L, count("SELECT COUNT(*) FROM oa_process_record WHERE process_id = " + firstProcessId
                + " AND action_type = 'APPROVE' AND operate_user_id = 3"), "后加签原处理人已记账的通过保留");
        assertEquals(0, pending(eve).getTotal(), "剩余加签任务销毁，不进任何人待处理");
        assertEquals(0L, count("SELECT COUNT(*) FROM oa_process_add_sign_task WHERE process_id = "
                + firstProcessId + " AND status = 'PENDING'"));

        // 前加签 + 拒绝：配对引擎任务走 rejectToEnd，整条拒绝。
        approval.startUp(alice, publishedLeaveForm());
        ProcessVo second = pending(zhangsan).getRecords().get(0);
        Long secondProcessId = second.getId();
        approval.addSign(zhangsan, secondProcessId, addSignVo(1, List.of(4L, 5L)));
        approval.approve(eve, decision(pending(eve).getRecords().get(0), -1));
        assertEquals(已拒绝, processStatus(secondProcessId));
        assertEquals(0, pending(lisi).getTotal());
        assertEquals(0, pending(zhangsan).getTotal());
        // 时间线（从留下拒绝记录的加签人读）：最后一行是 REJECT，加签记录仍在；
        // 未留通过/拒绝记录的原处理人按五关系严格口径不再可读（账本无他的动作行）。
        List<String> timeline = recordDescriptions(eve, secondProcessId);
        assertEquals("已拒绝", timeline.get(timeline.size() - 1));
        assertTrue(timeline.contains("已加签"));
        BusinessException initiatorLosesRead = assertThrows(BusinessException.class,
                () -> approval.show(zhangsan, secondProcessId));
        assertEquals(403, initiatorLosesRead.getCode());
    }

    @Test
    public void addSignAndTransferRefusalsAreNamedAndNeverWrite() {
        SysUser alice = user(2L, "alice", "小艾");
        SysUser zhangsan = user(3L, "zhangsan", "张三");
        approval.startUp(alice, publishedLeaveForm());
        Long processId = pending(zhangsan).getRecords().get(0).getId();

        assertNamed201(() -> approval.addSign(zhangsan, processId, addSignVo(1, List.of(3L))),
                "不能加签给自己");
        assertNamed201(() -> approval.addSign(zhangsan, processId, addSignVo(1, List.of(2L))),
                "不能加签给申请人");
        assertNamed201(() -> approval.addSign(zhangsan, processId, addSignVo(1, List.of(999L))),
                "加签人不存在或已停用");
        assertNamed201(() -> approval.addSign(zhangsan, processId, addSignVo(0, List.of(4L))),
                "加签类型只能为 1（前加签）或 2（后加签）");
        assertNamed201(() -> approval.transfer(zhangsan, processId, transferVo(3L)), "不能转办给自己");
        assertNamed201(() -> approval.transfer(zhangsan, processId, transferVo(2L)), "不能转办给申请人");
        assertNamed201(() -> approval.transfer(zhangsan, processId, transferVo(999L)), "转办目标不存在或已停用");
        assertNamed201(() -> approval.transfer(alice, processId, transferVo(5L)), "只有当前处理人可以转办");
        assertNamed201(() -> approval.addSign(alice, processId, addSignVo(1, List.of(4L))),
                "只有当前处理人可以加签");
        assertNamed201(() -> approval.transfer(zhangsan, 4321L, transferVo(5L)), "审批不存在");

        // 非审批中：终态后整条不可转办/加签。
        approval.approve(zhangsan, decision(pending(zhangsan).getRecords().get(0), -1));
        assertNamed201(() -> approval.transfer(zhangsan, processId, transferVo(5L)), "只有审批中的审批可以转办");
        assertNamed201(() -> approval.addSign(zhangsan, processId, addSignVo(1, List.of(4L))),
                "只有审批中的审批可以加签");

        // 全部拒绝路径零写入：无加签任务、无 TRANSFER 账本、无状态档变化。
        assertEquals(0L, count("SELECT COUNT(*) FROM oa_process_add_sign_task WHERE process_id = " + processId));
        assertEquals(0L, count("SELECT COUNT(*) FROM oa_process_record WHERE process_id = " + processId
                + " AND action_type = 'TRANSFER'"));
        assertEquals(已拒绝, processStatus(processId));
    }

    @Test
    public void transferMovesApprovalPermanentlyAndKeepsOriginalHandlerReadOnly() {
        SysUser alice = user(2L, "alice", "小艾");
        SysUser zhangsan = user(3L, "zhangsan", "张三");
        SysUser lisi = user(4L, "lisi", "李四");
        SysUser eve = user(5L, "eve", "小叶");

        approval.startUp(alice, publishedLeaveForm());
        ProcessVo engineRow = pending(zhangsan).getRecords().get(0);
        Long processId = engineRow.getId();

        approval.transfer(zhangsan, processId, transferVo(5L));

        // 账本与文案：TRANSFER 行 target=目标；等待文案指向新处理人。
        Map<String, Object> transferRow = jdbcTemplate.queryForMap(
                "SELECT operate_user_id, target_user_id, target_user_name, description FROM oa_process_record "
                        + "WHERE process_id = ? AND action_type = 'TRANSFER'", processId);
        assertEquals(3L, ((Number) transferRow.get("operate_user_id")).longValue());
        assertEquals(5L, ((Number) transferRow.get("target_user_id")).longValue());
        assertEquals("小叶", transferRow.get("target_user_name"));
        assertEquals("已转办", transferRow.get("description"));
        assertEquals("等待小叶审批", processDescription(processId));
        assertEquals(审批中, processStatus(processId), "转办不改变审批状态");

        // 原处理人成为转办经手人：只读（能看详情与附件、不能处理不能补传）。
        assertNotNull(show(zhangsan, processId).get("process"), "转办经手人能看详情");
        assertFalse(attachmentService.listForProcess(zhangsan, processId).canUpload(), "转办经手人不能补传");
        assertTrue(attachmentService.listForProcess(zhangsan, processId).attachments().isEmpty());
        assertEquals(0, pending(zhangsan).getTotal(), "转办经手人不再有待处理行");
        ApprovalMembership handler = membership(zhangsan, processId);
        assertTrue(handler.isTransferHandler());
        assertTrue(handler.canRead());
        assertFalse(handler.canUpload());
        assertFalse(handler.canAct(engineRow.getTaskId()));
        BusinessException readOnly = assertThrows(BusinessException.class,
                () -> approval.approve(zhangsan, decision(engineRow, 1)));
        assertEquals(403, readOnly.getCode());

        // 目标成为当前处理人：可动作。
        ProcessVo eveRow = pending(eve).getRecords().get(0);
        assertEquals(APPROVE, eveRow.getActionKind());
        assertEquals(eveRow.getTaskId(), pending(eve).getRecords().get(0).getTaskId());

        // 可再次转办（不设转回与接受态）：小叶转给李四，李四处理后整条继续。
        approval.transfer(eve, processId, transferVo(4L));
        assertEquals("等待李四审批", processDescription(processId));
        assertEquals(2L, count("SELECT COUNT(*) FROM oa_process_record WHERE process_id = " + processId
                + " AND action_type = 'TRANSFER'"));
        approval.approve(lisi, decision(pending(lisi).getRecords().get(0), 1));
        approval.approve(lisi, decision(pending(lisi).getRecords().get(0), 1));
        assertEquals(已通过, processStatus(processId));
    }

    @Test
    public void membershipFiveRelationMatrixCoversReadUploadAct() {
        SysUser alice = user(2L, "alice", "小艾");
        SysUser zhangsan = user(3L, "zhangsan", "张三");
        SysUser lisi = user(4L, "lisi", "李四");
        SysUser eve = user(5L, "eve", "小叶");
        SysUser ccReader = user(6L, "cc-reader", "抄送阅读人");
        approval.startUp(alice, publishedLeaveForm());
        Long processId = pending(zhangsan).getRecords().get(0).getId();
        String engineTaskId = pending(zhangsan).getRecords().get(0).getTaskId();
        jdbcTemplate.update(
                "INSERT INTO oa_process_cc (process_id, user_id, source_type, source_key, is_deleted) "
                        + "VALUES (?, 6, 'START', ?, 0)", processId, String.valueOf(processId));
        taskService.addUserIdentityLink(engineTaskId, "lisi", IdentityLinkType.CANDIDATE);

        // 申请人：读/补传 ✓，动作 ✗。当前处理人（assignee）：三档全 ✓。
        assertMatrix(membership(alice, processId), true, false);
        assertMatrix(membership(zhangsan, processId), true, true);
        // 候选人：处理即认领，三档全 ✓（动作面对同一引擎任务 id）。
        assertMatrix(membership(lisi, processId), true, true);

        // 前加签挂起的原处理人：读/补传 ✓、动作 ✗（不可动作标记）。
        approval.addSign(zhangsan, processId, addSignVo(1, List.of(5L)));
        ApprovalMembership suspendedHandler = membership(zhangsan, processId);
        assertMatrix(suspendedHandler, true, false);
        assertTrue(suspendedHandler.isSuspended());
        // 加签人：读/补传 ✓、动作只对加签任务 id。
        ApprovalMembership signAssignee = membership(eve, processId);
        assertMatrix(signAssignee, true, true);
        assertTrue(signAssignee.canAct(pending(eve).getRecords().get(0).getTaskId()));
        assertFalse(signAssignee.canAct(engineTaskId), "加签人对配对引擎任务不可动作");

        // 批次收口后原处理人通过进二审；历史处理人（账本 APPROVE）只读。
        approval.approve(eve, decision(pending(eve).getRecords().get(0), 1));
        approval.approve(zhangsan, decision(pending(zhangsan).getRecords().get(0), 1));
        ApprovalMembership pastApprover = membership(zhangsan, processId);
        assertTrue(pastApprover.isPastApprover());
        assertMatrix(pastApprover, false, false);

        // 转办经手人（账本 TRANSFER 推导）：只读。
        approval.transfer(lisi, processId, transferVo(5L));
        ApprovalMembership transferHandler = membership(lisi, processId);
        assertTrue(transferHandler.isTransferHandler());
        assertMatrix(transferHandler, false, false);
        // 抄送人：只读。局外人：连详情都不可读（canRead=false 的行为面）。
        assertMatrix(membership(ccReader, processId), false, false);
        SysUser outsider = user(9L, "outsider", "局外人");
        BusinessException invisible = assertThrows(BusinessException.class,
                () -> approval.show(outsider, processId));
        assertEquals(403, invisible.getCode());
    }

    @Test
    public void pendingSynthesisCoversThreeSourcesTotalsAndUrgeMarks() {
        SysUser alice = user(2L, "alice", "小艾");
        SysUser zhangsan = user(3L, "zhangsan", "张三");
        SysUser lisi = user(4L, "lisi", "李四");
        SysUser eve = user(5L, "eve", "小叶");

        // 审批 A：引擎 assignee 行（第一源）+ 催办标记（process 级判定，24h 窗口）。
        approval.startUp(alice, publishedLeaveForm());
        ProcessVo engineRow = pending(zhangsan).getRecords().get(0);
        Long processId = engineRow.getId();
        assertFalse(pending(zhangsan).getRecords().get(0).getUrged());
        approval.urge(alice, processId);
        assertTrue(pending(zhangsan).getRecords().get(0).getUrged());
        assertEquals(1L, count("SELECT COUNT(*) FROM oa_process_record WHERE process_id = " + processId
                + " AND action_type = 'URGE'"));

        // 审批 B：引擎候选人行（第二源）。认领前候选人与 assignee 同查（处理即认领）；
        // 催办标记不跨审批（process 级）。
        approval.startUp(alice, publishedLeaveForm());
        ProcessVo secondEngineRow = pending(zhangsan).getRecords().stream()
                .filter(row -> !row.getId().equals(processId)).findFirst().orElseThrow();
        Long secondProcessId = secondEngineRow.getId();
        taskService.unclaim(secondEngineRow.getTaskId());
        taskService.addUserIdentityLink(secondEngineRow.getTaskId(), "lisi", IdentityLinkType.CANDIDATE);
        taskService.addUserIdentityLink(secondEngineRow.getTaskId(), "eve", IdentityLinkType.CANDIDATE);
        assertEquals(1, pending(lisi).getTotal());
        assertEquals(CANDIDATE, pending(lisi).getRecords().get(0).getActionKind());
        assertFalse(pending(lisi).getRecords().get(0).getUrged(), "催办标记不跨审批");
        assertEquals("候选人之一，处理即认领", pendingStateLabel(lisi, secondProcessId));

        // 第三源加签：原处理人认领后前加签 → 挂起行（APPROVE+suspended）；候选人行随认领消失，
        // 加签行接管李四的待处理（每人每审批一行）。
        taskService.claim(secondEngineRow.getTaskId(), "zhangsan");
        approval.addSign(zhangsan, secondProcessId, addSignVo(1, List.of(4L)));
        ProcessVo suspendedRow = pending(zhangsan).getRecords().stream()
                .filter(row -> row.getId().equals(secondProcessId)).findFirst().orElseThrow();
        assertTrue(suspendedRow.getSuspended());
        assertEquals(APPROVE, suspendedRow.getActionKind());
        assertEquals("等待加签人处理", pendingStateLabel(zhangsan, secondProcessId));
        assertEquals(1, pending(lisi).getTotal());
        assertEquals(ADD_SIGN, pending(lisi).getRecords().get(0).getActionKind());
        assertNotEquals(secondEngineRow.getTaskId(), pending(lisi).getRecords().get(0).getTaskId());

        // total 按合成行数：zhangsan 两个审批各一行（A 行带催办标记、B 行挂起）。
        assertEquals(2, pending(zhangsan).getTotal());
    }

    @Test
    public void urgeTargetsIncludePendingAddSignAssignees() {
        SysUser alice = user(2L, "alice", "小艾");
        SysUser zhangsan = user(3L, "zhangsan", "张三");

        approval.startUp(alice, publishedLeaveForm());
        ProcessVo engineRow = pending(zhangsan).getRecords().get(0);
        Long processId = engineRow.getId();
        // 前加签批次进行中：加签人 eve 属当前处理人（ADR 0068「对象是当前全部处理人」），
        // 催办对象在引擎 assignee/候选人之外必须补上 PENDING 加签任务的加签人。
        approval.addSign(zhangsan, processId, addSignVo(1, List.of(5L)));

        approval.urge(alice, processId);

        String targets = jdbcTemplate.queryForObject(
                "SELECT target_user_name FROM oa_process_record "
                        + "WHERE process_id = ? AND action_type = 'URGE'", String.class, processId);
        assertTrue(targets.contains("张三"), "引擎 assignee 在催办对象里：" + targets);
        assertTrue(targets.contains("小叶"), "PENDING 加签任务的加签人在催办对象里：" + targets);
    }

    @Test
    public void candidateClaimsConcurrentlyAndLatecomerIsRefused() {
        SysUser alice = user(2L, "alice", "小艾");
        SysUser lisi = user(4L, "lisi", "李四");
        SysUser eve = user(5L, "eve", "小叶");

        approval.startUp(alice, publishedLeaveForm());
        ProcessVo engineRow = pending(zhangsan()).getRecords().get(0);
        // 认领前：候选人与 assignee 同查——先到者处理即认领，后到者被拒（并发面由
        // ApprovalContractTest 的 complete 屏障用例钉 201；此处钉认领后资格面的拒绝）。
        taskService.unclaim(engineRow.getTaskId());
        taskService.addUserIdentityLink(engineRow.getTaskId(), "lisi", IdentityLinkType.CANDIDATE);
        taskService.addUserIdentityLink(engineRow.getTaskId(), "eve", IdentityLinkType.CANDIDATE);
        assertEquals(1, pending(lisi).getTotal());
        assertEquals(1, pending(eve).getTotal());

        approval.approve(lisi, decision(pending(lisi).getRecords().get(0), 1));
        BusinessException latecomer = assertThrows(BusinessException.class,
                () -> approval.approve(eve, decision(engineRow, 1)));
        assertEquals(403, latecomer.getCode(), "后到者已不是当前处理人（任务被认领并处理）");
        assertEquals(0, pending(eve).getTotal(), "二审是 lisi，eve 的候选行随认领消失");
    }

    @Test
    public void httpActionEnvelopesPinRefusalMessagesAndSignTaskApproves() throws Exception {
        SysUser alice = user(2L, "alice", "小艾");
        SysUser zhangsan = user(3L, "zhangsan", "张三");
        SysUser lisi = user(4L, "lisi", "李四");
        approval.startUp(alice, publishedLeaveForm());
        Long processId = pending(zhangsan).getRecords().get(0).getId();
        String aliceToken = loginToken("alice");
        String zhangsanToken = loginToken("zhangsan");
        String lisiToken = loginToken("lisi");

        // 选人端点：启用用户、排除申请人；登录即可（不挂页面 gate）。
        mockMvc.perform(get("/admin/process/actionTargets/" + processId).header("token", aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(5))
                .andExpect(jsonPath("$.data[?(@.id == 2)]").isEmpty());

        // 加签（前加签李四）后，加签任务 id 走同一 approve 端点；重复提交命名拒绝。
        mockMvc.perform(post("/admin/process/addSign/" + processId)
                        .header("token", zhangsanToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"signType\":1,\"assigneeIds\":[4]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post("/admin/process/addSign/" + processId)
                        .header("token", zhangsanToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"signType\":3,\"assigneeIds\":[4]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("加签处理中，暂不能加签"));
        String signTaskId = pending(lisi).getRecords().get(0).getTaskId();
        mockMvc.perform(post("/admin/process/approve")
                        .header("token", lisiToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"processId\":" + processId + ",\"taskId\":\"" + signTaskId
                                + "\",\"status\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post("/admin/process/approve")
                        .header("token", lisiToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"processId\":" + processId + ",\"taskId\":\"" + signTaskId
                                + "\",\"status\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("该审批已被其他人处理"));

        // 转办：目标非法 201 钉 $.message；合法转办后原处理人 201。
        mockMvc.perform(post("/admin/process/transfer/" + processId)
                        .header("token", zhangsanToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetUserId\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("不能转办给申请人"));
        mockMvc.perform(post("/admin/process/transfer/" + processId)
                        .header("token", zhangsanToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetUserId\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post("/admin/process/transfer/" + processId)
                        .header("token", zhangsanToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetUserId\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("只有当前处理人可以转办"));
    }

    @TestConfiguration
    static class InMemoryRedisConfig {
        @Bean
        @Primary
        public org.springframework.data.redis.core.RedisTemplate<String, String> redisTemplate() {
            return new com.lautung.oa.support.InMemoryRedisTemplate();
        }
    }

    // ---- 夹具与 helpers（与 ApprovalActionContractTest 同款形状） ----

    private void assertNamed201(Runnable action, String expectedMessage) {
        BusinessException refused = assertThrows(BusinessException.class, action::run);
        assertEquals(201, refused.getCode());
        assertEquals(expectedMessage, refused.getMessage());
    }

    private void assertMatrix(ApprovalMembership membership, boolean canUpload, boolean hasActionable) {
        assertTrue(membership.canRead(), "五种关系皆可读");
        assertEquals(canUpload, membership.canUpload());
        assertEquals(hasActionable, membership.hasActionableTask());
    }

    private ApprovalMembership membership(SysUser user, Long processId) {
        com.lautung.oa.model.process.Process process =
                approval.requireApprovalMember(processId, user);
        return membershipResolver.resolve(user, process);
    }

    /** 行状态呈现语义（#288）：挂起行/候选人行/加签行的文案与客户端共用同一映射。 */
    private String pendingStateLabel(SysUser user, Long processId) {
        Object row = show(user, processId).get("pendingRow");
        if (row == null) {
            return "";
        }
        ProcessVo pendingRow = (ProcessVo) row;
        if (Boolean.TRUE.equals(pendingRow.getSuspended())) {
            return "等待加签人处理";
        }
        if (CANDIDATE.equals(pendingRow.getActionKind())) {
            return "候选人之一，处理即认领";
        }
        return "";
    }

    private Map<String, Object> show(SysUser user, Long processId) {
        return approval.show(user, processId);
    }

    private List<String> recordDescriptions(SysUser user, Long processId) {
        @SuppressWarnings("unchecked")
        List<ProcessRecord> records =
                (List<ProcessRecord>) approval.show(user, processId).get("processRecordList");
        return records.stream().map(ProcessRecord::getDescription).toList();
    }

    private String processDescription(Long processId) {
        return jdbcTemplate.queryForObject(
                "SELECT description FROM oa_process WHERE id = ?", String.class, processId);
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

    private IPage<ProcessVo> pending(SysUser user) {
        return approvalListReader.pending(new Page<>(1, 10), user);
    }

    private IPage<ProcessVo> processed(SysUser user) {
        return approvalListReader.processed(new Page<>(1, 10), user);
    }

    private ApprovalVo decision(ProcessVo pending, int status) {
        ApprovalVo approvalVo = new ApprovalVo();
        approvalVo.setProcessId(pending.getId());
        approvalVo.setTaskId(pending.getTaskId());
        approvalVo.setStatus(status);
        return approvalVo;
    }

    private static AddSignVo addSignVo(int signType, List<Long> assigneeIds) {
        AddSignVo vo = new AddSignVo();
        vo.setSignType(signType);
        vo.setAssigneeIds(assigneeIds);
        return vo;
    }

    private static TransferVo transferVo(Long targetUserId) {
        TransferVo vo = new TransferVo();
        vo.setTargetUserId(targetUserId);
        return vo;
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

    private SysUser zhangsan() {
        return user(3L, "zhangsan", "张三");
    }

    private void clearEngineInstances() {
        runtimeService.createProcessInstanceQuery().list()
                .forEach(instance -> runtimeService.deleteProcessInstance(instance.getId(), "contract-reset"));
    }

    private static byte[] approvalTwoStepZip() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (InputStream bpmn = ApprovalTransferAddSignContractTest.class.getClassLoader()
                     .getResourceAsStream("process/approval-two-step.bpmn20.xml");
             ZipOutputStream zip = new ZipOutputStream(bytes)) {
            if (bpmn == null) throw new IllegalStateException("审批合同 BPMN 不存在");
            zip.putNextEntry(new ZipEntry("approval-two-step.bpmn20.xml"));
            bpmn.transferTo(zip);
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }
}
