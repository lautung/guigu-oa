package com.lautung.oa.audit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lautung.oa.audit.entity.SysOperLog;
import com.lautung.oa.audit.mapper.SysOperLogMapper;
import com.lautung.oa.framework.config.exception.BusinessException;
import com.lautung.oa.model.system.DataScope;
import com.lautung.oa.vo.system.SysOperLogQueryVo;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 操作日志的查询面（ADR 0054/0057，#214）。
 *
 * <p>审计表是历史事实：所有读都直接打 {@code sys_oper_log}，没有 {@code is_deleted} 逻辑，
 * 删除与清空都是物理删除。数据范围只接参数——部门列 {@code dept_id}、本人列 {@code oper_id}，
 * 用 wrapper 的 {@code in}/{@code or} 落查询，不写手写 XML，也不出现 {@code ${}} 片段。
 */
@Service
public class SysOperLogService {

    private final SysOperLogMapper operLogMapper;

    public SysOperLogService(SysOperLogMapper operLogMapper) {
        this.operLogMapper = operLogMapper;
    }

    /** 分页列表：稳定排序 {@code create_time DESC, id DESC}（ADR 0045 的纪律）。 */
    public IPage<SysOperLog> page(Page<SysOperLog> page, SysOperLogQueryVo query, DataScope scope) {
        LambdaQueryWrapper<SysOperLog> wrapper = new LambdaQueryWrapper<>();
        if (query != null) {
            if (StringUtils.hasText(query.getOperName())) {
                wrapper.like(SysOperLog::getOperName, query.getOperName());
            }
            if (StringUtils.hasText(query.getTitle())) {
                wrapper.like(SysOperLog::getTitle, query.getTitle());
            }
            if (query.getBusinessType() != null) {
                wrapper.eq(SysOperLog::getBusinessType, query.getBusinessType());
            }
            if (query.getStatus() != null) {
                wrapper.eq(SysOperLog::getStatus, query.getStatus());
            }
            if (StringUtils.hasText(query.getCreateTimeBegin())) {
                wrapper.ge(SysOperLog::getCreateTime, query.getCreateTimeBegin());
            }
            if (StringUtils.hasText(query.getCreateTimeEnd())) {
                wrapper.le(SysOperLog::getCreateTime, query.getCreateTimeEnd());
            }
        }
        applyDataScope(wrapper, scope);
        wrapper.orderByDesc(SysOperLog::getCreateTime).orderByDesc(SysOperLog::getId);
        return operLogMapper.selectPage(page, wrapper);
    }

    /**
     * 详情：范围条件与列表同源，范围外的 id 返回 {@code null}，不先读整行再丢弃。
     */
    public SysOperLog get(Long id, DataScope scope) {
        if (id == null) {
            return null;
        }
        LambdaQueryWrapper<SysOperLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysOperLog::getId, id);
        applyDataScope(wrapper, scope);
        return operLogMapper.selectOne(wrapper);
    }

    /**
     * 按 id 批量物理删除。空列表（含全 null 元素）是明确拒绝，不是幂等无操作（ADR 0040）。
     *
     * @return 至少删掉一行时为 {@code true}；id 全不存在时为 {@code false}（控制器落 201「失败」）。
     */
    public boolean removeByIds(List<Long> idList) {
        Set<Long> ids = new LinkedHashSet<>();
        if (idList != null) {
            idList.stream().filter(Objects::nonNull).forEach(ids::add);
        }
        if (ids.isEmpty()) {
            throw new BusinessException(201, "删除列表不能为空");
        }
        return operLogMapper.deleteByIds(ids) > 0;
    }

    /** 清空：物理 {@code DELETE} 全表，返回删除行数（0 行也算成功清空）。 */
    public int clean() {
        return operLogMapper.delete(null);
    }

    /**
     * 查询侧只接受参数（ADR 0053）：部门集用 wrapper 的 {@code in}，本人行用 {@code eq}，
     * 两者并集用 {@code or}；空集用常量 {@code 1 = 0} 得到零行，不是全部。
     */
    private void applyDataScope(LambdaQueryWrapper<SysOperLog> wrapper, DataScope scope) {
        if (scope == null || scope.isUnrestricted()) {
            return;
        }
        Set<Long> deptIds = scope.getDeptIds();
        Long selfUserId = scope.getSelfUserId();
        if (deptIds.isEmpty() && selfUserId == null) {
            wrapper.apply("1 = 0");
            return;
        }
        wrapper.and(nested -> {
            if (!deptIds.isEmpty()) {
                nested.in(SysOperLog::getDeptId, deptIds);
            }
            if (selfUserId != null) {
                if (!deptIds.isEmpty()) {
                    nested.or();
                }
                nested.eq(SysOperLog::getOperId, selfUserId);
            }
        });
    }
}
