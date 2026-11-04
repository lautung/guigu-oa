package com.lautung.oa.config.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.lautung.oa.model.system.SysConfig;

/**
 * 参数服务（#252）：CRUD、内置项保护、缓存写后失效与手动刷新。
 *
 * <p>保护判定按库里的 {@code builtin} 行做，不信任请求体：内置项不可删、不可改 key，
 * 只允许改 value（名称/类型/备注同样不许改）。
 */
public interface SysConfigService extends IService<SysConfig> {

    /** 新增参数；键重复按 201 拒绝，值类型做轻校验，builtin 固定为 0。 */
    void saveConfig(SysConfig config);

    /** 更新参数；行不存在与内置项改 key/元数据都按 201 拒绝。 */
    void updateConfig(SysConfig config);

    /** 删除参数；内置项拒绝。软删前让出 config_key，允许同一键重新创建。 */
    void removeConfig(Long id);

    /** 清掉全部 {@code config:} 缓存键，返回实际删除数；下次读从库里重建。 */
    long refreshCache();
}
