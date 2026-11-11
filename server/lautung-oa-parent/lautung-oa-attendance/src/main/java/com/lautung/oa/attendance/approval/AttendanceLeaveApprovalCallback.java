package com.lautung.oa.attendance.approval;

import com.lautung.oa.attendance.application.AttendanceLeaveDocumentAdapter;
import com.lautung.oa.attendance.application.DocumentLifecycle;
import com.lautung.oa.model.attendance.AttendanceBusinessTypes;
import com.lautung.oa.process.business.BusinessApprovalCallback;
import com.lautung.oa.process.business.BusinessApprovalOutcome;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * 请假单审批终态回调（#282，ADR 0067/0073；#294 收成委托，ADR 0075）：由 process 在
 * 审批动作同一事务内、账本更新之后调用。bean 保留只为 {@link #businessType()} 注册；
 * 同值 no-op、异值冲突、CAS 0 行重读与「通过后写扣减流水」的仲裁归生命周期 module
 * 与请假适配器。
 *
 * <p>{@code lifecycle} 用 {@code @Lazy}：回调只在审批终态时用到它，构造期不参与装配，
 * 断开 callback → lifecycle → starter → writer → registry 的环。
 */
@Component
public class AttendanceLeaveApprovalCallback implements BusinessApprovalCallback {

    private final DocumentLifecycle lifecycle;

    private final AttendanceLeaveDocumentAdapter adapter;

    public AttendanceLeaveApprovalCallback(@Lazy DocumentLifecycle lifecycle,
                                           AttendanceLeaveDocumentAdapter adapter) {
        this.lifecycle = lifecycle;
        this.adapter = adapter;
    }

    @Override
    public String businessType() {
        return AttendanceBusinessTypes.LEAVE;
    }

    @Override
    public void onTerminal(Long businessId, BusinessApprovalOutcome outcome) {
        lifecycle.settle(adapter, businessId, outcome);
    }
}
