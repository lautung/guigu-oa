package com.lautung.oa.demo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 演示审批的流程产物：classpath 里的 BPMN 是唯一来源，启动时现打成可发布的 zip。
 */
public final class DemoApprovalArtifact {

    public static final String ARCHIVE_NAME = "demoApproval.zip";
    static final String BPMN_RESOURCE = "processes/demo-approval.bpmn20.xml";
    private static final String ZIP_ENTRY = "demo-approval.bpmn20.xml";

    private DemoApprovalArtifact() {
    }

    public static byte[] zip() throws IOException {
        try (InputStream bpmn = DemoApprovalArtifact.class.getClassLoader().getResourceAsStream(BPMN_RESOURCE)) {
            if (bpmn == null) {
                throw new IllegalStateException("缺少演示流程定义: " + BPMN_RESOURCE);
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
                zip.putNextEntry(new ZipEntry(ZIP_ENTRY));
                bpmn.transferTo(zip);
                zip.closeEntry();
            }
            return bytes.toByteArray();
        }
    }
}
