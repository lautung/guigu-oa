package com.lautung.oa.process.business;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 业务审批回调注册表（ADR 0067，#289）：启动时收集全部 {@link BusinessApprovalCallback}
 * bean，按业务类型索引。发起时校验注册（没有回调的审批「结束也没人回写」，不允许造出）；
 * 终态时经它取回调。同一业务类型注册两个回调是装配错误，启动即失败。
 */
@Component
public class BusinessApprovalCallbackRegistry {

	private final Map<String, BusinessApprovalCallback> callbacksByType;

	public BusinessApprovalCallbackRegistry(List<BusinessApprovalCallback> callbacks) {
		this.callbacksByType = callbacks.stream().collect(Collectors.toUnmodifiableMap(
				BusinessApprovalCallback::businessType, Function.identity(), (first, second) -> {
					throw new IllegalStateException("同一业务类型注册了多个审批回调: "
							+ first.businessType());
				}));
	}

	/** 无注册回调时返回 {@code null}（发起侧据此 201 拒绝）。 */
	public BusinessApprovalCallback get(String businessType) {
		return businessType == null ? null : callbacksByType.get(businessType);
	}
}
