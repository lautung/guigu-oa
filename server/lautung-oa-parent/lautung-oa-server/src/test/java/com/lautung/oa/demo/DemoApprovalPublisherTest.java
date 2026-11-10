package com.lautung.oa.demo;

import com.lautung.oa.model.process.ProcessTemplate;
import com.lautung.oa.process.ApprovalTemplateSupport;
import com.lautung.oa.process.ProcessDefinitionStorage;
import com.lautung.oa.process.service.ApprovalTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DemoApprovalPublisherTest {

    private static final Long TEMPLATE_ID = 3L;

    @TempDir
    Path directory;

    private final ApprovalTemplate templateService = mock(ApprovalTemplate.class);
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

    private ProcessDefinitionStorage storage;
    private DemoApprovalPublisher publisher;

    @BeforeEach
    void createPublisher() {
        storage = new ProcessDefinitionStorage(directory);
        publisher = new DemoApprovalPublisher(templateService, storage, jdbcTemplate);
    }

    @Test
    void publishesUnpublishedDemoTemplateWithClasspathArtifact() throws IOException {
        ProcessTemplate unpublished = template(ApprovalTemplateSupport.未发布, null);
        ProcessTemplate published = template(ApprovalTemplateSupport.已发布, true);
        whenTemplateIds(TEMPLATE_ID);
        when(templateService.read(TEMPLATE_ID)).thenReturn(unpublished);
        when(templateService.canWrite(TEMPLATE_ID)).thenReturn(true);
        when(templateService.findUniquePublishedByProcessDefinitionKey("demoApproval")).thenReturn(published);

        ProcessTemplate result = publisher.ensurePublished();

        assertSame(published, result);
        ArgumentCaptor<ProcessTemplate> saved = ArgumentCaptor.forClass(ProcessTemplate.class);
        verify(templateService).saveUnpublished(saved.capture());
        String artifactPath = saved.getValue().getProcessDefinitionPath();
        assertEquals("demoApproval", saved.getValue().getProcessDefinitionKey());
        assertTrue(artifactPath.startsWith("processes/sha256/"));
        assertEquals(artifactPath, "processes/sha256/" + storage.sha256(artifactPath) + ".zip");
        verify(templateService).publish(TEMPLATE_ID);
        assertTrue(demoBpmnEntry(artifactPath).contains("id=\"demoApproval\""));
    }

    @Test
    void restartAfterPublishOnlyReadsAndNeverRepublishes() {
        ProcessTemplate unpublished = template(ApprovalTemplateSupport.未发布, null);
        ProcessTemplate published = template(ApprovalTemplateSupport.已发布, true);
        whenTemplateIds(TEMPLATE_ID);
        when(templateService.read(TEMPLATE_ID)).thenReturn(unpublished, published);
        when(templateService.canWrite(TEMPLATE_ID)).thenReturn(true, false);
        when(templateService.findUniquePublishedByProcessDefinitionKey("demoApproval")).thenReturn(published);

        assertSame(published, publisher.ensurePublished());
        assertSame(published, publisher.ensurePublished());

        verify(templateService).saveUnpublished(any(ProcessTemplate.class));
        verify(templateService).publish(TEMPLATE_ID);
    }

    @Test
    void skipsWhenPublishedTemplateHasNoUsableArtifact() {
        ProcessTemplate published = template(ApprovalTemplateSupport.已发布, false);
        whenTemplateIds(TEMPLATE_ID);
        when(templateService.read(TEMPLATE_ID)).thenReturn(published);
        when(templateService.canWrite(TEMPLATE_ID)).thenReturn(false);
        when(templateService.findUniquePublishedByProcessDefinitionKey("demoApproval")).thenReturn(published);

        assertNull(publisher.ensurePublished());

        verify(templateService, never()).saveUnpublished(any(ProcessTemplate.class));
        verify(templateService, never()).publish(any());
    }

    @Test
    void skipsWhenDemoTemplateIsMissingOrAmbiguous() {
        whenTemplateIds();

        assertNull(publisher.ensurePublished());
        verify(templateService, never()).read(any());

        whenTemplateIds(2L, 3L);
        assertNull(publisher.ensurePublished());
        verify(templateService, never()).publish(any());
    }

    @Test
    void skipsWhenDemoTemplateDisappearedBetweenLookupAndRead() {
        whenTemplateIds(TEMPLATE_ID);
        when(templateService.read(TEMPLATE_ID)).thenReturn(null);

        assertNull(publisher.ensurePublished());

        verify(templateService, never()).publish(any());
    }

    @Test
    void skipsWhenPublishLeavesNoUniquePublishedTemplate() {
        ProcessTemplate unpublished = template(ApprovalTemplateSupport.未发布, null);
        whenTemplateIds(TEMPLATE_ID);
        when(templateService.read(TEMPLATE_ID)).thenReturn(unpublished);
        when(templateService.canWrite(TEMPLATE_ID)).thenReturn(true);
        when(templateService.findUniquePublishedByProcessDefinitionKey("demoApproval")).thenReturn(null);

        assertNull(publisher.ensurePublished());

        verify(templateService).publish(TEMPLATE_ID);
    }

    @Test
    void publishesAttendanceTemplatesWithTheirOwnClasspathArtifacts() throws IOException {
        ProcessTemplate leave = template(ApprovalTemplateSupport.未发布, null);
        ProcessTemplate overtime = template(ApprovalTemplateSupport.未发布, null);
        ProcessTemplate published = template(ApprovalTemplateSupport.已发布, true);
        whenTemplateIds(TEMPLATE_ID);
        when(templateService.read(TEMPLATE_ID)).thenReturn(leave, overtime);
        when(templateService.canWrite(TEMPLATE_ID)).thenReturn(true);
        when(templateService.findUniquePublishedByProcessDefinitionKey(anyString()))
                .thenReturn(published);

        publisher.ensureAttendancePublished();

        ArgumentCaptor<ProcessTemplate> saved = ArgumentCaptor.forClass(ProcessTemplate.class);
        verify(templateService, times(2)).saveUnpublished(saved.capture());
        verify(templateService, times(2)).publish(TEMPLATE_ID);
        assertEquals(
                List.of("attendanceLeave", "attendanceOvertime"),
                saved.getAllValues().stream().map(ProcessTemplate::getProcessDefinitionKey).toList());
        for (ProcessTemplate template : saved.getAllValues()) {
            String artifactPath = template.getProcessDefinitionPath();
            assertTrue(artifactPath.startsWith("processes/sha256/"));
            assertEquals(artifactPath, "processes/sha256/" + storage.sha256(artifactPath) + ".zip");
            String bpmn = zipEntry(artifactPath);
            assertTrue(bpmn.contains("id=\"" + template.getProcessDefinitionKey() + "\""));
            assertTrue(bpmn.contains("id=\"deptLeaderReview\""));
        }
    }

    @Test
    void attendancePublishingKeepsGoingWhenOneTemplateIsMissing() throws IOException {
        whenTemplateIds();
        publisher.ensureAttendancePublished();
        verify(templateService, never()).publish(any());
    }

    private void whenTemplateIds(Long... ids) {
        when(jdbcTemplate.query(anyString(), ArgumentMatchers.<RowMapper<Long>>any(), any(Object[].class)))
                .thenReturn(List.of(ids));
    }

    private String demoBpmnEntry(String artifactPath) throws IOException {
        try (ZipInputStream zip = openZip(artifactPath)) {
            ZipEntry entry = zip.getNextEntry();
            assertNotNull(entry);
            assertEquals("demo-approval.bpmn20.xml", entry.getName());
            String content = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
            assertFalse(content.isBlank());
            return content;
        }
    }

    private String zipEntry(String artifactPath) throws IOException {
        try (ZipInputStream zip = openZip(artifactPath)) {
            ZipEntry entry = zip.getNextEntry();
            assertNotNull(entry);
            return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private ZipInputStream openZip(String artifactPath) throws IOException {
        byte[] archive = Files.readAllBytes(storage.resolve(artifactPath));
        return new ZipInputStream(new java.io.ByteArrayInputStream(archive));
    }

    private static ProcessTemplate template(int status, Boolean availableForInitiation) {
        ProcessTemplate template = new ProcessTemplate();
        template.setId(TEMPLATE_ID);
        template.setStatus(status);
        template.setAvailableForInitiation(availableForInitiation);
        return template;
    }
}
