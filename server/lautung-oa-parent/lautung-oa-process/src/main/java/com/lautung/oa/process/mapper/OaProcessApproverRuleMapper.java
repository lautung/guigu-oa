package com.lautung.oa.process.mapper;

import com.lautung.oa.model.process.ProcessApproverRule;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface OaProcessApproverRuleMapper extends BaseMapper<ProcessApproverRule> {

    /**
     * 规则保存是「整体替换」：硬删旧行再插入新行，已删行不再占
     * {@code uk_approver_rule_node} 的唯一键位（软删会让同节点重存撞唯一索引）。
     * 规则只属于未发布模板（配置数据，不是发布证据），硬删不丢任何可核实凭据。
     */
    @Delete("DELETE FROM oa_process_approver_rule WHERE process_template_id = #{templateId}")
    int deleteByTemplateId(@Param("templateId") Long templateId);
}
