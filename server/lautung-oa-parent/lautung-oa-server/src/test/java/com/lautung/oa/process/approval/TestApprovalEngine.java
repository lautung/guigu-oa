package com.lautung.oa.process.approval;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test adapter for the approval engine port: delegates to the real Flowable adapter, and can
 * assert write order at the engine boundary, fail the next command, or barrier concurrent completes.
 */
public class TestApprovalEngine implements ApprovalEngine {

    private final FlowableApprovalEngine delegate;
    private final JdbcTemplate jdbcTemplate;

    private boolean failNextStart;
    private boolean failNextComplete;
    private boolean checkStartOrder;
    private int expectedAttachmentCount = -1;
    private boolean checkDecisionRecordBeforeCurrentTasks;
    private boolean skipNextCurrentTasksCheck;
    private Long expectedRecordlessProcessId;

    private CountDownLatch bothTransactionsAtComplete;
    private final AtomicInteger activeTransactionsAtComplete = new AtomicInteger();

    public TestApprovalEngine(FlowableApprovalEngine delegate, JdbcTemplate jdbcTemplate) {
        this.delegate = delegate;
        this.jdbcTemplate = jdbcTemplate;
    }

    public void reset() {
        failNextStart = false;
        failNextComplete = false;
        checkStartOrder = false;
        expectedAttachmentCount = -1;
        checkDecisionRecordBeforeCurrentTasks = false;
        skipNextCurrentTasksCheck = false;
        expectedRecordlessProcessId = null;
        bothTransactionsAtComplete = null;
        activeTransactionsAtComplete.set(0);
    }

    public void failNextStart() {
        failNextStart = true;
    }

    public void failNextComplete() {
        failNextComplete = true;
    }

    public void expectLedgerAndAttachmentsBeforeStart(int attachmentCount) {
        checkStartOrder = true;
        expectedAttachmentCount = attachmentCount;
    }

    public void expectDecisionRecordBeforeCurrentTasks() {
        checkDecisionRecordBeforeCurrentTasks = true;
        // Membership resolves first through the same engine seam; the ordered read is the next-task read.
        skipNextCurrentTasksCheck = true;
    }

    public void expectNoDecisionRecordBeforeComplete(Long processId) {
        expectedRecordlessProcessId = processId;
    }

    public void armCompleteBarrier() {
        bothTransactionsAtComplete = new CountDownLatch(2);
        activeTransactionsAtComplete.set(0);
    }

    public boolean awaitCompleteBarrier() throws InterruptedException {
        CountDownLatch barrier = bothTransactionsAtComplete;
        return barrier != null && barrier.await(10, TimeUnit.SECONDS);
    }

    public int activeTransactionsAtComplete() {
        return activeTransactionsAtComplete.get();
    }

    @Override
    public String start(String processDefinitionId, String businessKey, Map<String, Object> variables) {
        if (failNextStart) {
            failNextStart = false;
            throw new IllegalStateException("Approval engine start failed for test");
        }
        if (checkStartOrder) {
            Long processCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM oa_process WHERE id = ?", Long.class, Long.valueOf(businessKey));
            if (processCount == null || processCount != 1L) {
                throw new AssertionError("engine start 前账本尚未写入");
            }
            if (expectedAttachmentCount >= 0) {
                Long attachedCount = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM oa_attachment WHERE process_id = ? AND status = 'ATTACHED'",
                        Long.class,
                        Long.valueOf(businessKey));
                if (attachedCount == null || attachedCount != expectedAttachmentCount) {
                    throw new AssertionError("engine start 前附件尚未全部绑定");
                }
            }
            Long recordCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM oa_process_record WHERE process_id = ?",
                    Long.class,
                    Long.valueOf(businessKey));
            if (recordCount == null || recordCount != 0L) {
                throw new AssertionError("engine start 前不应写入流转记录");
            }
        }
        return delegate.start(processDefinitionId, businessKey, variables);
    }

    @Override
    public void claim(String taskId, String username) {
        delegate.claim(taskId, username);
    }

    @Override
    public void complete(String taskId) {
        if (failNextComplete) {
            failNextComplete = false;
            throw new IllegalStateException("Approval engine complete failed for test");
        }
        if (expectedRecordlessProcessId != null) {
            Long decisionCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM oa_process_record WHERE process_id = ? "
                            + "AND description IN ('已通过', '已拒绝')",
                    Long.class,
                    expectedRecordlessProcessId);
            if (decisionCount == null || decisionCount != 0L) {
                throw new AssertionError("engine complete 前不应写入本次处理记录");
            }
        }
        CountDownLatch barrier = bothTransactionsAtComplete;
        if (barrier != null) {
            if (!TransactionSynchronizationManager.isActualTransactionActive()) {
                throw new IllegalStateException("Flowable complete 必须在审批业务事务中执行");
            }
            activeTransactionsAtComplete.incrementAndGet();
            barrier.countDown();
            try {
                if (!barrier.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("两个并发审批未同时进入 Flowable complete");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("等待并发审批同步点时被中断", interrupted);
            }
        }
        delegate.complete(taskId);
    }

    @Override
    public void rejectToEnd(String taskId) {
        delegate.rejectToEnd(taskId);
    }

    @Override
    public void cancel(String processInstanceId, String reason) {
        delegate.cancel(processInstanceId, reason);
    }

    @Override
    public List<CurrentTask> currentTasks(String processInstanceId) {
        if (checkDecisionRecordBeforeCurrentTasks) {
            if (skipNextCurrentTasksCheck) {
                skipNextCurrentTasksCheck = false;
            } else {
                Long decisionCount = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM oa_process_record r "
                                + "JOIN oa_process p ON p.id = r.process_id "
                                + "WHERE p.process_instance_id = ? AND r.description IN ('已通过', '已拒绝')",
                        Long.class,
                        processInstanceId);
                if (decisionCount == null || decisionCount == 0) {
                    throw new AssertionError("读取下一任务前本次处理记录尚未写入");
                }
            }
        }
        return delegate.currentTasks(processInstanceId);
    }

    @Override
    public List<PendingTask> pendingTasks(String username, int firstResult, int maxResults) {
        return delegate.pendingTasks(username, firstResult, maxResults);
    }

    @Override
    public long pendingCount(String username) {
        return delegate.pendingCount(username);
    }

    @Override
    public void reassign(String taskId, String fromUsername, String toUsername) {
        delegate.reassign(taskId, fromUsername, toUsername);
    }
}
