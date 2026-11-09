package com.lautung.oa.process.attachment;

import com.lautung.oa.framework.config.exception.BusinessException;
import com.lautung.oa.model.process.Attachment;
import com.lautung.oa.model.process.Process;
import com.lautung.oa.model.system.SysUser;
import com.lautung.oa.process.mapper.OaProcessMapper;
import com.lautung.oa.process.membership.ApprovalMembership;
import com.lautung.oa.process.membership.ApprovalMembershipResolver;
import com.lautung.oa.storage.StorageS3Properties;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AttachmentServiceTest {

    private static final String DRAFT_ID = "11111111-1111-1111-1111-111111111111";

    private final AttachmentMapper attachmentMapper = mock(AttachmentMapper.class);
    private final OaProcessMapper processMapper = mock(OaProcessMapper.class);
    private final AttachmentObjectStore objectStore = mock(AttachmentObjectStore.class);
    private final StorageS3Properties properties = new StorageS3Properties();
    private final ApprovalMembershipResolver membershipResolver = mock(ApprovalMembershipResolver.class);
    private final AttachmentService service = new AttachmentService(
            attachmentMapper, processMapper, objectStore, properties, membershipResolver);

    private final SysUser user = user(1L);

    @BeforeAll
    static void registerTableMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), Attachment.class);
    }

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void rejectsNullRequestsAndInvalidContentTypes() {
        assertEquals(201, code(() -> service.createUpload(user, null)));
        assertEquals(201, code(() -> service.createUpload(user, request("a.txt", "not a type", 1L, null))));
        assertEquals(201, code(() -> service.createUpload(user, request("a.txt", "a/".repeat(200), 1L, null))));
    }

    @Test
    void rejectsFilesAboveTheConfiguredLimit() {
        properties.setMaxFileSizeBytes(10L);

        assertEquals(201, code(() ->
                service.createUpload(user, request("a.txt", "text/plain", 11L, DRAFT_ID))));
    }

    @Test
    void rejectsInvalidDraftIds() {
        assertEquals(201, code(() ->
                service.createUpload(user, request("a.txt", "text/plain", 1L, "not-a-uuid"))));
    }

    @Test
    void defaultsTheContentTypeAndRegistersADraftUpload() {
        when(objectStore.presignUpload(any(), any(), any())).thenReturn("https://s3.test/put");

        AttachmentUploadGrant grant = service.createUpload(
                user, request("dir\\a.txt", " text/plain ", 10L, DRAFT_ID));

        assertEquals("https://s3.test/put", grant.uploadUrl());
        ArgumentCaptor<Attachment> inserted = ArgumentCaptor.forClass(Attachment.class);
        verify(attachmentMapper).insert(inserted.capture());
        Attachment attachment = inserted.getValue();
        assertEquals(1L, attachment.getOwnerUserId());
        assertNull(attachment.getProcessId());
        assertEquals(DRAFT_ID, attachment.getDraftId());
        assertEquals("a.txt", attachment.getFileName());
        assertEquals("text/plain", attachment.getContentType());
        assertEquals(Attachment.UPLOADING, attachment.getStatus());
        assertTrue(attachment.getObjectKey().startsWith("pending/"));
        assertTrue(attachment.getUploadExpiresAt().after(new Date()));
    }

    @Test
    void rejectsAnUploadWhenTheApprovalMembershipForbidsIt() {
        when(processMapper.selectById(9L)).thenReturn(process(9L));
        when(membershipResolver.resolve(any(), any())).thenReturn(ApprovalMembership.empty());

        assertEquals(205, code(() ->
                service.createUpload(user, request("a.txt", "text/plain", 1L, 9L, null))));
    }

    @Test
    void registersAnUploadOnTheApprovalForAnApplicant() {
        when(processMapper.selectById(9L)).thenReturn(process(9L));
        when(membershipResolver.resolve(any(), any()))
                .thenReturn(new ApprovalMembership(true, Set.of(), Set.of(), Set.of(), false, false, false));
        when(objectStore.presignUpload(any(), any(), any())).thenReturn("https://s3.test/put");

        service.createUpload(user, request("a.txt", "text/plain", 1L, 9L, null));

        ArgumentCaptor<Attachment> inserted = ArgumentCaptor.forClass(Attachment.class);
        verify(attachmentMapper).insert(inserted.capture());
        assertEquals(9L, inserted.getValue().getProcessId());
        assertNull(inserted.getValue().getDraftId());
    }

    @Test
    void completeUploadRejectsMissingForeignOrNoLongerPendingAttachments() {
        assertEquals(205, code(() -> service.completeUpload(user, 1L)));

        Attachment foreign = uploading(1L, 2L);
        when(attachmentMapper.selectById(1L)).thenReturn(foreign);
        assertEquals(205, code(() -> service.completeUpload(user, 1L)));

        Attachment notUploading = uploading(1L, 1L);
        notUploading.setStatus(Attachment.PENDING);
        when(attachmentMapper.selectById(2L)).thenReturn(notUploading);
        assertEquals(205, code(() -> service.completeUpload(user, 2L)));

        Attachment expired = uploading(1L, 1L);
        expired.setUploadExpiresAt(Date.from(Instant.now().minusSeconds(60)));
        when(attachmentMapper.selectById(3L)).thenReturn(expired);
        assertEquals(201, code(() -> service.completeUpload(user, 3L)));
    }

    @Test
    void completeUploadRejectsChangedContentAndForbiddenMembership() {
        Attachment attachment = uploading(1L, 1L);
        when(attachmentMapper.selectById(1L)).thenReturn(attachment);
        when(objectStore.head("pending/storage-1"))
                .thenReturn(new AttachmentObjectStore.StoredObject(11L, "text/plain"));
        assertEquals(201, code(() -> service.completeUpload(user, 1L)));

        when(objectStore.head("pending/storage-1"))
                .thenReturn(new AttachmentObjectStore.StoredObject(10L, "image/png"));
        assertEquals(201, code(() -> service.completeUpload(user, 1L)));

        Attachment onProcess = uploading(1L, 1L);
        onProcess.setProcessId(9L);
        when(attachmentMapper.selectById(2L)).thenReturn(onProcess);
        when(processMapper.selectById(9L)).thenReturn(process(9L));
        when(membershipResolver.resolve(any(), any())).thenReturn(ApprovalMembership.empty());
        assertEquals(205, code(() -> service.completeUpload(user, 2L)));
    }

    @Test
    void completeUploadMovesTheDraftToPending() {
        Attachment attachment = uploading(1L, 1L);
        when(attachmentMapper.selectById(1L)).thenReturn(attachment);
        when(objectStore.head("pending/storage-1"))
                .thenReturn(new AttachmentObjectStore.StoredObject(10L, "text/plain"));

        AttachmentView view = service.completeUpload(user, 1L);

        assertEquals("a.txt", view.fileName());
        assertEquals(Attachment.PENDING, attachment.getStatus());
        verify(attachmentMapper).updateById(attachment);
        verify(objectStore, never()).copy(any(), any());
    }

    @Test
    void completeUploadCopiesToPermanentStorageAndDeletesTheTemporaryObjectAfterCommit() {
        Attachment attachment = uploading(1L, 1L);
        attachment.setProcessId(9L);
        when(attachmentMapper.selectById(1L)).thenReturn(attachment);
        when(processMapper.selectById(9L)).thenReturn(process(9L));
        when(membershipResolver.resolve(any(), any()))
                .thenReturn(new ApprovalMembership(true, Set.of(), Set.of(), Set.of(), false, false, false));
        when(objectStore.head("pending/storage-1"))
                .thenReturn(new AttachmentObjectStore.StoredObject(10L, "text/plain"));
        TransactionSynchronizationManager.initSynchronization();

        service.completeUpload(user, 1L);

        assertEquals(Attachment.ATTACHED, attachment.getStatus());
        assertEquals("attachments/storage-1", attachment.getObjectKey());
        assertEquals("pending/storage-1", attachment.getTemporaryObjectKey());
        verify(objectStore).copy("pending/storage-1", "attachments/storage-1");
        List<TransactionSynchronization> synchronizations =
                TransactionSynchronizationManager.getSynchronizations();
        assertEquals(1, synchronizations.size());
        synchronizations.get(0).afterCommit();
        verify(objectStore).delete("pending/storage-1");
    }

    @Test
    void completeUploadSwallowsCleanupFailures() {
        Attachment attachment = uploading(1L, 1L);
        attachment.setProcessId(9L);
        when(attachmentMapper.selectById(1L)).thenReturn(attachment);
        when(processMapper.selectById(9L)).thenReturn(process(9L));
        when(membershipResolver.resolve(any(), any()))
                .thenReturn(new ApprovalMembership(true, Set.of(), Set.of(), Set.of(), false, false, false));
        when(objectStore.head("pending/storage-1"))
                .thenReturn(new AttachmentObjectStore.StoredObject(10L, "text/plain"));
        org.mockito.Mockito.doThrow(new IllegalStateException("s3 down"))
                .when(objectStore).delete("pending/storage-1");
        TransactionSynchronizationManager.initSynchronization();

        service.completeUpload(user, 1L);
        TransactionSynchronizationManager.getSynchronizations().get(0).afterCommit();
    }

    @Test
    void bindDraftRejectsInvalidListsAndMismatchedRows() {
        service.bindDraft(user, DRAFT_ID, null, 9L);
        service.bindDraft(user, DRAFT_ID, List.of(), 9L);

        assertEquals(201, code(() -> service.bindDraft(user, DRAFT_ID, List.of(1L, 1L), 9L)));
        List<Long> withNull = new ArrayList<>();
        withNull.add(1L);
        withNull.add(null);
        assertEquals(201, code(() -> service.bindDraft(user, DRAFT_ID, withNull, 9L)));
        assertEquals(201, code(() -> service.bindDraft(user, "not-a-uuid", List.of(1L), 9L)));

        when(attachmentMapper.selectList(any())).thenReturn(List.of());
        assertEquals(201, code(() -> service.bindDraft(user, DRAFT_ID, List.of(1L), 9L)));
    }

    @Test
    void bindDraftCopiesPendingObjectsAndCompensatesOnlyOnRollback() {
        Attachment first = pending(1L, "storage-1");
        Attachment second = pending(2L, "storage-2");
        when(attachmentMapper.selectList(any())).thenReturn(List.of(first, second));
        TransactionSynchronizationManager.initSynchronization();

        service.bindDraft(user, DRAFT_ID, List.of(1L, 2L), 9L);

        verify(objectStore).copy("pending/storage-1", "attachments/storage-1");
        verify(objectStore).copy("pending/storage-2", "attachments/storage-2");
        assertEquals(4, TransactionSynchronizationManager.getSynchronizations().size());
        for (TransactionSynchronization synchronization
                : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }
        verify(objectStore).delete("attachments/storage-1");
        verify(objectStore).delete("attachments/storage-2");
    }

    @Test
    void bindDraftKeepsThePermanentCopyWhenTheCommitSucceeds() {
        when(attachmentMapper.selectList(any())).thenReturn(List.of(pending(1L, "storage-1")));
        TransactionSynchronizationManager.initSynchronization();

        service.bindDraft(user, DRAFT_ID, List.of(1L), 9L);
        for (TransactionSynchronization synchronization
                : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        }

        verify(objectStore, never()).delete("attachments/storage-1");
    }

    @Test
    void listDraftValidatesTheDraftIdAndMapsRows() {
        assertEquals(201, code(() -> service.listDraft(user, "bad")));

        when(attachmentMapper.selectList(any())).thenReturn(List.of(pending(1L, "storage-1")));
        List<AttachmentView> views = service.listDraft(user, DRAFT_ID);

        assertEquals(1, views.size());
        assertEquals("a.txt", views.get(0).fileName());
    }

    @Test
    void listForProcessRequiresReadAccessAndReportsUploadCapability() {
        when(processMapper.selectById(9L)).thenReturn(process(9L));
        when(membershipResolver.resolve(any(), any())).thenReturn(ApprovalMembership.empty());
        assertEquals(205, code(() -> service.listForProcess(user, 9L)));

        when(membershipResolver.resolve(any(), any()))
                .thenReturn(new ApprovalMembership(true, Set.of(), Set.of(), Set.of(), false, false, false));
        when(attachmentMapper.selectList(any())).thenReturn(List.of(pending(1L, "storage-1")));
        ProcessAttachmentList list = service.listForProcess(user, 9L);

        assertEquals(1, list.attachments().size());
        assertTrue(list.canUpload());
    }

    @Test
    void deletePendingGuardsOwnershipAndDeletesBothObjectCopies() {
        assertEquals(205, code(() -> service.deletePending(user, 1L)));

        Attachment foreign = pending(1L, "storage-1");
        foreign.setOwnerUserId(2L);
        when(attachmentMapper.selectById(1L)).thenReturn(foreign);
        assertEquals(205, code(() -> service.deletePending(user, 1L)));

        Attachment bound = pending(2L, "storage-1");
        bound.setProcessId(9L);
        when(attachmentMapper.selectById(2L)).thenReturn(bound);
        assertEquals(205, code(() -> service.deletePending(user, 2L)));

        Attachment attached = pending(3L, "storage-1");
        attached.setStatus(Attachment.ATTACHED);
        when(attachmentMapper.selectById(3L)).thenReturn(attached);
        assertEquals(205, code(() -> service.deletePending(user, 3L)));

        Attachment deletable = pending(4L, "storage-1");
        when(attachmentMapper.selectById(4L)).thenReturn(deletable);
        service.deletePending(user, 4L);

        assertEquals(Attachment.DELETED, deletable.getStatus());
        verify(attachmentMapper).updateById(deletable);
        verify(objectStore).delete("pending/storage-1");
        verify(objectStore).delete("attachments/storage-1");
    }

    @Test
    void createDownloadGrantGuardsTheAttachmentAndPresigns() {
        assertEquals(205, code(() -> service.createDownloadGrant(user, 9L, 1L)));

        Attachment foreignProcess = attached(1L, 8L);
        when(attachmentMapper.selectById(1L)).thenReturn(foreignProcess);
        assertEquals(205, code(() -> service.createDownloadGrant(user, 9L, 1L)));

        Attachment pendingAttachment = attached(2L, 9L);
        pendingAttachment.setStatus(Attachment.PENDING);
        when(attachmentMapper.selectById(2L)).thenReturn(pendingAttachment);
        assertEquals(205, code(() -> service.createDownloadGrant(user, 9L, 2L)));

        Attachment attachment = attached(3L, 9L);
        when(attachmentMapper.selectById(3L)).thenReturn(attachment);
        when(processMapper.selectById(9L)).thenReturn(process(9L));
        when(membershipResolver.resolve(any(), any())).thenReturn(ApprovalMembership.empty());
        assertEquals(205, code(() -> service.createDownloadGrant(user, 9L, 3L)));

        when(membershipResolver.resolve(any(), any()))
                .thenReturn(new ApprovalMembership(false, Set.of(), Set.of(), Set.of(), false, true, false));
        when(objectStore.presignDownload(any(), any(), any())).thenReturn("https://s3.test/get");
        AttachmentDownloadGrant grant = service.createDownloadGrant(user, 9L, 3L);

        assertEquals("https://s3.test/get", grant.downloadUrl());
        assertTrue(grant.expiresAt().after(new Date()));
    }

    @Test
    void cleanupExpiredPendingDeletesMetadataAndSurvivesStorageFailures() {
        Attachment first = pending(1L, "storage-1");
        first.setStatus(Attachment.UPLOADING);
        Attachment second = attached(2L, 9L);
        second.setTemporaryObjectKey("pending/storage-2");
        when(attachmentMapper.selectList(any())).thenReturn(List.of(first), List.of(second));
        org.mockito.Mockito.doThrow(new IllegalStateException("s3 down"))
                .when(objectStore).delete("pending/storage-1");

        service.cleanupExpiredPending();

        verify(attachmentMapper, never()).deleteById(1L);
        verify(objectStore).delete("pending/storage-2");
        verify(attachmentMapper).update(eq(null), any());
    }

    private int code(org.junit.jupiter.api.function.Executable executable) {
        return assertThrows(BusinessException.class, executable).getCode();
    }

    private SysUser user(Long id) {
        SysUser user = new SysUser();
        user.setId(id);
        return user;
    }

    private Process process(Long id) {
        Process process = new Process();
        process.setId(id);
        return process;
    }

    private AttachmentUploadRequest request(String fileName, String contentType, Long size, String draftId) {
        return request(fileName, contentType, size, null, draftId);
    }

    private AttachmentUploadRequest request(String fileName, String contentType, Long size,
                                            Long processId, String draftId) {
        AttachmentUploadRequest request = new AttachmentUploadRequest();
        request.setFileName(fileName);
        request.setContentType(contentType);
        request.setSizeBytes(size);
        request.setProcessId(processId);
        request.setDraftId(draftId);
        return request;
    }

    private Attachment uploading(Long id, Long owner) {
        Attachment attachment = base(id, owner);
        attachment.setStatus(Attachment.UPLOADING);
        return attachment;
    }

    private Attachment pending(Long id, String storageId) {
        Attachment attachment = base(id, 1L);
        attachment.setStatus(Attachment.PENDING);
        attachment.setStorageId(storageId);
        attachment.setObjectKey("pending/" + storageId);
        return attachment;
    }

    private Attachment attached(Long id, Long processId) {
        Attachment attachment = base(id, 1L);
        attachment.setStatus(Attachment.ATTACHED);
        attachment.setProcessId(processId);
        attachment.setStorageId("storage-" + id);
        attachment.setObjectKey("attachments/storage-" + id);
        return attachment;
    }

    private Attachment base(Long id, Long owner) {
        Attachment attachment = new Attachment();
        attachment.setId(id);
        attachment.setOwnerUserId(owner);
        attachment.setStorageId("storage-" + id);
        attachment.setObjectKey("pending/storage-" + id);
        attachment.setFileName("a.txt");
        attachment.setContentType("text/plain");
        attachment.setSizeBytes(10L);
        return attachment;
    }
}
