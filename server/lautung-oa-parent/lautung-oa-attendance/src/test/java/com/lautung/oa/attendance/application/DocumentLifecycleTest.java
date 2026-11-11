package com.lautung.oa.attendance.application;

import com.lautung.oa.attendance.mapper.AttendanceUserMapper;
import com.lautung.oa.framework.config.exception.BusinessException;
import com.lautung.oa.model.attendance.AttendanceApplyStatus;
import com.lautung.oa.model.system.SysUser;
import com.lautung.oa.process.business.BusinessApprovalOutcome;
import com.lautung.oa.process.business.BusinessApprovalReader;
import com.lautung.oa.process.business.BusinessApprovalStarter;
import com.lautung.oa.vo.attendance.AttendanceApplyQueryVo;
import com.lautung.oa.vo.process.BusinessApprovalStart;
import com.lautung.oa.vo.process.BusinessApprovalSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 业务单据生命周期 module 单测（#294，ADR 0075）：module 的接口就是测试面——草稿 CAS
 * 顺序、提交快照与摘要、同键重放、事务边界与「冲突重读在事务外」、终态同值 no-op /
 * 异值冲突 / CAS 0 行重读、状态迁移表。用假适配器驱动，不启动 Spring 上下文；
 * 事务管理器记录 begin/commit/rollback，回滚时把假适配器的单据状态复原。
 */
class DocumentLifecycleTest {

    private static final long APPLICANT = 29401L;
    private static final long OTHER_APPLICANT = 29402L;

    private final List<String> events = new ArrayList<>();

    private final FakeAdapter adapter = new FakeAdapter();

    private final RecordingTransactionManager transactions = new RecordingTransactionManager();

    private final BusinessApprovalStarter approvalStarter = mock(BusinessApprovalStarter.class);

    private final BusinessApprovalReader approvalReader = mock(BusinessApprovalReader.class);

    private final AttendanceUserMapper userMapper = mock(AttendanceUserMapper.class);

    private final DocumentLifecycle lifecycle;

    DocumentLifecycleTest() {
        transactions.onRollback = adapter::rollback;
        lifecycle = new DocumentLifecycle(approvalStarter, approvalReader, userMapper, transactions);
    }

    @BeforeEach
    void setUp() {
        events.clear();
        adapter.reset();
        when(userMapper.selectById(APPLICANT)).thenReturn(user(APPLICANT, "申请人", "t294"));
        when(userMapper.selectById(OTHER_APPLICANT))
                .thenReturn(user(OTHER_APPLICANT, "他人", "t294-other"));
        when(approvalStarter.start(any())).thenAnswer(invocation -> {
            events.add("start");
            return null;
        });
        // Mockito 对包装类型返回 0 而不是 null：显式钉住「没有在途提交」的默认读。
        when(approvalReader.inFlightSubmissionProcessId(any(), any(), any(), any())).thenReturn(null);
    }

    // ------------------------------------------------------------ 草稿编排

    @Test
    void saveDraftInsertsThenBindsLatestApprovalSummary() {
        BusinessApprovalSummary summary =
                new BusinessApprovalSummary(7L, 1, "审批人", "等待审批", new Date(), null);
        when(approvalReader.latestSummary("fake.document", 1L)).thenReturn(summary);

        String projection = lifecycle.saveDraft(adapter, APPLICANT, new FakeDocument(0, APPLICANT, null));

        assertEquals("detail:1:DRAFT", projection);
        assertSame(summary, adapter.boundSummary);
        assertEquals(List.of("insert", "bind"), events);
    }

