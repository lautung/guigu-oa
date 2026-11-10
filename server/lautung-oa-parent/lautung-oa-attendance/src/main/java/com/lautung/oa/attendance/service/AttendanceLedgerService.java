package com.lautung.oa.attendance.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lautung.oa.model.system.DataScope;
import com.lautung.oa.vo.attendance.AttendanceLeaveBalanceVo;
import com.lautung.oa.vo.attendance.AttendanceLedgerGrantRequest;
import com.lautung.oa.vo.attendance.AttendanceLedgerQueryVo;
import com.lautung.oa.vo.attendance.AttendanceLedgerVo;

import java.util.List;

/**
 * 假期余额账本（#280，ADR 0073）：发放一次多人、每人一行 append-only 流水；
 * 后台流水列表按五档数据范围；办事余额只读本人。
 *
 * <p>可用余额 = Σ发放 + Σ入账 − Σ扣减 − Σ在途。发放与调整用负数行冲抵，
 * 不提供更新/删除入口。Schema 校验只在本票建账本，余额不足拒绝归 #282。
 */
public interface AttendanceLedgerService {

    /** 一次多人发放/调整；返回写入的流水行数。 */
    int grant(AttendanceLedgerGrantRequest request, Long operatorUserId, DataScope scope);

    /** 发放流水分页（部门列 sys_user.dept_id 当前 + 本人列）。 */
    IPage<AttendanceLedgerVo> page(Page<AttendanceLedgerVo> page,
                                   AttendanceLedgerQueryVo query,
                                   DataScope scope);

    /** 本人各需要余额类型的可用余额（登录即有，只读本人）。 */
    List<AttendanceLeaveBalanceVo> balances(Long userId);
}
