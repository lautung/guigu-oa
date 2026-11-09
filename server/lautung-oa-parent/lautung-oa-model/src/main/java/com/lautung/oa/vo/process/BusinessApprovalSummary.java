package com.lautung.oa.vo.process;

import java.util.Date;

/**
 * 业务键回查的最近一条审批摘要（ADR 0067，#289）：单据表不存 process_id，域详情按
 * {@code (business_type, business_id)} 回查拼装「单据 + 审批摘要」，不嵌完整审批载荷；
 * 点进审批详情走现有详情页与成员资格 gate。
 *
 * @param processId      审批 id（最近一条；一张单据可积累多次审批，最近一条即当前真相）
 * @param status         oa_process.status（1 审批中 / 2 通过 / -1 拒绝 / -2 已撤回）
 * @param currentApprover 当前审批人（审批中时为当前任务处理人姓名清单，顿号连接；
 *                        终态为 {@code null}）
 * @param description    等待/完成文案（description 唯一拥有展示文案，ADR 0043）
 * @param startTime      发起时间
 * @param endTime        终态时间（最后动作时刻；审批中为 {@code null}）
 */
public record BusinessApprovalSummary(
		Long processId,
		Integer status,
		String currentApprover,
		String description,
		Date startTime,
		Date endTime) {
}
