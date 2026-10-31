package com.atguigu.process.mapper;

import com.atguigu.model.process.Process;
import com.atguigu.vo.process.ProcessQueryVo;
import com.atguigu.vo.process.ProcessVo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 审批账本 Mapper。行形状唯一 owner 是同目录 xml 里的共享列清单（#181）：
 * 五种取数（已发起/配置查询、待处理回查、已处理回查、抄送我的）全部引用同一份列与 join。
 */
public interface OaProcessMapper extends BaseMapper<Process> {

    //审批管理列表与已发起列表共用（勿命名为 selectPage，会与 BaseMapper 分页方法冲突导致 Parameter 'ew' not found）
    IPage<ProcessVo> selectProcessPage(Page<ProcessVo> pageParam, @Param("vo") ProcessQueryVo processQueryVo);

    //待处理回查：按 businessKey 集合批量取行，服务层按引擎顺序重排
    List<ProcessVo> selectRowsByIds(@Param("ids") List<Long> ids);

    //已处理回查：按引擎历史任务的实例 id 集合批量取行，服务层按引擎顺序重排
    List<ProcessVo> selectRowsByInstanceIds(@Param("instanceIds") List<String> instanceIds);

    //抄送我的计数：total=去重审批数（DISTINCT 语义）
    long countCarbonCopiedRows(@Param("userId") Long userId);

    //抄送我的行：原生 LIMIT/OFFSET 分页（不走分页插件，#122 口径）
    List<ProcessVo> selectCarbonCopiedRows(@Param("userId") Long userId,
                                           @Param("offset") long offset,
                                           @Param("limit") long limit);
}
