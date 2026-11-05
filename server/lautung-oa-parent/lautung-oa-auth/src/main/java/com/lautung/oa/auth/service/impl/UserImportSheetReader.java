package com.lautung.oa.auth.service.impl;

import com.lautung.oa.auth.service.SysUserExcelService;
import com.lautung.oa.framework.config.exception.BusinessException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler.SheetContentsHandler;
import org.apache.poi.xssf.model.SharedStrings;
import org.apache.poi.xssf.model.Styles;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户导入的流式读取（#263）：{@link XSSFReader} + SAX 逐行产出，不构建
 * {@code XSSFWorkbook}/整表单元格图。行、列、单元格总数上限在读取过程中执行，
 * 任一超限整文件拒绝，不做部分导入；空行仍跳过且不计入行数。
 */
final class UserImportSheetReader {

    /** Excel 自身的行号上限（1-based）与列号上限（1-based）；越界说明文件结构不可信。 */
    private static final int MAX_EXCEL_ROWS = 1_048_576;

    private static final int MAX_EXCEL_COLUMNS = 16_384;

    private UserImportSheetReader() {
    }

    /** 一行数据：行号从 1 开始（表头是第 1 行），cells 固定模板列数、已 trim。 */
    record ImportRow(int rowNumber, List<String> cells) {
    }

    static List<ImportRow> read(byte[] content) {
        try (OPCPackage pkg = OPCPackage.open(new ByteArrayInputStream(content))) {
            XSSFReader reader = new XSSFReader(pkg);
            Styles styles = reader.getStylesTable();
            SharedStrings strings = reader.getSharedStringsTable();
            XSSFReader.SheetIterator sheets = reader.getSheetIterator();
            if (!sheets.hasNext()) {
                throw new BusinessException(201, "导入文件模板不正确");
            }
            ImportSheetHandler handler = new ImportSheetHandler();
            try (InputStream sheet = sheets.next()) {
                XMLReader parser = XMLHelper.newXMLReader();
                parser.setContentHandler(new XSSFSheetXMLHandler(
                        styles, strings, handler, new DataFormatter(), false));
                parser.parse(new InputSource(sheet));
            }
            return handler.rows();
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            BusinessException business = findBusiness(exception);
            if (business != null) {
                throw business;
            }
            throw new BusinessException(201, "导入文件不是有效的 Excel 文件");
        }
    }

    /** SAX 解析器可能把回调里的业务异常包进 SAXException，逐层找回来。 */
    private static BusinessException findBusiness(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof BusinessException business) {
                return business;
            }
        }
        return null;
    }

    private static final class ImportSheetHandler implements SheetContentsHandler {

        private final List<ImportRow> rows = new ArrayList<>();

        private List<String> cells = new ArrayList<>();

        private int currentRow = -1;

        private int nextColumn;

        private boolean rowHasValue;

        private boolean headerSeen;

        private int dataRows;

        private int dataCells;

        @Override
        public void startRow(int rowNum) {
            currentRow = rowNum;
            nextColumn = 0;
            rowHasValue = false;
            cells = new ArrayList<>(SysUserExcelService.COLUMNS.size());
        }

        @Override
        public void cell(String cellReference, String formattedValue, XSSFComment comment) {
            int column = columnOf(cellReference);
            nextColumn = column + 1;
            requireDimension(column);
            if (column >= SysUserExcelService.MAX_IMPORT_COLUMNS) {
                throw new BusinessException(201,
                        "导入文件超过 " + SysUserExcelService.MAX_IMPORT_COLUMNS + " 列限制");
            }
            if (currentRow > 0) {
                dataCells++;
                if (dataCells > SysUserExcelService.MAX_IMPORT_CELLS) {
                    throw new BusinessException(201,
                            "导入文件超过 " + SysUserExcelService.MAX_IMPORT_CELLS + " 个单元格限制");
                }
            }
            if (column >= SysUserExcelService.COLUMNS.size()) {
                return;
            }
            String value = formattedValue == null ? "" : formattedValue.trim();
            put(cells, column, value);
            if (currentRow > 0 && !value.isEmpty()) {
                rowHasValue = true;
            }
        }

        @Override
        public void endRow(int rowNum) {
            if (rowNum == 0) {
                requireHeader();
                return;
            }
            if (!rowHasValue) {
                return;
            }
            dataRows++;
            if (dataRows > SysUserExcelService.MAX_IMPORT_ROWS) {
                throw new BusinessException(201,
                        "导入文件超过 " + SysUserExcelService.MAX_IMPORT_ROWS + " 行限制");
            }
            rows.add(new ImportRow(rowNum + 1, padded(cells)));
        }

        List<ImportRow> rows() {
            if (!headerSeen) {
                throw new BusinessException(201, "导入文件模板不正确");
            }
            return List.copyOf(rows);
        }

        private void requireHeader() {
            headerSeen = true;
            for (int index = 0; index < SysUserExcelService.COLUMNS.size(); index++) {
                String actual = index < cells.size() ? cells.get(index) : "";
                if (!SysUserExcelService.COLUMNS.get(index).equals(actual)) {
                    throw new BusinessException(201, "导入文件模板不正确");
                }
            }
        }

        private int columnOf(String cellReference) {
            if (cellReference == null || cellReference.isBlank()) {
                // 缺 r 属性时按出现顺序推断列号；上限仍然照常执行。
                return nextColumn;
            }
            CellReference reference;
            try {
                reference = new CellReference(cellReference);
            } catch (IllegalArgumentException exception) {
                throw new BusinessException(201, "导入文件数据维度越界");
            }
            if (reference.getRow() != currentRow) {
                throw new BusinessException(201, "导入文件数据维度越界");
            }
            return reference.getCol();
        }

        private void requireDimension(int column) {
            if (currentRow + 1 > MAX_EXCEL_ROWS || column >= MAX_EXCEL_COLUMNS) {
                throw new BusinessException(201, "导入文件数据维度越界");
            }
        }

        private static void put(List<String> cells, int column, String value) {
            while (cells.size() <= column) {
                cells.add("");
            }
            cells.set(column, value);
        }

        private static List<String> padded(List<String> cells) {
            List<String> values = new ArrayList<>(SysUserExcelService.COLUMNS.size());
            for (int index = 0; index < SysUserExcelService.COLUMNS.size(); index++) {
                values.add(index < cells.size() ? cells.get(index) : "");
            }
            return List.copyOf(values);
        }
    }
}
