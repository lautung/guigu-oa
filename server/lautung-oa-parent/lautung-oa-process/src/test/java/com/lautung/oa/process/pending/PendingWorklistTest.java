package com.lautung.oa.process.pending;

import com.lautung.oa.auth.service.SysUserService;
import com.lautung.oa.model.process.Process;
import com.lautung.oa.model.process.ProcessRecord;
import com.lautung.oa.model.system.SysUser;
import com.lautung.oa.process.ProcessApprovalSupport;
import com.lautung.oa.process.approval.ApprovalEngine;
import com.lautung.oa.process.mapper.OaProcessMapper;
import com.lautung.oa.process.service.OaProcessRecordService;
import com.lautung.oa.vo.process.ProcessVo;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link PendingWorklist} 的单测面（#297，ADR 0076）：三源并集、AFTER/BEFORE 判定、
 * pendingRow 优先级、姓名投影与窗口排序都钉在公开方法上；引擎与加签证据用假实现，
 * 用户服务用 stub，不启动 Spring。
 */
class PendingWorklistTest {

    private final FakeApprovalEngine engine = new FakeApprovalEngine();
    private final FakePendingSignTaskEvidence signEvidence = new FakePendingSignTaskEvidence();
    private final OaProcessMapper processMapper = mock(OaProcessMapper.class);
    private final OaProcessRecordService processRecordService = mock(OaProcessRecordService.class);
    private final SysUserService userService = mock(SysUserService.class);

