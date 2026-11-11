package com.lautung.oa.attendance.application;

import com.lautung.oa.attendance.mapper.AttendanceUserMapper;
import com.lautung.oa.framework.config.exception.BusinessException;
import com.lautung.oa.model.attendance.AttendanceApplyStatus;
import com.lautung.oa.model.system.SysUser;
import com.lautung.oa.process.business.BusinessApprovalOutcome;
import com.lautung.oa.process.business.BusinessApprovalReader;
import com.lautung.oa.process.business.BusinessApprovalStarter;
import com.lautung.oa.vo.attendance.AttendanceApplyQueryVo;
import com.lautung.oa.vo.process.BusinessApprovalStart;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;

/**
 * 业务单据生命周期 module（#294，ADR 0075）：一张单据从草稿到审批终态的共同过程。
 *
 * <p>module 拥有草稿编排（加载本人、可编辑判定、CAS 更新/删除的顺序与文案）、提交编排
 * （领域校验 → 定稿快照 → 状态 CAS → 起审批）、同键重放识别、审批摘要绑定、终态回写
 * 仲裁（同值 no-op、异值 201、CAS 0 行重读）、提交的事务边界与冲突重读，以及审批结果
 * 到单据状态的映射。领域差异全部经 {@link ApplyDocumentAdapter} 交回适配器。
 *
 * <p>提交用程序化事务包住「状态 CAS + 起审批」：唯一索引冲突时该事务已回滚，随后在
 * <em>事务外</em>按提交标识重读——只有同一单据的同一在途提交才返回原审批，其余按 201
 * 命名拒绝。事务边界归 module 自己（{@code REQUIRES_NEW}）：无论调用方有没有事务，
 * 冲突重读都落在新事务里，才看得见对手已提交的审批（ADR 0032/0075）。
 */
@Service
public class DocumentLifecycle {

    private final BusinessApprovalStarter approvalStarter;

    private final BusinessApprovalReader approvalReader;

    private final AttendanceUserMapper userMapper;

    private final TransactionTemplate transactions;

