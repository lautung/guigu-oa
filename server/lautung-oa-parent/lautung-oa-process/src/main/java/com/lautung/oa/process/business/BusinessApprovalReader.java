package com.lautung.oa.process.business;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lautung.oa.auth.service.SysUserService;
import com.lautung.oa.model.process.Process;
import com.lautung.oa.model.system.SysUser;
import com.lautung.oa.process.approval.ApprovalEngine;
import com.lautung.oa.process.approval.ApproverNames;
import com.lautung.oa.process.mapper.OaProcessMapper;
import com.lautung.oa.vo.process.BusinessApprovalSummary;
import com.lautung.oa.vo.process.ProcessVo;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 业务键回查读能力（ADR 0067，#289）：按 {@code (business_type, business_id)} 取最近一条
 * 审批摘要，供域详情拼装「单据 + 审批摘要」。单据表不存 process_id——一张单据可积累
 * 多次审批（重提起新审批），id 最大的那条即当前真相；行取数与列形状复用共享清单
 * （OaProcessMapper.xml selectLatestBusinessRow，含 approvalRowLiveness）。
 * 本读不做成员资格 gate：调用方是业务域的详情读，可见性由域自行判定。
 */
@Service
public class BusinessApprovalReader {

	private final OaProcessMapper processMapper;
	private final ApprovalEngine approvalEngine;
	private final SysUserService sysUserService;

	public BusinessApprovalReader(
			OaProcessMapper processMapper,
			ApprovalEngine approvalEngine,
			SysUserService sysUserService) {
		this.processMapper = processMapper;
		this.approvalEngine = approvalEngine;
		this.sysUserService = sysUserService;
	}

	/** 最近一条审批摘要；该业务单还没有审批（或入参缺失）时返回 {@code null}。 */
	public BusinessApprovalSummary latestSummary(String businessType, Long businessId) {
		if (!StringUtils.hasText(businessType) || businessId == null) {
			return null;
		}
		ProcessVo row = processMapper.selectLatestBusinessRow(businessType, businessId);
		if (row == null) {
			return null;
		}
		return new BusinessApprovalSummary(
				row.getId(),
				row.getStatus(),
				currentApprover(row),
				row.getDescription(),
				row.getCreateTime(),
				endTime(row));
	}

	/**
	 * 同一张业务单、同一提交键对应的审批 id（#282，ADR 0067/0032）：业务域的
	 * 「同键重放」判定读缝——单据状态已是审批中时，只有能在这里认出同键才按重放
	 * 返回同一条审批；认不出就是不同键的并发提交，由域按 201 拒绝。
	 * 没有对应审批返回 {@code null}。
	 */
	public Long processIdOfSubmission(String businessType, Long businessId,
									  Long applicantId, String idempotencyKey) {
		if (!StringUtils.hasText(businessType) || businessId == null
				|| applicantId == null || !StringUtils.hasText(idempotencyKey)) {
			return null;
		}
		List<Process> rows = processMapper.selectList(new LambdaQueryWrapper<Process>()
				.eq(Process::getBusinessType, businessType)
				.eq(Process::getBusinessId, businessId)
				.eq(Process::getUserId, applicantId)
				.eq(Process::getIdempotencyKey, idempotencyKey));
		return rows.isEmpty() ? null : rows.get(0).getId();
	}

	/** 当前审批人：仅审批中读引擎当前任务（处理人/候选名单，与等待文案同口径），终态为 null。 */
	private String currentApprover(ProcessVo row) {
		if (!Integer.valueOf(1).equals(row.getStatus())
				|| !StringUtils.hasText(row.getProcessInstanceId())) {
			return null;
		}
		List<ApprovalEngine.CurrentTask> currentTasks =
				approvalEngine.currentTasks(row.getProcessInstanceId());
		List<String> names = ApproverNames.of(currentTasks, this::displayName);
		return names.isEmpty() ? null : String.join("、", names);
	}

	/** 用户名 → 显示名（共享投影的解析缝；找不到用户或无名退回用户名）。 */
	private String displayName(String username) {
		SysUser assignee = sysUserService.getUserByUserName(username);
		return assignee != null && StringUtils.hasText(assignee.getName())
				? assignee.getName()
				: username;
	}

	/** 终态时间：审批中的最后更新时刻不是结束时间，不下发（endTime null）。 */
	private java.util.Date endTime(ProcessVo row) {
		Integer status = row.getStatus();
		boolean terminal = status != null && (status == 2 || status == -1 || status == -2);
		return terminal ? row.getUpdateTime() : null;
	}
}
