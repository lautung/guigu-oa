package com.lautung.oa.process.pending;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lautung.oa.auth.service.SysUserService;
import com.lautung.oa.framework.page.PageWindow;
import com.lautung.oa.model.process.Process;
import com.lautung.oa.model.process.ProcessRecord;
import com.lautung.oa.model.system.SysUser;
import com.lautung.oa.process.ProcessApprovalSupport;
import com.lautung.oa.process.ProcessApprovalSupport.AddSignType;
import com.lautung.oa.process.approval.ApprovalEngine;
import com.lautung.oa.process.mapper.OaProcessMapper;
import com.lautung.oa.process.service.OaProcessRecordService;
import com.lautung.oa.vo.process.ProcessVo;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 待处理 = 引擎 assignee + 引擎候选人 + 未决加签任务（#297，ADR 0076）。本 module 拥有
 * 三源取数、并集、AFTER 批次排除、BEFORE 挂起标记、total 口径、合并排序与窗口、
 * pendingRow 的选择规则，以及待处理名单的姓名投影。
 */
@Service
public class PendingWorklist {

    /** 引擎任务源在合并排序里的位次（同刻时引擎行在前、加签行在后，保证翻页稳定）。 */
    private static final int SOURCE_RANK_ENGINE = 0;
    private static final int SOURCE_RANK_SIGN = 1;

    private final ApprovalEngine approvalEngine;
    private final PendingSignTaskEvidence signTaskEvidence;
    private final OaProcessMapper processMapper;
    private final OaProcessRecordService processRecordService;
    private final SysUserService sysUserService;

    public PendingWorklist(
            ApprovalEngine approvalEngine,
            PendingSignTaskEvidence signTaskEvidence,
            OaProcessMapper processMapper,
            OaProcessRecordService processRecordService,
            SysUserService sysUserService) {
        this.approvalEngine = approvalEngine;
        this.signTaskEvidence = signTaskEvidence;
        this.processMapper = processMapper;
        this.processRecordService = processRecordService;
        this.sysUserService = sysUserService;
    }

