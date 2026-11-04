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
import com.lautung.oa.vo.system.UserImportResultVo;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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

    private final PasswordEncoder passwordEncoder;

    public SysUserExcelServiceImpl(
            SysUserService sysUserService,
            SysUserPostService sysUserPostService,
            SysDeptService sysDeptService,
            SysPostService sysPostService,
            ObjectMapper objectMapper,
            PasswordEncoder passwordEncoder) {
        this.sysUserService = sysUserService;
        this.sysUserPostService = sysUserPostService;
        this.sysDeptService = sysDeptService;
        this.sysPostService = sysPostService;
        this.objectMapper = objectMapper;
        this.passwordEncoder = passwordEncoder;
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

    @Override
    public void writeTemplate(OutputStream output) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("用户");
            writeHeader(sheet);
            for (int index = 0; index < COLUMNS.size(); index++) {
                sheet.setColumnWidth(index, 20 * 256);
            }
            workbook.write(output);
        } catch (IOException exception) {
            throw new BusinessException(201, "模板生成失败");
        }
    }

    @Override
    public UserImportResultVo importUsers(MultipartFile file) {
        byte[] content = requireXlsx(file);
        XSSFWorkbook workbook = openWorkbook(content);
        try (workbook) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new BusinessException(201, "导入文件模板不正确");
            }
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            requireHeader(sheet, formatter);
            return importRows(sheet, formatter);
        } catch (IOException exception) {
            throw new BusinessException(201, "导入文件读取失败");
        }
    }

    private byte[] requireXlsx(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(201, "导入文件不能为空");
        }
        if (file.getSize() > MAX_IMPORT_BYTES) {
            throw new BusinessException(201, "导入文件超过 2 MB 限制");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new BusinessException(201, "仅支持 xlsx 文件");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw new BusinessException(201, "导入文件读取失败");
        }
        if (content.length == 0) {
            throw new BusinessException(201, "导入文件不能为空");
        }
        if (content.length > MAX_IMPORT_BYTES) {
            throw new BusinessException(201, "导入文件超过 2 MB 限制");
        }
        // OOXML 是 ZIP：先看魔数，交给 POI 前把明显不是 xlsx 的载荷挡在外面。
        if (content.length < 4
                || content[0] != 'P' || content[1] != 'K' || content[2] != 3 || content[3] != 4) {
            throw new BusinessException(201, "导入文件不是有效的 Excel 文件");
        }
        return content;
    }

    private XSSFWorkbook openWorkbook(byte[] content) {
        try {
            return new XSSFWorkbook(new ByteArrayInputStream(content));
        } catch (IOException | RuntimeException exception) {
            throw new BusinessException(201, "导入文件不是有效的 Excel 文件");
        }
    }

    private void requireHeader(Sheet sheet, DataFormatter formatter) {
        Row header = sheet.getRow(0);
        if (header == null) {
            throw new BusinessException(201, "导入文件模板不正确");
        }
        for (int index = 0; index < COLUMNS.size(); index++) {
            if (!COLUMNS.get(index).equals(cellText(header, index, formatter))) {
                throw new BusinessException(201, "导入文件模板不正确");
            }
        }
    }

    private UserImportResultVo importRows(Sheet sheet, DataFormatter formatter) {
        UserImportResultVo result = new UserImportResultVo();
        Map<String, List<SysDept>> deptsByName = sysDeptService.list().stream()
                .collect(Collectors.groupingBy(dept -> normalize(dept.getName())));
        Map<String, List<SysPost>> postsByName = sysPostService.list().stream()
                .collect(Collectors.groupingBy(post -> normalize(post.getName())));

        int total = 0;
        int success = 0;
        int lastRow = sheet.getLastRowNum();
        for (int index = 1; index <= lastRow; index++) {
            Row row = sheet.getRow(index);
            if (isBlankRow(row, formatter)) {
                continue;
            }
            total++;
            int rowNumber = index + 1;
            String username = cellText(row, 0, formatter);
            try {
                ParsedRow parsed = userFromRow(row, formatter, deptsByName, postsByName);
                sysUserService.saveUser(parsed.user(), parsed.postIds());
                success++;
            } catch (BusinessException exception) {
                result.addFailure(rowNumber, username, exception.getMsg());
            }
        }
        if (total == 0) {
            throw new BusinessException(201, "导入文件没有数据");
        }
        result.setTotal(total);
        result.setSuccessCount(success);
        return result;
    }

    private ParsedRow userFromRow(
            Row row,
            DataFormatter formatter,
            Map<String, List<SysDept>> deptsByName,
            Map<String, List<SysPost>> postsByName) {
        String username = cellText(row, 0, formatter);
        String name = cellText(row, 1, formatter);
        String deptName = cellText(row, 2, formatter);
        String postNames = cellText(row, 3, formatter);
        String phone = cellText(row, 4, formatter);
        String statusText = cellText(row, 5, formatter);

        if (!StringUtils.hasText(username)) {
            throw new BusinessException(201, "用户名不能为空");
        }
        if (username.length() > 20) {
            throw new BusinessException(201, "用户名过长");
        }
        if (!StringUtils.hasText(name)) {
            throw new BusinessException(201, "姓名不能为空");
        }
        if (name.length() > 20) {
            throw new BusinessException(201, "姓名过长");
        }
        if (phone.length() > 20) {
            throw new BusinessException(201, "手机号过长");
        }

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setName(name);
        user.setPhone(phone);
        user.setStatus(resolveStatus(statusText));
        user.setDeptId(resolveDeptId(deptName, deptsByName));
        user.setPassword(passwordEncoder.encode(DEFAULT_IMPORT_PASSWORD));
        List<Long> postIds = resolvePostIds(postNames, postsByName);

        // 用户名唯一性不在这里查：saveUser → save 已按 ADR 0042 查重（唯一索引兜底），
        // 重复行同样得到 201「用户名已存在」，少一次逐行查询。
        return new ParsedRow(user, postIds);
    }

    private record ParsedRow(SysUser user, List<Long> postIds) {
    }

    private int resolveStatus(String statusText) {
        if (!StringUtils.hasText(statusText)) {
            return 1;
        }
        String value = statusText.trim();
        if ("正常".equals(value) || "启用".equals(value) || "1".equals(value)) {
            return 1;
        }
        if ("停用".equals(value) || "禁用".equals(value) || "0".equals(value)) {
            return 0;
        }
        throw new BusinessException(201, "状态无效：" + value);
    }

    private Long resolveDeptId(String deptName, Map<String, List<SysDept>> deptsByName) {
        if (!StringUtils.hasText(deptName)) {
            return null;
        }
        String value = deptName.trim();
        List<SysDept> matches = deptsByName.getOrDefault(value, List.of());
        if (matches.isEmpty()) {
            throw new BusinessException(201, "部门不存在：" + value);
        }
        if (matches.size() > 1) {
            throw new BusinessException(201, "部门不唯一：" + value);
        }
        SysDept dept = matches.get(0);
        if (dept.getStatus() == null || dept.getStatus() != 1) {
            throw new BusinessException(201, "部门已停用：" + value);
        }
        return dept.getId();
    }

    private List<Long> resolvePostIds(String postNames, Map<String, List<SysPost>> postsByName) {
        if (!StringUtils.hasText(postNames)) {
            return List.of();
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (String raw : postNames.split("[,，、]")) {
            String value = raw.trim();
            if (value.isEmpty()) {
                continue;
            }
            List<SysPost> matches = postsByName.getOrDefault(value, List.of());
            if (matches.isEmpty()) {
                throw new BusinessException(201, "岗位不存在：" + value);
            }
            if (matches.size() > 1) {
                throw new BusinessException(201, "岗位不唯一：" + value);
            }
            SysPost post = matches.get(0);
            if (post.getStatus() == null || post.getStatus() != 1) {
                throw new BusinessException(201, "岗位已停用：" + value);
            }
            ids.add(post.getId());
        }
        return List.copyOf(ids);
    }

    private boolean isBlankRow(Row row, DataFormatter formatter) {
        if (row == null) {
            return true;
        }
        for (int index = 0; index < COLUMNS.size(); index++) {
            if (StringUtils.hasText(cellText(row, index, formatter))) {
                return false;
            }
        }
        return true;
    }

    private static String cellText(Row row, int index, DataFormatter formatter) {
        if (row == null || row.getCell(index) == null) {
            return "";
        }
        return formatter.formatCellValue(row.getCell(index)).trim();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
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
