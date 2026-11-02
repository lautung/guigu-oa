package com.atguigu.process;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessDefinitionStorageTest {

    @TempDir
    Path directory;

    @Test
    void rejectsMissingEmptyAndNonZipFiles() {
        ProcessDefinitionStorage storage = new ProcessDefinitionStorage(directory);

        assertThrows(IllegalArgumentException.class, () -> storage.store(null));
        assertThrows(IllegalArgumentException.class, () -> storage.store(
                new MockMultipartFile("file", "leave.zip", "application/zip", new byte[0])));
        assertThrows(IllegalArgumentException.class, () -> storage.store(
                new MockMultipartFile("file", "leave.txt", "text/plain", new byte[] {1})));
        assertThrows(IllegalArgumentException.class, () -> storage.store(
                new MockMultipartFile("file", ".zip", "application/zip", new byte[] {1})));
        assertThrows(IllegalArgumentException.class, () -> storage.store(
                new MockMultipartFile("file", null, "application/zip", new byte[] {1})));
    }

    @Test
    void storesInMemoryArchivesWithTheSameContentAddressedRules() throws IOException {
        ProcessDefinitionStorage storage = new ProcessDefinitionStorage(directory);
        byte[] archive = {1, 2, 3};

        ProcessDefinitionStorage.UploadedProcessDefinition first = storage.store("demoApproval.zip", archive);
        ProcessDefinitionStorage.UploadedProcessDefinition second = storage.store("demoApproval.zip", archive);

        assertEquals("demoApproval", first.processDefinitionKey());
        assertEquals(first.processDefinitionPath(), second.processDefinitionPath());
        assertEquals(first.sha256(), second.sha256());
        assertTrue(storage.isContentAddressedPath(first.processDefinitionPath(), first.sha256()));
        assertArrayEquals(archive, Files.readAllBytes(storage.resolve(first.processDefinitionPath())));
        assertThrows(IllegalArgumentException.class, () -> storage.store("demoApproval.zip", new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> storage.store("demoApproval.txt", archive));
        assertThrows(IllegalArgumentException.class, () -> storage.store(".zip", archive));
        assertThrows(IllegalArgumentException.class, () -> storage.store(null, archive));
    }

    @Test
    void storesContentAddressedArchivesAndDeduplicates() throws IOException {
        ProcessDefinitionStorage storage = new ProcessDefinitionStorage(directory);
        MultipartFile file = new MockMultipartFile(
                "file", "leave.zip", "application/zip", new byte[] {1, 2, 3});

        ProcessDefinitionStorage.UploadedProcessDefinition first = storage.store(file);
        ProcessDefinitionStorage.UploadedProcessDefinition second = storage.store(file);

        assertEquals("leave", first.processDefinitionKey());
        assertTrue(first.processDefinitionPath().startsWith("processes/sha256/"));
        assertTrue(first.processDefinitionPath().endsWith(".zip"));
        assertEquals(first.processDefinitionPath(), second.processDefinitionPath());
        assertEquals(first.sha256(), second.sha256());
        assertTrue(Files.isRegularFile(storage.resolve(first.processDefinitionPath())));
    }

    @Test
    void fallsBackToMoveWhenHardLinksAreUnsupported() throws IOException {
        ProcessDefinitionStorage storage = new ProcessDefinitionStorage(directory, (link, existing) -> {
            throw new UnsupportedOperationException("no hard links");
        });

        ProcessDefinitionStorage.UploadedProcessDefinition uploaded = storage.store(
                new MockMultipartFile("file", "leave.zip", "application/zip", new byte[] {4, 5}));

        assertTrue(Files.isRegularFile(storage.resolve(uploaded.processDefinitionPath())));
    }

    @Test
    void opensStoredArchivesAndReportsMissingOnes() throws IOException {
        ProcessDefinitionStorage storage = new ProcessDefinitionStorage(directory);
        ProcessDefinitionStorage.UploadedProcessDefinition uploaded = storage.store(
                new MockMultipartFile("file", "leave.zip", "application/zip", new byte[] {7, 8}));

        try (InputStream input = storage.open(uploaded.processDefinitionPath())) {
            assertArrayEquals(new byte[] {7, 8}, input.readAllBytes());
        }
        assertNull(storage.open("processes/sha256/missing.zip"));
    }

    @Test
    void refusesPathsThatAreBlankAbsoluteOrEscapeTheDirectory() {
        ProcessDefinitionStorage storage = new ProcessDefinitionStorage(directory);

        assertThrows(IllegalArgumentException.class, () -> storage.resolve(""));
        assertThrows(IllegalArgumentException.class, () -> storage.resolve("/etc/passwd"));
        assertThrows(IllegalArgumentException.class, () -> storage.resolve("../outside.zip"));
    }

    @Test
    void verifiesChecksumsAndContentAddressedPaths() throws IOException {
        ProcessDefinitionStorage storage = new ProcessDefinitionStorage(directory);
        ProcessDefinitionStorage.UploadedProcessDefinition uploaded = storage.store(
                new MockMultipartFile("file", "leave.zip", "application/zip", new byte[] {9}));

        assertFalse(storage.matchesSha256(uploaded.processDefinitionPath(), ""));
        assertFalse(storage.matchesSha256("processes/sha256/missing.zip", uploaded.sha256()));
        assertTrue(storage.matchesSha256(uploaded.processDefinitionPath(), uploaded.sha256()));
        assertFalse(storage.matchesSha256(uploaded.processDefinitionPath(), "0".repeat(64)));

        assertFalse(storage.isContentAddressedPath(uploaded.processDefinitionPath(), ""));
        assertFalse(storage.isContentAddressedPath(uploaded.processDefinitionPath(), "0".repeat(64)));
        assertTrue(storage.isContentAddressedPath(
                uploaded.processDefinitionPath(), uploaded.sha256()));
    }

    @Test
    void hashesStoredFilesAndRejectsMissingOnes() throws IOException {
        ProcessDefinitionStorage storage = new ProcessDefinitionStorage(directory);
        ProcessDefinitionStorage.UploadedProcessDefinition uploaded = storage.store(
                new MockMultipartFile("file", "leave.zip", "application/zip", new byte[] {1, 2}));

        assertEquals(uploaded.sha256(), storage.sha256(uploaded.processDefinitionPath()));
        assertThrows(IOException.class, () -> storage.sha256("processes/sha256/missing.zip"));
        assertEquals(64, ProcessDefinitionStorage.sha256(
                storage.resolve(uploaded.processDefinitionPath())).length());
    }
}
