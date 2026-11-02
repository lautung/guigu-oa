package com.lautung.oa.security.datascope;

import com.lautung.oa.model.system.DataScope;

/**
 * 数据范围的只读端口（ADR 0057）：解析归 auth，消费方只拿值对象。
 *
 * <p>为什么在 security：audit 只允许依赖 common/framework/security/model，不能反向依赖
 * auth；把端口放 security 后，日志这些消费方按接口取范围，实现仍由
 * {@code com.lautung.oa.auth.datascope.DataScopeResolver} 供给（启动时由装配模块拼起来）。
 * 端口不猜业务列：部门列与本人列由每个查询自己回答。
 */
public interface DataScopeProvider {

    /**
     * 解析该用户可见范围。未认证（{@code userId == null}）按零行处理；调用方仍需先过认证与 gate。
     */
    DataScope resolve(Long userId);
}