    /**
     * 待处理页（#297，ADR 0076，口径原样承接 #288）：引擎 assignee/候选人与 PENDING 加签任务
     * 三源合成，每人每审批一行。两个源各自从第一页取到窗口底（firstResult+size），跨源按
     * （创建时间倒序、源位次、源内 id）合并后切窗口；total = 引擎任务数 + 加签任务数 −
     * AFTER 批次排除数。
     */
    public IPage<ProcessVo> pending(Page<ProcessVo> page, SysUser user) {
        long firstResult = PageWindow.firstResult(page);
        int windowBottom = (int) Math.min(firstResult + page.getSize(), Integer.MAX_VALUE);

        List<ApprovalEngine.PendingTask> engineTasks = windowBottom > 0
                ? approvalEngine.pendingTasks(user.getUsername(), 0, windowBottom)
                : List.of();
        long engineTotal = approvalEngine.pendingCount(user.getUsername());
        List<PendingSignTaskEvidence.SignTaskSnapshot> signTasks = windowBottom > 0
                ? signTaskEvidence.pendingForUser(user.getId(), 0, windowBottom)
                : List.of();
        long signTotal = signTaskEvidence.countPendingForUser(user.getId());

        // total 收口例外：后加签批次进行中，原处理人已记账通过，其引擎行既不进待处理行也不进 total。
        long afterExcluded = 0;
        List<PendingSignTaskEvidence.SignTaskSnapshot> afterBatches =
                signTaskEvidence.pendingByType(AddSignType.AFTER.name());
        if (!afterBatches.isEmpty() && engineTotal > 0) {
            Set<String> afterBatched = afterBatches.stream()
                    .map(PendingSignTaskEvidence.SignTaskSnapshot::engineTaskId)
                    .collect(Collectors.toSet());
            afterExcluded = approvalEngine.pendingTasks(user.getUsername(), 0, (int) engineTotal).stream()
                    .map(ApprovalEngine.PendingTask::taskId)
                    .filter(afterBatched::contains)
                    .count();
        }

        // 挂起与收口判定（一次批查）：BEFORE 行保留并带标记，AFTER 原处理人不再进待处理。
        Map<String, String> batchTypeByEngineTask = new HashMap<>();
        List<String> engineTaskIds = engineTasks.stream()
                .map(ApprovalEngine.PendingTask::taskId)
                .toList();
        if (!engineTaskIds.isEmpty()) {
            for (PendingSignTaskEvidence.SignTaskSnapshot batch
                    : signTaskEvidence.pendingByEngineTaskIds(engineTaskIds)) {
                batchTypeByEngineTask.putIfAbsent(batch.engineTaskId(), batch.signType());
            }
        }

        // 行本体一次回查（引擎源按 businessKey、加签源按 process_id），跳过已删/缺失的审批行；
        // businessKey 非数字维持 NumberFormatException（与旧行为一致）。
        List<Long> processIds = new ArrayList<>();
        for (ApprovalEngine.PendingTask task : engineTasks) {
            if (!StringUtils.hasText(task.businessKey())) {
                continue;
            }
            if (AddSignType.AFTER.name().equals(batchTypeByEngineTask.get(task.taskId()))) {
                continue;
            }
            processIds.add(Long.parseLong(task.businessKey()));
        }
        signTasks.forEach(signTask -> processIds.add(signTask.processId()));
        Map<Long, ProcessVo> rowsById = rowsById(processIds);

        List<SynthesizedRow> merged = new ArrayList<>();
        for (ApprovalEngine.PendingTask task : engineTasks) {
            if (!StringUtils.hasText(task.businessKey())) {
                continue;
            }
            String batchType = batchTypeByEngineTask.get(task.taskId());
            if (AddSignType.AFTER.name().equals(batchType)) {
                continue;
            }
            ProcessVo row = rowsById.get(Long.parseLong(task.businessKey()));
            if (row == null) {
                continue;
            }
            boolean assignee = user.getUsername() != null && user.getUsername().equals(task.assignee());
            row.setTaskId(task.taskId());
            row.setActionKind(assignee
                    ? ProcessApprovalSupport.ACTION_KIND_APPROVE
                    : ProcessApprovalSupport.ACTION_KIND_CANDIDATE);
            row.setSuspended(AddSignType.BEFORE.name().equals(batchType));
            merged.add(new SynthesizedRow(task.createTime(), SOURCE_RANK_ENGINE, task.taskId(), row));
        }
        for (PendingSignTaskEvidence.SignTaskSnapshot signTask : signTasks) {
            ProcessVo row = rowsById.get(signTask.processId());
            if (row == null) {
                continue;
            }
            row.setTaskId(String.valueOf(signTask.id()));
            row.setActionKind(ProcessApprovalSupport.ACTION_KIND_ADD_SIGN);
            row.setSuspended(false);
            merged.add(new SynthesizedRow(signTask.createTime(), SOURCE_RANK_SIGN,
                    String.valueOf(signTask.id()), row));
        }
        merged.sort(SynthesizedRow.ORDER);

        List<ProcessVo> records = new ArrayList<>();
        for (int i = (int) firstResult; i < merged.size() && records.size() < page.getSize(); i++) {
            records.add(merged.get(i).row());
        }
        markRecentUrges(records);
        return pageOf(page, engineTotal + signTotal - afterExcluded, records);
    }

    /**
     * 单条待处理行（#297，ADR 0076，规则原样冻结）：引擎源优先；本人引擎任务命中但处于
     * AFTER 批次时直接返回 {@code null}，不落到加签源；否则取本人最新一条 PENDING 加签任务
     * （id 倒序）。行上带 actionKind/挂起标记/催办标记。
     */
    public ProcessVo pendingRow(SysUser user, Process process) {
        if (user == null || user.getId() == null || process == null || process.getId() == null) {
            return null;
        }
        String username = user.getUsername();
        if (StringUtils.hasText(username) && StringUtils.hasText(process.getProcessInstanceId())) {
            for (ApprovalEngine.CurrentTask task
                    : approvalEngine.currentTasks(process.getProcessInstanceId())) {
                if (task == null || !StringUtils.hasText(task.taskId())) {
                    continue;
                }
                boolean mine = username.equals(task.assignee()) || task.candidates().contains(username);
                if (!mine) {
                    continue;
                }
                List<PendingSignTaskEvidence.SignTaskSnapshot> batches =
                        signTaskEvidence.pendingByEngineTaskIds(List.of(task.taskId()));
                if (batches.stream()
                        .anyMatch(batch -> AddSignType.AFTER.name().equals(batch.signType()))) {
                    // 后加签批次进行中：原处理人已记账通过，不再有可动作的任务面。
                    return null;
                }
                ProcessVo row = rowsById(List.of(process.getId())).get(process.getId());
                if (row == null) {
                    return null;
                }
                row.setTaskId(task.taskId());
                row.setActionKind(username.equals(task.assignee())
                        ? ProcessApprovalSupport.ACTION_KIND_APPROVE
                        : ProcessApprovalSupport.ACTION_KIND_CANDIDATE);
                row.setSuspended(batches.stream()
                        .anyMatch(batch -> AddSignType.BEFORE.name().equals(batch.signType())));
                row.setUrged(recentUrgeProcessIds(Set.of(process.getId())).contains(process.getId()));
                return row;
            }
        }
        // 加签任务源：本人对该审批的未决加签任务（同一审批至多一个未决批次，取最新 id）。
        PendingSignTaskEvidence.SignTaskSnapshot signTask = signTaskEvidence.pendingForProcess(process.getId())
                .stream()
                .filter(task -> task != null && task.id() != null)
                .filter(task -> user.getId().equals(task.assigneeId()))
                .max(Comparator.comparing(PendingSignTaskEvidence.SignTaskSnapshot::id))
                .orElse(null);
        if (signTask == null) {
            return null;
        }
        ProcessVo row = rowsById(List.of(process.getId())).get(process.getId());
        if (row == null) {
            return null;
        }
        row.setTaskId(String.valueOf(signTask.id()));
        row.setActionKind(ProcessApprovalSupport.ACTION_KIND_ADD_SIGN);
        row.setSuspended(false);
        row.setUrged(recentUrgeProcessIds(Set.of(process.getId())).contains(process.getId()));
        return row;
    }

