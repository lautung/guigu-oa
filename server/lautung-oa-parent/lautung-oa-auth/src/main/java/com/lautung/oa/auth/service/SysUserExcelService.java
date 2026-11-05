package com.lautung.oa.auth.service;

import com.lautung.oa.model.system.DataScope;
import com.lautung.oa.vo.system.SysUserQueryVo;
import com.lautung.oa.vo.system.UserImportResultVo;
import org.springframework.web.multipart.MultipartFile;

import java.io.OutputStream;
import java.util.List;

/**
 * 用户 xlsx 导入导出（#259/#263）：导出按列表同一组筛选与 DataScope 流式写出，导入逐行校验、
 * 失败行不写并汇总返回。密码/口令版本永远不进导出；导入模板列与导出列一致，初始密码是
 * 导入动作的参数而不是数据列。
 */
public interface SysUserExcelService {

    /** xlsx 的正式 MIME；导入按扩展名 + ZIP 魔数判型，导出响应头用它。 */
    String XLSX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /** 导出列固定：用户名、姓名、部门、岗位、手机、状态、创建时间。 */
    List<String> COLUMNS = List.of("用户名", "姓名", "部门", "岗位", "手机", "状态", "创建时间");

    /** 写 xlsx 时每页读取的用户行数，与分页上限一致。 */
    int EXPORT_PAGE_SIZE = 500;

    /** 导入文件业务上限：容器放到 6MB，service 再拒 2MB 以上。 */
    long MAX_IMPORT_BYTES = 2L * 1024L * 1024L;

    /** 导入数据行上限（跳过空行）：每行独立事务 + BCrypt，行数不设限会把线程占满。 */
    int MAX_IMPORT_ROWS = 1000;

    /** 导入列数上限：模板 7 列 + 余量；隐藏列再多就整文件拒绝，不做部分导入。 */
    int MAX_IMPORT_COLUMNS = 20;

    /** 导入数据区单元格总数上限（表头不计）：1000 行 × 20 列的边界值。 */
    int MAX_IMPORT_CELLS = 20_000;

    /** 初始密码长度区间：与重置密码/注册同一策略（min 6 / max 100）。 */
    int MIN_INITIAL_PASSWORD_LENGTH = 6;

    int MAX_INITIAL_PASSWORD_LENGTH = 100;

    /**
     * 按当前筛选与数据范围流式写出 xlsx；调用方负责响应头与状态码。
     */
    void export(SysUserQueryVo query, DataScope scope, OutputStream output);

    /** 下载导入模板：只有表头，列与导出完全一致。 */
    void writeTemplate(OutputStream output);

    /**
     * 逐行导入 xlsx：初始密码必填且 6–100 位，按提交值 BCrypt 落库；用户名必填且唯一，
     * 部门/岗位存在且启用；失败行不写，成功与失败行号/原因汇总返回，不做全量回滚，
     * 也不覆盖已有用户。行列与单元格超限整文件拒绝。
     */
    UserImportResultVo importUsers(MultipartFile file, String initialPassword);
}
