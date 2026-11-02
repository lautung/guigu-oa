package com.lautung.oa.auth.service.impl;

import com.lautung.oa.auth.service.SysUserService;
import com.lautung.oa.model.system.SysUser;
import com.lautung.oa.security.custom.CustomUser;
import com.lautung.oa.security.custom.UserDetailsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsPasswordService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserDetailsServiceImpl implements UserDetailsService, UserDetailsPasswordService {

    @Autowired
    private SysUserService sysUserService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser sysUser = sysUserService.getUserByUserName(username);
        if(null == sysUser) {
            throw new UsernameNotFoundException("用户名不存在！");
        }
        return new CustomUser(sysUser, List.of());
    }

    @Override
    public UserDetails updatePassword(UserDetails user, String newPassword) {
        CustomUser customUser = (CustomUser) user;
        SysUser sysUser = customUser.getSysUser();
        sysUser.setPassword(newPassword);
        sysUserService.updateById(sysUser);
        return new CustomUser(sysUser, customUser.getAuthorities());
    }
}
