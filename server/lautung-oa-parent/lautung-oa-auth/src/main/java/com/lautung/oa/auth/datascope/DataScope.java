package com.lautung.oa.auth.datascope;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 数据范围解析结果（ADR 0057）：一行可见性由「部门集合 ∪ 本人」表达，且与「全部可见」互不相容。
 *
 * <p>语义（由四个接入查询各自落到自己的列上）：
 * <ul>
 *   <li>{@code unrestricted} → 不加任何条件；</li>
 *   <li>{@code deptIds} 非空 → 部门列 {@code in (...)}；</li>
 *   <li>{@code selfUserId} 非空 → 再 {@code OR 本人列 = ?}；</li>
 *   <li>两者都空且不是 {@code unrestricted} → 零行，<b>不是全部</b>。</li>
 * </ul>
 *
 * <p>不可变；getter 用普通 JavaBean 形状（{@code isUnrestricted}/{@code getDeptIds}/
 * {@code getSelfUserId}），让 MyBatis XML 的 OGNL 能直接读属性。
 */
public final class DataScope {

    private final boolean unrestricted;
    private final Set<Long> deptIds;
    private final Long selfUserId;

    private DataScope(boolean unrestricted, Set<Long> deptIds, Long selfUserId) {
        this.unrestricted = unrestricted;
        this.deptIds = deptIds;
        this.selfUserId = selfUserId;
    }

    /** 全部可见：不加条件。 */
    public static DataScope unrestricted() {
        return new DataScope(true, Set.of(), null);
    }

    /**
     * 部门集合 ∪ 本人行。空集 + null 本人 = 零行（不是全部）；null 集合按空集处理。
     */
    public static DataScope of(Set<Long> deptIds, Long selfUserId) {
        Set<Long> copy = new LinkedHashSet<>();
        if (deptIds != null) {
            for (Long deptId : deptIds) {
                if (deptId != null) {
                    copy.add(deptId);
                }
            }
        }
        Set<Long> immutable = Collections.unmodifiableSet(copy);
        return new DataScope(false, immutable.isEmpty() ? Set.of() : immutable, selfUserId);
    }

    public boolean isUnrestricted() {
        return unrestricted;
    }

    public Set<Long> getDeptIds() {
        return deptIds;
    }

    public Long getSelfUserId() {
        return selfUserId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DataScope that)) {
            return false;
        }
        return unrestricted == that.unrestricted
                && deptIds.equals(that.deptIds)
                && Objects.equals(selfUserId, that.selfUserId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(unrestricted, deptIds, selfUserId);
    }

    @Override
    public String toString() {
        return "DataScope{unrestricted=" + unrestricted
                + ", deptIds=" + deptIds
                + ", selfUserId=" + selfUserId + '}';
    }
}
