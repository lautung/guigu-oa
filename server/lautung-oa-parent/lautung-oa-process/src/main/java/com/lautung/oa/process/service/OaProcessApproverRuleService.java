package com.lautung.oa.process.service;

import com.lautung.oa.model.process.ProcessApproverRule;
import com.lautung.oa.model.process.ProcessTemplate;
import com.lautung.oa.process.publication.BpmnUserTasks;
import com.lautung.oa.vo.process.ApproverRuleRequest;

import java.util.List;

/**
 * 审批人规则（ADR 0069，#285）：未发布可改（整体替换），发布时随产物冻结。
 * 本 service 拥有规则行的读写与两条校验——保存校验与发布校验（每节点至少一个来源、
 * 规则目标存在且启用），全部 201 命名拒绝。
 */
public interface OaProcessApproverRuleService {

    /** 模板的当前规则行（配置页回显）。 */
    List<ProcessApproverRule> findRules(Long templateId);

    /**
     * 模板上传 zip 的 BPMN userTask 清单（配置页节点来源；BPMN 解析不出时为空列表，
     * 允许保存、发布时拒绝）。
     */
    List<BpmnUserTasks.UserTaskRef> findUserTasks(ProcessTemplate template);

    /** 配置页一次读取：节点清单 + 当前规则。 */
    RuleConfig readConfig(Long templateId);

    /** 整体替换未发布模板的规则；请求为空列表即清空。校验失败 201，不落半份规则。 */
    void replaceRules(Long templateId, List<ApproverRuleRequest> requests);

    /**
     * 发布校验（在 publish 里、部署之前调用）：每个 userTask 至少一个来源
     * （规则或 BPMN assignee）、每条规则目标存在且启用、规则节点在流程里存在。
     */
    void validateForPublish(List<BpmnUserTasks.UserTaskRef> userTasks, List<ProcessApproverRule> rules);

    /** 删除未发布模板时清掉它的规则行（硬删）。 */
    void removeAll(Long templateId);

    /** 配置页读取结果：节点清单 + 当前规则。 */
    record RuleConfig(List<BpmnUserTasks.UserTaskRef> userTasks, List<ProcessApproverRule> rules) {
    }
}
