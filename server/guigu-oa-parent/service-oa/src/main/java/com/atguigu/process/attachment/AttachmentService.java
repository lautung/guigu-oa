package com.atguigu.process.attachment;

import com.atguigu.common.config.exception.GuiguException;
import com.atguigu.model.process.Attachment;
import com.atguigu.model.process.Process;
import com.atguigu.model.system.SysUser;
import com.atguigu.process.mapper.OaProcessMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class AttachmentService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentService.class);
    private static final Pattern CONTENT_TYPE = Pattern.compile("^[A-Za-z0-9!#$&^_.+-]+/[A-Za-z0-9!#$&^_.+-]+$");

    private final AttachmentMapper attachmentMapper;
    private final OaProcessMapper processMapper;
    private final AttachmentObjectStore objectStore;
    private final AttachmentS3Properties properties;
    private final ProcessMemberAuthorizer memberAuthorizer;

    public AttachmentService(
            AttachmentMapper attachmentMapper,
            OaProcessMapper processMapper,
            AttachmentObjectStore objectStore,
            AttachmentS3Properties properties,
            ProcessMemberAuthorizer memberAuthorizer) {
        this.attachmentMapper = attachmentMapper;
        this.processMapper = processMapper;
        this.objectStore = objectStore;
        this.properties = properties;
        this.memberAuthorizer = memberAuthorizer;
    }

    @Transactional
    public AttachmentUploadGrant createUpload(SysUser user, AttachmentUploadRequest request) {
        validateRequest(request);
        if (properties.getMaxFileSizeBytes() > 0 && request.getSizeBytes() > properties.getMaxFileSizeBytes()) {
            throw new GuiguException(201, "附件超过大小限制");
        }

        String draftId = null;
        Process process = null;
        if (request.getProcessId() == null) {
            draftId = validDraftId(request.getDraftId());
        } else {
            process = processMapper.selectById(request.getProcessId());
            if (!memberAuthorizer.canUpload(user, process)) {
                throw denied();
            }
        }

        String storageId = UUID.randomUUID().toString();
        Attachment attachment = new Attachment();
        attachment.setOwnerUserId(user.getId());
        attachment.setProcessId(process == null ? null : process.getId());
        attachment.setDraftId(draftId);
        attachment.setStorageId(storageId);
        attachment.setObjectKey(pendingKey(storageId));
        attachment.setFileName(safeFileName(request.getFileName()));
        attachment.setContentType(request.getContentType());
        attachment.setSizeBytes(request.getSizeBytes());
        attachment.setStatus(Attachment.UPLOADING);
        Date expiresAt = Date.from(Instant.now().plus(properties.getUploadUrlTtl()));
        attachment.setUploadExpiresAt(expiresAt);
        attachmentMapper.insert(attachment);

        String uploadUrl = objectStore.presignUpload(
                attachment.getObjectKey(), attachment.getContentType(), properties.getUploadUrlTtl());
        return new AttachmentUploadGrant(
                attachment.getId(), uploadUrl, Map.of("Content-Type", attachment.getContentType()), expiresAt);
    }

    @Transactional
    public AttachmentView completeUpload(SysUser user, Long attachmentId) {
        Attachment attachment = attachmentMapper.selectById(attachmentId);
        if (attachment == null || !user.getId().equals(attachment.getOwnerUserId())
                || !Attachment.UPLOADING.equals(attachment.getStatus())) {
            throw denied();
        }
        if (attachment.getUploadExpiresAt() != null && attachment.getUploadExpiresAt().before(new Date())) {
            throw new GuiguException(201, "附件上传凭证已过期，请重新选择文件");
        }
        if (attachment.getProcessId() != null) {
            Process process = processMapper.selectById(attachment.getProcessId());
            if (!memberAuthorizer.canUpload(user, process)) {
                throw denied();
            }
        }

        AttachmentObjectStore.StoredObject uploaded = objectStore.head(attachment.getObjectKey());
        if (uploaded.sizeBytes() != attachment.getSizeBytes()
                || !attachment.getContentType().equalsIgnoreCase(uploaded.contentType())) {
            throw new GuiguException(201, "附件内容与登记信息不一致");
        }

        if (attachment.getProcessId() == null) {
            attachment.setStatus(Attachment.PENDING);
        } else {
            String temporaryKey = attachment.getObjectKey();
            String permanentKey = permanentKey(attachment.getStorageId());
            objectStore.copy(temporaryKey, permanentKey);
            attachment.setObjectKey(permanentKey);
            attachment.setTemporaryObjectKey(temporaryKey);
            attachment.setStatus(Attachment.ATTACHED);
            deleteAfterCommit(temporaryKey);
        }
        attachmentMapper.updateById(attachment);
        return AttachmentView.from(attachment);
    }

    @Transactional
    public void bindDraft(SysUser user, String draftId, List<Long> attachmentIds, Long processId) {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return;
        }
        String validatedDraftId = validDraftId(draftId);
        Set<Long> requestedIds = new HashSet<>(attachmentIds);
        if (requestedIds.size() != attachmentIds.size() || requestedIds.contains(null)) {
            throw new GuiguException(201, "附件列表无效");
        }

        LambdaQueryWrapper<Attachment> query = new LambdaQueryWrapper<>();
        query.eq(Attachment::getOwnerUserId, user.getId())
                .eq(Attachment::getDraftId, validatedDraftId)
                .eq(Attachment::getStatus, Attachment.PENDING)
                .isNull(Attachment::getProcessId)
                .ge(Attachment::getCreateTime, Date.from(Instant.now().minus(properties.getPendingTtl())))
                .in(Attachment::getId, requestedIds)
                .last("FOR UPDATE");
        List<Attachment> pending = attachmentMapper.selectList(query);
        if (pending.size() != requestedIds.size()) {
            throw new GuiguException(201, "所选附件不存在、尚未上传完成或已被绑定");
        }
        for (Attachment attachment : pending) {
            String temporaryKey = pendingKey(attachment.getStorageId());
            String permanentKey = permanentKey(attachment.getStorageId());
            objectStore.copy(temporaryKey, permanentKey);
            attachment.setObjectKey(permanentKey);
            attachment.setTemporaryObjectKey(temporaryKey);
            attachment.setProcessId(processId);
            attachment.setDraftId(null);
            attachment.setStatus(Attachment.ATTACHED);
            attachmentMapper.updateById(attachment);
            deleteAfterCommit(temporaryKey);
        }
    }

    public List<AttachmentView> listDraft(SysUser user, String draftId) {
        String validatedDraftId = validDraftId(draftId);
        LambdaQueryWrapper<Attachment> query = new LambdaQueryWrapper<>();
        query.eq(Attachment::getOwnerUserId, user.getId())
                .eq(Attachment::getDraftId, validatedDraftId)
                .eq(Attachment::getStatus, Attachment.PENDING)
                .isNull(Attachment::getProcessId)
                .ge(Attachment::getCreateTime, Date.from(Instant.now().minus(properties.getPendingTtl())))
                .orderByAsc(Attachment::getId);
        return attachmentMapper.selectList(query).stream().map(AttachmentView::from).toList();
    }

    public ProcessAttachmentList listForProcess(SysUser user, Long processId) {
        Process process = processMapper.selectById(processId);
        if (!memberAuthorizer.canRead(user, process)) {
            throw denied();
        }
        LambdaQueryWrapper<Attachment> query = new LambdaQueryWrapper<>();
        query.eq(Attachment::getProcessId, processId)
                .eq(Attachment::getStatus, Attachment.ATTACHED)
                .orderByAsc(Attachment::getId);
        return new ProcessAttachmentList(
                attachmentMapper.selectList(query).stream().map(AttachmentView::from).toList(),
                memberAuthorizer.canUpload(user, process));
    }

    @Transactional
    public void deletePending(SysUser user, Long attachmentId) {
        Attachment attachment = attachmentMapper.selectById(attachmentId);
        if (attachment == null || !user.getId().equals(attachment.getOwnerUserId())
                || attachment.getProcessId() != null
                || !(Attachment.PENDING.equals(attachment.getStatus())
                || Attachment.UPLOADING.equals(attachment.getStatus()))) {
            throw denied();
        }
        attachment.setStatus(Attachment.DELETED);
        attachmentMapper.updateById(attachment);
        deleteAfterCommit(pendingKey(attachment.getStorageId()));
        deleteAfterCommit(permanentKey(attachment.getStorageId()));
    }

    public AttachmentDownloadGrant createDownloadGrant(
            SysUser user, Long processId, Long attachmentId) {
        Attachment attachment = attachmentMapper.selectById(attachmentId);
        if (attachment == null || !processId.equals(attachment.getProcessId())
                || !Attachment.ATTACHED.equals(attachment.getStatus())) {
            throw denied();
        }
        Process process = processMapper.selectById(processId);
        if (!memberAuthorizer.canRead(user, process)) {
            throw denied();
        }
        Date expiresAt = Date.from(Instant.now().plus(properties.getDownloadUrlTtl()));
        String url = objectStore.presignDownload(
                attachment.getObjectKey(), attachment.getFileName(), properties.getDownloadUrlTtl());
        return new AttachmentDownloadGrant(url, expiresAt);
    }

    @Scheduled(
            fixedDelayString = "${guigu.oa.attachments.cleanup-interval:PT1H}",
            initialDelayString = "${guigu.oa.attachments.cleanup-interval:PT1H}")
    @Transactional
    public void cleanupExpiredPending() {
        Date cutoff = Date.from(Instant.now().minus(properties.getPendingTtl()));
        LambdaQueryWrapper<Attachment> query = new LambdaQueryWrapper<>();
        query.in(Attachment::getStatus, List.of(Attachment.UPLOADING, Attachment.PENDING, Attachment.DELETED))
                .le(Attachment::getCreateTime, cutoff)
                .and(filter -> filter.isNull(Attachment::getProcessId)
                        .or().eq(Attachment::getStatus, Attachment.UPLOADING));
        for (Attachment attachment : attachmentMapper.selectList(query)) {
            try {
                objectStore.delete(pendingKey(attachment.getStorageId()));
                objectStore.delete(permanentKey(attachment.getStorageId()));
                attachmentMapper.deleteById(attachment.getId());
            } catch (RuntimeException exception) {
                log.warn("Unable to collect expired pending attachment metadata id={}", attachment.getId());
            }
        }

        LambdaQueryWrapper<Attachment> temporaryObjects = new LambdaQueryWrapper<>();
        temporaryObjects.eq(Attachment::getStatus, Attachment.ATTACHED)
                .isNotNull(Attachment::getTemporaryObjectKey)
                .le(Attachment::getUploadExpiresAt, new Date());
        for (Attachment attachment : attachmentMapper.selectList(temporaryObjects)) {
            try {
                objectStore.delete(attachment.getTemporaryObjectKey());
                LambdaUpdateWrapper<Attachment> clearTemporaryKey = new LambdaUpdateWrapper<>();
                clearTemporaryKey.eq(Attachment::getId, attachment.getId())
                        .set(Attachment::getTemporaryObjectKey, null);
                attachmentMapper.update(null, clearTemporaryKey);
            } catch (RuntimeException exception) {
                log.warn("Unable to collect temporary object for attached metadata id={}", attachment.getId());
            }
        }
    }

    private void validateRequest(AttachmentUploadRequest request) {
        if (request == null || !StringUtils.hasText(request.getFileName())
                || request.getFileName().length() > 512
                || request.getSizeBytes() == null || request.getSizeBytes() <= 0) {
            throw new GuiguException(201, "附件信息无效");
        }
        String contentType = StringUtils.hasText(request.getContentType())
                ? request.getContentType().trim()
                : "application/octet-stream";
        if (contentType.length() > 255 || !CONTENT_TYPE.matcher(contentType).matches()) {
            throw new GuiguException(201, "附件类型无效");
        }
        request.setContentType(contentType);
    }

    private String validDraftId(String draftId) {
        try {
            return UUID.fromString(draftId).toString();
        } catch (RuntimeException exception) {
            throw new GuiguException(201, "附件草稿无效");
        }
    }

    private String safeFileName(String fileName) {
        String normalized = fileName.replace('\\', '/');
        int lastSeparator = normalized.lastIndexOf('/');
        String baseName = normalized.substring(lastSeparator + 1);
        String cleaned = baseName.replaceAll("[\\p{Cntrl}]", "_").trim();
        if (!StringUtils.hasText(cleaned)) {
            throw new GuiguException(201, "附件文件名无效");
        }
        return cleaned.length() <= 255 ? cleaned : cleaned.substring(cleaned.length() - 255);
    }

    private String pendingKey(String storageId) {
        return "pending/" + storageId;
    }

    private String permanentKey(String storageId) {
        return "attachments/" + storageId;
    }

    private void deleteAfterCommit(String key) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        objectStore.delete(key);
                    } catch (RuntimeException exception) {
                        log.warn("Unable to delete temporary attachment object after commit");
                    }
                }
            });
            return;
        }
        try {
            objectStore.delete(key);
        } catch (RuntimeException exception) {
            log.warn("Unable to delete temporary attachment object");
        }
    }

    private GuiguException denied() {
        return new GuiguException(205, "没有操作权限");
    }
}
