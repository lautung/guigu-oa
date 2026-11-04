package com.lautung.oa.vo.system;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户导入的部分成功汇总（#259）：逐行处理，失败行不写，行号与原因逐条返回；
 * 不做全量回滚，也不覆盖已有用户。
 */
@Data
@Schema(description = "用户导入结果")
public class UserImportResultVo {

    @Schema(description = "数据行总数（不含表头与空行）")
    private int total;

    @Schema(description = "成功写入的用户数")
    private int successCount;

    @Schema(description = "失败行数")
    private int failureCount;

    @Schema(description = "失败行明细")
    private List<RowFailure> failures = new ArrayList<>();

    public void addFailure(int rowNumber, String username, String reason) {
        failures.add(new RowFailure(rowNumber, username, reason));
        failureCount = failures.size();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "导入失败行")
    public static class RowFailure {

        @Schema(description = "Excel 行号（表头是第 1 行）")
        private int rowNumber;

        @Schema(description = "该行提交的用户名")
        private String username;

        @Schema(description = "失败原因")
        private String reason;
    }
}
