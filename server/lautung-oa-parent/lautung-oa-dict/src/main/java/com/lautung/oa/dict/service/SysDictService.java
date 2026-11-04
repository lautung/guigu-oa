package com.lautung.oa.dict.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.IService;
import com.lautung.oa.model.system.SysDictData;
import com.lautung.oa.model.system.SysDictType;
import com.lautung.oa.vo.system.DictDataVo;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 字典服务（#251，ADR 0062）：两级 CRUD、删除保护、{@code dict:} 缓存与手动刷新。
 *
 * <p>字典只存值与标签，不做外键校验；删除类型不级联删数据。缓存按类型存启用的数据行，
 * 写动作立即失效受影响类型，手动刷新清掉全部 {@code dict:} 键。
 */
public interface SysDictService extends IService<SysDictType> {

    /** 新增类型；名称或编码重复按 201 拒绝。 */
    void saveType(SysDictType type);

    /** 更新类型；行不存在、名称/编码重复按 201 拒绝，旧/新编码的缓存都失效。 */
    void updateType(SysDictType type);

    /** 删除类型；类型下仍有数据行时按 201 拒绝。 */
    void removeType(Long id);

    /** 字典数据分页读（管理页右栏）：条件由控制器拼，Mapper 只在服务端。 */
    IPage<SysDictData> pageData(IPage<SysDictData> page, Wrapper<SysDictData> queryWrapper);

    /** 新增数据；同一类型下值重复按 201 拒绝。 */
    void saveData(SysDictData data);

    /** 更新数据；行不存在、同类型下值重复按 201 拒绝。 */
    void updateData(SysDictData data);

    /** 删除数据；只失效所属类型缓存。 */
    void removeData(Long id);

    /** 某类型下启用的数据行，按 {@code sort_value}、id 升序；走 {@code dict:} 缓存。 */
    List<DictDataVo> findEnabledData(String dictType);

    /** 批量取多组字典（客户端一次请求），键为类型编码；未定义的类型返回空列表。 */
    Map<String, List<DictDataVo>> findEnabledData(Collection<String> dictTypes);

    /** 清掉全部 {@code dict:} 缓存键，返回实际删除数；下次读从库里重建。 */
    long refreshCache();
}
