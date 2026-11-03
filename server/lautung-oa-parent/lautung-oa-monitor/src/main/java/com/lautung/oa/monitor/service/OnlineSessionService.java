package com.lautung.oa.monitor.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lautung.oa.framework.page.PageWindow;
import com.lautung.oa.monitor.mapper.OnlineSessionUser;
import com.lautung.oa.monitor.mapper.OnlineSessionUserMapper;
import com.lautung.oa.security.custom.AccountStatus;
import com.lautung.oa.security.session.OnlineSession;
import com.lautung.oa.security.session.OnlineSessionRegistry;
import com.lautung.oa.vo.monitor.OnlineSessionQueryVo;
import com.lautung.oa.vo.monitor.OnlineSessionVo;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 在线用户列表的读侧（ADR 0060）：登记来自 security 端口，失效口径在这里按用户行现值判定。
 *
 * <p>用户不存在/已删/停用/登记时的 {@code tokenVersion} 与现值不一致的会话直接不展示，
 * 也不删登记键——键由 TTL 收尾，退出与强退才主动删。列举与分页都在内存里完成，
 * 因为登记本来就是 Redis 的运行时产物（一行 = 一次登录）。
 */
@Service
public class OnlineSessionService {

    private final OnlineSessionRegistry onlineSessionRegistry;

    private final OnlineSessionUserMapper userMapper;

    public OnlineSessionService(
            OnlineSessionRegistry onlineSessionRegistry,
            OnlineSessionUserMapper userMapper) {
        this.onlineSessionRegistry = onlineSessionRegistry;
        this.userMapper = userMapper;
    }

    public Page<OnlineSessionVo> page(
            Long page,
            Long limit,
            OnlineSessionQueryVo queryVo,
            String currentSessionId) {
        Page<OnlineSessionVo> pageParam = PageWindow.of(page, limit);
        List<OnlineSessionVo> rows = liveSessions().stream()
                .filter(session -> matchesUsername(session, queryVo))
                .filter(session -> matchesLoginTime(session, queryVo))
                .sorted(Comparator.comparing(
                        OnlineSession::loginTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(session -> toVo(session, currentSessionId))
                .toList();
        pageParam.setTotal(rows.size());
        pageParam.setRecords(slice(rows, pageParam));
        return pageParam;
    }

    /**
     * @return 该 sessionId 曾经登记过时 true；未知 sessionId 由调用方按 201 命名拒绝
     */
    public boolean forceLogout(String sessionId) {
        return onlineSessionRegistry.forceLogout(sessionId);
    }

    private List<OnlineSession> liveSessions() {
        List<OnlineSession> sessions = onlineSessionRegistry.list();
        if (sessions.isEmpty()) {
            return List.of();
        }
        List<Long> userIds = sessions.stream()
                .map(OnlineSession::userId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            return List.of();
        }
        Map<Long, OnlineSessionUser> users = userMapper.findByIds(userIds).stream()
                .collect(Collectors.toMap(OnlineSessionUser::getId, Function.identity()));
        return sessions.stream()
                .filter(session -> isLive(session, users.get(session.userId())))
                .toList();
    }

    private static boolean isLive(OnlineSession session, OnlineSessionUser user) {
        if (user == null || !Objects.equals(0, user.getIsDeleted())) {
            return false;
        }
        // 与认证链同一口径（AccountStatus/SessionVerifier），避免「能认证、列表不可见」的分叉。
        if (!AccountStatus.isEnabled(user.getStatus())) {
            return false;
        }        // 改密递增 token_version，旧会话从这里退场（ADR 0029/0060）。
        return user.tokenVersionOrDefault() == session.tokenVersion();
    }

    private static boolean matchesUsername(OnlineSession session, OnlineSessionQueryVo queryVo) {
        if (queryVo == null || !StringUtils.hasText(queryVo.getUsername())) {
            return true;
        }
        String needle = queryVo.getUsername().trim().toLowerCase(Locale.ROOT);
        return session.username() != null
                && session.username().toLowerCase(Locale.ROOT).contains(needle);
    }

    /** 登录时间与查询参数都是 {@code yyyy-MM-dd HH:mm:ss}，同格式字符串比较即时间顺序。 */
    private static boolean matchesLoginTime(OnlineSession session, OnlineSessionQueryVo queryVo) {
        if (queryVo == null) {
            return true;
        }
        String loginTime = session.loginTime();
        if (!StringUtils.hasText(queryVo.getBeginTime()) && !StringUtils.hasText(queryVo.getEndTime())) {
            return true;
        }
        if (!StringUtils.hasText(loginTime)) {
            return false;
        }
        if (StringUtils.hasText(queryVo.getBeginTime())
                && loginTime.compareTo(queryVo.getBeginTime().trim()) < 0) {
            return false;
        }
        return !StringUtils.hasText(queryVo.getEndTime())
                || loginTime.compareTo(queryVo.getEndTime().trim()) <= 0;
    }

    private static OnlineSessionVo toVo(OnlineSession session, String currentSessionId) {
        OnlineSessionVo row = new OnlineSessionVo();
        row.setSessionId(session.sessionId());
        row.setUsername(session.username());
        row.setName(session.name());
        row.setLoginTime(session.loginTime());
        row.setIp(session.ip());
        row.setBrowser(session.browser());
        row.setOs(session.os());
        row.setLastActive(session.lastActive());
        row.setCurrent(session.sessionId() != null && session.sessionId().equals(currentSessionId));
        return row;
    }

    private static List<OnlineSessionVo> slice(List<OnlineSessionVo> rows, Page<OnlineSessionVo> page) {
        long from = Math.min(PageWindow.offset(page), rows.size());
        long to = Math.min(from + page.getSize(), rows.size());
        return rows.subList((int) from, (int) to);
    }
}
