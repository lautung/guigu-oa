package com.lautung.oa.auth.service;

import com.lautung.oa.model.system.SysRole;
import com.lautung.oa.vo.system.AssginRoleVo;
import com.lautung.oa.vo.system.SysDeptOptionVo;
import com.lautung.oa.vo.system.SysRoleDetailVo;
import com.baomidou.mybatisplus.spring.service.IService;

import java.util.List;
import java.util.Map;

public interface SysRoleService extends IService<SysRole> {

    //1 查询所有角色 和 当前用户所属角色
    Map<String, Object> findRoleDataByUserId(Long userId);

    //2 为用户分配角色
    void doAssign(AssginRoleVo assginRoleVo);

    //3 用户被授予的角色 key（/info 展示用，不参与判定）
    List<String> findRoleCodesByUserId(Long userId);

    /**
     * 新建角色并同步自定部门（同一事务）。dataScope=2 时 deptIds 不能为空；
     * dataScope 由 {@code toEntity()} 落成默认 5。
     */
    void saveRole(SysRole role, List<Long> deptIds);

    /**
     * 更新角色并同步自定部门（同一事务）。字段为空不覆盖（ADR 0034）：
     * 最终范围为 2 且 deptIds 非空时替换部门行；范围为非 2 时清空部门行。
     */
    void updateRole(SysRole role, List<Long> deptIds);

    /** 角色编辑回读：实体字段 + 自定部门id列表。 */
    SysRoleDetailVo findDetail(Long id);

    /** 角色数据范围的自定部门选项：启用且未删除，按 parent/sort/id 排序。 */
    List<SysDeptOptionVo> findDeptOptions();
}
