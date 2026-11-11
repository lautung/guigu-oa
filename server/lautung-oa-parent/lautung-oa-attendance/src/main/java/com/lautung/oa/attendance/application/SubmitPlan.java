package com.lautung.oa.attendance.application;

/**
 * 提交快照（#294，ADR 0075）：适配器在提交前领域校验之后给出的「要落库的载荷」
 * 与审批摘要文案；生命周期 module 只负责把载荷写回并原样发送文案。
 */
public record SubmitPlan<S>(S snapshot, String summaryText) {
}
