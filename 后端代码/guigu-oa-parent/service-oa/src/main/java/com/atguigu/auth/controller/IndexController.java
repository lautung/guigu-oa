package com.atguigu.auth.controller;

import com.atguigu.auth.service.SysMenuService;
import com.atguigu.auth.service.SysUserService;
import com.atguigu.common.jwt.JwtHelper;
import com.atguigu.common.result.Result;
import com.atguigu.model.system.SysUser;
import com.atguigu.vo.system.RouterVo;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "后台登录管理")
@RestController
@RequestMapping("/admin/system/index")
public class IndexController {

    @Autowired
    private SysUserService sysUserService;

    @Autowired
    private SysMenuService sysMenuService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping("info")
    public Result info(HttpServletRequest request) {
        String token = request.getHeader("token");
        Long userId = JwtHelper.getUserId(token);

        SysUser sysUser = sysUserService.getById(userId);
        List<RouterVo> routerList = sysMenuService.findUserMenuListByUserId(userId);
        List<String> permsList = sysMenuService.findUserPermsByUserId(userId);
        if (permsList == null) {
            permsList = List.of();
        }
        refreshCachedAuthorities(sysUser.getUsername(), permsList);

        Map<String, Object> map = new HashMap<>();
        map.put("roles", "[admin]");
        map.put("name", sysUser.getName());
        map.put("avatar", "https://oss.aliyuncs.com/aliyun_id_photo_bucket/default_handsome.jpg");
        map.put("routers", routerList);
        map.put("buttons", permsList);
        return Result.ok(map);
    }

    @PostMapping("logout")
    public Result logout() {
        return Result.ok();
    }

    private void refreshCachedAuthorities(String username, List<String> permsList) {
        if (!StringUtils.hasText(username)) {
            return;
        }
        List<Map<String, String>> authorities = permsList.stream()
                .filter(StringUtils::hasText)
                .map(perm -> Map.of("authority", perm.trim()))
                .collect(Collectors.toList());
        redisTemplate.opsForValue().set(username, objectMapper.writeValueAsString(authorities));
    }
}
