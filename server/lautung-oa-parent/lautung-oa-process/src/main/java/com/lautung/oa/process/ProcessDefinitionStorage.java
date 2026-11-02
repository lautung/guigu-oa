package com.lautung.oa.process;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.FileAlreadyExistsException;
import java.util.Locale;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Stores process definition archives in a persistent filesystem directory. */
@Component
public class ProcessDefinitionStorage {

    private final Path directory;
    private final HardLinkCreator hardLinkCreator;

    @Autowired
    public ProcessDefinitionStorage(
            @Value("${lautung.oa.process-definition-dir}") String directory) {
        this(Path.of(directory));
    }

    public ProcessDefinitionStorage(Path directory) {
        this(directory, Files::createLink);
    }

    ProcessDefinitionStorage(Path directory, HardLinkCreator hardLinkCreator) {
        this.directory = directory.toAbsolutePath().normalize();
        this.hardLinkCreator = hardLinkCreator;
    }

    public UploadedProcessDefinition store(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("流程 zip 不能为空");
        }

        String filename = archiveFilename(file.getOriginalFilename());
        Path temporaryFile = createTemporaryArchive();
        try {
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, temporaryFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            return storeTemporaryArchive(filename, temporaryFile);
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    /** Stores an in-memory archive under the same content-addressed rules as an upload. */
    public UploadedProcessDefinition store(String filename, byte[] content) throws IOException {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("流程 zip 不能为空");
        }

        String safeName = archiveFilename(filename);
        Path temporaryFile = createTemporaryArchive();
        try {
            Files.write(temporaryFile, content, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
            return storeTemporaryArchive(safeName, temporaryFile);
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    private Path createTemporaryArchive() throws IOException {
        Path artifactDirectory = directory.resolve("processes/sha256");
        Files.createDirectories(artifactDirectory);
        return Files.createTempFile(artifactDirectory, "upload-", ".tmp");
    }

    private UploadedProcessDefinition storeTemporaryArchive(String filename, Path temporaryFile) throws IOException {
        String sha256 = sha256(temporaryFile);
        String relativePath = "processes/sha256/" + sha256 + ".zip";
        Path destination = resolve(relativePath);
        createOrReuseWithoutReplacement(temporaryFile, destination);
        String processDefinitionKey = filename.substring(0, filename.lastIndexOf('.'));
        return new UploadedProcessDefinition(relativePath, processDefinitionKey, sha256);
    }

    private static String archiveFilename(String originalFilename) {
        String filename = safeFilename(originalFilename);
        if (!filename.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw new IllegalArgumentException("流程文件必须是 zip");
        }
        if (filename.lastIndexOf('.') == 0) {
            throw new IllegalArgumentException("流程 zip 文件名无效");
        }
        return filename;
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

    public boolean matchesSha256(String relativePath, String expectedSha256) throws IOException {
        if (!StringUtils.hasText(expectedSha256)) {
            return false;
        }
        Path artifact = resolve(relativePath);
        return Files.isRegularFile(artifact) && expectedSha256.equals(sha256(artifact));
    }

    public boolean isContentAddressedPath(String relativePath, String expectedSha256) {
        return StringUtils.hasText(expectedSha256)
                && ("processes/sha256/" + expectedSha256 + ".zip").equals(relativePath);
    }

    public String sha256(String relativePath) throws IOException {
        Path artifact = resolve(relativePath);
        if (!Files.isRegularFile(artifact)) throw new IOException("流程 zip 不存在");
        return sha256(artifact);
    }

    public static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 Java 不支持 SHA-256", e);
        }
    }

    private void createOrReuseWithoutReplacement(Path temporaryFile, Path destination) throws IOException {
        try {
            hardLinkCreator.create(destination, temporaryFile);
        } catch (FileAlreadyExistsException e) {
            requireSameBytes(temporaryFile, destination, e);
        } catch (IOException | UnsupportedOperationException linkFailure) {
            try {
                Files.move(temporaryFile, destination);
            } catch (FileAlreadyExistsException e) {
                requireSameBytes(temporaryFile, destination, e);
            } catch (IOException moveFailure) {
                moveFailure.addSuppressed(linkFailure);
                throw moveFailure;
            }
        }
    }

    private static void requireSameBytes(Path requested, Path existing, IOException cause) throws IOException {
        if (Files.mismatch(requested, existing) != -1) {
            throw new IOException("内容寻址流程 zip 与已有产物不一致", cause);
        }
    }

    @FunctionalInterface
    interface HardLinkCreator {
        void create(Path link, Path existing) throws IOException;
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

    public record UploadedProcessDefinition(String processDefinitionPath, String processDefinitionKey, String sha256) {
    }
}
