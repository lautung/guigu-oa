package com.lautung.oa.process;

import com.lautung.oa.framework.config.exception.BusinessException;
import com.lautung.oa.model.process.Process;
import com.lautung.oa.model.process.ProcessRecord;
import com.lautung.oa.model.process.ProcessTemplate;
import com.lautung.oa.model.system.SysUser;
import com.lautung.oa.process.attachment.TestAttachmentObjectStore;
import com.lautung.oa.process.business.BusinessApprovalCallback;
import com.lautung.oa.process.business.BusinessApprovalOutcome;
import com.lautung.oa.process.business.BusinessApprovalReader;
import com.lautung.oa.process.business.BusinessApprovalStarter;
import com.lautung.oa.process.mapper.OaProcessMapper;
import com.lautung.oa.process.read.ApprovalListReader;
import com.lautung.oa.process.service.Approval;
import com.lautung.oa.process.service.ApprovalTemplate;
import com.lautung.oa.support.InMemoryRedisTemplate;
import com.lautung.oa.vo.process.ApprovalVo;
import com.lautung.oa.vo.process.BusinessApprovalStart;
import com.lautung.oa.vo.process.BusinessApprovalSummary;
import com.lautung.oa.vo.process.ProcessFormVo;
import com.lautung.oa.vo.process.ProcessQueryVo;
import com.lautung.oa.vo.process.ProcessVo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 业务键底座合同（#289，ADR 0067）：桩业务域（test.leave）注册回调 bean，覆盖
 * 发起快照与业务键行形状、三态回调同事务、同值 no-op、异值回滚（断言引擎动作也回滚）、
 * 发起 201 路径（幂等键缺失/空白/超长、无注册回调、映射缺失/未发布/证据失效）、
 * 同键重放同一条审批、办事列表与实例查询的 businessType 列、通用审批两列 NULL 回归、
 * 回查最近一条摘要。生产装配不引用本文件的桩（桩只活在本测试上下文）。
 *
 * <p>「回调与动作同事务」的证据是桩回调日志表（business_approval_stub_callback_log）：
 * 回调先落日志再判定同值/异值，异值抛错回滚后日志行必须不存在——若回调在独立事务里
 * 提交，它会在动作回滚后存活。
 */
@SpringBootTest
@ActiveProfiles("test")
@Sql({"/auth-contract-schema.sql", "/approval-contract-schema.sql", "/business-approval-contract-schema.sql"})
public class BusinessApprovalContractTest {

	private static final int 审批中 = 1;
	private static final int 审批通过 = 2;
	private static final int 审批拒绝 = -1;
	private static final int 已撤回 = -2;
	private static final int 通过 = 1;
	private static final int 拒绝 = -1;

	private static final String STUB_TYPE = StubLeaveCallback.BUSINESS_TYPE;

	@Autowired
	private BusinessApprovalStarter businessApprovalStarter;

	@Autowired
	private BusinessApprovalReader businessApprovalReader;

	@Autowired
	private Approval approval;

	@Autowired
	private ApprovalListReader approvalListReader;

	@Autowired
	private ApprovalTemplate approvalTemplate;

	@Autowired
	private OaProcessMapper processMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private RepositoryService repositoryService;

	@Autowired
	private RuntimeService runtimeService;

	@Autowired
	private HistoryService historyService;

	@Autowired
	private TaskService taskService;

	@Autowired
	private TestAttachmentObjectStore objectStore;

	@BeforeEach
	public void deployPublishedTemplateEngineDefinition() throws IOException {
		clearEngineInstances();
		objectStore.clear();
		repositoryService.createProcessDefinitionQuery()
				.processDefinitionKey("approvalTwoStep")
				.list()
				.forEach(definition -> repositoryService.deleteDeployment(definition.getDeploymentId(), true));
		jdbcTemplate.update("UPDATE oa_process_template SET status = 0 WHERE id = 1");
		ProcessTemplate template = approvalTemplate.read(1L);
		var uploaded = approvalTemplate.uploadProcessDefinition(new MockMultipartFile(
				"file", "approvalTwoStep.zip", "application/zip", approvalTwoStepZip()));
		template.setProcessDefinitionKey(uploaded.processDefinitionKey());
		template.setProcessDefinitionPath(uploaded.processDefinitionPath());
		approvalTemplate.saveUnpublished(template);
		approvalTemplate.publish(template.getId());
	}