    public DocumentLifecycle(BusinessApprovalStarter approvalStarter,
                             BusinessApprovalReader approvalReader,
                             AttendanceUserMapper userMapper,
                             PlatformTransactionManager transactionManager) {
        this.approvalStarter = approvalStarter;
        this.approvalReader = approvalReader;
        this.userMapper = userMapper;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** 列表筛选的档位校验（缺省不过滤；未知档位 201）。 */
    public void validateStatusFilter(AttendanceApplyQueryVo query) {
        if (query != null && query.getStatus() != null
                && AttendanceApplyStatus.fromCode(query.getStatus()) == null) {
            throw new BusinessException(201, "单据状态不合法");
        }
    }

    /** 详情投影 + 最近一条审批摘要绑定（草稿无审批时为 null）。 */
    public <D, S, V> V detail(ApplyDocumentAdapter<D, S, V> adapter, Long applicantId, Long id) {
        V projection = adapter.detail(applicantId, id);
        adapter.bindApproval(projection, approvalReader.latestSummary(adapter.businessType(), id));
        return projection;
    }

    /** 新建草稿：插入后按详情投影返回。请求形状与领域校验在调用方（适配器）。 */
    public <D, S, V> V saveDraft(ApplyDocumentAdapter<D, S, V> adapter, Long applicantId, D draft) {
        Long id = adapter.insertDraft(draft);
        return detail(adapter, applicantId, id);
    }

    /** 修改草稿：加载本人 → 可编辑判定 → CAS 更新（顺序与文案的唯一 owner）。 */
    public <D, S, V> V updateDraft(ApplyDocumentAdapter<D, S, V> adapter, Long applicantId,
                                   Long id, D draft) {
        DocumentSnapshot<D> document = loadOwn(adapter, applicantId, id);
        requireEditable(adapter, document.status());
        requireCas(adapter.casUpdateDraft(applicantId, id, draft));
        return detail(adapter, applicantId, id);
    }

    /** 删除草稿：加载本人 → 可编辑判定 → CAS 删除。 */
    public <D, S, V> void removeDraft(ApplyDocumentAdapter<D, S, V> adapter, Long applicantId, Long id) {
        DocumentSnapshot<D> document = loadOwn(adapter, applicantId, id);
        requireEditable(adapter, document.status());
        requireCas(adapter.casDeleteDraft(applicantId, id));
    }

    /**
     * 提交：同一状态 CAS 与起审批在一个事务里；唯一索引冲突在事务回滚后、于事务外
     * 重读同一提交标识。同键重放返回原审批，其余 201 命名拒绝，单据保持提交前状态。
     */
    public <D, S, V> V submit(ApplyDocumentAdapter<D, S, V> adapter, Long applicantId, Long id,
                              String rawIdempotencyKey) {
        String idempotencyKey = requireIdempotencyKey(rawIdempotencyKey);
        DocumentSnapshot<D> document = loadOwn(adapter, applicantId, id);
        if (document.status() == AttendanceApplyStatus.SUBMITTED) {
            if (approvalReader.inFlightSubmissionProcessId(
                    adapter.businessType(), id, applicantId, idempotencyKey) != null) {
                return detail(adapter, applicantId, id);
            }
            throw new BusinessException(201, "单据已在审批中");
        }
        if (document.status() == null || !document.status().isSubmittable()) {
            throw new BusinessException(201, "单据已通过，不能重新提交");
        }

        SubmitPlan<S> plan = adapter.prepareSubmit(document);
        SysUser applicant = requireApplicant(applicantId);
        BusinessApprovalStart command = new BusinessApprovalStart(
                applicant, adapter.businessType(), id, idempotencyKey, plan.summaryText(), List.of());
        try {
            transactions.executeWithoutResult(status -> {
                requireCas(adapter.casMarkSubmitted(applicantId, id, plan.snapshot(), new Date()));
                approvalStarter.start(command);
            });
        } catch (DuplicateKeyException conflict) {
            // 事务已回滚：单据回到提交前状态。重读同一提交标识必须落在事务外的新事务里，
            // 才能看见唯一索引挡住的那次提交（ADR 0032/0075）。
            if (approvalReader.inFlightSubmissionProcessId(
                    adapter.businessType(), id, applicantId, idempotencyKey) != null) {
                return detail(adapter, applicantId, id);
            }
            throw new BusinessException(201, "提交标识已被使用");
        }
        return detail(adapter, applicantId, id);
    }

    /**
     * 审批终态回写仲裁：同值 no-op 成功、异值 201 回滚；审批中 → 终态的 CAS 0 行时重读，
     * 后到者若已是同一终态则 no-op。通过时在 CAS 之后调用适配器的域副作用，同一事务。
     */
    public <D, S, V> void settle(ApplyDocumentAdapter<D, S, V> adapter, Long businessId,
                                 BusinessApprovalOutcome outcome) {
        if (businessId == null || outcome == null) {
            throw new BusinessException(201, adapter.label() + "终态回写参数不完整");
        }
        DocumentSnapshot<D> document = adapter.load(businessId);
        if (document == null) {
            throw new BusinessException(201, adapter.label() + "不存在");
        }
        AttendanceApplyStatus target = terminalStatusOf(outcome);
        if (target == document.status()) {
            return;
        }
        if (document.status() != AttendanceApplyStatus.SUBMITTED) {
            throw new BusinessException(201, adapter.label() + "终态冲突");
        }

        int updated = adapter.casMarkTerminal(businessId, target, new Date());
        if (updated == 0) {
            DocumentSnapshot<D> current = adapter.load(businessId);
            if (current != null && current.status() == target) {
                return;
            }
            throw new BusinessException(201, adapter.label() + "终态冲突");
        }
        if (outcome == BusinessApprovalOutcome.APPROVED) {
            adapter.settleApproved(businessId);
        }
    }

    /** 审批结果 → 单据终态的映射唯一 owner（回调不再各写一份 switch）。 */
    private static AttendanceApplyStatus terminalStatusOf(BusinessApprovalOutcome outcome) {
        return switch (outcome) {
            case APPROVED -> AttendanceApplyStatus.APPROVED;
            case REJECTED -> AttendanceApplyStatus.REJECTED;
            case WITHDRAWN -> AttendanceApplyStatus.WITHDRAWN;
        };
    }

    /** 加载本人单据：不存在或非本人一律「<单据名>不存在」。 */
    private <D, S, V> DocumentSnapshot<D> loadOwn(ApplyDocumentAdapter<D, S, V> adapter,
                                                  Long applicantId, Long id) {
        DocumentSnapshot<D> document = adapter.load(id);
        if (document == null || !applicantId.equals(document.applicantId())) {
            throw new BusinessException(201, adapter.label() + "不存在");
        }
        return document;
    }

    private static void requireEditable(ApplyDocumentAdapter<?, ?, ?> adapter,
                                        AttendanceApplyStatus status) {
        if (status == null || !status.isEditable()) {
            throw new BusinessException(201, adapter.label() + "当前状态不能修改或删除");
        }
    }

    private static void requireCas(int updated) {
        if (updated == 0) {
            throw new BusinessException(201, "单据状态已变化，请刷新后重试");
        }
    }

    private static String requireIdempotencyKey(String rawIdempotencyKey) {
        if (rawIdempotencyKey == null || rawIdempotencyKey.trim().isEmpty()) {
            throw new BusinessException(201, "提交标识不能为空");
        }
        // 长度上限由 BusinessApprovalStarter 校验（W1 端口唯一 owner）；超长在发起时
        // 201 回滚整笔提交，单据保持草稿。
        return rawIdempotencyKey.trim();
    }

    /** 申请人回查（发起人快照）；姓名缺失回退用户名。 */
    private SysUser requireApplicant(Long applicantId) {
        SysUser applicant = userMapper.selectById(applicantId);
        if (applicant == null) {
            throw new BusinessException(201, "申请人不存在");
        }
        if (!StringUtils.hasText(applicant.getName())) {
            applicant.setName(applicant.getUsername());
        }
        return applicant;
    }
}
