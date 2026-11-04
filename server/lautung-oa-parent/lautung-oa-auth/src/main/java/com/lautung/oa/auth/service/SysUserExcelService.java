package com.lautung.oa.auth.service;

import com.lautung.oa.model.system.DataScope;
import com.lautung.oa.vo.system.SysUserQueryVo;

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

    /**
     * 按当前筛选与数据范围流式写出 xlsx；调用方负责响应头与状态码。
     */
    void export(SysUserQueryVo query, DataScope scope, OutputStream output);
}
