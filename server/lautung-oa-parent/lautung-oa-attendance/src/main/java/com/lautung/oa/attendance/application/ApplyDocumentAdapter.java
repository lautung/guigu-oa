package com.lautung.oa.attendance.application;

import com.lautung.oa.model.attendance.AttendanceApplyStatus;
import com.lautung.oa.vo.process.BusinessApprovalSummary;

import java.util.Date;

/**
 * 每单据一个的适配器（#294，ADR 0075）：持久化与实体映射、提交前领域校验、终态
 * 副作用、摘要文案与 VO 投影。生命周期 module 只经本端口调用，不 import 任何具体
 * 单据实体或 VO 类型——泛型参数由实现方钉死，module 不认识它们。
 *
 * <p>契约：
 * <ul>
 *   <li>所有 CAS 方法返回影响行数；调用方（module）负责把 0 行译成 201 文案。</li>
 *   <li>{@link #load} 只按 id 读，不做本人判定——本人判定在 module 的 {@code loadOwn}。</li>
 *   <li>{@link #prepareSubmit} 在提交事务之外执行，允许读到旧快照：真正仲裁的是
 *       {@link #casMarkSubmitted} 的状态 CAS。</li>
 *   <li>{@link #casMarkTerminal} 与 {@link #settleApproved} 由审批动作的事务调用，
 *       必须与回写同事务、不要自己开新事务（ADR 0067）。</li>
 * </ul>
 *
 * @param <D> 适配器自己的持久化载体（实体）
 * @param <S> 提交快照载荷（提交时要写回的领域字段）
 * @param <V> 详情投影
 */
public interface ApplyDocumentAdapter<D, S, V> {

    /** 业务类型（值域 {@code <域>.<单据>}，与审批回调注册同一常量）。 */
    String businessType();

    /** 单据名（错误文案前缀，如「请假单」）。 */
    String label();

    /** 按 id 读数并把库内状态映射成枚举；不存在返回 {@code null}。 */
    DocumentSnapshot<D> load(Long id);

    /** 插入草稿并返回新单据 id。 */
    Long insertDraft(D draft);

    /** 草稿/拒绝/撤回的 CAS 更新；返回影响行数。 */
    int casUpdateDraft(Long applicantId, Long id, D draft);

    /** 草稿/拒绝/撤回的 CAS 删除；返回影响行数。 */
    int casDeleteDraft(Long applicantId, Long id);

    /** 提交前领域校验与快照；不可提交时按 201 命名拒绝。 */
    SubmitPlan<S> prepareSubmit(DocumentSnapshot<D> document);

    /** 审批中的 CAS：写入提交快照与提交时刻；返回影响行数。 */
    int casMarkSubmitted(Long applicantId, Long id, S snapshot, Date submitTime);

    /** 终态的 CAS：审批中 → {@code target}，写终态时刻；返回影响行数。 */
    int casMarkTerminal(Long id, AttendanceApplyStatus target, Date finishTime);

    /** 通过后的域副作用（请假扣减 / 加班入账），与终态同一事务。 */
    void settleApproved(Long id);

    /** 详情投影（只读本人，不含审批摘要）。 */
    V detail(Long applicantId, Long id);

    /** 把最近一条审批摘要挂到投影上。 */
    void bindApproval(V projection, BusinessApprovalSummary summary);
}
