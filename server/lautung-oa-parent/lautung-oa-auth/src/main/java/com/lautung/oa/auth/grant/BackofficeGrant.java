package com.lautung.oa.auth.grant;

import com.lautung.oa.auth.mapper.SysMenuMapper;
import com.lautung.oa.model.system.SysMenu;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 后台授予闭包（ADR 0052/0056）：{@code sys_menu} 全表即后台，只认 type 0/1/2。
 *
 * <p>输出三层里的前两层：被授予页面的<b>绝对路径</b>集合与随页面生效的按钮点标识集合。
 * 只授目录或只授按钮都不构成后台权限；隐藏页跟可见父级同一条授予，
 * 单独授予隐藏页而父级未授予时无效。gate、{@code /info} 与菜单维护共用这份闭包。
 */
@Component
public class BackofficeGrant {

    private final SysMenuMapper menuMapper;

    public BackofficeGrant(SysMenuMapper menuMapper) {
        this.menuMapper = menuMapper;
    }

    /**
     * 读取时补祖先：{@code sys_role_menu} 只存实际授予，展示闭包在这里算。
     * 页面路径一律解析成绝对路径；按钮点只在父页面也被授予时进入 permissions。
     */
    public Snapshot snapshot(Long userId) {
        if (userId == null) {
            return Snapshot.empty();
        }

        Map<Long, SysMenu> all = loadAll();
        List<SysMenu> granted = menuMapper.findMenuListByUserId(userId);
        Map<Long, GrantedMenu> entries = new LinkedHashMap<>();
        Set<String> pages = new LinkedHashSet<>();

        for (SysMenu menu : granted) {
            if (!isVisiblePage(menu, all)) {
                continue;
            }
            addPageAndDirectories(menu, all, entries);
            addPath(pages, routePath(menu, all));

            String visibleParentPath = routePath(menu, all);
            for (SysMenu child : all.values()) {
                if (isHiddenPageOf(child, menu, all)) {
                    entries.putIfAbsent(child.getId(), new GrantedMenu(
                            routePath(child, all),
                            visibleParentPath,
                            child.getName(),
                            child.getType(),
                            true,
                            child.getSortValue(),
                            child.getIcon()));
                    addPath(pages, routePath(child, all));
                }
            }
        }

        Set<String> permissions = collectPermissions(granted, all, pages);
        List<GrantedMenu> ordered = entries.values().stream()
                .sorted(Comparator.comparing(GrantedMenu::sort, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(GrantedMenu::path))
                .toList();
        return new Snapshot(ordered, pages, permissions);
    }

    /** 菜单管理可维护的后台菜单：目录、页面与按钮，不看启用状态。 */
    public List<SysMenu> backofficeMenus() {
        Map<Long, SysMenu> all = loadAll();
        return all.values().stream()
                .filter(menu -> isBackofficeMenu(menu, all))
                .sorted(Comparator.comparing(SysMenu::getSortValue, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(SysMenu::getId))
                .collect(Collectors.toList());
    }

    /** 后台菜单（type 0/1/2 且祖先链完整），不看启用状态。 */
    public boolean isBackofficeMenu(SysMenu menu) {
        return isBackofficeMenu(menu, loadAll());
    }

    /** 可授予的后台菜单：后台菜单 + 自身与祖先都启用。 */
    public boolean isGrantableBackofficeMenu(SysMenu menu) {
        return isGrantable(menu, loadAll());
    }

    private Set<String> collectPermissions(List<SysMenu> granted, Map<Long, SysMenu> all,
                                           Set<String> pages) {
        Set<String> permissions = new LinkedHashSet<>();
        for (SysMenu menu : granted) {
            if (!Objects.equals(menu.getType(), 2)
                    || !StringUtils.hasText(menu.getPerms())
                    || !isGrantable(menu, all)) {
                continue;
            }
            SysMenu page = menu.getParentId() == null ? null : all.get(menu.getParentId());
            if (page != null && isPage(page) && pages.contains(routePath(page, all))) {
                permissions.add(menu.getPerms());
            }
        }
        return permissions;
    }

    private void addPageAndDirectories(SysMenu page, Map<Long, SysMenu> all,
                                       Map<Long, GrantedMenu> entries) {
        addEntry(page, all, entries);
        SysMenu current = page;
        while (current.getParentId() != null && current.getParentId() != 0L) {
            SysMenu parent = all.get(current.getParentId());
            if (parent == null || !isDirectory(parent)) {
                break;
            }
            addEntry(parent, all, entries);
            current = parent;
        }
    }

    private void addEntry(SysMenu menu, Map<Long, SysMenu> all, Map<Long, GrantedMenu> entries) {
        String path = routePath(menu, all);
        if (!StringUtils.hasText(path)) {
            return;
        }
        entries.putIfAbsent(menu.getId(), new GrantedMenu(
                path,
                routeParentPath(menu, all),
                menu.getName(),
                menu.getType(),
                false,
                menu.getSortValue(),
                menu.getIcon()));
    }

    private void addPath(Set<String> paths, String path) {
        if (StringUtils.hasText(path)) {
            paths.add(path);
        }
    }

    private boolean isVisiblePage(SysMenu menu, Map<Long, SysMenu> all) {
        return Objects.equals(menu.getType(), 1)
                && StringUtils.hasText(menu.getPath())
                && isGrantable(menu, all)
                && visiblePageParent(menu, all) == null;
    }

    /**
     * 隐藏页跟可见父级同一条授予：父级页面被授予即有效，不需要隐藏页自己出现在 sys_role_menu。
     * 隐藏页单独被授予而父级未授予时不会被任何可见页扫描到，因此保持无效。
     */
    private boolean isHiddenPageOf(SysMenu candidate, SysMenu visiblePage, Map<Long, SysMenu> all) {
        SysMenu parent = visiblePageParent(candidate, all);
        return Objects.equals(candidate.getType(), 1)
                && StringUtils.hasText(candidate.getPath())
                && StringUtils.hasText(candidate.getComponent())
                && isGrantable(candidate, all)
                && parent != null
                && Objects.equals(parent.getId(), visiblePage.getId());
    }

    private SysMenu visiblePageParent(SysMenu menu, Map<Long, SysMenu> all) {
        SysMenu visible = null;
        SysMenu current = menu;
        while (current.getParentId() != null && current.getParentId() != 0L) {
            SysMenu parent = all.get(current.getParentId());
            if (parent == null) {
                return visible;
            }
            if (isPage(parent)) {
                visible = parent;
            }
            current = parent;
        }
        return visible;
    }

    private String routeParentPath(SysMenu menu, Map<Long, SysMenu> all) {
        if (isDirectory(menu)) {
            SysMenu parent = menu.getParentId() == null || menu.getParentId() == 0L
                    ? null
                    : all.get(menu.getParentId());
            return parent != null && isDirectory(parent) ? routePath(parent, all) : null;
        }
        SysMenu current = menu;
        while (current.getParentId() != null && current.getParentId() != 0L) {
            SysMenu parent = all.get(current.getParentId());
            if (parent == null) {
                return null;
            }
            if (isDirectory(parent)) {
                return routePath(parent, all);
            }
            current = parent;
        }
        return null;
    }

    /**
     * 路由地址只拼目录祖先；隐藏页的语义父级是页面，但它在客户端提升到根目录下，
     * 所以 /system/sysRole 下的 assignAuth 得到 /system/assignAuth。
     */
    private String routePath(SysMenu menu, Map<Long, SysMenu> all) {
        String path = menu.getPath();
        if (!StringUtils.hasText(path)) {
            return "";
        }
        if (path.startsWith("/")) {
            return normalizePath(path);
        }

        List<String> segments = new ArrayList<>();
        SysMenu current = menu;
        while (current.getParentId() != null && current.getParentId() != 0L) {
            SysMenu parent = all.get(current.getParentId());
            if (parent == null) {
                break;
            }
            if (isDirectory(parent) && StringUtils.hasText(parent.getPath())) {
                segments.add(0, normalizeSegment(parent.getPath()));
            }
            current = parent;
        }
        segments.add(normalizeSegment(path));
        return normalizePath("/" + String.join("/", segments));
    }

    private boolean isBackofficeMenu(SysMenu menu, Map<Long, SysMenu> all) {
        if (menu == null) {
            return false;
        }
        Set<Long> visited = new HashSet<>();
        SysMenu current = menu;
        while (current != null) {
            if (!isBackofficeNode(current)) {
                return false;
            }
            if (current.getParentId() == null) {
                return false;
            }
            if (current.getParentId() == 0L) {
                return isDirectory(current);
            }
            if (current.getId() != null && !visited.add(current.getId())) {
                return false;
            }
            current = all.get(current.getParentId());
        }
        return false;
    }

    private boolean isGrantable(SysMenu menu, Map<Long, SysMenu> all) {
        if (menu == null) {
            return false;
        }
        Set<Long> visited = new HashSet<>();
        SysMenu current = menu;
        while (current != null) {
            if (!isBackofficeNode(current) || !Objects.equals(current.getStatus(), 1)) {
                return false;
            }
            if (current.getParentId() == null) {
                return false;
            }
            if (current.getParentId() == 0L) {
                return isDirectory(current);
            }
            if (current.getId() != null && !visited.add(current.getId())) {
                return false;
            }
            current = all.get(current.getParentId());
        }
        return false;
    }

    private boolean isBackofficeNode(SysMenu menu) {
        return Objects.equals(menu.getType(), 0)
                || Objects.equals(menu.getType(), 1)
                || Objects.equals(menu.getType(), 2);
    }

    private boolean isDirectory(SysMenu menu) {
        return Objects.equals(menu.getType(), 0);
    }

    private boolean isPage(SysMenu menu) {
        return Objects.equals(menu.getType(), 1);
    }

    private Map<Long, SysMenu> loadAll() {
        Map<Long, SysMenu> all = new LinkedHashMap<>();
        for (SysMenu menu : menuMapper.selectList(null)) {
            if (menu.getId() != null) {
                all.put(menu.getId(), menu);
            }
        }
        return all;
    }

    private String normalizePath(String path) {
        String value = path.trim().replaceAll("/{2,}", "/");
        if (!value.startsWith("/")) {
            value = "/" + value;
        }
        if (value.length() > 1 && value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String normalizeSegment(String path) {
        return normalizePath(path).replaceAll("^/+", "").replaceAll("/+$", "");
    }

    public record Snapshot(List<GrantedMenu> menus, Set<String> pages, Set<String> permissions) {

        public Snapshot {
            menus = List.copyOf(menus);
            pages = Set.copyOf(pages);
            permissions = Set.copyOf(permissions);
        }

        public static Snapshot empty() {
            return new Snapshot(List.of(), Set.of(), Set.of());
        }

        public boolean hasAnyPage() {
            return !pages.isEmpty();
        }

        public boolean covers(String pagePath) {
            return pagePath != null && pages.contains(pagePath);
        }

        public boolean allows(String pagePath, String permission) {
            return covers(pagePath) && (permission == null || permissions.contains(permission));
        }
    }

    public record GrantedMenu(
            String path,
            String parentPath,
            String title,
            Integer type,
            boolean hidden,
            Integer sort,
            String icon) {
    }
}