	@AfterEach
	public void resetStubState() {
		jdbcTemplate.update("DELETE FROM business_approval_stub_doc");
		jdbcTemplate.update("DELETE FROM business_approval_stub_callback_log");
		jdbcTemplate.update("DELETE FROM oa_business_template");
	}

	@Test
	public void businessStartWritesBusinessKeyRowAndRendersThroughExistingPaths() {
		SysUser alice = user(2L, "alice", "小艾");
		Approval.StartOutcome started = startLeave(alice, 501L, "biz-start-1");

		assertNotNull(started.processId());
		Process row = processMapper.selectById(started.processId());
		assertEquals(STUB_TYPE, row.getBusinessType());
		assertEquals(501L, row.getBusinessId());
		assertEquals(审批中, row.getStatus());
		assertEquals("小艾发起请假申请申请", row.getTitle());
		// 提交快照：摘要写进 form_values 的 formShowData（现有详情页渲染路径直接可用）
		assertEquals("{\"formData\":{},\"formShowData\":{\"摘要\":\"3 天事假，5 月 12 日至 14 日\"}}",
				row.getFormValues());

		// 办事列表不排除业务审批：已发起、待处理行都带 businessType（共享列清单）
		ProcessVo startedRow = startedRows(alice).stream()
				.filter(r -> r.getId().equals(started.processId())).findFirst().orElseThrow();
		assertEquals(STUB_TYPE, startedRow.getBusinessType());
		ProcessVo pendingRow = approvalListReader.pending(new Page<>(1, 10), user(3L, "zhangsan", "张三"))
				.getRecords().stream()
				.filter(r -> r.getId().equals(started.processId())).findFirst().orElseThrow();
		assertEquals(STUB_TYPE, pendingRow.getBusinessType());

		// 后台审批实例查询行带 businessType/businessId
		ProcessVo queried = instanceQueryRows().stream()
				.filter(r -> r.getId().equals(started.processId())).findFirst().orElseThrow();
		assertEquals(STUB_TYPE, queried.getBusinessType());
		assertEquals(501L, queried.getBusinessId());
	}

	@Test
	public void genericApprovalRowsKeepNullBusinessColumns() {
		SysUser alice = user(2L, "alice", "小艾");
		approval.startUp(alice, publishedLeaveForm());

		ProcessVo startedRow = startedRows(alice).get(0);
		assertNull(startedRow.getBusinessType());
		assertNull(startedRow.getBusinessId());
		ProcessVo queried = instanceQueryRows().get(0);
		assertNull(queried.getBusinessType());
		assertNull(queried.getBusinessId());
	}

	@Test
	public void genericStartRefusesBusinessMappedTemplate() {
		// 通用发起只接受无专用单据的模板（ADR 0066）：业务模板只能从域内发起页走。
		seedMapping(STUB_TYPE, 1L);

		BusinessException rejected = assertThrows(BusinessException.class,
				() -> approval.startUp(user(2L, "alice", "小艾"), publishedLeaveForm()));
		assertEquals(201, rejected.getCode());
		assertEquals("业务单据请从业务页发起", rejected.getMessage());
	}

	@Test
	public void approveFinalPassInvokesCallbackApprovedInsideActionTransaction() {
		SysUser alice = user(2L, "alice", "小艾");
		Approval.StartOutcome started = startLeave(alice, 601L, "biz-approve-1");
		approve(user(3L, "zhangsan", "张三"), started.processId());
		approve(user(4L, "lisi", "李四"), started.processId());

		assertEquals(审批通过, processMapper.selectById(started.processId()).getStatus());
		// 回调发生在动作同一事务内（in_transaction 探针），随动作提交一起落库
		assertEquals(List.of("601|APPROVED"), committedOutcomeLog());
		assertEquals(List.of("true"), committedInTransactionLog());
		assertEquals("APPROVED", stubDocumentStatus(601L));
	}

	@Test
	public void rejectToEndInvokesCallbackRejected() {
		SysUser alice = user(2L, "alice", "小艾");
		Approval.StartOutcome started = startLeave(alice, 602L, "biz-reject-1");
		reject(user(3L, "zhangsan", "张三"), started.processId());

		assertEquals(审批拒绝, processMapper.selectById(started.processId()).getStatus());
		assertEquals("REJECTED", stubDocumentStatus(602L));
	}

