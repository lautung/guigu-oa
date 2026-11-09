package com.lautung.oa.process.read;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lautung.oa.framework.page.PageWindow;
import com.lautung.oa.model.process.Process;
import com.lautung.oa.model.process.ProcessAddSignTask;
import com.lautung.oa.model.process.ProcessRecord;
import com.lautung.oa.model.system.DataScope;
import com.lautung.oa.model.system.SysUser;
import com.lautung.oa.process.ProcessApprovalSupport;
import com.lautung.oa.process.ProcessApprovalSupport.AddSignType;
import com.lautung.oa.process.approval.ApprovalEngine;
import com.lautung.oa.process.mapper.OaProcessAddSignTaskMapper;
import com.lautung.oa.process.mapper.OaProcessMapper;
import com.lautung.oa.process.service.OaProcessRecordService;
import com.lautung.oa.vo.process.ProcessQueryVo;
import com.lautung.oa.vo.process.ProcessVo;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * {@link ApprovalListReader} 的唯一实现。待处理是三源合成（#288，ADR 0068 修订 ADR 0045）：
 * 引擎 assignee、引擎候选人（taskCandidateOrAssigned）与加签任务，每人每审批一行；行带
 * actionKind、前加签挂起标记与催办标记，total 按合成行数。已处理按账本动作记录去重供给
 * （#186，ADR 0049）；已发起与配置实例查询共用一条 SQL；抄送我的与已处理走原生 LIMIT/OFFSET
 * （#122 口径）。窗口算术一律从 {@link PageWindow} 派生，不手算。
 */
@Service
public class LedgerApprovalListReader implements ApprovalListReader {

    /** 引擎任务源在合并排序里的位次（同刻时引擎行在前、加签行在后，保证翻页稳定）。 */
    private static final int SOURCE_RANK_ENGINE = 0;
    private static final int SOURCE_RANK_SIGN = 1;

    private final ApprovalEngine approvalEngine;
    private final OaProcessMapper processMapper;
    private final OaProcessAddSignTaskMapper addSignTaskMapper;
    private final OaProcessRecordService processRecordService;

    public LedgerApprovalListReader(
            ApprovalEngine approvalEngine,
            OaProcessMapper processMapper,
            OaProcessAddSignTaskMapper addSignTaskMapper,
            OaProcessRecordService processRecordService) {
        this.approvalEngine = approvalEngine;
        this.processMapper = processMapper;
        this.addSignTaskMapper = addSignTaskMapper;
        this.processRecordService = processRecordService;
    }

    @Override
    public IPage<ProcessVo> pending(Page<ProcessVo> page, SysUser user) {
        // 三源合成（#288）：两个源各自从第一页取到窗口底（firstResult+size）。全局窗口内的行
        // 必落在各自行源的前 firstResult+size 名（窗口内任何元素在自己的源里前面至多
        // firstResult+size-1 个），跨源按（创建时间倒序、源位次、源内 id）合并后切窗口，
        // 翻页不重不漏。单人待处理量超过几千行不是现实负载，深页的整段重取可接受。
        long firstResult = PageWindow.firstResult(page);
        int windowBottom = (int) Math.min(firstResult + page.getSize(), Integer.MAX_VALUE);

        List<ApprovalEngine.PendingTask> engineTasks = windowBottom > 0
                ? approvalEngine.pendingTasks(user.getUsername(), 0, windowBottom)
                : List.of();
        long engineTotal = approvalEngine.pendingCount(user.getUsername());
        List<ProcessAddSignTask> signTasks = windowBottom > 0
                ? addSignTaskMapper.selectPendingForUser(user.getId(), 0, windowBottom)
                : List.of();
        long signTotal = addSignTaskMapper.countPendingForUser(user.getId());

        // total 收口例外（#288）：后加签批次进行中，原处理人已记账通过，其引擎行既不进待处理行
        // 也不进 total。活跃 AFTER 批次全局是少量行，仅在其存在时回查本人全部待处理任务求交。
        long afterExcluded = 0;
        List<ProcessAddSignTask> afterBatches = addSignTaskMapper.selectPendingByType(AddSignType.AFTER.name());
        if (!afterBatches.isEmpty() && engineTotal > 0) {
            Set<String> afterBatched = afterBatches.stream()
                    .map(ProcessAddSignTask::getEngineTaskId)
                    .collect(Collectors.toSet());
            afterExcluded = approvalEngine.pendingTasks(user.getUsername(), 0, (int) engineTotal).stream()
                    .map(ApprovalEngine.PendingTask::taskId)
                    .filter(afterBatched::contains)
                    .count();
        }

        // 挂起与收口判定（一次批查）：引擎任务上的未决批次——前加签挂起行保留并带标记，
        // 后加签的原处理人已记账通过，行不再进待处理。
        Map<String, String> batchTypeByEngineTask = new HashMap<>();
        List<String> engineTaskIds = engineTasks.stream()
                .map(ApprovalEngine.PendingTask::taskId)
                .toList();
        if (!engineTaskIds.isEmpty()) {
            for (ProcessAddSignTask batch : addSignTaskMapper.selectPendingByEngineTaskIds(engineTaskIds)) {
                batchTypeByEngineTask.putIfAbsent(batch.getEngineTaskId(), batch.getSignType());
            }
        }

        // 行本体一次回查（引擎源按 businessKey、加签源按 process_id），跳过已删/缺失的审批行；
        // businessKey 非数字维持 NumberFormatException（与旧行为一致：建批查集合与重排共用同一份解析）。
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
        signTasks.forEach(signTask -> processIds.add(signTask.getProcessId()));
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
        for (ProcessAddSignTask signTask : signTasks) {
            ProcessVo row = rowsById.get(signTask.getProcessId());
            if (row == null) {
                continue;
            }
            row.setTaskId(String.valueOf(signTask.getId()));
            row.setActionKind(ProcessApprovalSupport.ACTION_KIND_ADD_SIGN);
            row.setSuspended(false);
            merged.add(new SynthesizedRow(signTask.getCreateTime(), SOURCE_RANK_SIGN,
                    String.valueOf(signTask.getId()), row));
        }
        merged.sort(SynthesizedRow.ORDER);

        List<ProcessVo> records = new ArrayList<>();
        for (int i = (int) firstResult; i < merged.size() && records.size() < page.getSize(); i++) {
            records.add(merged.get(i).row());
        }
        markRecentUrges(records);
        return pageOf(page, engineTotal + signTotal - afterExcluded, records);
    }

