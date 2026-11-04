package com.lautung.oa.audit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lautung.oa.audit.entity.SysLoginLog;
import com.lautung.oa.audit.mapper.SysLoginLogMapper;
import com.lautung.oa.framework.config.exception.BusinessException;
import com.lautung.oa.model.system.DataScope;
import com.lautung.oa.security.login.LoginAttemptLimiter;
import com.lautung.oa.vo.system.SysLoginLogQueryVo;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 登录日志的查询面与手动解锁（ADR 0054/0057，#216）。
 *
 * <p>审计表是历史事实：所有读都直接打 {@code sys_login_log}，没有 {@code is_deleted} 逻辑，
 * 删除与清空都是物理删除。数据范围只接参数——部门列 {@code dept_id}、本人列 {@code user_id}，
 * 用 wrapper 的 {@code in}/{@code or} 落查询，不写手写 XML，也不出现 {@code ${}} 片段。
 * 删除/清空/解锁是按 id、全表与账号的写动作，不套数据范围（ADR 0054）。
 */
@Service
public class SysLoginLogService {

    static final String UNLOCK_USERNAME_REQUIRED = "解锁用户名不能为空";

    private final SysLoginLogMapper loginLogMapper;

    private final LoginAttemptLimiter loginAttemptLimiter;

    public SysLoginLogService(SysLoginLogMapper loginLogMapper, LoginAttemptLimiter loginAttemptLimiter) {
        this.loginLogMapper = loginLogMapper;
        this.loginAttemptLimiter = loginAttemptLimiter;
    }

    /** 分页列表：稳定排序 {@code create_time DESC, id DESC}（ADR 0045 的纪律）。 */
    public IPage<SysLoginLog> page(Page<SysLoginLog> page, SysLoginLogQueryVo query, DataScope scope) {
        LambdaQueryWrapper<SysLoginLog> wrapper = new LambdaQueryWrapper<>();
        if (query != null) {
            if (StringUtils.hasText(query.getUsername())) {
                wrapper.like(SysLoginLog::getUsername, query.getUsername());
            }
            if (StringUtils.hasText(query.getIp())) {
                wrapper.like(SysLoginLog::getIp, query.getIp());
            }
            if (query.getStatus() != null) {
                wrapper.eq(SysLoginLog::getStatus, query.getStatus());
            }
            if (StringUtils.hasText(query.getEvent())) {
                wrapper.eq(SysLoginLog::getEvent, query.getEvent().trim());
            }
            if (StringUtils.hasText(query.getCreateTimeBegin())) {
                wrapper.ge(SysLoginLog::getCreateTime, query.getCreateTimeBegin());
            }
            if (StringUtils.hasText(query.getCreateTimeEnd())) {
                wrapper.le(SysLoginLog::getCreateTime, query.getCreateTimeEnd());
            }
        }
        applyDataScope(wrapper, scope);
        wrapper.orderByDesc(SysLoginLog::getCreateTime).orderByDesc(SysLoginLog::getId);
        return loginLogMapper.selectPage(page, wrapper);
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
        return loginLogMapper.deleteByIds(ids) > 0;
    }

    /** 清空：物理 {@code DELETE} 全表，返回删除行数（0 行也算成功清空）。 */
    public int clean() {
        return loginLogMapper.delete(null);
    }

    /**
     * 手动解锁只解账号维度（ADR 0054）：清 {@code login:attempt:account:<name>:*} 三个键，
     * 并由限流端口写一条 {@code status=3} 的登录日志行。空账号是明确拒绝——否则会去清占位
     * 维度的键，并把「解锁」记到一个不存在的账号上。
     */
    public void unlock(String username) {
        if (!StringUtils.hasText(username)) {
            throw new BusinessException(201, UNLOCK_USERNAME_REQUIRED);
        }
        loginAttemptLimiter.unlockAccount(username.trim());
    }

    /**
     * 查询侧只接受参数（ADR 0053）：部门集用 wrapper 的 {@code in}，本人行用 {@code eq}，
     * 两者并集用 {@code or}；空集用常量 {@code 1 = 0} 得到零行，不是全部。{@code user_id}
     * 为空的历史失败行不落任何部门集，也就只在 {@code unrestricted} 下可见。
     */
    private void applyDataScope(LambdaQueryWrapper<SysLoginLog> wrapper, DataScope scope) {
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
                nested.in(SysLoginLog::getDeptId, deptIds);
            }
            if (selfUserId != null) {
                if (!deptIds.isEmpty()) {
                    nested.or();
                }
                nested.eq(SysLoginLog::getUserId, selfUserId);
            }
        });
    }
}
