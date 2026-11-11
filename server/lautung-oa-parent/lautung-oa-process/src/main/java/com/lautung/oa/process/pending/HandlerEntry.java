package com.lautung.oa.process.pending;

import java.util.Date;

/**
 * 待处理名单上的一个处理人事实（#297，ADR 0076）：引擎任务的 assignee/候选人与未决加签
 * 任务的加签人。只描述事实，不解析姓名；加签条目的 {@code username} 可空、靠 {@code userId}
 * 标识。{@code afterExcluded} 表示该引擎任务上有后加签批次（原处理人已记账通过，待处理名单
 * 不再收他），{@code suspended} 表示有前加签批次（行保留但不可动作）。
 */
public record HandlerEntry(
        String username,
        Long userId,
        Source source,
        String engineTaskId,
        String signTaskId,
        boolean suspended,
        boolean afterExcluded,
        Date createTime) {

    public enum Source {
        ENGINE_ASSIGNEE,
        ENGINE_CANDIDATE,
        ADD_SIGN
    }
}
