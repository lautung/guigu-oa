package com.lautung.oa.process.business;

import com.lautung.oa.framework.config.exception.BusinessException;
import com.lautung.oa.model.process.OaBusinessTemplate;
import com.lautung.oa.process.approval.ApprovalWriter;
import com.lautung.oa.process.approval.ApproverNames;
import com.lautung.oa.process.mapper.OaBusinessTemplateMapper;
import com.lautung.oa.process.service.Approval;
import com.lautung.oa.vo.process.BusinessApprovalStart;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 业务审批发起端口（ADR 0067，#289）：业务域调用的唯一发起缝。业务域依赖 process、
 * process 不依赖业务域——本类只认 {@link BusinessApprovalCallback} 注册与
 * {@code oa_business_template} 映射，不引用任何业务域类型。
 *
 * <p>复用 {@link ApprovalWriter} 的次序、事务与 {@code (user_id, idempotency_key)} 幂等索引
 * （ADR 0032）。同键重放识别不归本类：业务发起键的语义是「同一单据的同一提交」，只有
 * 单据自己的生命周期知道当前提交是哪一次（ADR 0075）。{@code uk_oa_process_idempotency_key}
 * 的冲突原样上抛，调用方必须让自己的事务先回滚，再到事务外按提交标识重读。
 *
 * <p>校验先于写，所有 201 的含义都是「什么都没发生，单据侧由域自行保持草稿」：
 * 幂等键缺失/空白/超长、businessType 未注册回调、映射缺失、模板未发布、发布证据失效。
 */
@Service
public class BusinessApprovalStarter {

	private final ApprovalWriter approvalWriter;
	private final BusinessApprovalCallbackRegistry callbackRegistry;
	private final OaBusinessTemplateMapper businessTemplateMapper;

	public BusinessApprovalStarter(
			ApprovalWriter approvalWriter,
			BusinessApprovalCallbackRegistry callbackRegistry,
			OaBusinessTemplateMapper businessTemplateMapper) {
		this.approvalWriter = approvalWriter;
		this.callbackRegistry = callbackRegistry;
		this.businessTemplateMapper = businessTemplateMapper;
	}

	public Approval.StartOutcome start(BusinessApprovalStart command) {
		requireCommandShape(command);
		String idempotencyKey = requireIdempotencyKey(command.idempotencyKey());
		requireRegisteredCallback(command.businessType());
		Long processTemplateId = requireMappedTemplate(command.businessType());
		return approvalWriter.startBusiness(command, idempotencyKey, processTemplateId);
	}

	private static void requireCommandShape(BusinessApprovalStart command) {
		if (command == null || command.applicant() == null || command.applicant().getId() == null) {
			throw new BusinessException(201, "发起人不能为空");
		}
		if (!StringUtils.hasText(command.businessType())) {
			throw new BusinessException(201, "业务类型不能为空");
		}
		if (command.businessId() == null) {
			throw new BusinessException(201, "业务单据标识不能为空");
		}
	}

	/** 业务发起键必填（与通用审批「缺省不去重」不同，ADR 0067）；缺失/空白/超长 201。 */
	private static String requireIdempotencyKey(String rawIdempotencyKey) {
		if (rawIdempotencyKey == null || rawIdempotencyKey.trim().isEmpty()) {
			throw new BusinessException(201, "提交标识不能为空");
		}
		String idempotencyKey = rawIdempotencyKey.trim();
		if (idempotencyKey.length() > ApproverNames.MAX_IDEMPOTENCY_KEY_LENGTH) {
			throw new BusinessException(201, "提交标识过长");
		}
		return idempotencyKey;
	}

	/** 没有回调的审批「结束也没人回写」，发起即拒绝（ADR 0067）。 */
	private void requireRegisteredCallback(String businessType) {
		if (callbackRegistry.get(businessType) == null) {
			throw new BusinessException(201, "该业务类型未注册审批回调，不能发起");
		}
	}

	/** 映射缺失 201；模板未发布/发布证据失效由 {@code resolveForInitiation} 在写入事务里
	 * 于任何 insert 之前命名拒绝（与通用发起同一校验位置，校验先于写）。 */
	private Long requireMappedTemplate(String businessType) {
		OaBusinessTemplate mapping = businessTemplateMapper.selectById(businessType);
		if (mapping == null) {
			throw new BusinessException(201, "该业务类型未配置审批模板，不能发起");
		}
		return mapping.getProcessTemplateId();
	}
}