    /**
     * 处理人事实（#297）：引擎当前任务的 assignee/候选人与 PENDING 加签任务，按引擎任务序
     * （每任务 assignee 先、候选人后）+ 加签任务 id 排列。不解析姓名、不查用户。
     */
    public List<HandlerEntry> handlers(Process process) {
        if (process == null || process.getId() == null) {
            return List.of();
        }
        List<ApprovalEngine.CurrentTask> currentTasks = StringUtils.hasText(process.getProcessInstanceId())
                ? approvalEngine.currentTasks(process.getProcessInstanceId())
                : List.of();
        List<ApprovalEngine.CurrentTask> validTasks = currentTasks == null
                ? List.of()
                : currentTasks.stream()
                        .filter(task -> task != null && StringUtils.hasText(task.taskId()))
                        .toList();

        Map<String, Set<String>> batchTypesByEngineTask = new HashMap<>();
        if (!validTasks.isEmpty()) {
            List<String> engineTaskIds = validTasks.stream()
                    .map(ApprovalEngine.CurrentTask::taskId)
                    .distinct()
                    .toList();
            for (PendingSignTaskEvidence.SignTaskSnapshot batch
                    : signTaskEvidence.pendingByEngineTaskIds(engineTaskIds)) {
                if (batch == null || !StringUtils.hasText(batch.engineTaskId())) {
                    continue;
                }
                batchTypesByEngineTask
                        .computeIfAbsent(batch.engineTaskId(), key -> new HashSet<>())
                        .add(batch.signType());
            }
        }

        List<HandlerEntry> handlers = new ArrayList<>();
        for (ApprovalEngine.CurrentTask task : validTasks) {
            Set<String> batchTypes = batchTypesByEngineTask.getOrDefault(task.taskId(), Set.of());
            boolean suspended = batchTypes.contains(AddSignType.BEFORE.name());
            boolean afterExcluded = batchTypes.contains(AddSignType.AFTER.name());
            boolean hasAssignee = StringUtils.hasText(task.assignee());
            if (hasAssignee) {
                handlers.add(new HandlerEntry(task.assignee(), null, HandlerEntry.Source.ENGINE_ASSIGNEE,
                        task.taskId(), null, suspended, afterExcluded, null));
            }
            for (String candidate : task.candidates()) {
                if (!StringUtils.hasText(candidate)
                        || (hasAssignee && task.assignee().equals(candidate))) {
                    continue;
                }
                handlers.add(new HandlerEntry(candidate, null, HandlerEntry.Source.ENGINE_CANDIDATE,
                        task.taskId(), null, suspended, afterExcluded, null));
            }
        }

        List<PendingSignTaskEvidence.SignTaskSnapshot> signTasks =
                new ArrayList<>(signTaskEvidence.pendingForProcess(process.getId()));
        signTasks.removeIf(task -> task == null);
        signTasks.sort(Comparator.comparing(
                PendingSignTaskEvidence.SignTaskSnapshot::id,
                Comparator.nullsLast(Comparator.naturalOrder())));
        for (PendingSignTaskEvidence.SignTaskSnapshot signTask : signTasks) {
            handlers.add(new HandlerEntry(
                    null,
                    signTask.assigneeId(),
                    HandlerEntry.Source.ADD_SIGN,
                    signTask.engineTaskId(),
                    signTask.id() == null ? null : String.valueOf(signTask.id()),
                    false,
                    false,
                    signTask.createTime()));
        }
        return handlers;
    }