    @Test
    void updateDraftRequiresOwnershipThenEditabilityThenCas() {
        long id = adapter.seed(APPLICANT, AttendanceApplyStatus.DRAFT);

        String projection = lifecycle.updateDraft(adapter, APPLICANT, id, new FakeDocument(id, APPLICANT, null));

        assertEquals("detail:" + id + ":DRAFT", projection);
        assertEquals("改了内容", adapter.store.get(id).payload);

        BusinessException notOwn = assertThrows(BusinessException.class,
                () -> lifecycle.updateDraft(adapter, OTHER_APPLICANT, id, new FakeDocument(id, OTHER_APPLICANT, null)));
        assertEquals("假单据不存在", notOwn.getMessage());

        long submitted = adapter.seed(APPLICANT, AttendanceApplyStatus.SUBMITTED);
        events.clear();
        BusinessException locked = assertThrows(BusinessException.class,
                () -> lifecycle.updateDraft(adapter, APPLICANT, submitted, new FakeDocument(submitted, APPLICANT, null)));
        assertEquals("假单据当前状态不能修改或删除", locked.getMessage());
        assertFalse(events.contains("cas:update"));
    }

    @Test
    void updateAndDeleteTranslateZeroCasRowsIntoRefreshMessage() {
        long id = adapter.seed(APPLICANT, AttendanceApplyStatus.DRAFT);

        adapter.forcedUpdateRows = 0;
        BusinessException updateConflict = assertThrows(BusinessException.class,
                () -> lifecycle.updateDraft(adapter, APPLICANT, id, new FakeDocument(id, APPLICANT, null)));
        assertEquals("单据状态已变化，请刷新后重试", updateConflict.getMessage());

        adapter.forcedDeleteRows = 0;
        BusinessException deleteConflict = assertThrows(BusinessException.class,
                () -> lifecycle.removeDraft(adapter, APPLICANT, id));
        assertEquals("单据状态已变化，请刷新后重试", deleteConflict.getMessage());

        adapter.forcedDeleteRows = -1;
        lifecycle.removeDraft(adapter, APPLICANT, id);
        assertFalse(adapter.store.containsKey(id));
    }

    // ------------------------------------------------------------ 提交流程

    @Test
    void submitWritesSnapshotAndSummaryInsideOneTransactionThenStartsApproval() {
        long id = adapter.seed(APPLICANT, AttendanceApplyStatus.DRAFT);

        String projection = lifecycle.submit(adapter, APPLICANT, id, "  key-1  ");

        assertEquals("detail:" + id + ":SUBMITTED", projection);
        assertEquals("snapshot", adapter.store.get(id).payload);
        assertEquals(List.of("load:" + id, "prepare", "tx:begin", "cas:submit", "start", "tx:commit", "bind"),
                events);

        ArgumentCaptor<BusinessApprovalStart> command =
                ArgumentCaptor.forClass(BusinessApprovalStart.class);
        verify(approvalStarter).start(command.capture());
        assertEquals("fake.document", command.getValue().businessType());
        assertEquals(id, command.getValue().businessId());
        assertEquals("key-1", command.getValue().idempotencyKey());
        assertEquals("假单据摘要", command.getValue().summaryText());
        assertEquals("申请人", command.getValue().applicant().getName());
    }

    @Test
    void submitReplaysInFlightSameKeyWithoutTouchingDocument() {
        long id = adapter.seed(APPLICANT, AttendanceApplyStatus.SUBMITTED);
        when(approvalReader.inFlightSubmissionProcessId("fake.document", id, APPLICANT, "key-1"))
                .thenReturn(55L);

        String projection = lifecycle.submit(adapter, APPLICANT, id, "key-1");

        assertEquals("detail:" + id + ":SUBMITTED", projection);
        assertEquals(List.of("load:" + id, "bind"), events);
        verifyNoInteractions(approvalStarter);
    }

    @Test
    void submitRejectsInFlightDocumentWithoutMatchingKey() {
        long id = adapter.seed(APPLICANT, AttendanceApplyStatus.SUBMITTED);

        BusinessException rejected = assertThrows(BusinessException.class,
                () -> lifecycle.submit(adapter, APPLICANT, id, "key-2"));

        assertEquals("单据已在审批中", rejected.getMessage());
        verifyNoInteractions(approvalStarter);
    }

