package com.lautung.oa.process.business;

/**
 * 业务审批终态三分类（ADR 0067，#289）：process 侧只讲这三类，{@code oa_process.status}
 * 的数值与撤回档由 process 映射，业务域状态机按本分类回写单据。
 */
public enum BusinessApprovalOutcome {

	/** 审批最终通过（最后一节点完成）。 */
	APPROVED,

	/** 审批拒绝（任一节点 rejectToEnd 到终态）。 */
	REJECTED,

	/** 审批撤回（申请人撤回）。 */
	WITHDRAWN;

	/**
	 * oa_process.status → 业务终态分类；审批中（1）与其他非终态返回 {@code null}
	 * （中间节点不回调）。
	 */
	public static BusinessApprovalOutcome ofStatus(Integer status) {
		if (status == null) {
			return null;
		}
		return switch (status) {
			case 2 -> APPROVED;
			case -1 -> REJECTED;
			case -2 -> WITHDRAWN;
			default -> null;
		};
	}
}
