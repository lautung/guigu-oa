package com.lautung.oa.process.read;

import com.lautung.oa.model.system.DataScope;
import com.lautung.oa.model.system.SysUser;
import com.lautung.oa.vo.process.ProcessQueryVo;
import com.lautung.oa.vo.process.ProcessVo;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 五种审批列表的读 module（#181）：行形状、窗口算术、回查批量化、排序在这里一处拥有。
 * 行形状唯一 owner 是 OaProcessMapper.xml 的共享列清单，{@code ProcessVo} 是唯一行类型；
 * 五个方法参数强类型，不设 kind 字符串。本 interface 只读，不写引擎与账本。
 */
public interface ApprovalListReader {

    /** 待处理：当前轮到该用户处理的审批，按引擎任务创建时间倒序（同刻由任务 id 决胜）。 */
    IPage<ProcessVo> pending(Page<ProcessVo> page, SysUser user);

    /** 已处理：该用户已经处理过的审批（GLOSSARY 口径）；同一审批处理过多个节点只出一行，total=去重审批数，
     * 按最近一次处理时间倒序、同刻由审批 id 决胜（#186）。 */
    IPage<ProcessVo> processed(Page<ProcessVo> page, SysUser user);

    /** 已发起：该用户发起的审批，按创建时间倒序、同刻由审批 id 决胜（#179）。 */
    IPage<ProcessVo> started(Page<ProcessVo> page, SysUser user);

    /** 抄送我的：送该用户知情的审批，按创建时间倒序；total=去重审批数，同一审批多种来源只计一次。 */
    IPage<ProcessVo> carbonCopied(Page<ProcessVo> page, SysUser user);

    /** 配置实例查询：按关键字/状态/时间范围过滤的审批，按创建时间倒序、同刻由审批 id 决胜（#179）。
     * {@code scope} 按发起人部门/发起人过滤（ADR 0053/0057）；办事四种列表不带数据范围。 */
    IPage<ProcessVo> instanceQuery(Page<ProcessVo> page, ProcessQueryVo query, DataScope scope);
}
