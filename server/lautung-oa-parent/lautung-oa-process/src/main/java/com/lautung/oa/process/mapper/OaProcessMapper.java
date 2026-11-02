package com.lautung.oa.process.mapper;

import com.lautung.oa.auth.datascope.DataScope;
import com.lautung.oa.model.process.Process;
import com.lautung.oa.vo.process.ProcessQueryVo;
import com.lautung.oa.vo.process.ProcessVo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 审批账本 Mapper。行形状唯一 owner 是同目录 xml 里的共享列清单（#181）：
 * 四条行取数（已发起/配置查询、待处理回查、抄送我的行、已处理行）全部引用同一份列与 join；
 * 两个计数（{@link #countCarbonCopiedRows}、{@link #countProcessedRows}）是 count 聚合，不引用共享列清单。
 */
public interface OaProcessMapper extends BaseMapper<Process> {

    //审批管理列表与已发起列表共用（勿命名为 selectPage，会与 BaseMapper 分页方法冲突导致 Parameter 'ew' not found）
    //scope 只由配置实例查询传入（ADR 0057）：部门列 = 发起人部门 d.dept_id，本人列 = 发起人 a.user_id；
    //已发起传 null，保持办事列表不带数据范围。
    IPage<ProcessVo> selectProcessPage(Page<ProcessVo> pageParam,
                                       @Param("vo") ProcessQueryVo processQueryVo,
                                       @Param("scope") DataScope scope);

    //待处理回查：按 businessKey 集合批量取行，服务层按引擎顺序重排
    List<ProcessVo> selectRowsByIds(@Param("ids") List<Long> ids);

    //已处理计数：total=去重审批数（同一审批处理过多个节点只计一次，「发起申请」流转记录不算处理）
    long countProcessedRows(@Param("userId") Long userId,
                            @Param("excludeDescription") String excludeDescription);

    //已处理行：处理动作记录按审批去重，原生 LIMIT/OFFSET 分页（#122 口径），最近一次处理在前
    List<ProcessVo> selectProcessedRows(@Param("userId") Long userId,
                                        @Param("excludeDescription") String excludeDescription,
                                        @Param("offset") long offset,
                                        @Param("limit") long limit);

    //抄送我的计数：total=去重审批数（DISTINCT 语义）
    long countCarbonCopiedRows(@Param("userId") Long userId);

    //抄送我的行：原生 LIMIT/OFFSET 分页（不走分页插件，#122 口径）
    List<ProcessVo> selectCarbonCopiedRows(@Param("userId") Long userId,
                                           @Param("offset") long offset,
                                           @Param("limit") long limit);
}
