package com.lautung.oa.process.business;

import com.lautung.oa.auth.service.SysUserService;
import com.lautung.oa.model.system.SysUser;
import com.lautung.oa.process.approval.ApprovalEngine;
import com.lautung.oa.process.mapper.OaProcessMapper;
import com.lautung.oa.vo.process.BusinessApprovalSummary;
import com.lautung.oa.vo.process.ProcessVo;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
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

	/** 当前审批人：仅审批中读引擎当前任务（处理人/候选名单，与等待文案同源），终态为 null。 */
	private String currentApprover(ProcessVo row) {
		if (!Integer.valueOf(1).equals(row.getStatus())
				|| !StringUtils.hasText(row.getProcessInstanceId())) {
			return null;
		}
		List<ApprovalEngine.CurrentTask> currentTasks =
				approvalEngine.currentTasks(row.getProcessInstanceId());
		List<String> names = new ArrayList<>();
		for (ApprovalEngine.CurrentTask task : currentTasks) {
			// 候选任务尚无 assignee：按候选用户集给名单（与 ApprovalWriter 等待文案同口径）。
			LinkedHashSet<String> usernames = new LinkedHashSet<>();
			if (StringUtils.hasText(task.assignee())) {
				usernames.add(task.assignee());
			}
			usernames.addAll(task.candidates());
			for (String username : usernames) {
				SysUser assignee = sysUserService.getUserByUserName(username);
				names.add(assignee != null && StringUtils.hasText(assignee.getName())
						? assignee.getName()
						: username);
			}
		}
		return names.isEmpty() ? null : String.join("、", names);
	}

	/** 终态时间：审批中的最后更新时刻不是结束时间，不下发（endTime null）。 */
	private java.util.Date endTime(ProcessVo row) {
		Integer status = row.getStatus();
		boolean terminal = status != null && (status == 2 || status == -1 || status == -2);
		return terminal ? row.getUpdateTime() : null;
	}
}
