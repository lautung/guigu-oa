package com.lautung.oa.auth.service;

import com.lautung.oa.model.system.SysRole;
import com.lautung.oa.vo.system.AssginRoleVo;
import com.lautung.oa.vo.system.SysDeptOptionVo;
import com.lautung.oa.vo.system.SysRoleDetailVo;
import com.lautung.oa.vo.system.SysRoleUserVo;
import com.baomidou.mybatisplus.core.metadata.IPage;
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

    /**
     * 角色侧成员分页（#256）。{@code assigned} 为 true 只看成员、false 只看非成员、null 不过滤；
     * 角色是全局配置，不套数据范围。关键字匹配用户名/姓名/手机号。
     */
    IPage<SysRoleUserVo> findRoleUsers(Long roleId, String keyword, Boolean assigned, Long page, Long limit);

    /**
     * 角色侧整体替换成员集合（#256）：与用户侧「分配角色」共用 {@code sys_user_role} 写路径，
     * 空列表清空；非法用户先校验后删除，受影响用户（移出与加入）的权限缓存都在提交后失效。
     */
    void assignUsers(Long roleId, List<Long> userIds);
}
