package com.lautung.oa.auth.service.impl;

import com.lautung.oa.auth.service.SysDeptService;
import com.lautung.oa.auth.service.SysPostService;
import com.lautung.oa.auth.service.SysUserExcelService;
import com.lautung.oa.auth.service.SysUserPostService;
import com.lautung.oa.auth.service.SysUserService;
import com.lautung.oa.framework.config.exception.BusinessException;
import com.lautung.oa.vo.system.UserImportResultVo;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 导入逐行容错与行数上限（PR #260 审查）：非业务运行时异常只记该行失败，后续行继续
 * 处理；数据行超过 1000 行在装载/写库前整文件拒绝。
 */
class SysUserExcelServiceImplTest {

    private final SysUserService sysUserService = mock(SysUserService.class);

    private final SysUserPostService sysUserPostService = mock(SysUserPostService.class);

    private final SysDeptService sysDeptService = mock(SysDeptService.class);

    private final SysPostService sysPostService = mock(SysPostService.class);

    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

    private final SysUserExcelServiceImpl service = new SysUserExcelServiceImpl(
            sysUserService, sysUserPostService, sysDeptService, sysPostService,
            new ObjectMapper(), passwordEncoder);

    @Test
    void aRuntimeFailureOnOneRowDoesNotAbortTheRestOfTheBatch() throws Exception {
        when(sysDeptService.list()).thenReturn(List.of());
        when(sysPostService.list()).thenReturn(List.of());
        doThrow(new IllegalStateException("db down"))
                .doNothing()
                .when(sysUserService).saveUser(any(), anyList());

        UserImportResultVo result = service.importUsers(workbook(2));

        assertEquals(2, result.getTotal());
        assertEquals(1, result.getSuccessCount());
        assertEquals(1, result.getFailureCount());
        assertEquals(2, result.getFailures().get(0).getRowNumber());
        assertEquals("t259_0", result.getFailures().get(0).getUsername());
        assertEquals("该行处理失败", result.getFailures().get(0).getReason());
    }

    @Test
    void importAcceptsExactlyTheRowLimitAndSkipsTrailingBlankRows() throws Exception {
        when(sysDeptService.list()).thenReturn(List.of());
        when(sysPostService.list()).thenReturn(List.of());

        UserImportResultVo result = service.importUsers(
                workbook(SysUserExcelService.MAX_IMPORT_ROWS));

        assertEquals(SysUserExcelService.MAX_IMPORT_ROWS, result.getTotal());
        assertEquals(SysUserExcelService.MAX_IMPORT_ROWS, result.getSuccessCount());
        verify(sysUserService, times(SysUserExcelService.MAX_IMPORT_ROWS))
                .saveUser(any(), anyList());
    }

    @Test
    void importRejectsMoreThanTheRowLimitBeforeLoadingOrWriting() throws Exception {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.importUsers(workbook(SysUserExcelService.MAX_IMPORT_ROWS + 1)));

        assertEquals(201, error.getCode());
        assertEquals("导入文件超过 1000 行限制", error.getMsg());
        verify(sysDeptService, never()).list();
        verify(sysPostService, never()).list();
        verify(sysUserService, never()).saveUser(any(), anyList());
    }

    private MockMultipartFile workbook(int dataRows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("用户");
            Row header = sheet.createRow(0);
            for (int index = 0; index < SysUserExcelService.COLUMNS.size(); index++) {
                header.createCell(index).setCellValue(SysUserExcelService.COLUMNS.get(index));
            }
            for (int index = 0; index < dataRows; index++) {
                row(sheet.createRow(index + 1), "t259_" + index, "用户" + index);
            }
            // 尾部空行不参与计数：数据行恰好 1000 时仍应通过。
            sheet.createRow(dataRows + 1);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            workbook.write(output);
            return new MockMultipartFile("file", "users.xlsx",
                    SysUserExcelService.XLSX_CONTENT_TYPE, output.toByteArray());
        }
    }

    private static void row(Row row, String username, String name) {
        row.createCell(0).setCellValue(username);
        row.createCell(1).setCellValue(name);
        row.createCell(2).setCellValue("");
        row.createCell(3).setCellValue("");
        row.createCell(4).setCellValue("");
        row.createCell(5).setCellValue("");
    }
}