	@Test
	public void withdrawInvokesCallbackWithdrawn() {
		SysUser alice = user(2L, "alice", "小艾");
		Approval.StartOutcome started = startLeave(alice, 603L, "biz-withdraw-1");
		approval.withdraw(alice, started.processId());

		assertEquals(已撤回, processMapper.selectById(started.processId()).getStatus());
		assertEquals("WITHDRAWN", stubDocumentStatus(603L));
	}

	@Test
	public void sameValueTerminalIsIdempotentNoOp() {
		SysUser alice = user(2L, "alice", "小艾");
		Approval.StartOutcome started = startLeave(alice, 604L, "biz-idem-1");
		// 桩单据已是 APPROVED（模拟并发对手先提交了同值终态）：本次动作照常完成
		jdbcTemplate.update(
				"INSERT INTO business_approval_stub_doc (business_id, status) VALUES (604, 'APPROVED')");

		approve(user(3L, "zhangsan", "张三"), started.processId());
		approve(user(4L, "lisi", "李四"), started.processId());

		assertEquals(审批通过, processMapper.selectById(started.processId()).getStatus());
		assertEquals("APPROVED", stubDocumentStatus(604L));
		assertEquals(List.of("604|APPROVED"), committedOutcomeLog());
	}

	@Test
	public void differentValueTerminalRollsBackWholeActionIncludingEngine() {
		SysUser alice = user(2L, "alice", "小艾");
		Approval.StartOutcome started = startLeave(alice, 605L, "biz-conflict-1");
		approve(user(3L, "zhangsan", "张三"), started.processId());
		// 桩单据已是 REJECTED（模拟并发对手先写了不同终态）：最后一节点通过时回调抛错
		jdbcTemplate.update(
				"INSERT INTO business_approval_stub_doc (business_id, status) VALUES (605, 'REJECTED')");

		BusinessException rejected = assertThrows(BusinessException.class,
				() -> approve(user(4L, "lisi", "李四"), started.processId()));
		assertEquals(201, rejected.getCode());
		assertTrue(rejected.getMessage().contains("终态冲突"));

		// 整个动作回滚：账本状态不变、回调日志行不存在（同事务证据）、引擎动作也回滚
		assertEquals(审批中, processMapper.selectById(started.processId()).getStatus());
		assertEquals("REJECTED", stubDocumentStatus(605L));
		assertTrue(committedOutcomeLog().isEmpty(),
				"回调日志行必须随动作一起回滚，否则回调就不在动作事务里");
		String instanceId = processMapper.selectById(started.processId()).getProcessInstanceId();
		assertEquals(1, taskService.createTaskQuery().processInstanceId(instanceId).count(),
				"最后一节点的 complete 必须随事务一起回滚，任务仍待处理");
		assertEquals(List.of("发起申请", "已通过"), recordDescriptions(started.processId()));
	}

	@Test
	public void staleWithdrawAgainstSettledDocumentRollsBack() {
		// ADR 0067 的「撤回与通过竞态」顺态版：同单据的另一条审批先通过（APPROVED 落单据），
		// 对旧审批的撤回回调 WITHDRAWN 异值 → 整个撤回动作回滚（含引擎 cancel）。
		SysUser alice = user(2L, "alice", "小艾");
		Approval.StartOutcome stale = startLeave(alice, 606L, "biz-race-1");
		Approval.StartOutcome fresh = startLeave(alice, 606L, "biz-race-2");
		approve(user(3L, "zhangsan", "张三"), fresh.processId());
		approve(user(4L, "lisi", "李四"), fresh.processId());
		assertEquals(审批通过, processMapper.selectById(fresh.processId()).getStatus());

		BusinessException rejected = assertThrows(BusinessException.class,
				() -> approval.withdraw(alice, stale.processId()));
		assertEquals(201, rejected.getCode());

		// 撤回整体回滚：旧审批仍在审批中、引擎实例未取消、单据仍是 APPROVED
		assertEquals(审批中, processMapper.selectById(stale.processId()).getStatus());
		String staleInstanceId = processMapper.selectById(stale.processId()).getProcessInstanceId();
		assertEquals(1, taskService.createTaskQuery().processInstanceId(staleInstanceId).count());
		assertEquals("APPROVED", stubDocumentStatus(606L));
		assertEquals(List.of("606|APPROVED"), committedOutcomeLog());
	}

