package com.atguigu.process.attachment;

import com.atguigu.auth.service.SysUserService;
import com.atguigu.common.result.Result;
import com.atguigu.model.system.SysUser;
import com.atguigu.security.custom.LoginUserInfoHelper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "审批附件")
@RestController
@RequestMapping("/admin/attachments")
public class AttachmentController {

    private final AttachmentService attachmentService;
    private final SysUserService sysUserService;

    public AttachmentController(AttachmentService attachmentService, SysUserService sysUserService) {
        this.attachmentService = attachmentService;
        this.sysUserService = sysUserService;
    }

    @Operation(summary = "申请附件直传凭证")
    @PostMapping("/uploads")
    public Result<AttachmentUploadGrant> createUpload(@Valid @RequestBody AttachmentUploadRequest request) {
        return Result.ok(attachmentService.createUpload(currentUser(), request));
    }

    @Operation(summary = "登记已上传的附件")
    @PostMapping("/{attachmentId}/complete")
    public Result<AttachmentView> completeUpload(@PathVariable Long attachmentId) {
        return Result.ok(attachmentService.completeUpload(currentUser(), attachmentId));
    }

    @Operation(summary = "读取当前表单草稿中的待绑定附件")
    @GetMapping("/drafts/{draftId}")
    public Result<List<AttachmentView>> listDraft(@PathVariable String draftId) {
        return Result.ok(attachmentService.listDraft(currentUser(), draftId));
    }

    @Operation(summary = "删除尚未发起的附件")
    @DeleteMapping("/pending/{attachmentId}")
    public Result<Void> deletePending(@PathVariable Long attachmentId) {
        attachmentService.deletePending(currentUser(), attachmentId);
        return Result.ok();
    }

    @Operation(summary = "读取审批附件")
    @GetMapping("/processes/{processId}")
    public Result<ProcessAttachmentList> listForProcess(@PathVariable Long processId) {
        return Result.ok(attachmentService.listForProcess(currentUser(), processId));
    }

    @Operation(summary = "为审批成员签发附件下载凭证")
    @PostMapping("/processes/{processId}/{attachmentId}/download-url")
    public Result<AttachmentDownloadGrant> createDownloadGrant(
            @PathVariable Long processId,
            @PathVariable Long attachmentId) {
        return Result.ok(attachmentService.createDownloadGrant(currentUser(), processId, attachmentId));
    }

    private SysUser currentUser() {
        return sysUserService.getById(LoginUserInfoHelper.getUserId());
    }
}
