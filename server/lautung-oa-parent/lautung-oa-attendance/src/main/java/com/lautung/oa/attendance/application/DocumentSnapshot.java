package com.lautung.oa.attendance.application;

import com.lautung.oa.model.attendance.AttendanceApplyStatus;

/**
 * 单据最小视图（#294，ADR 0075）：生命周期 module 只认标识、申请人与状态；
 * {@code document} 是适配器自己的持久化载体，module 原样交回适配器，不解释其类型。
 */
public record DocumentSnapshot<D>(D document, Long id, Long applicantId,
                                  AttendanceApplyStatus status) {
}