    @Test
    void submitRejectsApprovedDocumentAndBlankKey() {
        long approved = adapter.seed(APPLICANT, AttendanceApplyStatus.APPROVED);

        BusinessException terminal = assertThrows(BusinessException.class,
                () -> lifecycle.submit(adapter, APPLICANT, approved, "key-1"));
        assertEquals("单据已通过，不能重新提交", terminal.getMessage());

        long draft = adapter.seed(APPLICANT, AttendanceApplyStatus.DRAFT);
        BusinessException blank = assertThrows(BusinessException.class,
                () -> lifecycle.submit(adapter, APPLICANT, draft, "   "));
        assertEquals("提交标识不能为空", blank.getMessage());
        assertFalse(events.contains("prepare"));
    }

    @Test
    void submitConflictRollsBackBeforeReReadingOutsideTransaction() {
        long id = adapter.seed(APPLICANT, AttendanceApplyStatus.DRAFT);
        adapter.keepCommittedOnRollback = true;
        when(approvalStarter.start(any())).thenAnswer(invocation -> {
            events.add("start");
            throw new DuplicateKeyException("uk_oa_process_idempotency_key");
        });
        when(approvalReader.inFlightSubmissionProcessId("fake.document", id, APPLICANT, "key-1"))
                .thenAnswer(invocation -> {
                    events.add("read:inflight");
                    return 42L;
                });

        String projection = lifecycle.submit(adapter, APPLICANT, id, "key-1");

        assertEquals("detail:" + id + ":SUBMITTED", projection);
        assertEquals(AttendanceApplyStatus.SUBMITTED, adapter.store.get(id).status,
                "对手的同键提交通常已提交，本事务回滚不影响它");
        int rollback = events.indexOf("tx:rollback");
        int reRead = events.indexOf("read:inflight");
        assertTrue(rollback >= 0, "冲突必须回滚提交事务");
        assertTrue(reRead > rollback, "冲突重读必须在事务回滚之后（事务外）");
    }

    @Test
    void submitConflictWithFinishedOrForeignKeyIsNamedRejection() {
        long id = adapter.seed(APPLICANT, AttendanceApplyStatus.DRAFT);
        when(approvalStarter.start(any())).thenAnswer(invocation -> {
            events.add("start");
            throw new DuplicateKeyException("uk_oa_process_idempotency_key");
        });

        BusinessException rejected = assertThrows(BusinessException.class,
                () -> lifecycle.submit(adapter, APPLICANT, id, "old-key"));

        assertEquals("提交标识已被使用", rejected.getMessage());
        assertEquals(AttendanceApplyStatus.DRAFT, adapter.store.get(id).status,
                "拒绝后单据保持可编辑");
    }

    @Test
    void submitCasZeroInsideTransactionRollsBackWithoutStartingApproval() {
        long id = adapter.seed(APPLICANT, AttendanceApplyStatus.DRAFT);
        adapter.forcedSubmitRows = 0;

        BusinessException conflict = assertThrows(BusinessException.class,
                () -> lifecycle.submit(adapter, APPLICANT, id, "key-1"));

        assertEquals("单据状态已变化，请刷新后重试", conflict.getMessage());
        assertEquals(AttendanceApplyStatus.DRAFT, adapter.store.get(id).status);
        assertTrue(events.contains("tx:rollback"));
        verify(approvalStarter, never()).start(any());
    }

    // ------------------------------------------------------------ 终态仲裁

    @Test
    void terminalSameValueIsNoOpWithoutCasOrSideEffect() {
        long id = adapter.seed(APPLICANT, AttendanceApplyStatus.APPROVED);

        lifecycle.settle(adapter, id, BusinessApprovalOutcome.APPROVED);

        assertEquals(List.of("load:" + id), events);
        assertEquals(AttendanceApplyStatus.APPROVED, adapter.store.get(id).status);
    }

