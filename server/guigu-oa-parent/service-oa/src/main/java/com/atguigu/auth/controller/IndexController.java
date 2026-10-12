package com.atguigu.auth.controller;

import com.atguigu.auth.service.SysMenuService;
import com.atguigu.auth.service.SysUserService;
import com.atguigu.common.jwt.JwtHelper;
import com.atguigu.common.result.Result;
import com.atguigu.common.result.ResultCodeEnum;
import com.atguigu.model.system.SysUser;
import com.atguigu.vo.system.RefreshTokenVo;
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

    @Autowired
    private JwtHelper jwtHelper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping("info")
    public Result info(HttpServletRequest request) {
        String token = request.getHeader("token");
        Long userId = jwtHelper.getUserId(token);

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

    @PostMapping("refresh")
    public Result refresh(@RequestBody RefreshTokenVo refreshTokenVo) {
        String refreshToken = refreshTokenVo == null ? null : refreshTokenVo.getRefreshToken();
        Long userId = jwtHelper.getRefreshUserId(refreshToken);
        String username = jwtHelper.getRefreshUsername(refreshToken);
        if (userId == null || !StringUtils.hasText(username)) {
            return Result.build(null, ResultCodeEnum.LOGIN_ERROR);
        }

        SysUser user = sysUserService.getById(userId);
        if (user == null || !username.equals(user.getUsername())) {
            return Result.build(null, ResultCodeEnum.LOGIN_ERROR);
        }
        if (user.getStatus() == null || user.getStatus() == 0) {
            return Result.build(null, ResultCodeEnum.ACCOUNT_DISABLED);
        }

        String accessToken = jwtHelper.createAccessToken(userId, username);
        Map<String, Object> tokenMap = new HashMap<>();
        tokenMap.put("token", accessToken);
        // Keep the refresh token's original 7-day expiry instead of extending it on each refresh.
        tokenMap.put("refreshToken", refreshToken);
        return Result.ok(tokenMap);
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
