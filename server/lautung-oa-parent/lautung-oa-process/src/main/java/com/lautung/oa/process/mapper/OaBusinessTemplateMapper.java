package com.lautung.oa.process.mapper;

import com.lautung.oa.model.process.OaBusinessTemplate;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * 业务类型 → 审批模板映射（ADR 0067，#289）。读侧只按业务类型主键取行；
 * 映射行的写入随各域票种子，本票没有管理面。
 */
public interface OaBusinessTemplateMapper extends BaseMapper<OaBusinessTemplate> {
}