    @Test
    void terminalDifferentValueOnNonSubmittedDocumentIsConflict() {
        long id = adapter.seed(APPLICANT, AttendanceApplyStatus.DRAFT);

        BusinessException conflict = assertThrows(BusinessException.class,
                () -> lifecycle.settle(adapter, id, BusinessApprovalOutcome.APPROVED));

        assertEquals("假单据终态冲突", conflict.getMessage());
        assertEquals(List.of("load:" + id), events);
    }

    @Test
    void terminalCasZeroRereadsAndAcceptsTheWinningSameStatus() {
        long id = adapter.seed(APPLICANT, AttendanceApplyStatus.SUBMITTED);
        adapter.forcedTerminalRows = 0;
        adapter.forceLoad(AttendanceApplyStatus.SUBMITTED);
        adapter.forceLoad(AttendanceApplyStatus.REJECTED);

        lifecycle.settle(adapter, id, BusinessApprovalOutcome.REJECTED);

        assertEquals(List.of("load:" + id, "cas:terminal:REJECTED", "load:" + id), events);
        assertFalse(adapter.settled.contains(id));
    }

    @Test
    void terminalCasZeroRereadWithAnotherStatusIsConflict() {
        long id = adapter.seed(APPLICANT, AttendanceApplyStatus.SUBMITTED);
        adapter.forcedTerminalRows = 0;
        adapter.forceLoad(AttendanceApplyStatus.SUBMITTED);
        adapter.forceLoad(AttendanceApplyStatus.APPROVED);

        BusinessException conflict = assertThrows(BusinessException.class,
                () -> lifecycle.settle(adapter, id, BusinessApprovalOutcome.REJECTED));

        assertEquals("假单据终态冲突", conflict.getMessage());
    }

    @Test
    void approvedTerminalWritesStatusThenDomainSideEffect() {
        long id = adapter.seed(APPLICANT, AttendanceApplyStatus.SUBMITTED);

        lifecycle.settle(adapter, id, BusinessApprovalOutcome.APPROVED);

        assertEquals(AttendanceApplyStatus.APPROVED, adapter.store.get(id).status);
        assertEquals(List.of("load:" + id, "cas:terminal:APPROVED", "settle:approved"), events);
    }

    @Test
    void terminalParamValidationAndMissingDocument() {
        long id = adapter.seed(APPLICANT, AttendanceApplyStatus.SUBMITTED);

        BusinessException incomplete = assertThrows(BusinessException.class,
                () -> lifecycle.settle(adapter, null, BusinessApprovalOutcome.APPROVED));
        assertEquals("假单据终态回写参数不完整", incomplete.getMessage());

        BusinessException noOutcome = assertThrows(BusinessException.class,
                () -> lifecycle.settle(adapter, id, null));
        assertEquals("假单据终态回写参数不完整", noOutcome.getMessage());

        BusinessException missing = assertThrows(BusinessException.class,
                () -> lifecycle.settle(adapter, 999L, BusinessApprovalOutcome.APPROVED));
        assertEquals("假单据不存在", missing.getMessage());
    }

    // ------------------------------------------------------------ 状态表

    @Test
    void statusTableIsTheSingleOwnerOfCodesAndTransitions() {
        assertEquals(0, AttendanceApplyStatus.DRAFT.code());
        assertEquals(1, AttendanceApplyStatus.SUBMITTED.code());
        assertEquals(2, AttendanceApplyStatus.APPROVED.code());
        assertEquals(-1, AttendanceApplyStatus.REJECTED.code());
        assertEquals(-2, AttendanceApplyStatus.WITHDRAWN.code());
        assertEquals(List.of(0, -1, -2), AttendanceApplyStatus.submittableCodes());

        for (AttendanceApplyStatus status : AttendanceApplyStatus.values()) {
            assertEquals(status, AttendanceApplyStatus.fromCode(status.code()));
            assertEquals(status.isSubmittable(), status.isEditable());
            boolean submittable = status == AttendanceApplyStatus.DRAFT
                    || status == AttendanceApplyStatus.REJECTED
                    || status == AttendanceApplyStatus.WITHDRAWN;
            assertEquals(submittable, status.isSubmittable(), status.name());
        }
        assertNull(AttendanceApplyStatus.fromCode(99));
        assertNull(AttendanceApplyStatus.fromCode(null));
    }

