package com.lautung.oa.process.mapper;

import com.lautung.oa.model.process.ProcessAddSignTask;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 加签任务 Mapper（ADR 0068，#288）。行形状只有本表自己的列（不 join 审批行），
 * 待处理第三源的分页与计数收在这里；审批行回查仍走 {@link OaProcessMapper#selectRowsByIds}。
 */
public interface OaProcessAddSignTaskMapper extends BaseMapper<ProcessAddSignTask> {

    /** 待处理第三源：该用户的未决加签任务，按创建时间倒序、同刻由 id 决胜；原生 LIMIT/OFFSET（#122 口径）。 */
    List<ProcessAddSignTask> selectPendingForUser(@Param("assigneeId") Long assigneeId,
                                                  @Param("offset") long offset,
                                                  @Param("limit") long limit);

    /** 待处理第三源计数：PENDING 加签任务数（每人每审批一行，同一审批至多一个未决批次）。 */
    long countPendingForUser(@Param("assigneeId") Long assigneeId);

    /** 挂起/批次判定：这些引擎任务上的未决加签任务（含类型），空集合返回空列表。 */
    List<ProcessAddSignTask> selectPendingByEngineTaskIds(@Param("engineTaskIds") List<String> engineTaskIds);

    /** total 收口例外（#288）：指定类型的全部未决批次（活跃批次是少量行，用于后加签排除计数）。 */
    List<ProcessAddSignTask> selectPendingByType(@Param("signType") String signType);
}
