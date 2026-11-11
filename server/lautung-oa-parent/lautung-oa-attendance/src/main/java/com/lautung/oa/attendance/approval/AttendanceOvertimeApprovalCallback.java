package com.lautung.oa.attendance.approval;

import com.lautung.oa.attendance.application.AttendanceOvertimeDocumentAdapter;
import com.lautung.oa.attendance.application.DocumentLifecycle;
import com.lautung.oa.model.attendance.AttendanceBusinessTypes;
import com.lautung.oa.process.business.BusinessApprovalCallback;
import com.lautung.oa.process.business.BusinessApprovalOutcome;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * 加班单审批终态回调（#282，ADR 0067/0073；#294 收成委托，ADR 0075）：由 process 在
 * 审批动作同一事务内、账本更新之后调用。bean 保留只为 {@link #businessType()} 注册；
 * APPROVED → 2 + 调休入账（entry_type=3、恒正）、REJECTED → -1、WITHDRAWN → -2 的
 * 仲裁与副作用归生命周期 module 与加班适配器。入账后不可逆（ADR 0073），错发用负数
 * 发放行冲抵。
 *
 * <p>{@code lifecycle} 用 {@code @Lazy}：回调只在审批终态时用到它，构造期不参与装配，
 * 断开 callback → lifecycle → starter → writer → registry 的环。
 */
@Component
public class AttendanceOvertimeApprovalCallback implements BusinessApprovalCallback {

    private final DocumentLifecycle lifecycle;

    private final AttendanceOvertimeDocumentAdapter adapter;

    public AttendanceOvertimeApprovalCallback(@Lazy DocumentLifecycle lifecycle,
                                              AttendanceOvertimeDocumentAdapter adapter) {
        this.lifecycle = lifecycle;
        this.adapter = adapter;
    }

    @Override
    public String businessType() {
        return AttendanceBusinessTypes.OVERTIME;
    }

    @Override
    public void onTerminal(Long businessId, BusinessApprovalOutcome outcome) {
        lifecycle.settle(adapter, businessId, outcome);
    }
}
