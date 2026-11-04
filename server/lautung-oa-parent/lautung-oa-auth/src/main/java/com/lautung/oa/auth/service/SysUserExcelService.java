package com.lautung.oa.auth.service;

import com.lautung.oa.model.system.DataScope;
import com.lautung.oa.vo.system.SysUserQueryVo;
import com.lautung.oa.vo.system.UserImportResultVo;
import org.springframework.web.multipart.MultipartFile;

import java.io.OutputStream;
import java.util.List;

/**
 * 用户 xlsx 导入导出（#259）：导出按列表同一组筛选与 DataScope 流式写出，导入逐行校验、
 * 失败行不写并汇总返回。密码/口令版本永远不进导出；导入模板列与导出列一致。
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

    /** 模板列与导出列一致，导入后账号没有密码列，统一落这个初始密码并强制 BCrypt。 */
    String DEFAULT_IMPORT_PASSWORD = "123456";

    /**
     * 按当前筛选与数据范围流式写出 xlsx；调用方负责响应头与状态码。
     */
    void export(SysUserQueryVo query, DataScope scope, OutputStream output);

    /** 下载导入模板：只有表头，列与导出完全一致。 */
    void writeTemplate(OutputStream output);

    /**
     * 逐行导入 xlsx：用户名必填且唯一，部门/岗位存在且启用；失败行不写，
     * 成功与失败行号/原因汇总返回，不做全量回滚，也不覆盖已有用户。
     */
    UserImportResultVo importUsers(MultipartFile file);
}
