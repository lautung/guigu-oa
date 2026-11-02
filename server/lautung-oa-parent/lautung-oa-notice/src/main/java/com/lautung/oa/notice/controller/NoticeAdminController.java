package com.lautung.oa.notice.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lautung.oa.common.result.Result;
import com.lautung.oa.framework.operlog.BusinessType;
import com.lautung.oa.framework.operlog.OperLog;
import com.lautung.oa.notice.service.NoticeImageService;
import com.lautung.oa.notice.service.NoticeService;
import com.lautung.oa.security.custom.LoginUserInfoHelper;
import com.lautung.oa.vo.notice.NoticeAdminDetailVo;
import com.lautung.oa.vo.notice.NoticeAdminListVo;
import com.lautung.oa.vo.notice.NoticeImageVo;
import com.lautung.oa.vo.notice.NoticeRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 通知公告管理（ADR 0055/0056）：页面 {@code /system/notice} + 按钮点
 * {@code system:notice:add/edit/remove/publish}。没有 {@code :query} 读权限——页面授予覆盖读。
 *
 * <p>状态机：草稿(0) → 已发布(1) → 已撤回(2)。发布显式；撤回可再发布；任意状态可软删。
 * 图片上传复用 {@code add} 按钮点（新增与编辑共用同一编辑器）。
 */
@Tag(name = "通知公告管理")
@RestController
@RequestMapping("/admin/system/notices")
public class NoticeAdminController {

    private final NoticeService noticeService;
    private final NoticeImageService imageService;

    public NoticeAdminController(NoticeService noticeService, NoticeImageService imageService) {
        this.noticeService = noticeService;
        this.imageService = imageService;
    }

    @PreAuthorize("@backofficeGate.isAllowed('/system/notice')")
    @Operation(summary = "公告分页（管理侧，不含正文）")
    @GetMapping
    public Result<IPage<NoticeAdminListVo>> page(
            @RequestParam(defaultValue = "1") Long page,
            @RequestParam(defaultValue = "10") Long limit,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) Integer status) {
        return Result.ok(noticeService.adminPage(page, limit, title, type, status));
    }

    @PreAuthorize("@backofficeGate.isAllowed('/system/notice')")
    @Operation(summary = "公告详情（管理侧，含草稿正文）")
    @GetMapping("/{id}")
    public Result<NoticeAdminDetailVo> detail(@PathVariable Long id) {
        return Result.ok(noticeService.adminDetail(id));
    }

    @PreAuthorize("@backofficeGate.isAllowed('/system/notice','system:notice:add')")
    @OperLog(title = "通知公告·新增", businessType = BusinessType.CREATE)
    @Operation(summary = "新建公告草稿")
    @PostMapping
    public Result<NoticeAdminDetailVo> create(@Valid @RequestBody NoticeRequest request) {
        return Result.ok(noticeService.create(currentUserId(), request));
    }

    @PreAuthorize("@backofficeGate.isAllowed('/system/notice','system:notice:edit')")
    @OperLog(title = "通知公告·修改", businessType = BusinessType.UPDATE)
    @Operation(summary = "编辑公告（草稿或已发布）")
    @PutMapping("/{id}")
    public Result<NoticeAdminDetailVo> update(
            @PathVariable Long id, @Valid @RequestBody NoticeRequest request) {
        return Result.ok(noticeService.update(currentUserId(), id, request));
    }

    @PreAuthorize("@backofficeGate.isAllowed('/system/notice','system:notice:remove')")
    @OperLog(title = "通知公告·删除", businessType = BusinessType.DELETE)
    @Operation(summary = "删除公告（软删，等价于隐式撤回）")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        noticeService.delete(id);
        return Result.ok();
    }

    @PreAuthorize("@backofficeGate.isAllowed('/system/notice','system:notice:publish')")
    @OperLog(title = "通知公告·发布", businessType = BusinessType.PUBLISH)
    @Operation(summary = "发布公告（草稿或已撤回）")
    @PostMapping("/{id}/publish")
    public Result<NoticeAdminDetailVo> publish(@PathVariable Long id) {
        return Result.ok(noticeService.publish(currentUserId(), id));
    }

    @PreAuthorize("@backofficeGate.isAllowed('/system/notice','system:notice:publish')")
    @OperLog(title = "通知公告·撤回", businessType = BusinessType.UPDATE)
    @Operation(summary = "撤回已发布公告")
    @PostMapping("/{id}/withdraw")
    public Result<NoticeAdminDetailVo> withdraw(@PathVariable Long id) {
        return Result.ok(noticeService.withdraw(id));
    }

    @PreAuthorize("@backofficeGate.isAllowed('/system/notice','system:notice:add')")
    @OperLog(title = "通知公告·上传图片", businessType = BusinessType.CREATE)
    @Operation(summary = "上传公告图片（≤5MB，jpeg/png/gif/webp）")
    @PostMapping("/images")
    public Result<NoticeImageVo> upload(@RequestParam("file") MultipartFile file) {
        return Result.ok(imageService.upload(currentUserId(), file));
    }

    private Long currentUserId() {
        return LoginUserInfoHelper.getUserId();
    }
}
