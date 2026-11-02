package com.lautung.oa.auth.service;

import com.lautung.oa.model.system.SysRole;
import com.lautung.oa.vo.system.AssginRoleVo;
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
}
