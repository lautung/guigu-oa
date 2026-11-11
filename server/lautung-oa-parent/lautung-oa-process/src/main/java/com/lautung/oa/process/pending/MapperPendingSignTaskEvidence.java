package com.lautung.oa.process.pending;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lautung.oa.model.process.ProcessAddSignTask;
import com.lautung.oa.process.ProcessApprovalSupport.AddSignStatus;
import com.lautung.oa.process.mapper.OaProcessAddSignTaskMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * {@link PendingSignTaskEvidence} 的加签任务表实现（#297，ADR 0076）：mapper 只出现在本
 * adapter 内，{@link PendingWorklist} 不依赖 {@link OaProcessAddSignTaskMapper}。
 */
@Service
public class MapperPendingSignTaskEvidence implements PendingSignTaskEvidence {

    private final OaProcessAddSignTaskMapper addSignTaskMapper;

    public MapperPendingSignTaskEvidence(OaProcessAddSignTaskMapper addSignTaskMapper) {
        this.addSignTaskMapper = addSignTaskMapper;
    }

    @Override
    public List<SignTaskSnapshot> pendingForUser(Long assigneeId, long offset, long limit) {
        if (assigneeId == null || limit <= 0) {
            return List.of();
        }
        return addSignTaskMapper.selectPendingForUser(assigneeId, offset, limit).stream()
                .map(MapperPendingSignTaskEvidence::snapshot)
                .toList();
    }

    @Override
    public long countPendingForUser(Long assigneeId) {
        if (assigneeId == null) {
            return 0;
        }
        return addSignTaskMapper.countPendingForUser(assigneeId);
    }

    @Override
    public List<SignTaskSnapshot> pendingForProcess(Long processId) {
        if (processId == null) {
            return List.of();
        }
        return addSignTaskMapper.selectList(new LambdaQueryWrapper<ProcessAddSignTask>()
                        .eq(ProcessAddSignTask::getProcessId, processId)
                        .eq(ProcessAddSignTask::getStatus, AddSignStatus.PENDING.name()))
                .stream()
                .map(MapperPendingSignTaskEvidence::snapshot)
                .toList();
    }

    @Override
    public List<SignTaskSnapshot> pendingByEngineTaskIds(List<String> engineTaskIds) {
        if (engineTaskIds == null || engineTaskIds.isEmpty()) {
            return List.of();
        }
        return addSignTaskMapper.selectPendingByEngineTaskIds(engineTaskIds).stream()
                .map(MapperPendingSignTaskEvidence::snapshot)
                .toList();
    }

    @Override
    public List<SignTaskSnapshot> pendingByType(String signType) {
        if (signType == null || signType.isBlank()) {
            return List.of();
        }
        return addSignTaskMapper.selectPendingByType(signType).stream()
                .map(MapperPendingSignTaskEvidence::snapshot)
                .toList();
    }

    private static SignTaskSnapshot snapshot(ProcessAddSignTask task) {
        return new SignTaskSnapshot(
                task.getId(),
                task.getProcessId(),
                task.getEngineTaskId(),
                task.getSignType(),
                task.getAssigneeId(),
                task.getCreateTime());
    }
}