	@Test
	public void startRejectsWhenIdempotencyKeyMissingBlankOrOverlong() {
		SysUser alice = user(2L, "alice", "小艾");
		assertStartRejected(alice, 701L, null, "提交标识不能为空");
		assertStartRejected(alice, 702L, "   ", "提交标识不能为空");
		assertStartRejected(alice, 703L, "k".repeat(65), "提交标识过长");

		for (long businessId : List.of(701L, 702L, 703L)) {
			assertNull(businessApprovalReader.latestSummary(STUB_TYPE, businessId),
					"201 的含义是「什么都没发生」：" + businessId);
		}
	}

	@Test
	public void startRejectsWhenCallbackNotRegistered() {
		SysUser alice = user(2L, "alice", "小艾");
		BusinessApprovalStart command = new BusinessApprovalStart(
				alice, "test.nocallback", 704L, "biz-nocb-1", "摘要", List.of());

		BusinessException rejected = assertThrows(BusinessException.class,
				() -> businessApprovalStarter.start(command));
		assertEquals(201, rejected.getCode());
		assertEquals("该业务类型未注册审批回调，不能发起", rejected.getMessage());
		assertNull(businessApprovalReader.latestSummary("test.nocallback", 704L));
	}

	@Test
	public void sameKeyReplayReturnsSameApproval() {
		SysUser alice = user(2L, "alice", "小艾");
		Approval.StartOutcome first = startLeave(alice, 705L, "biz-replay-1");
		Approval.StartOutcome replayed = startLeave(alice, 705L, "biz-replay-1");

		assertEquals(first.processId(), replayed.processId());
		Long count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM oa_process WHERE business_type = ? AND business_id = ?",
				Long.class, STUB_TYPE, 705L);
		assertEquals(1L, count);
	}

	@Test
	public void startRejectsWhenMappingMissingOrTemplateUnpublishedOrEvidenceBroken() {
		SysUser alice = user(2L, "alice", "小艾");

		// 映射缺失
		BusinessApprovalStart unmapped = new BusinessApprovalStart(
				alice, STUB_TYPE, 801L, "biz-map-1", "摘要", List.of());
		BusinessException missing = assertThrows(BusinessException.class,
				() -> businessApprovalStarter.start(unmapped));
		assertEquals(201, missing.getCode());
		assertEquals("该业务类型未配置审批模板，不能发起", missing.getMessage());
		assertNull(businessApprovalReader.latestSummary(STUB_TYPE, 801L));

		// 映射指向未发布模板（模板 3 状态 0）
		seedMapping(STUB_TYPE, 3L);
		BusinessApprovalStart unpublished = new BusinessApprovalStart(
				alice, STUB_TYPE, 802L, "biz-map-2", "摘要", List.of());
		BusinessException notPublished = assertThrows(BusinessException.class,
				() -> businessApprovalStarter.start(unpublished));
		assertEquals(201, notPublished.getCode());
		assertEquals("未发布的审批模板不能发起", notPublished.getMessage());
		assertNull(businessApprovalReader.latestSummary(STUB_TYPE, 802L));

		// 发布证据失效（sha256 与存档内容不符）
		seedMapping(STUB_TYPE, 1L);
		jdbcTemplate.update(
				"UPDATE oa_process_template_publication SET artifact_sha256 = ? WHERE process_template_id = 1",
				"0".repeat(64));
		BusinessApprovalStart broken = new BusinessApprovalStart(
				alice, STUB_TYPE, 803L, "biz-map-3", "摘要", List.of());
		BusinessException evidenceBroken = assertThrows(BusinessException.class,
				() -> businessApprovalStarter.start(broken));
		assertEquals(201, evidenceBroken.getCode());
		assertEquals("暂不可发起，需核实原审批内容", evidenceBroken.getMessage());
		assertNull(businessApprovalReader.latestSummary(STUB_TYPE, 803L));
	}

	@Test
	public void latestSummaryReturnsMostRecentApprovalOfTheBusinessDocument() {
		SysUser alice = user(2L, "alice", "小艾");
		SysUser zhangsan = user(3L, "zhangsan", "张三");
		SysUser lisi = user(4L, "lisi", "李四");

		Approval.StartOutcome first = startLeave(alice, 901L, "biz-sum-1");
		Approval.StartOutcome second = startLeave(alice, 901L, "biz-sum-2");
		assertFalse(first.processId().equals(second.processId()));

		// 最近一条即当前真相：回查取 id 最大者
		BusinessApprovalSummary summary = businessApprovalReader.latestSummary(STUB_TYPE, 901L);
		assertNotNull(summary);
		assertEquals(second.processId(), summary.processId());
		assertEquals(审批中, summary.status());
		assertEquals("张三", summary.currentApprover());
		assertNotNull(summary.startTime());
		assertNull(summary.endTime());

		// 最新一条走到终态：endTime 到位、当前审批人清空；旧审批不参与回查
		approve(zhangsan, second.processId());
		approve(lisi, second.processId());
		BusinessApprovalSummary settled = businessApprovalReader.latestSummary(STUB_TYPE, 901L);
		assertEquals(second.processId(), settled.processId());
		assertEquals(审批通过, settled.status());
		assertNull(settled.currentApprover());
		assertNotNull(settled.endTime());

		// 没有审批的业务单 / 缺失入参：null
		assertNull(businessApprovalReader.latestSummary(STUB_TYPE, 999L));
		assertNull(businessApprovalReader.latestSummary(null, 901L));
	}

	// ---------------------------------------------------------------- fixture

	private Approval.StartOutcome startLeave(SysUser alice, Long businessId, String idempotencyKey) {
		seedMappingOnce();
		return businessApprovalStarter.start(new BusinessApprovalStart(
				alice, STUB_TYPE, businessId, idempotencyKey, "3 天事假，5 月 12 日至 14 日", List.of()));
	}

	private void assertStartRejected(SysUser alice, Long businessId, String key, String message) {
		BusinessApprovalStart command = new BusinessApprovalStart(
				alice, STUB_TYPE, businessId, key, "摘要", List.of());
		BusinessException rejected = assertThrows(BusinessException.class,
				() -> businessApprovalStarter.start(command));
		assertEquals(201, rejected.getCode());
		assertEquals(message, rejected.getMessage());
	}

	private void seedMappingOnce() {
		Integer count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM oa_business_template WHERE business_type = ?", Integer.class, STUB_TYPE);
		if (count == null || count == 0) {
			seedMapping(STUB_TYPE, 1L);
		}
	}

	private void seedMapping(String businessType, long templateId) {
		// 同一测试内可能对同一业务类型重种（映射缺失/未发布/证据失效三连）：
		// 先删后插，主键不冲突
		jdbcTemplate.update("DELETE FROM oa_business_template WHERE business_type = ?", businessType);
		jdbcTemplate.update(
				"INSERT INTO oa_business_template (business_type, process_template_id) VALUES (?, ?)",
				businessType, templateId);
	}

	private String stubDocumentStatus(Long businessId) {
		return jdbcTemplate.queryForObject(
				"SELECT status FROM business_approval_stub_doc WHERE business_id = ?",
				String.class, businessId);
	}

	/** 已提交的回调日志（business_id|outcome）：随动作事务提交，回滚后不存在。 */
	private List<String> committedOutcomeLog() {
		return jdbcTemplate.queryForList(
				"SELECT business_id || '|' || outcome FROM business_approval_stub_callback_log ORDER BY id",
				String.class);
	}

	private List<String> committedInTransactionLog() {
		return jdbcTemplate.queryForList(
				"SELECT in_transaction FROM business_approval_stub_callback_log ORDER BY id",
				String.class);
	}

	private List<ProcessVo> startedRows(SysUser user) {
		return approvalListReader.started(new Page<>(1, 10), user).getRecords();
	}

	private List<ProcessVo> instanceQueryRows() {
		return approvalListReader.instanceQuery(new Page<>(1, 10), new ProcessQueryVo(), null)
				.getRecords();
	}

	private void approve(SysUser handler, Long processId) {
		decide(handler, processId, 通过);
	}

	private void reject(SysUser handler, Long processId) {
		decide(handler, processId, 拒绝);
	}

	private void decide(SysUser handler, Long processId, int status) {
		ProcessVo pending = approvalListReader.pending(new Page<>(1, 50), handler)
				.getRecords().stream()
				.filter(row -> row.getId().equals(processId)).findFirst().orElseThrow();
		ApprovalVo approvalVo = new ApprovalVo();
		approvalVo.setProcessId(processId);
		approvalVo.setTaskId(pending.getTaskId());
		approvalVo.setStatus(status);
		approval.approve(handler, approvalVo);
	}

	private List<String> recordDescriptions(Long processId) {
		@SuppressWarnings("unchecked")
		List<ProcessRecord> records =
				(List<ProcessRecord>) approval.show(user(2L, "alice", "小艾"), processId)
						.get("processRecordList");
		return records.stream().map(ProcessRecord::getDescription).toList();
	}

	private static SysUser user(Long id, String username, String name) {
		SysUser user = new SysUser();
		user.setId(id);
		user.setUsername(username);
		user.setName(name);
		return user;
	}

	private static ProcessFormVo publishedLeaveForm() {
		ProcessFormVo form = new ProcessFormVo();
		form.setProcessTemplateId(1L);
		form.setProcessTypeId(1L);
		form.setFormValues("{\"formData\":{\"days\":1},\"formShowData\":{\"请假天数\":1}}");
		return form;
	}

	private void clearEngineInstances() {
		runtimeService.createProcessInstanceQuery().list()
				.forEach(instance -> runtimeService.deleteProcessInstance(instance.getId(), "contract-reset"));
		historyService.createHistoricProcessInstanceQuery().list()
				.forEach(instance -> historyService.deleteHistoricProcessInstance(instance.getId()));
	}

	private static byte[] approvalTwoStepZip() throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (InputStream bpmn = BusinessApprovalContractTest.class.getClassLoader()
				.getResourceAsStream("process/approval-two-step.bpmn20.xml");
				ZipOutputStream zip = new ZipOutputStream(bytes)) {
			if (bpmn == null) throw new IOException("审批合同 BPMN 不存在");
			zip.putNextEntry(new ZipEntry("approval-two-step.bpmn20.xml"));
			bpmn.transferTo(zip);
			zip.closeEntry();
		}
		return bytes.toByteArray();
	}

	/**
	 * 桩业务域回调（#289 合同测试专用，只在本测试上下文注册）：businessType=test.leave
	 * （值域 {@code <域>.<单据>}），单据状态落 business_approval_stub_doc。幂等契约与
	 * 生产实现同形：同值终态 no-op、异值抛错（整个动作回滚）。每次回调先落一行日志
	 * （含事务探针），用于断言「回调与审批动作同事务」。
	 */
	public static class StubLeaveCallback implements BusinessApprovalCallback {

		static final String BUSINESS_TYPE = "test.leave";

		private final JdbcTemplate jdbcTemplate;

		public StubLeaveCallback(JdbcTemplate jdbcTemplate) {
			this.jdbcTemplate = jdbcTemplate;
		}

		@Override
		public String businessType() {
			return BUSINESS_TYPE;
		}

		@Override
		public void onTerminal(Long businessId, BusinessApprovalOutcome outcome) {
			jdbcTemplate.update(
					"INSERT INTO business_approval_stub_callback_log (business_id, outcome, in_transaction) VALUES (?, ?, ?)",
					businessId, outcome.name(),
					String.valueOf(TransactionSynchronizationManager.isActualTransactionActive()));
			List<String> current = jdbcTemplate.queryForList(
					"SELECT status FROM business_approval_stub_doc WHERE business_id = ?",
					String.class, businessId);
			if (current.isEmpty()) {
				jdbcTemplate.update(
						"INSERT INTO business_approval_stub_doc (business_id, status, update_time) VALUES (?, ?, CURRENT_TIMESTAMP)",
						businessId, outcome.name());
				return;
			}
			if (outcome.name().equals(current.get(0))) {
				return; // 同值终态 no-op 成功
			}
			throw new BusinessException(201,
					"单据终态冲突：" + current.get(0) + " -> " + outcome.name());
		}
	}

	@TestConfiguration
	static class BusinessStubConfig {

		@Bean
		public StubLeaveCallback stubLeaveCallback(JdbcTemplate jdbcTemplate) {
			return new StubLeaveCallback(jdbcTemplate);
		}

		@Bean
		@Primary
		public TestAttachmentObjectStore testAttachmentObjectStore() {
			return new TestAttachmentObjectStore();
		}

		@Bean
		@Primary
		public RedisTemplate<String, String> redisTemplate() {
			return new InMemoryRedisTemplate();
		}
	}
}
