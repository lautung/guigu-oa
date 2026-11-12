package com.lautung.oa.process.read;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lautung.oa.framework.page.PageWindow;
import com.lautung.oa.model.process.Process;
import com.lautung.oa.model.system.DataScope;
import com.lautung.oa.model.system.SysUser;
import com.lautung.oa.process.mapper.OaProcessMapper;
import com.lautung.oa.process.pending.PendingWorklist;
import com.lautung.oa.vo.process.ProcessQueryVo;
import com.lautung.oa.vo.process.ProcessVo;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * {@link ApprovalListReader} 的唯一实现。待处理与单行读委托 {@link PendingWorklist}
 * （#297，ADR 0076）；本类只留已处理、已发起、抄送我的与配置实例查询四种列表。已处理按
 * 账本动作记录去重供给（#186，ADR 0049）；已发起与配置实例查询共用一条 SQL；抄送我的与
 * 已处理走原生 LIMIT/OFFSET（#122 口径）。窗口算术一律从 {@link PageWindow} 派生，不手算。
 */
@Service
public class LedgerApprovalListReader implements ApprovalListReader {

    private final OaProcessMapper processMapper;
    private final PendingWorklist pendingWorklist;

    public LedgerApprovalListReader(OaProcessMapper processMapper, PendingWorklist pendingWorklist) {
        this.processMapper = processMapper;
        this.pendingWorklist = pendingWorklist;
    }

    @Override
    public IPage<ProcessVo> pending(Page<ProcessVo> page, SysUser user) {
        return pendingWorklist.pending(page, user);
    }

    @Override
    public ProcessVo pendingRow(SysUser user, Process process) {
        return pendingWorklist.pendingRow(user, process);
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

    private IPage<ProcessVo> pageOf(Page<ProcessVo> page, long total, List<ProcessVo> records) {
        IPage<ProcessVo> result = new Page<>(page.getCurrent(), page.getSize(), total);
        result.setRecords(records);
        return result;
    }
}
