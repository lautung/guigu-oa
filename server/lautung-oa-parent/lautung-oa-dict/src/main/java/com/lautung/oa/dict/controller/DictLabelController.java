package com.lautung.oa.dict.controller;

import com.lautung.oa.common.result.Result;
import com.lautung.oa.dict.service.SysDictService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * 字典标签读面（#251，ADR 0062）：登录即有，不设页面授予。
 *
 * <p>字典值不是授权来源；公告、定时任务、登录日志等页面按值渲染标签时不需要
 * {@code /system/dict} 页面授予。与公告读面 {@code NoticeReadController} 同一分层：
 * 读面与后台管理面分开，管理写动作仍由 {@link SysDictController} 收口。
 */
@Tag(name = "字典标签接口")
@RestController
@RequestMapping("/admin/system/dict/data")
public class DictLabelController {

    @Autowired
    private SysDictService sysDictService;

    /** 单类型标签读：只回启用行，按 {@code sort_value} 升序。 */
    @Operation(summary = "按类型读取启用的字典数据")
    @GetMapping("type/{dictType}")
    public Result findByType(@PathVariable String dictType) {
        return Result.ok(sysDictService.findEnabledData(dictType));
    }

    /** 批量标签读（客户端 `useDict` 一次取多组）：空 types 返回空对象。 */
    @Operation(summary = "批量读取启用的字典数据")
    @GetMapping("batch")
    public Result findByTypes(@RequestParam(name = "types", required = false) List<String> types) {
        List<String> requested = types == null
                ? List.of()
                : types.stream()
                        .flatMap(value -> Arrays.stream(value.split(",")))
                        .map(String::trim)
                        .filter(StringUtils::hasText)
                        .distinct()
                        .toList();
        return Result.ok(sysDictService.findEnabledData(requested));
    }
}
