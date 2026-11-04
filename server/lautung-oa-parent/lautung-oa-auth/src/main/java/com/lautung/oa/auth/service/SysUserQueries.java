package com.lautung.oa.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lautung.oa.model.system.DataScope;
import com.lautung.oa.model.system.SysUser;
import com.lautung.oa.vo.system.SysUserQueryVo;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

/**
 * 用户列表与导出共用的查询条件（#259）：关键字、部门子树、状态与 DataScope 只在这里组装一次，
 * 防止「列表能看到的行」和「导出的行」各写一套而悄悄分叉。
 *
 * <p>department 子树由调用方用 {@link SysDeptService#findSubtreeIds} 解析后传入；空列表表示
 * 选中部门不存在/已删，得到零行。数据范围按 ADR 0057：部门集 {@code in} 与本人行 {@code eq}
 * 并集，两者都空时是 {@code 1 = 0} 而不是整表。
 */
public final class SysUserQueries {

    private SysUserQueries() {
    }

    public static LambdaQueryWrapper<SysUser> wrapper(
            SysUserQueryVo query, List<Long> deptSubtreeIds, DataScope scope, boolean ordered) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.like(SysUser::getUsername, query.getKeyword());
        }
        if (query.getStatus() != null) {
            wrapper.eq(SysUser::getStatus, query.getStatus());
        }
        if (query.getDeptId() != null) {
            // 只是额外的 AND，不放宽数据范围；子树空即零行。
            if (deptSubtreeIds == null || deptSubtreeIds.isEmpty()) {
                wrapper.apply("1 = 0");
            } else {
                wrapper.in(SysUser::getDeptId, deptSubtreeIds);
            }
        }
        applyDataScope(wrapper, scope);
        if (ordered) {
            wrapper.orderByAsc(SysUser::getId);
        }
        return wrapper;
    }

    private static void applyDataScope(LambdaQueryWrapper<SysUser> wrapper, DataScope scope) {
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
                nested.in(SysUser::getDeptId, deptIds);
            }
            if (selfUserId != null) {
                if (!deptIds.isEmpty()) {
                    nested.or();
                }
                nested.eq(SysUser::getId, selfUserId);
            }
        });
    }
}
