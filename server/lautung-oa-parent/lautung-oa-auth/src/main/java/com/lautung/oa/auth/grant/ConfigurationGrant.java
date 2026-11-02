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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 配置授予：某用户被授予了哪些配置菜单、某页是否被覆盖、菜单树应该发什么。
 * 菜单树是授予权威；gate、/info 与菜单维护都从这里取同一份闭包。
 */
@Component
public class ConfigurationGrant {

    private static final Set<String> CONFIGURATION_ROOT_PATHS =
            Set.of("/system", "/processSet", "/processMgr");
    private static final Set<String> EXCLUDED_MENU_COMPONENTS = Set.of(
            "system/sysdept/list", "system/syspost/list", "system/sysoperlog/list", "system/sysloginlog/list");

    private final SysMenuMapper menuMapper;

    public ConfigurationGrant(SysMenuMapper menuMapper) {
        this.menuMapper = menuMapper;
    }

    /**
     * 读取时补祖先：sys_role_menu 只存实际授予，展示闭包在这里算。
     * 只授目录不算配置权限；隐藏页跟可见父级同一条授予，单独授予隐藏页而父级未授予时无效。
     */
    public GrantedConfiguration snapshot(Long userId) {
        if (userId == null) {
            return GrantedConfiguration.empty();
        }

        Map<Long, SysMenu> all = loadAll();
        List<SysMenu> granted = menuMapper.findMenuListByUserId(userId);
        Map<Long, GrantedMenu> entries = new LinkedHashMap<>();
        Set<String> pagePaths = new LinkedHashSet<>();

        for (SysMenu menu : granted) {
            if (!isVisiblePage(menu, all)) {
                continue;
            }
            addPageAndDirectories(menu, all, entries);
            addPath(pagePaths, menu.getPath());

            String visibleParentPath = routePath(menu, all);
            for (SysMenu child : all.values()) {
                if (isHiddenPageOf(child, menu, all)) {
                    entries.putIfAbsent(child.getId(), new GrantedMenu(
                            routePath(child, all),
                            visibleParentPath,
                            child.getName(),
                            child.getType(),
                            true,
                            child.getSortValue()));
                    addPath(pagePaths, child.getPath());
                }
            }
        }

        List<GrantedMenu> ordered = entries.values().stream()
                .sorted(Comparator.comparing(GrantedMenu::sort, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(GrantedMenu::path))
                .toList();
        return new GrantedConfiguration(ordered, pagePaths);
    }

    /** 菜单管理可维护的配置类菜单：目录、页面与按钮，不看启用状态。 */
    public List<SysMenu> configurationMenus() {
        Map<Long, SysMenu> all = loadAll();
        return all.values().stream()
                .filter(menu -> isConfigurationMenu(menu, all))
                .sorted(Comparator.comparing(SysMenu::getSortValue, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(SysMenu::getId))
                .collect(Collectors.toList());
    }

    /** 配置类菜单（type 0/1/2，不含办事与排除项），不看启用状态。 */
    public boolean isConfigurationMenu(SysMenu menu) {
        return isConfigurationMenu(menu, loadAll());
    }

    /** 可授予的配置菜单：配置类 + 自身与祖先都启用。 */
    public boolean isGrantableConfigurationMenu(SysMenu menu) {
        return isGrantable(menu, loadAll());
    }

    private void addPageAndDirectories(SysMenu page, Map<Long, SysMenu> all,
                                       Map<Long, GrantedMenu> entries) {
        addEntry(page, all, entries, false);
        SysMenu current = page;
        while (current.getParentId() != null && current.getParentId() != 0L) {
            SysMenu parent = all.get(current.getParentId());
            if (parent == null || !isDirectory(parent)) {
                break;
            }
            addEntry(parent, all, entries, false);
            current = parent;
        }
    }

    private void addEntry(SysMenu menu, Map<Long, SysMenu> all,
                          Map<Long, GrantedMenu> entries, boolean hidden) {
        String path = routePath(menu, all);
        if (!StringUtils.hasText(path)) {
            return;
        }
        entries.putIfAbsent(menu.getId(), new GrantedMenu(
                path,
                hidden ? null : routeParentPath(menu, all),
                menu.getName(),
                menu.getType(),
                hidden,
                menu.getSortValue()));
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

    private boolean isConfigurationMenu(SysMenu menu, Map<Long, SysMenu> all) {
        if (menu == null) {
            return false;
        }
        Set<Long> visited = new HashSet<>();
        SysMenu current = menu;
        while (current != null) {
            if (!isConfigurationNode(current)) {
                return false;
            }
            if (current.getParentId() == null) {
                return false;
            }
            if (current.getParentId() == 0L) {
                return isConfigurationRoot(current);
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
            if (!isConfigurationNode(current) || !Objects.equals(current.getStatus(), 1)) {
                return false;
            }
            if (current.getParentId() == null) {
                return false;
            }
            if (current.getParentId() == 0L) {
                return isConfigurationRoot(current);
            }
            if (current.getId() != null && !visited.add(current.getId())) {
                return false;
            }
            current = all.get(current.getParentId());
        }
        return false;
    }

    private boolean isConfigurationNode(SysMenu menu) {
        if (!Objects.equals(menu.getType(), 0)
                && !Objects.equals(menu.getType(), 1)
                && !Objects.equals(menu.getType(), 2)) {
            return false;
        }

        String path = Objects.toString(menu.getPath(), "").toLowerCase(Locale.ROOT);
        String component = Objects.toString(menu.getComponent(), "").toLowerCase(Locale.ROOT);
        return !isWorkMenuPath(path)
                && !path.contains("wechat") && !component.contains("wechat")
                && !path.contains("dashboard") && !component.contains("dashboard")
                && !path.contains("onlineprocessset") && !component.contains("onlineprocessset")
                && !EXCLUDED_MENU_COMPONENTS.contains(component);
    }

    private boolean isConfigurationRoot(SysMenu menu) {
        return Objects.equals(menu.getType(), 0)
                && menu.getPath() != null
                && CONFIGURATION_ROOT_PATHS.contains(menu.getPath());
    }

    private boolean isWorkMenuPath(String path) {
        return path.equals("work") || path.startsWith("/work") || path.startsWith("work/");
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

    public record GrantedConfiguration(List<GrantedMenu> entries, Set<String> pagePaths) {

        public GrantedConfiguration {
            entries = List.copyOf(entries);
            pagePaths = Set.copyOf(pagePaths);
        }

        public static GrantedConfiguration empty() {
            return new GrantedConfiguration(List.of(), Set.of());
        }

        public boolean hasAnyPage() {
            return !pagePaths.isEmpty();
        }

        public boolean covers(String menuPath) {
            return menuPath != null && pagePaths.contains(menuPath);
        }
    }

    public record GrantedMenu(
            String path,
            String parentPath,
            String title,
            Integer type,
            boolean hidden,
            Integer sort) {
    }
}
