package com.lautung.oa.auth.controller;

import com.lautung.oa.auth.grant.BackofficeGrant;
import com.lautung.oa.auth.service.SysRoleService;
import com.lautung.oa.auth.service.SysUserService;
import com.lautung.oa.common.jwt.JwtHelper;
import com.lautung.oa.common.result.Result;
import com.lautung.oa.model.system.SysUser;
import com.lautung.oa.security.custom.LoginUserInfoHelper;
import com.lautung.oa.security.response.AuthenticationRejections;
import com.lautung.oa.security.revocation.TokenRevocation;
import com.lautung.oa.security.session.SessionVerifier;
import com.lautung.oa.vo.system.BackofficeMenuVo;
import com.lautung.oa.vo.system.RefreshTokenVo;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
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
    private SysRoleService sysRoleService;

    @Autowired
    private BackofficeGrant backofficeGrant;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private JwtHelper jwtHelper;

    @Autowired
    private TokenRevocation tokenRevocation;

    @Autowired
    private SessionVerifier sessionVerifier;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping("info")
    public Result info() {
        // /info 不是匿名路径：过滤器已完成会话核验并设置了当前用户（#173），这里不再重解析令牌。
        Long userId = LoginUserInfoHelper.getUserId();
        SysUser sysUser = sysUserService.getById(userId);
        BackofficeGrant.Snapshot grant = backofficeGrant.snapshot(userId);
        List<BackofficeMenuVo> menus = grant.menus().stream()
                .map(entry -> new BackofficeMenuVo(
                        entry.path(),
                        entry.parentPath(),
                        entry.title(),
                        entry.type(),
                        entry.hidden(),
                        entry.sort(),
                        entry.icon()))
                .collect(Collectors.toList());
        List<String> permissions = List.copyOf(grant.permissions());
        refreshCachedAuthorities(sysUser.getUsername(), permissions);

        Map<String, Object> map = new HashMap<>();
        map.put("roles", sysRoleService.findRoleCodesByUserId(userId));
        map.put("name", sysUser.getName());
        // 字段保留，空串由客户端同源默认图兜底：不再下发第三方占位图（#139 CSP）。
        map.put("avatar", "");
        map.put("permissions", permissions);
        map.put("menus", menus);
        return Result.ok(map);
    }

    /**
     * Anonymous on purpose: an idle client's access token may already be expired, and refusing
     * that request would leave its still-valid refresh token usable. The client turns in both,
     * and any token that parses revokes the session it belongs to.
     */
    @PostMapping("logout")
    public Result logout(
            HttpServletRequest request,
            @RequestBody(required = false) RefreshTokenVo refreshTokenVo) {
        revoke(jwtHelper.readAccessToken(request.getHeader("token")));
        revoke(jwtHelper.readRefreshToken(
                refreshTokenVo == null ? null : refreshTokenVo.getRefreshToken()));
        return Result.ok();
    }

    private void revoke(JwtHelper.TokenClaims claims) {
        if (claims != null) {
            tokenRevocation.revoke(claims.sessionId());
        }
    }

    /**
     * Anonymous on purpose: the access token that would carry the user here may already be
     * expired — the refresh token is the credential. Its absence or failure is an authentication
     * failure, so rejections join the filter family at HTTP 401 + code 208/209 (#155) instead of
     * the 200-envelope business failures other endpoints return.
     */
    @PostMapping("refresh")
    public ResponseEntity<Result> refresh(@RequestBody(required = false) RefreshTokenVo refreshTokenVo) {
        String refreshToken = refreshTokenVo == null ? null : refreshTokenVo.getRefreshToken();
        SessionVerifier.Outcome outcome = sessionVerifier.verify(SessionVerifier.TokenKind.REFRESH, refreshToken);
        if (outcome instanceof SessionVerifier.Outcome.Rejected rejected) {
            return AuthenticationRejections.entity(rejected.failure());
        }
        SessionVerifier.Outcome.Recognized recognized = (SessionVerifier.Outcome.Recognized) outcome;
        JwtHelper.TokenClaims claims = recognized.claims();
        SysUser user = recognized.user();

        String accessToken = jwtHelper.createAccessToken(
                claims.userId(), claims.username(), claims.sessionId(), user.tokenVersionOrDefault());
        Map<String, Object> tokenMap = new HashMap<>();
        tokenMap.put("token", accessToken);
        // Keep the refresh token's original 7-day expiry instead of extending it on each refresh.
        tokenMap.put("refreshToken", refreshToken);
        return ResponseEntity.ok(Result.ok(tokenMap));
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
