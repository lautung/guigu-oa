package com.lautung.oa.process.business;

/**
 * 业务域实现的审批回调（ADR 0067，#289）：按 {@link #businessType()} 注册（Spring bean，
 * {@link BusinessApprovalCallbackRegistry} 收集），审批终态时由 process 在动作同一事务内、
 * 账本更新之后调用。process 不依赖任何业务域类型，业务域依赖 process 只经本端口。
 *
 * <p>实现契约：
 * <ul>
 *   <li>在本回调里完成单据状态回写与域内副作用；抛出的任何异常都回滚整个审批动作
 *       （含引擎动作），所以回写必须与回调同事务、不要自己开新事务。</li>
 *   <li>必须幂等：并发终态（撤回与通过竞态等）可能让两个动作都判定为终态——
 *       同值终态 no-op 成功；不同值抛错并回滚后到的动作（先提交者胜）。
 *       判定依据是单据自己的状态（单据状态机是唯一仲裁）。</li>
 *   <li>中间节点不回调；回调只拿业务单据 id，单据表不存 process_id，
 *       详情拼装走 {@link BusinessApprovalReader}。</li>
 * </ul>
 */
public interface BusinessApprovalCallback {

	/** 本回调归属的业务类型（值域 {@code <域>.<单据>}，如 hr.transfer）；全局唯一。 */
	String businessType();

	/**
	 * 终态回写。同一事务内、账本更新之后调用；同值重复送达必须 no-op 成功，
	 * 异值抛错（整个动作回滚）。
	 */
	void onTerminal(Long businessId, BusinessApprovalOutcome outcome);
}
