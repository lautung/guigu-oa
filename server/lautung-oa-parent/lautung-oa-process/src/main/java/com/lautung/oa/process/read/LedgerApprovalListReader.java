package com.lautung.oa.process.read;

import com.lautung.oa.framework.page.PageWindow;
import com.lautung.oa.model.system.SysUser;
import com.lautung.oa.process.ProcessApprovalSupport;
import com.lautung.oa.process.approval.ApprovalEngine;
import com.lautung.oa.process.mapper.OaProcessMapper;
import com.lautung.oa.vo.process.ProcessQueryVo;
import com.lautung.oa.vo.process.ProcessVo;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * {@link ApprovalListReader} 的唯一实现。待处理由引擎分页与排序，账本行按页批量回查后
 * 按引擎顺序重排（N+1 消除，#181）；已处理按账本动作记录去重供给（#186，ADR 0049）；
 * 已发起与配置实例查询共用一条 SQL；抄送我的与已处理走原生 LIMIT/OFFSET（#122 口径）。
 * 窗口算术一律从 {@link PageWindow} 派生，不手算。
 */
@Service
public class LedgerApprovalListReader implements ApprovalListReader {

    private final ApprovalEngine approvalEngine;
    private final OaProcessMapper processMapper;

    public LedgerApprovalListReader(ApprovalEngine approvalEngine, OaProcessMapper processMapper) {
        this.approvalEngine = approvalEngine;
        this.processMapper = processMapper;
    }

    @Override
    public IPage<ProcessVo> pending(Page<ProcessVo> page, SysUser user) {
        List<ApprovalEngine.PendingTask> taskList = approvalEngine.pendingTasks(
                user.getUsername(), PageWindow.firstResult(page), (int) page.getSize());
        long totalCount = approvalEngine.pendingCount(user.getUsername());

        // 跳过空 businessKey 与已删/缺失账本行的边界不变；回查按页批量一次取回。
        // businessKey 只解析一次：空值跳过、非数字维持 NumberFormatException，建批查集合与
        // 重排循环共用同一份解析结果，不重复 parseLong。
        List<ApprovalEngine.PendingTask> keyedTasks = new ArrayList<>();
        List<Long> keyedIds = new ArrayList<>();
        for (ApprovalEngine.PendingTask task : taskList) {
            if (StringUtils.hasText(task.businessKey())) {
                keyedTasks.add(task);
                keyedIds.add(Long.parseLong(task.businessKey()));
            }
        }
        Map<Long, ProcessVo> rowsById = rowsById(keyedIds);
        List<ProcessVo> records = new ArrayList<>();
        for (int i = 0; i < keyedTasks.size(); i++) {
            ProcessVo row = rowsById.get(keyedIds.get(i));
            if (row == null) {
                continue;
            }
            row.setTaskId(keyedTasks.get(i).taskId());
            records.add(row);
        }
        return pageOf(page, totalCount, records);
    }

    @Override
    public IPage<ProcessVo> processed(Page<ProcessVo> page, SysUser user) {
        // 行=审批（GLOSSARY「已处理」）：该用户的处理动作记录按审批去重（同人两节点只出一行，#186），
        // 「发起申请」流转记录不算处理；最近一次处理时间倒序、同刻由审批 id 决胜，total=去重审批数。
        String excludeDescription = ProcessApprovalSupport.INITIATION_DESCRIPTION;
        long total = processMapper.countProcessedRows(user.getId(), excludeDescription);
        List<ProcessVo> records = processMapper.selectProcessedRows(
                user.getId(), excludeDescription, PageWindow.offset(page), page.getSize());
        return pageOf(page, total, records);
    }

    @Override
    public IPage<ProcessVo> started(Page<ProcessVo> page, SysUser user) {
        ProcessQueryVo query = new ProcessQueryVo();
        query.setUserId(user.getId());
        return processMapper.selectProcessPage(page, query);
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
    public IPage<ProcessVo> instanceQuery(Page<ProcessVo> page, ProcessQueryVo query) {
        return processMapper.selectProcessPage(page, query);
    }

    private Map<Long, ProcessVo> rowsById(List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return processMapper.selectRowsByIds(ids).stream()
                .collect(Collectors.toMap(ProcessVo::getId, Function.identity(), (first, second) -> first));
    }

    private IPage<ProcessVo> pageOf(Page<ProcessVo> page, long total, List<ProcessVo> records) {
        IPage<ProcessVo> result = new Page<>(page.getCurrent(), page.getSize(), total);
        result.setRecords(records);
        return result;
    }
}
