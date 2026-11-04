package com.lautung.oa.monitor.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lautung.oa.framework.page.PageWindow;
import com.lautung.oa.model.monitor.OnlineSession;
import com.lautung.oa.model.monitor.SessionTime;
import com.lautung.oa.monitor.mapper.OnlineSessionUser;
import com.lautung.oa.monitor.mapper.OnlineSessionUserMapper;
import com.lautung.oa.security.custom.AccountStatus;
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
import java.util.function.Predicate;
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
                .filter(loginTimeRange(queryVo))
                .sorted(Comparator.comparing(
                        OnlineSession::loginTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(session -> OnlineSessionVo.of(session, currentSessionId))
                .toList();
        pageParam.setTotal(rows.size());
        pageParam.setRecords(slice(rows, pageParam));
        return pageParam;
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
        }
        // 改密递增 token_version，旧会话从这里退场（ADR 0029/0060）。
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

    /**
     * 登录时间闭区间：边界只解析一次（{@code yyyy-MM-dd HH:mm:ss}）。形状不合法的边界按
     * 未提供处理——契约只承诺这个格式，坏值不该放大成整页失败；登记里缺登录时间的行在有
     * 时间约束时不展示。
     */
    private static Predicate<OnlineSession> loginTimeRange(OnlineSessionQueryVo queryVo) {
        SessionTime begin = queryVo == null ? null : SessionTime.parse(queryVo.getBeginTime());
        SessionTime end = queryVo == null ? null : SessionTime.parse(queryVo.getEndTime());
        if (begin == null && end == null) {
            return session -> true;
        }
        return session -> {
            SessionTime loginTime = session.loginTime();
            if (loginTime == null) {
                return false;
            }
            if (begin != null && loginTime.compareTo(begin) < 0) {
                return false;
            }
            return end == null || loginTime.compareTo(end) <= 0;
        };
    }

    private static List<OnlineSessionVo> slice(List<OnlineSessionVo> rows, Page<OnlineSessionVo> page) {
        long from = Math.min(PageWindow.offset(page), rows.size());
        long to = Math.min(from + page.getSize(), rows.size());
        return rows.subList((int) from, (int) to);
    }
}
