package com.atguigu.process;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/** Stores process definition archives in a persistent filesystem directory. */
@Component
public class ProcessDefinitionStorage {

    private final Path directory;

    @Autowired
    public ProcessDefinitionStorage(
            @Value("${guigu.oa.process-definition-dir:./data/process-definitions}") String directory) {
        this(Path.of(directory));
    }

    public ProcessDefinitionStorage(Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
    }

    public UploadedProcessDefinition store(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("流程 zip 不能为空");
        }

        String filename = safeFilename(file.getOriginalFilename());
        if (!filename.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw new IllegalArgumentException("流程文件必须是 zip");
        }
        int extensionIndex = filename.lastIndexOf('.');
        if (extensionIndex == 0) {
            throw new IllegalArgumentException("流程 zip 文件名无效");
        }

        String relativePath = "processes/" + filename;
        Path destination = resolve(relativePath);
        Files.createDirectories(destination.getParent());
        Path temporaryFile = Files.createTempFile(destination.getParent(), filename + ".", ".upload");
        try {
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, temporaryFile, StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(temporaryFile, destination,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporaryFile, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporaryFile);
        }

        String processDefinitionKey = filename.substring(0, extensionIndex);
        return new UploadedProcessDefinition(relativePath, processDefinitionKey);
    }

    public InputStream open(String relativePath) throws IOException {
        Path file = resolve(relativePath);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        return Files.newInputStream(file);
    }

    public Path resolve(String relativePath) {
        if (!StringUtils.hasText(relativePath)) {
            throw new IllegalArgumentException("流程 zip 路径不能为空");
        }

        Path relative = Path.of(relativePath.replace('\\', '/'));
        if (relative.isAbsolute()) {
            throw new IllegalArgumentException("流程 zip 路径必须是相对路径");
        }

        Path resolved = directory.resolve(relative).normalize();
        if (!resolved.startsWith(directory)) {
            throw new IllegalArgumentException("流程 zip 路径不能离开配置目录");
        }
        return resolved;
    }

    private static String safeFilename(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            throw new IllegalArgumentException("流程 zip 文件名不能为空");
        }
        String normalized = originalFilename.replace('\\', '/');
        String filename = normalized.substring(normalized.lastIndexOf('/') + 1);
        if (filename.isBlank() || ".".equals(filename) || "..".equals(filename)) {
            throw new IllegalArgumentException("流程 zip 文件名无效");
        }
        return filename;
    }

    public record UploadedProcessDefinition(String processDefinitionPath, String processDefinitionKey) {
    }
}