    /**
     * 待处理名单的显示名（#297，ADR 0076）：{@link #handlers(Process)} 去掉 AFTER 批次排除的
     * 引擎条目，按 username 去重后的顺序。引擎条目逐 username 查 SysUserService，找不到用户或
     * 空名退回 username；加签人按 userId 一次批查用户名，查无用户或空 username 的条目跳过。
     */
    public List<String> pendingHandlerNames(Process process) {
        List<HandlerEntry> handlers = handlers(process);
        if (handlers.isEmpty()) {
            return List.of();
        }
        List<Long> signUserIds = handlers.stream()
                .filter(handler -> handler.source() == HandlerEntry.Source.ADD_SIGN)
                .map(HandlerEntry::userId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, SysUser> signUsersById = new HashMap<>();
        if (!signUserIds.isEmpty()) {
            List<SysUser> signUsers = sysUserService.listByIds(signUserIds);
            if (signUsers != null) {
                for (SysUser signUser : signUsers) {
                    if (signUser != null && signUser.getId() != null) {
                        signUsersById.putIfAbsent(signUser.getId(), signUser);
                    }
                }
            }
        }

        Map<String, String> displayNameByUsername = new HashMap<>();
        Set<String> seenUsernames = new HashSet<>();
        List<String> names = new ArrayList<>();
        for (HandlerEntry handler : handlers) {
            if (handler.afterExcluded()) {
                continue;
            }
            String username;
            String displayName;
            if (handler.source() == HandlerEntry.Source.ADD_SIGN) {
                SysUser signUser = handler.userId() == null ? null : signUsersById.get(handler.userId());
                if (signUser == null || !StringUtils.hasText(signUser.getUsername())) {
                    continue;
                }
                username = signUser.getUsername();
                displayName = StringUtils.hasText(signUser.getName())
                        ? signUser.getName()
                        : username;
            } else {
                username = handler.username();
                if (!StringUtils.hasText(username)) {
                    continue;
                }
                displayName = displayNameByUsername.computeIfAbsent(username, this::engineDisplayName);
            }
            if (seenUsernames.add(username)) {
                names.add(displayName);
            }
        }
        return names;
    }

    /** 引擎条目的用户名 → 显示名（找不到用户或空名退回 username）。 */
    private String engineDisplayName(String username) {
        SysUser user = sysUserService.getUserByUserName(username);
        return user != null && StringUtils.hasText(user.getName()) ? user.getName() : username;
    }

    /**
     * 催办标记（process 级判定）：最近 24 小时内该审批存在 URGE 账本行，当页全部待处理行
     * （含挂起行与加签行）带上标记；URGE target 侧是姓名清单，与标记无关。
     */
    private void markRecentUrges(List<ProcessVo> records) {
        if (records.isEmpty()) {
            return;
        }
        Set<Long> processIds = records.stream()
                .map(ProcessVo::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> urged = recentUrgeProcessIds(processIds);
        for (ProcessVo row : records) {
            row.setUrged(urged.contains(row.getId()));
        }
    }

    private Set<Long> recentUrgeProcessIds(Set<Long> processIds) {
        if (processIds.isEmpty()) {
            return Set.of();
        }
        Date since = Date.from(Instant.now().minus(ProcessApprovalSupport.URGE_MARK_WINDOW));
        return processRecordService.list(new LambdaQueryWrapper<ProcessRecord>()
                        .in(ProcessRecord::getProcessId, processIds)
                        .eq(ProcessRecord::getActionType,
                                ProcessApprovalSupport.ApprovalActionType.URGE.name())
                        .gt(ProcessRecord::getCreateTime, since))
                .stream()
                .map(ProcessRecord::getProcessId)
                .collect(Collectors.toSet());
    }

    private Map<Long, ProcessVo> rowsById(List<Long> ids) {
        List<Long> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return processMapper.selectRowsByIds(distinct).stream()
                .collect(Collectors.toMap(ProcessVo::getId, Function.identity(), (first, second) -> first));
    }

    private IPage<ProcessVo> pageOf(Page<ProcessVo> page, long total, List<ProcessVo> records) {
        IPage<ProcessVo> result = new Page<>(page.getCurrent(), page.getSize(), total);
        result.setRecords(records);
        return result;
    }

    /** 合并排序的行：创建时间倒序、引擎源在前、源内 id 倒序（只求确定，不另设语义）。 */
    private record SynthesizedRow(Date sortTime, int sourceRank, String nativeId, ProcessVo row) {

        private static final Comparator<SynthesizedRow> ORDER =
                Comparator.comparing(SynthesizedRow::sortTime,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(SynthesizedRow::sourceRank)
                        .thenComparing(SynthesizedRow::nativeId, Comparator.reverseOrder());
    }
}
