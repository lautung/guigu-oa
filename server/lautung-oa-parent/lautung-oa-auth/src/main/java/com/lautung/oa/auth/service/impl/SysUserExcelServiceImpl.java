package com.lautung.oa.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lautung.oa.auth.service.SysDeptService;
import com.lautung.oa.auth.service.SysPostService;
import com.lautung.oa.auth.service.SysUserExcelService;
import com.lautung.oa.auth.service.SysUserPostService;
import com.lautung.oa.auth.service.SysUserQueries;
import com.lautung.oa.auth.service.SysUserService;
import com.lautung.oa.framework.config.exception.BusinessException;
import com.lautung.oa.model.system.DataScope;
import com.lautung.oa.model.system.SysDept;
import com.lautung.oa.model.system.SysPost;
import com.lautung.oa.model.system.SysUser;
import com.lautung.oa.model.system.SysUserPost;
import com.lautung.oa.vo.system.SysUserQueryVo;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 用户 xlsx 导出（#259）：SXSSFWorkbook 按窗口写行，用户行按 {@value #EXPORT_PAGE_SIZE}
 * 一页读取；部门与岗位名按页批量查，避免逐行 N+1。查询条件与列表共用
 * {@link SysUserQueries}，所以关键字/部门/状态/DataScope 的语义只有一份。
 */
@Service
public class SysUserExcelServiceImpl implements SysUserExcelService {

    private final SysUserService sysUserService;

    private final SysUserPostService sysUserPostService;

    private final SysDeptService sysDeptService;

    private final SysPostService sysPostService;

    private final ObjectMapper objectMapper;

    public SysUserExcelServiceImpl(
            SysUserService sysUserService,
            SysUserPostService sysUserPostService,
            SysDeptService sysDeptService,
            SysPostService sysPostService,
            ObjectMapper objectMapper) {
        this.sysUserService = sysUserService;
        this.sysUserPostService = sysUserPostService;
        this.sysDeptService = sysDeptService;
        this.sysPostService = sysPostService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void export(SysUserQueryVo query, DataScope scope, OutputStream output) {
        List<Long> subtreeIds = query.getDeptId() == null
                ? List.of()
                : sysDeptService.findSubtreeIds(query.getDeptId());
        LambdaQueryWrapper<SysUser> wrapper = SysUserQueries.wrapper(query, subtreeIds, scope, true);

        SXSSFWorkbook workbook = new SXSSFWorkbook(100);
        try {
            Sheet sheet = workbook.createSheet("用户");
            writeHeader(sheet);
            int rowIndex = 1;
            long pageNumber = 1;
            while (true) {
                Page<SysUser> page = new Page<>(pageNumber, EXPORT_PAGE_SIZE);
                page.setSearchCount(false);
                List<SysUser> users = sysUserService.page(page, wrapper).getRecords();
                if (users.isEmpty()) {
                    break;
                }
                ExportNames names = namesFor(users);
                for (SysUser user : users) {
                    writeUser(sheet.createRow(rowIndex++), user, names);
                }
                if (users.size() < EXPORT_PAGE_SIZE) {
                    break;
                }
                pageNumber++;
            }
            workbook.write(output);
        } catch (IOException exception) {
            throw new BusinessException(201, "导出失败");
        } finally {
            try {
                workbook.close();
            } catch (IOException ignored) {
                // 关闭失败不覆盖 response 已写出的内容；临时文件由 dispose 清掉。
            }
            workbook.dispose();
        }
    }

    private void writeHeader(Sheet sheet) {
        Row header = sheet.createRow(0);
        for (int index = 0; index < COLUMNS.size(); index++) {
            header.createCell(index).setCellValue(COLUMNS.get(index));
        }
    }

    private void writeUser(Row row, SysUser user, ExportNames names) {
        set(row, 0, user.getUsername());
        set(row, 1, user.getName());
        set(row, 2, user.getDeptId() == null ? "" : names.deptNames.getOrDefault(user.getDeptId(), ""));
        set(row, 3, String.join("、", names.postNames.getOrDefault(user.getId(), List.of())));
        set(row, 4, user.getPhone());
        set(row, 5, Objects.equals(user.getStatus(), 1) ? "正常" : "停用");
        set(row, 6, formatTime(user.getCreateTime()));
    }

    private static void set(Row row, int index, String value) {
        Cell cell = row.createCell(index);
        cell.setCellValue(value == null ? "" : value);
    }

    /** 日期格式与 JSON 输出同源：复用应用配置的 ObjectMapper（date-format / time-zone）。 */
    private String formatTime(java.util.Date value) {
        if (value == null) {
            return "";
        }
        String json = objectMapper.writeValueAsString(value);
        return json.length() >= 2 && json.startsWith("\"") && json.endsWith("\"")
                ? json.substring(1, json.length() - 1)
                : json;
    }

    private ExportNames namesFor(List<SysUser> users) {
        Set<Long> deptIds = new LinkedHashSet<>();
        List<Long> userIds = new ArrayList<>();
        for (SysUser user : users) {
            if (user.getDeptId() != null) {
                deptIds.add(user.getDeptId());
            }
            userIds.add(user.getId());
        }

        Map<Long, String> deptNames = new HashMap<>();
        if (!deptIds.isEmpty()) {
            for (SysDept dept : sysDeptService.listByIds(deptIds)) {
                deptNames.put(dept.getId(), dept.getName());
            }
        }

        Map<Long, List<String>> postNames = new HashMap<>();
        LambdaQueryWrapper<SysUserPost> links = new LambdaQueryWrapper<SysUserPost>()
                .in(SysUserPost::getUserId, userIds)
                .orderByAsc(SysUserPost::getId);
        List<SysUserPost> relations = sysUserPostService.list(links);
        Set<Long> postIds = new LinkedHashSet<>();
        for (SysUserPost relation : relations) {
            if (relation.getPostId() != null) {
                postIds.add(relation.getPostId());
            }
        }
        Map<Long, String> postsById = new HashMap<>();
        if (!postIds.isEmpty()) {
            for (SysPost post : sysPostService.listByIds(postIds)) {
                postsById.put(post.getId(), post.getName());
            }
        }
        for (SysUserPost relation : relations) {
            String name = postsById.get(relation.getPostId());
            if (name != null) {
                postNames.computeIfAbsent(relation.getUserId(), key -> new ArrayList<>()).add(name);
            }
        }
        return new ExportNames(deptNames, postNames);
    }

    private record ExportNames(Map<Long, String> deptNames, Map<Long, List<String>> postNames) {
    }
}