    @Override
    public ProcessVo pendingRow(SysUser user, Process process) {
        if (user == null || user.getId() == null || process == null || process.getId() == null) {
            return null;
        }
        String username = user.getUsername();
        if (StringUtils.hasText(username) && StringUtils.hasText(process.getProcessInstanceId())) {
            for (ApprovalEngine.CurrentTask task : approvalEngine.currentTasks(process.getProcessInstanceId())) {
                if (task == null || !StringUtils.hasText(task.taskId())) {
                    continue;
                }
                boolean mine = username.equals(task.assignee()) || task.candidates().contains(username);
                if (!mine) {
                    continue;
                }
                List<ProcessAddSignTask> batches =
                        addSignTaskMapper.selectPendingByEngineTaskIds(List.of(task.taskId()));
                if (batches.stream().anyMatch(batch -> AddSignType.AFTER.name().equals(batch.getSignType()))) {
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
                        .anyMatch(batch -> AddSignType.BEFORE.name().equals(batch.getSignType())));
                row.setUrged(recentUrgeProcessIds(Set.of(process.getId())).contains(process.getId()));
                return row;
            }
        }
        // 加签任务源：本人对该审批的未决加签任务（同一审批至多一个未决批次）。
        ProcessAddSignTask signTask = addSignTaskMapper.selectList(
                new LambdaQueryWrapper<ProcessAddSignTask>()
                        .eq(ProcessAddSignTask::getProcessId, process.getId())
                        .eq(ProcessAddSignTask::getAssigneeId, user.getId())
                        .eq(ProcessAddSignTask::getStatus,
                                ProcessApprovalSupport.AddSignStatus.PENDING.name())
                        .orderByDesc(ProcessAddSignTask::getId)
                        .last("LIMIT 1")).stream().findFirst().orElse(null);
        if (signTask == null) {
            return null;
        }
        ProcessVo row = rowsById(List.of(process.getId())).get(process.getId());
        if (row == null) {
            return null;
        }
        row.setTaskId(String.valueOf(signTask.getId()));
        row.setActionKind(ProcessApprovalSupport.ACTION_KIND_ADD_SIGN);
        row.setSuspended(false);
        row.setUrged(recentUrgeProcessIds(Set.of(process.getId())).contains(process.getId()));
        return row;
    }

    @Override
    public IPage<ProcessVo> processed(Page<ProcessVo> page, SysUser user) {
        // 行=审批（GLOSSARY「已处理」）：该用户的处理动作记录按审批去重（同人两节点只出一行，#186）；
        // 谓词是动作类型白名单（#287，ADR 0049 修订）：只有 APPROVE/REJECT 算处理，负匹配退役。
        // 最近一次处理时间倒序、同刻由审批 id 决胜，total=去重审批数。
        long total = processMapper.countProcessedRows(user.getId());
        List<ProcessVo> records = processMapper.selectProcessedRows(
                user.getId(), PageWindow.offset(page), page.getSize());
        return pageOf(page, total, records);
    }

    @Override
    public IPage<ProcessVo> started(Page<ProcessVo> page, SysUser user) {
        ProcessQueryVo query = new ProcessQueryVo();
        query.setUserId(user.getId());
        // 办事列表不套数据范围：成员资格是更强的可见性真相（ADR 0053/0057）。
        return processMapper.selectProcessPage(page, query, null);
    }

    @Override
    public IPage<ProcessVo> carbonCopied(Page<ProcessVo> page, SysUser user) {
        long offset = PageWindow.offset(page);
        long total = processMapper.countCarbonCopiedRows(user.getId());
        List<ProcessVo> records =
                processMapper.selectCarbonCopiedRows(user.getId(), offset, page.getSize());
        return pageOf(page, total, records);
    }

    @Override
    public IPage<ProcessVo> instanceQuery(Page<ProcessVo> page, ProcessQueryVo query, DataScope scope) {
        return processMapper.selectProcessPage(page, query, scope);
    }

    /**
     * 催办标记（#288，process 级判定）：最近 24 小时内该审批存在 URGE 账本行，当页全部
     * 待处理行（含挂起行与加签行）带上标记；URGE target 侧是姓名清单，与标记无关。
     */
    private void markRecentUrges(List<ProcessVo> records) {
        if (records.isEmpty()) {
            return;
        }
        Set<Long> processIds = records.stream()
                .map(ProcessVo::getId)
                .filter(java.util.Objects::nonNull)
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
        List<Long> distinct = ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
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
