package com.lautung.oa.auth.service.impl;

import com.lautung.oa.auth.service.SysDeptService;
import com.lautung.oa.auth.service.SysPostService;
import com.lautung.oa.auth.service.SysUserExcelService;
import com.lautung.oa.auth.service.SysUserPostService;
import com.lautung.oa.auth.service.SysUserService;
import com.lautung.oa.framework.config.exception.BusinessException;
import com.lautung.oa.model.system.SysUser;
import com.lautung.oa.vo.system.UserImportResultVo;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
 * 导入逐行容错、初始密码与流式解析上限（#263）：非业务运行时异常只记该行失败，后续行继续
 * 处理；数据行超过 1000 行、列数超过 20、单元格超过 20000 都在装载/写库前整文件拒绝；
 * 极远稀疏单元格按列上限拒绝，不构建整表单元格图；初始密码只编码一次并写到每一行。
 */
class SysUserExcelServiceImplTest {

    private static final String PASSWORD = "Import#263pw";

    private static final String ENCODED_PASSWORD = "$2a$10$import-hash";

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
        when(passwordEncoder.encode(PASSWORD)).thenReturn(ENCODED_PASSWORD);
        doThrow(new IllegalStateException("db down"))
                .doNothing()
                .when(sysUserService).saveUser(any(), anyList());

        UserImportResultVo result = service.importUsers(workbook(2), PASSWORD);

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
        when(passwordEncoder.encode(PASSWORD)).thenReturn(ENCODED_PASSWORD);

        UserImportResultVo result = service.importUsers(
                workbook(SysUserExcelService.MAX_IMPORT_ROWS), PASSWORD);

