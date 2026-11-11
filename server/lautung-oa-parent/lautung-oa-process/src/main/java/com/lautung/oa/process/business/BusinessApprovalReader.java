package com.lautung.oa.process.business;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lautung.oa.model.process.Process;
import com.lautung.oa.process.mapper.OaProcessMapper;
import com.lautung.oa.process.pending.PendingWorklist;
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
	private final PendingWorklist pendingWorklist;

	public BusinessApprovalReader(
			OaProcessMapper processMapper,
			PendingWorklist pendingWorklist) {
		this.processMapper = processMapper;
		this.pendingWorklist = pendingWorklist;
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
	 * 同一张业务单、同一提交键、且仍在审批中的审批 id（#282/#294，ADR 0067/0032/0075）：
	 * 业务单据生命周期 module 的「同键重放」判定读缝。单据状态已是审批中、或唯一索引
	 * 冲突回滚后重读时，只有能在这里认出同一单据的同一在途提交才按重放返回同一条审批；
	 * 认不出（不同键、或键属于已完成的历史提交）由生命周期按 201 命名拒绝。
	 * 没有对应在途审批返回 {@code null}。
	 */
	public Long inFlightSubmissionProcessId(String businessType, Long businessId,
											Long applicantId, String idempotencyKey) {
		if (!StringUtils.hasText(businessType) || businessId == null
				|| applicantId == null || !StringUtils.hasText(idempotencyKey)) {
			return null;
		}
		List<Process> rows = processMapper.selectList(new LambdaQueryWrapper<Process>()
				.eq(Process::getBusinessType, businessType)
				.eq(Process::getBusinessId, businessId)
				.eq(Process::getUserId, applicantId)
				.eq(Process::getIdempotencyKey, idempotencyKey)
				.eq(Process::getStatus, 1));
		return rows.isEmpty() ? null : rows.get(0).getId();
	}

	/** 当前审批人：仅审批中读待处理名单投影（处理人/候选人/加签人，与等待文案同口径），终态为 null。 */
	private String currentApprover(ProcessVo row) {
		if (!Integer.valueOf(1).equals(row.getStatus())
				|| !StringUtils.hasText(row.getProcessInstanceId())) {
			return null;
		}
		List<String> names = pendingWorklist.pendingHandlerNames(row.getId(), row.getProcessInstanceId());
		return names.isEmpty() ? null : String.join("、", names);
	}

	/** 终态时间：审批中的最后更新时刻不是结束时间，不下发（endTime null）。 */
	private java.util.Date endTime(ProcessVo row) {
		Integer status = row.getStatus();
		boolean terminal = status != null && (status == 2 || status == -1 || status == -2);
		return terminal ? row.getUpdateTime() : null;
	}
}