    @Test
    void statusFilterAcceptsKnownCodesAndRejectsUnknown() {
        lifecycle.validateStatusFilter(null);

        AttendanceApplyQueryVo query = new AttendanceApplyQueryVo();
        for (AttendanceApplyStatus status : AttendanceApplyStatus.values()) {
            query.setStatus(status.code());
            lifecycle.validateStatusFilter(query);
        }

        query.setStatus(-3);
        BusinessException invalid = assertThrows(BusinessException.class,
                () -> lifecycle.validateStatusFilter(query));
        assertEquals("单据状态不合法", invalid.getMessage());
    }

    private static SysUser user(long id, String name, String username) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setName(name);
        user.setUsername(username);
        return user;
    }

    // ------------------------------------------------------------ 假适配器与记录型事务

    /** 假单据：适配器自己的持久化载荷，module 只经 {@link DocumentSnapshot} 看它。 */
    private static final class FakeDocument {

        private final long id;

        private final long applicantId;

        private AttendanceApplyStatus status;

        private String payload;

        FakeDocument(long id, long applicantId, AttendanceApplyStatus status) {
            this.id = id;
            this.applicantId = applicantId;
            this.status = status;
        }
    }

    private record FakeSnapshot(String value) {
    }

    /**
     * 假适配器：单据存在内存 Map 里，CAS 语义按真实 SQL 的样子实现（0 行 = 条件不再成立）。
     * 测试可用 {@code forced*Rows} 模拟并发输掉 CAS、用 {@code forceLoad} 制造重读序列。
     */
    private final class FakeAdapter implements ApplyDocumentAdapter<FakeDocument, FakeSnapshot, String> {

        private final Map<Long, FakeDocument> store = new LinkedHashMap<>();

        private final Deque<AttendanceApplyStatus> forcedLoads = new ArrayDeque<>();

        private final List<Long> settled = new ArrayList<>();

        private BusinessApprovalSummary boundSummary;

        private long nextId = 1L;

        private int forcedUpdateRows = -1;

        private int forcedDeleteRows = -1;

        private int forcedSubmitRows = -1;

        private int forcedTerminalRows = -1;

        /** true 时模拟「对手的同键提交已提交」：本事务回滚不撤销那一行。 */
        private boolean keepCommittedOnRollback;

        private AttendanceApplyStatus rollbackStatus;

        private String rollbackPayload;

        void reset() {
            store.clear();
            forcedLoads.clear();
            settled.clear();
            boundSummary = null;
            nextId = 1L;
            forcedUpdateRows = -1;
            forcedDeleteRows = -1;
            forcedSubmitRows = -1;
            forcedTerminalRows = -1;
            keepCommittedOnRollback = false;
            rollbackStatus = null;
            rollbackPayload = null;
        }

        long seed(long applicantId, AttendanceApplyStatus status) {
            long id = nextId++;
            store.put(id, new FakeDocument(id, applicantId, status));
            return id;
        }

        void forceLoad(AttendanceApplyStatus status) {
            forcedLoads.add(status);
        }

        void rollback() {
            if (keepCommittedOnRollback || rollbackStatus == null) {
                rollbackStatus = null;
                rollbackPayload = null;
                return;
            }
            FakeDocument document = store.values().stream()
                    .filter(candidate -> candidate.status == AttendanceApplyStatus.SUBMITTED)
                    .findFirst().orElse(null);
            if (document != null) {
                document.status = rollbackStatus;
                document.payload = rollbackPayload;
            }
            rollbackStatus = null;
            rollbackPayload = null;
        }

        @Override
        public String businessType() {
            return "fake.document";
        }

        @Override
        public String label() {
            return "假单据";
        }

        @Override
        public DocumentSnapshot<FakeDocument> load(Long id) {
            events.add("load:" + id);
            if (!forcedLoads.isEmpty()) {
                AttendanceApplyStatus status = forcedLoads.poll();
                FakeDocument placeholder = new FakeDocument(id, APPLICANT, status);
                return new DocumentSnapshot<>(placeholder, id, APPLICANT, status);
            }
            FakeDocument document = store.get(id);
            if (document == null) {
                return null;
            }
            return new DocumentSnapshot<>(
                    document, document.id, document.applicantId, document.status);
        }

        @Override
        public Long insertDraft(FakeDocument draft) {
            events.add("insert");
            return seed(draft.applicantId, AttendanceApplyStatus.DRAFT);
        }

        @Override
        public int casUpdateDraft(Long applicantId, Long id, FakeDocument draft) {
            events.add("cas:update");
            if (forcedUpdateRows >= 0) {
                return forcedUpdateRows;
            }
            FakeDocument document = store.get(id);
            if (document == null || !document.status.isEditable()) {
                return 0;
            }
            document.payload = "改了内容";
            return 1;
        }

        @Override
        public int casDeleteDraft(Long applicantId, Long id) {
            events.add("cas:delete");
            if (forcedDeleteRows >= 0) {
                return forcedDeleteRows;
            }
            FakeDocument document = store.get(id);
            if (document == null || !document.status.isEditable()) {
                return 0;
            }
            store.remove(id);
            return 1;
        }

        @Override
        public SubmitPlan<FakeSnapshot> prepareSubmit(DocumentSnapshot<FakeDocument> document) {
            events.add("prepare");
            return new SubmitPlan<>(new FakeSnapshot("snapshot"), "假单据摘要");
        }

        @Override
        public int casMarkSubmitted(Long applicantId, Long id, FakeSnapshot snapshot, Date submitTime) {
            events.add("cas:submit");
            if (forcedSubmitRows >= 0) {
                return forcedSubmitRows;
            }
            FakeDocument document = store.get(id);
            if (document == null || !document.status.isSubmittable()) {
                return 0;
            }
            rollbackStatus = document.status;
            rollbackPayload = document.payload;
            document.status = AttendanceApplyStatus.SUBMITTED;
            document.payload = snapshot.value();
            return 1;
        }

        @Override
        public int casMarkTerminal(Long id, AttendanceApplyStatus target, Date finishTime) {
            events.add("cas:terminal:" + target);
            if (forcedTerminalRows >= 0) {
                return forcedTerminalRows;
            }
            FakeDocument document = store.get(id);
            if (document == null || document.status != AttendanceApplyStatus.SUBMITTED) {
                return 0;
            }
            document.status = target;
            return 1;
        }

        @Override
        public void settleApproved(Long id) {
            events.add("settle:approved");
            settled.add(id);
        }

        @Override
        public String detail(Long applicantId, Long id) {
            FakeDocument document = store.get(id);
            if (document == null || document.applicantId != applicantId) {
                throw new BusinessException(201, label() + "不存在");
            }
            return "detail:" + id + ":" + document.status;
        }

        @Override
        public void bindApproval(String projection, BusinessApprovalSummary summary) {
            events.add("bind");
            boundSummary = summary;
        }
    }

    /** 记录型事务管理器：事件序列给「冲突重读在事务外」做可见断言。 */
    private final class RecordingTransactionManager implements PlatformTransactionManager {

        private Runnable onRollback = () -> {
        };

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            events.add("tx:begin");
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
            events.add("tx:commit");
        }

        @Override
        public void rollback(TransactionStatus status) {
            events.add("tx:rollback");
            onRollback.run();
        }
    }
}