        assertEquals(SysUserExcelService.MAX_IMPORT_ROWS, result.getTotal());
        assertEquals(SysUserExcelService.MAX_IMPORT_ROWS, result.getSuccessCount());
        verify(sysUserService, times(SysUserExcelService.MAX_IMPORT_ROWS))
                .saveUser(any(), anyList());
    }

    @Test
    void importRejectsMoreThanTheRowLimitBeforeLoadingOrWriting() throws Exception {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.importUsers(workbook(SysUserExcelService.MAX_IMPORT_ROWS + 1), PASSWORD));

        assertEquals(201, error.getCode());
        assertEquals("导入文件超过 1000 行限制", error.getMsg());
        verify(sysDeptService, never()).list();
        verify(sysPostService, never()).list();
        verify(sysUserService, never()).saveUser(any(), anyList());
    }

    @Test
    void encodesTheInitialPasswordOnceAndWritesItToEveryRow() throws Exception {
        when(sysDeptService.list()).thenReturn(List.of());
        when(sysPostService.list()).thenReturn(List.of());
        when(passwordEncoder.encode(PASSWORD)).thenReturn(ENCODED_PASSWORD);
        ArgumentCaptor<SysUser> users = ArgumentCaptor.forClass(SysUser.class);

        service.importUsers(workbook(2), PASSWORD);

        verify(passwordEncoder, times(1)).encode(PASSWORD);
        verify(sysUserService, times(2)).saveUser(users.capture(), anyList());
        assertEquals(List.of(ENCODED_PASSWORD, ENCODED_PASSWORD),
                users.getAllValues().stream().map(SysUser::getPassword).toList());
    }

    @Test
    void rejectsMissingOrOutOfRangeInitialPasswordBeforeReadingTheFile() throws Exception {
        for (String password : new String[]{null, "", "   "}) {
            BusinessException error = assertThrows(BusinessException.class,
                    () -> service.importUsers(workbook(1), password));
            assertEquals("初始密码不能为空", error.getMsg());
        }

        BusinessException tooShort = assertThrows(BusinessException.class,
                () -> service.importUsers(workbook(1), "12345"));
        assertEquals("初始密码长度必须在 6 到 100 位之间", tooShort.getMsg());

        BusinessException tooLong = assertThrows(BusinessException.class,
                () -> service.importUsers(workbook(1), "x".repeat(101)));
        assertEquals("初始密码长度必须在 6 到 100 位之间", tooLong.getMsg());

        verify(passwordEncoder, never()).encode(any());
        verify(sysDeptService, never()).list();
        verify(sysUserService, never()).saveUser(any(), anyList());
    }

    @Test
    void acceptsTheColumnBoundaryAndRejectsOneColumnMore() throws Exception {
        when(sysDeptService.list()).thenReturn(List.of());
        when(sysPostService.list()).thenReturn(List.of());
        when(passwordEncoder.encode(PASSWORD)).thenReturn(ENCODED_PASSWORD);

        UserImportResultVo result = service.importUsers(
                workbookWithColumns(SysUserExcelService.MAX_IMPORT_COLUMNS, 1), PASSWORD);
        assertEquals(1, result.getSuccessCount());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.importUsers(
                        workbookWithColumns(SysUserExcelService.MAX_IMPORT_COLUMNS + 1, 1), PASSWORD));
        assertEquals("导入文件超过 20 列限制", error.getMsg());
    }

    @Test
    void rejectsASparseFarCellWithoutLoadingOrWriting() throws Exception {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.importUsers(workbookWithFarCell(), PASSWORD));

        assertEquals("导入文件超过 20 列限制", error.getMsg());
        verify(sysDeptService, never()).list();
        verify(sysPostService, never()).list();
        verify(sysUserService, never()).saveUser(any(), anyList());
    }

    @Test
    void rejectsACellFloodFromWhitespaceOnlyRows() throws Exception {
        // 行是空白（不计入 1000 行），但单元格照常解析：单元格预算必须挡住这种放大。
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.importUsers(whitespaceFlood(2000, 20), PASSWORD));

        assertEquals("导入文件超过 20000 个单元格限制", error.getMsg());
        verify(sysDeptService, never()).list();
        verify(sysUserService, never()).saveUser(any(), anyList());
    }

    @Test
    void rejectsWrongHeadersAndHeaderOnlyFilesWithNamedMessages() throws Exception {
        BusinessException wrongHeader = assertThrows(BusinessException.class,
                () -> service.importUsers(workbookWithHeaders(List.of("用户名", "姓名")), PASSWORD));
        assertEquals("导入文件模板不正确", wrongHeader.getMsg());

        BusinessException headerOnly = assertThrows(BusinessException.class,
                () -> service.importUsers(workbook(0), PASSWORD));
        assertEquals("导入文件没有数据", headerOnly.getMsg());
    }

    private MockMultipartFile workbook(int dataRows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("用户");
            header(sheet, SysUserExcelService.COLUMNS.size());
            for (int index = 0; index < dataRows; index++) {
                row(sheet.createRow(index + 1), "t259_" + index, "用户" + index);
            }
            // 尾部空行不参与计数：数据行恰好 1000 时仍应通过。
            sheet.createRow(dataRows + 1);
            return multipart(workbook);
        }
    }

    private MockMultipartFile workbookWithHeaders(List<String> headers) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("用户");
            header(sheet, headers.size());
            for (int index = 0; index < headers.size(); index++) {
                sheet.getRow(0).getCell(index).setCellValue(headers.get(index));
            }
            return multipart(workbook);
        }
    }

    private MockMultipartFile workbookWithColumns(int columns, int dataRows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("用户");
            header(sheet, columns);
            for (int index = 0; index < dataRows; index++) {
                Row row = sheet.createRow(index + 1);
                row.createCell(0).setCellValue("t259_col_" + index);
                row.createCell(1).setCellValue("宽表用户");
                row.createCell(columns - 1).setCellValue("扩展值");
            }
            return multipart(workbook);
        }
    }

    private MockMultipartFile workbookWithFarCell() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("用户");
            header(sheet, SysUserExcelService.COLUMNS.size());
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("t259_sparse");
            row.createCell(1).setCellValue("稀疏用户");
            sheet.createRow(1_048_575).createCell(16_383).setCellValue("far");
            return multipart(workbook);
        }
    }

    private MockMultipartFile whitespaceFlood(int rows, int columns) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("用户");
            header(sheet, SysUserExcelService.COLUMNS.size());
            for (int index = 0; index < rows; index++) {
                Row row = sheet.createRow(index + 1);
                for (int column = 0; column < columns; column++) {
                    row.createCell(column).setCellValue(" ");
                }
            }
            return multipart(workbook);
        }
    }

    private static void header(Sheet sheet, int columns) {
        Row header = sheet.createRow(0);
        for (int index = 0; index < columns; index++) {
            header.createCell(index).setCellValue(
                    index < SysUserExcelService.COLUMNS.size()
                            ? SysUserExcelService.COLUMNS.get(index)
                            : "扩展" + index);
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

    private static MockMultipartFile multipart(XSSFWorkbook workbook) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        workbook.write(output);
        return new MockMultipartFile("file", "users.xlsx",
                SysUserExcelService.XLSX_CONTENT_TYPE, output.toByteArray());
    }
}
