package com.lautung.oa.process.pending;

import java.util.Date;
import java.util.List;

/**
 * 未决加签任务证据 port（#297，ADR 0076）：待处理的第三源。生产实现走
 * {@code OaProcessAddSignTaskMapper}；{@link PendingWorklist} 只认这个形状，不直接依赖 mapper。
 */
public interface PendingSignTaskEvidence {

    /** 该用户的未决加签任务，按创建时间倒序、同刻由 id 决胜，窗口底由调用方给。 */
    List<SignTaskSnapshot> pendingForUser(Long assigneeId, long offset, long limit);

    /** 该用户的未决加签任务数（total 的第三源口径）。 */
    long countPendingForUser(Long assigneeId);

    /** 该审批的全部未决加签任务（加签人事实与 pendingRow 的加签源）。 */
    List<SignTaskSnapshot> pendingForProcess(Long processId);

    /** 这些引擎任务上的未决加签任务（挂起/后加签排除的批次判定）。 */
    List<SignTaskSnapshot> pendingByEngineTaskIds(List<String> engineTaskIds);

    /** 指定类型的全部未决批次（后加签排除的 total 收口）。 */
    List<SignTaskSnapshot> pendingByType(String signType);

    /** 加签任务表的只读快照；时间字段是 oa_process_add_sign_task.create_time。 */
    record SignTaskSnapshot(
            Long id,
            Long processId,
            String engineTaskId,
            String signType,
            Long assigneeId,
            Date createTime) {
    }
}
