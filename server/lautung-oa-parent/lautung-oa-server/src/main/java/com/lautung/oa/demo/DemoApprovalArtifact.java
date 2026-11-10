package com.lautung.oa.demo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 演示审批的流程产物：classpath 里的 BPMN 是唯一来源，启动时现打成可发布的 zip。
 * 通用演示模板与考勤域的两份业务模板共用同一形状（一个 BPMN 条目一个 zip）。
 */
public final class DemoApprovalArtifact {

    public static final String ARCHIVE_NAME = "demoApproval.zip";
    static final String BPMN_RESOURCE = "processes/demo-approval.bpmn20.xml";

    public static final String ATTENDANCE_LEAVE_ARCHIVE_NAME = "attendanceLeave.zip";
    static final String ATTENDANCE_LEAVE_BPMN_RESOURCE = "processes/attendance-leave.bpmn20.xml";

    public static final String ATTENDANCE_OVERTIME_ARCHIVE_NAME = "attendanceOvertime.zip";
    static final String ATTENDANCE_OVERTIME_BPMN_RESOURCE = "processes/attendance-overtime.bpmn20.xml";

    private DemoApprovalArtifact() {
    }

    public static byte[] zip() throws IOException {
        return zip(BPMN_RESOURCE);
    }

    public static byte[] zip(String bpmnResource) throws IOException {
        try (InputStream bpmn = DemoApprovalArtifact.class.getClassLoader().getResourceAsStream(bpmnResource)) {
            if (bpmn == null) {
                throw new IllegalStateException("缺少演示流程定义: " + bpmnResource);
            }
            String entryName = Path.of(bpmnResource).getFileName().toString();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
                zip.putNextEntry(new ZipEntry(entryName));
                bpmn.transferTo(zip);
                zip.closeEntry();
            }
            return bytes.toByteArray();
        }
    }
}
