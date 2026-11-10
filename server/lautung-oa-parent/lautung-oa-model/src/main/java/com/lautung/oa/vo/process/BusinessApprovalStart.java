package com.lautung.oa.vo.process;

import com.lautung.oa.model.system.SysUser;

import java.util.List;

/**
 * 业务审批发起命令（ADR 0067，#289）：业务域经
 * {@code com.lautung.oa.process.business.BusinessApprovalStarter} 提交。
 *
 * @param applicant      发起人（业务域从自己的会话解析）
 * @param businessType   业务类型（值域 {@code <域>.<单据>}，如 hr.transfer）；必须已注册回调
 * @param businessId     业务单据 id（单据表不存 process_id，回写按回调、详情按两列回查）
 * @param idempotencyKey 客户端生成的提交标识（必填；缺失/空白/超长 201——业务单据的重放代价
 *                       大于通用审批，不提供「缺省不去重」档）
 * @param summaryText    提交快照摘要文本：process 侧写进 {@code form_values} 的
 *                       {@code formShowData}（现有详情页渲染路径直接可用），提交时定稿，
 *                       之后改单据不影响在途审批
 * @param ccUserIds      可选抄送人（发起时绑定）
 */
public record BusinessApprovalStart(
		SysUser applicant,
		String businessType,
		Long businessId,
		String idempotencyKey,
		String summaryText,
		List<Long> ccUserIds) {

	public BusinessApprovalStart {
		ccUserIds = ccUserIds == null ? List.of() : List.copyOf(ccUserIds);
	}
}