    @BeforeAll
    static void registerTableMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), ProcessRecord.class);
    }

    @Test
    void handlersReportEngineAndAddSignFactsWithoutResolvingNames() {
        engine.currentTasks("instance-1", List.of(
                new ApprovalEngine.CurrentTask("task-a", "alice", List.of("bob", "alice")),
                new ApprovalEngine.CurrentTask("task-b", "carol")));
        signEvidence.pending.addAll(List.of(
                new PendingSignTaskEvidence.SignTaskSnapshot(9002L, 42L, "task-a", "BEFORE", 4L, date(2)),
                new PendingSignTaskEvidence.SignTaskSnapshot(9001L, 42L, "task-a", "BEFORE", 5L, date(1)),
                new PendingSignTaskEvidence.SignTaskSnapshot(9003L, 42L, "task-b", "AFTER", 6L, date(3))));

        List<HandlerEntry> handlers = worklist().handlers(process(42L, "instance-1"));

        assertEquals(6, handlers.size());

        HandlerEntry assignee = handlers.get(0);
        assertEquals(HandlerEntry.Source.ENGINE_ASSIGNEE, assignee.source());
        assertEquals("alice", assignee.username());
        assertNull(assignee.userId());
        assertEquals("task-a", assignee.engineTaskId());
        assertNull(assignee.signTaskId());
        assertTrue(assignee.suspended());
        assertFalse(assignee.afterExcluded());
        assertNull(assignee.createTime());

        HandlerEntry candidate = handlers.get(1);
        assertEquals(HandlerEntry.Source.ENGINE_CANDIDATE, candidate.source());
        assertEquals("bob", candidate.username());
        assertEquals("task-a", candidate.engineTaskId());
        assertTrue(candidate.suspended());
        assertFalse(candidate.afterExcluded());

        HandlerEntry afterAssignee = handlers.get(2);
        assertEquals(HandlerEntry.Source.ENGINE_ASSIGNEE, afterAssignee.source());
        assertEquals("carol", afterAssignee.username());
        assertFalse(afterAssignee.suspended());
        assertTrue(afterAssignee.afterExcluded());

        HandlerEntry firstSign = handlers.get(3);
        assertEquals(HandlerEntry.Source.ADD_SIGN, firstSign.source());
        assertNull(firstSign.username());
        assertEquals(5L, firstSign.userId());
        assertEquals("task-a", firstSign.engineTaskId());
        assertEquals("9001", firstSign.signTaskId());
        assertFalse(firstSign.suspended());
        assertFalse(firstSign.afterExcluded());
        assertEquals(date(1), firstSign.createTime());

        HandlerEntry secondSign = handlers.get(4);
        assertEquals(4L, secondSign.userId());
        assertEquals("9002", secondSign.signTaskId());

        HandlerEntry afterSign = handlers.get(5);
        assertEquals(6L, afterSign.userId());
        assertEquals("task-b", afterSign.engineTaskId());
        assertEquals("9003", afterSign.signTaskId());

        verifyNoInteractions(userService);
    }

    @Test
    void pendingHandlerNamesKeepEngineOrderDedupeAndFallBackToUsername() {
        engine.currentTasks("instance-1", List.of(
                new ApprovalEngine.CurrentTask("task-a", "alice", List.of("bob")),
                new ApprovalEngine.CurrentTask("task-b", "carol")));
        signEvidence.pending.addAll(List.of(
                new PendingSignTaskEvidence.SignTaskSnapshot(9002L, 42L, "task-a", "BEFORE", 4L, date(2)),
                new PendingSignTaskEvidence.SignTaskSnapshot(9001L, 42L, "task-a", "BEFORE", 5L, date(1)),
                new PendingSignTaskEvidence.SignTaskSnapshot(9003L, 42L, "task-b", "AFTER", 6L, date(3)),
                new PendingSignTaskEvidence.SignTaskSnapshot(9004L, 42L, "task-a", "BEFORE", 7L, date(4))));
        when(userService.getUserByUserName("alice")).thenReturn(user(3L, "alice", "张三"));
        when(userService.getUserByUserName("bob")).thenReturn(null);
        when(userService.listByIds(anyList())).thenReturn(List.of(
                user(4L, "dave", "Dave"),
                user(5L, "alice", "张三"),
                user(7L, "", "   ")));

        List<String> names = worklist().pendingHandlerNames(process(42L, "instance-1"));

        // carol 在 AFTER 批次里被排除；alice 与加签人 alice 按 username 去重；
        // bob 查无用户退回 username；缺用户（6）与空 username（7）的加签人跳过。
        assertIterableEquals(List.of("张三", "bob", "Dave"), names);

        verify(userService).listByIds(argThat(ids ->
                new HashSet<>(ids).equals(Set.of(4L, 5L, 6L, 7L))));
    }

    @Test
    void pendingHandlerNamesDedupeSameAssigneeAcrossEngineTasks() {
        // 并行网关把同一人分到两个活动任务：等待文案/摘要按 username 去重，只出现一次。
        engine.currentTasks("instance-1", List.of(
                new ApprovalEngine.CurrentTask("task-a", "alice"),
                new ApprovalEngine.CurrentTask("task-b", "alice")));
        when(userService.getUserByUserName("alice")).thenReturn(user(3L, "alice", "张三"));

        assertIterableEquals(List.of("张三"),
                worklist().pendingHandlerNames(process(42L, "instance-1")));
    }

    @Test
    void pendingSynthesizesThreeSourcesAndSortsWithinTheWindow() {
        SysUser alice = user(3L, "alice", "小艾");
        engine.pendingTasks(3, List.of(
                pendingTask("task-b", "43", "alice", date(4)),
                pendingTask("task-a", "42", "alice", date(2)),
                pendingTask("task-c", "44", "bob", date(1))));
        signEvidence.pending.addAll(List.of(
                signTask(9002L, 46L, "task-other", "BEFORE", 3L, date(5)),
                signTask(9001L, 45L, "task-other", "BEFORE", 3L, date(3))));
        rowsById(row(42), row(43), row(44), row(45), row(46));

        IPage<ProcessVo> page = worklist().pending(new Page<>(1, 2), alice);

        assertEquals(5, page.getTotal());
        assertEquals(2, page.getRecords().size());
        ProcessVo first = page.getRecords().get(0);
        assertEquals(46L, first.getId());
        assertEquals("9002", first.getTaskId());
        assertEquals(ProcessApprovalSupport.ACTION_KIND_ADD_SIGN, first.getActionKind());
        assertFalse(first.getSuspended());
        ProcessVo second = page.getRecords().get(1);
        assertEquals(43L, second.getId());
        assertEquals("task-b", second.getTaskId());
        assertEquals(ProcessApprovalSupport.ACTION_KIND_APPROVE, second.getActionKind());
        assertFalse(second.getSuspended());

        // 窗口从两个源各取到 firstResult+size 行；全局排序 createTime desc → 源位次 → 源内 id。
        assertEquals(0, engine.lastFirstResult);
        assertEquals(2, engine.lastMaxResults);
        assertEquals(0L, signEvidence.lastOffset);
        assertEquals(2L, signEvidence.lastLimit);
    }

    @Test
    void pendingPageTwoUsesFirstResultPlusSizeWindowAndGlobalOrder() {
        SysUser alice = user(3L, "alice", "小艾");
        engine.pendingTasks(3, List.of(
                pendingTask("task-b", "43", "alice", date(4)),
                pendingTask("task-a", "42", "alice", date(2)),
                pendingTask("task-c", "44", "bob", date(1))));
        signEvidence.pending.addAll(List.of(
                signTask(9002L, 46L, "task-other", "BEFORE", 3L, date(5)),
                signTask(9001L, 45L, "task-other", "BEFORE", 3L, date(3))));
        rowsById(row(42), row(43), row(44), row(45), row(46));

        IPage<ProcessVo> page = worklist().pending(new Page<>(2, 2), alice);

        assertEquals(5, page.getTotal());
        assertEquals(2, page.getRecords().size());
        assertEquals(45L, page.getRecords().get(0).getId());
        assertEquals("9001", page.getRecords().get(0).getTaskId());
        assertEquals(ProcessApprovalSupport.ACTION_KIND_ADD_SIGN,
                page.getRecords().get(0).getActionKind());
        assertEquals(42L, page.getRecords().get(1).getId());
        assertEquals("task-a", page.getRecords().get(1).getTaskId());
        assertEquals(ProcessApprovalSupport.ACTION_KIND_APPROVE,
                page.getRecords().get(1).getActionKind());

        assertEquals(0, engine.lastFirstResult);
        assertEquals(4, engine.lastMaxResults);
        assertEquals(0L, signEvidence.lastOffset);
        assertEquals(4L, signEvidence.lastLimit);
    }

    @Test
    void pendingBreaksTiesBySourceRankThenSignIdDesc() {
        // 同刻行：引擎源在前；同为加签源时按源内 id 倒序。V137 的 create_time 是秒精度，
        // 同一批次的多行天然同刻，tie-break 不能只靠测试数据的日期差。
        SysUser alice = user(3L, "alice", "小艾");
        engine.pendingTasks(1, List.of(pendingTask("task-a", "42", "alice", date(1))));
        signEvidence.pending.addAll(List.of(
                signTask(9001L, 43L, "task-x", "BEFORE", 3L, date(1)),
                signTask(9002L, 44L, "task-y", "BEFORE", 3L, date(1))));
        rowsById(row(42), row(43), row(44));

        List<ProcessVo> records = worklist().pending(new Page<>(1, 10), alice).getRecords();

        assertEquals(3, records.size());
        assertEquals(List.of("task-a", "9002", "9001"),
                records.stream().map(ProcessVo::getTaskId).toList());
    }

    @Test
    void pendingExcludesAfterBatchesFromRowsAndTotalButKeepsBeforeSuspended() {
        SysUser alice = user(3L, "alice", "小艾");
        engine.pendingTasks(2, List.of(
                pendingTask("task-a", "42", "alice", date(2)),
                pendingTask("task-b", "43", "alice", date(1))));
        signEvidence.pending.addAll(List.of(
                signTask(9101L, 42L, "task-a", "BEFORE", 4L, date(2)),
                signTask(9102L, 43L, "task-b", "AFTER", 5L, date(1))));
        rowsById(row(42), row(43));

        IPage<ProcessVo> page = worklist().pending(new Page<>(1, 10), alice);

        // AFTER 批次的原处理人已记账通过：行与 total 都不收；BEFORE 行保留并挂起。
        assertEquals(1, page.getTotal());
        assertEquals(1, page.getRecords().size());
        ProcessVo only = page.getRecords().get(0);
        assertEquals(42L, only.getId());
        assertEquals("task-a", only.getTaskId());
        assertEquals(ProcessApprovalSupport.ACTION_KIND_APPROVE, only.getActionKind());
        assertTrue(only.getSuspended());
    }

    @Test
    void pendingMarksCandidateRowsWithCandidateActionKind() {
        SysUser alice = user(3L, "alice", "小艾");
        engine.pendingTasks(1, List.of(
                pendingTask("task-a", "42", "bob", date(1))));
        rowsById(row(42));

        IPage<ProcessVo> page = worklist().pending(new Page<>(1, 10), alice);

        assertEquals(1, page.getTotal());
        assertEquals(1, page.getRecords().size());
        assertEquals("task-a", page.getRecords().get(0).getTaskId());
        assertEquals(ProcessApprovalSupport.ACTION_KIND_CANDIDATE,
                page.getRecords().get(0).getActionKind());
        assertFalse(page.getRecords().get(0).getSuspended());
    }

    @Test
    void pendingRowPrefersEngineSourceAndKeepsBeforeSuspended() {
        SysUser alice = user(3L, "alice", "小艾");
        engine.currentTasks("instance-1", List.of(
                new ApprovalEngine.CurrentTask("task-a", "alice"),
                new ApprovalEngine.CurrentTask("task-b", "bob")));
        engine.currentTasks("instance-2", List.of(
                new ApprovalEngine.CurrentTask("task-c", "bob", List.of("alice"))));
        signEvidence.pending.addAll(List.of(
                signTask(9101L, 42L, "task-a", "BEFORE", 4L, date(2)),
                signTask(9001L, 42L, "task-a", "BEFORE", 3L, date(1))));
        rowsById(row(42), row(43));

        ProcessVo row = worklist().pendingRow(alice, process(42L, "instance-1"));

        assertEquals("task-a", row.getTaskId());
        assertEquals(ProcessApprovalSupport.ACTION_KIND_APPROVE, row.getActionKind());
        assertTrue(row.getSuspended());
        assertFalse(row.getUrged());

        ProcessVo candidateRow = worklist().pendingRow(alice, process(43L, "instance-2"));

        assertEquals("task-c", candidateRow.getTaskId());
        assertEquals(ProcessApprovalSupport.ACTION_KIND_CANDIDATE, candidateRow.getActionKind());
        assertFalse(candidateRow.getSuspended());
    }

    @Test
    void pendingRowReturnsNullOnAfterBatchWithoutFallingBackToSignSource() {
        SysUser alice = user(3L, "alice", "小艾");
        engine.currentTasks("instance-1", List.of(
                new ApprovalEngine.CurrentTask("task-a", "alice")));
        signEvidence.pending.addAll(List.of(
                signTask(9102L, 42L, "task-a", "AFTER", 4L, date(2)),
                signTask(9001L, 42L, "task-a", "BEFORE", 3L, date(1))));
        rowsById(row(42));

        assertNull(worklist().pendingRow(alice, process(42L, "instance-1")));
    }

    @Test
    void pendingRowFallsBackToLatestPendingSignTaskForTheUser() {
        SysUser alice = user(3L, "alice", "小艾");
        engine.currentTasks("instance-1", List.of(
                new ApprovalEngine.CurrentTask("task-a", "bob")));
        signEvidence.pending.addAll(List.of(
                signTask(9003L, 42L, "task-a", "BEFORE", 3L, date(3)),
                signTask(9001L, 42L, "task-a", "BEFORE", 3L, date(1)),
                signTask(9002L, 42L, "task-a", "BEFORE", 4L, date(2))));
        rowsById(row(42));

        ProcessVo row = worklist().pendingRow(alice, process(42L, "instance-1"));

        assertEquals("9003", row.getTaskId());
        assertEquals(ProcessApprovalSupport.ACTION_KIND_ADD_SIGN, row.getActionKind());
        assertFalse(row.getSuspended());
    }

    private PendingWorklist worklist() {
        return new PendingWorklist(engine, signEvidence, processMapper, processRecordService, userService);
    }

    private static Process process(Long id, String processInstanceId) {
        Process process = new Process();
        process.setId(id);
        process.setProcessInstanceId(processInstanceId);
        return process;
    }

    private static ProcessVo row(long id) {
        ProcessVo row = new ProcessVo();
        row.setId(id);
        return row;
    }

    private void rowsById(ProcessVo... rows) {
        Map<Long, ProcessVo> byId = new HashMap<>();
        for (ProcessVo row : rows) {
            byId.put(row.getId(), row);
        }
        when(processMapper.selectRowsByIds(anyList())).thenAnswer(invocation -> {
            List<Long> ids = invocation.getArgument(0);
            return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
        });
    }

    private static ApprovalEngine.PendingTask pendingTask(
            String taskId, String businessKey, String assignee, Date createTime) {
        return new ApprovalEngine.PendingTask(
                taskId, "instance-" + businessKey, businessKey, assignee, createTime);
    }

    private static PendingSignTaskEvidence.SignTaskSnapshot signTask(
            Long id, Long processId, String engineTaskId, String signType, Long assigneeId, Date createTime) {
        return new PendingSignTaskEvidence.SignTaskSnapshot(
                id, processId, engineTaskId, signType, assigneeId, createTime);
    }

    private static Date date(int day) {
        return new Date(1_700_000_000_000L + day * 86_400_000L);
    }

    private static SysUser user(Long id, String username) {
        return user(id, username, null);
    }

    private static SysUser user(Long id, String username, String name) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        user.setName(name);
        return user;
    }

    /** 引擎假实现：读侧按窗口切片，写侧一律不支持。 */
    private static final class FakeApprovalEngine implements ApprovalEngine {

        private final List<PendingTask> pending = new ArrayList<>();
        private final Map<String, List<CurrentTask>> currentByInstance = new HashMap<>();
        private long pendingCount;
        private int lastFirstResult;
        private int lastMaxResults;

        void pendingTasks(long total, List<PendingTask> tasks) {
            this.pendingCount = total;
            pending.clear();
            pending.addAll(tasks);
        }

        void currentTasks(String processInstanceId, List<CurrentTask> tasks) {
            currentByInstance.put(processInstanceId, tasks);
        }

        @Override
        public String start(String processDefinitionId, String businessKey, Map<String, Object> variables) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void complete(String taskId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void claim(String taskId, String username) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void rejectToEnd(String taskId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void cancel(String processInstanceId, String reason) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void reassign(String taskId, String fromUsername, String toUsername) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<PendingTask> pendingTasks(String username, int firstResult, int maxResults) {
            lastFirstResult = firstResult;
            lastMaxResults = maxResults;
            if (firstResult >= pending.size() || maxResults <= 0) {
                return List.of();
            }
            int to = (int) Math.min((long) firstResult + maxResults, pending.size());
            return pending.subList(firstResult, to);
        }

        @Override
        public long pendingCount(String username) {
            return pendingCount;
        }

        @Override
        public List<CurrentTask> currentTasks(String processInstanceId) {
            return currentByInstance.getOrDefault(processInstanceId, List.of());
        }
    }

    /** 加签任务证据假实现：同一份未决快照服务全部读方法。 */
    private static final class FakePendingSignTaskEvidence implements PendingSignTaskEvidence {

        private final List<SignTaskSnapshot> pending = new ArrayList<>();
        private Long pendingTotal;
        private Long lastAssigneeId;
        private long lastOffset;
        private long lastLimit;

        void pendingTotal(long total) {
            this.pendingTotal = total;
        }

        @Override
        public List<SignTaskSnapshot> pendingForUser(Long assigneeId, long offset, long limit) {
            lastAssigneeId = assigneeId;
            lastOffset = offset;
            lastLimit = limit;
            List<SignTaskSnapshot> mine = pending.stream()
                    .filter(task -> Objects.equals(task.assigneeId(), assigneeId))
                    .toList();
            if (offset >= mine.size() || limit <= 0) {
                return List.of();
            }
            int to = (int) Math.min(offset + limit, mine.size());
            return mine.subList((int) offset, to);
        }

        @Override
        public long countPendingForUser(Long assigneeId) {
            if (pendingTotal != null) {
                return pendingTotal;
            }
            return pending.stream()
                    .filter(task -> Objects.equals(task.assigneeId(), assigneeId))
                    .count();
        }

        @Override
        public List<SignTaskSnapshot> pendingForProcess(Long processId) {
            return pending.stream()
                    .filter(task -> Objects.equals(task.processId(), processId))
                    .toList();
        }

        @Override
        public List<SignTaskSnapshot> pendingByEngineTaskIds(List<String> engineTaskIds) {
            return pending.stream()
                    .filter(task -> engineTaskIds.contains(task.engineTaskId()))
                    .toList();
        }

        @Override
        public List<SignTaskSnapshot> pendingByType(String signType) {
            return pending.stream()
                    .filter(task -> Objects.equals(task.signType(), signType))
                    .toList();
        }
    }
}
