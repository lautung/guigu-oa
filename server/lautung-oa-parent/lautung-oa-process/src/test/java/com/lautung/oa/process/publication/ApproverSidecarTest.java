package com.lautung.oa.process.publication;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 发布合成 sidecar 的纯函数合同（ADR 0069，#285）。 */
class ApproverSidecarTest {

    @Test
    void synthesizeKeepsOriginalEntriesAndAppendsTheFrozenSidecar() throws IOException {
        byte[] original = zip("flow.bpmn20.xml", "<definitions/>");
        List<ApproverSidecar.SidecarRule> rules = List.of(
                new ApproverSidecar.SidecarRule("first", "DEPT_LEADER", null, 1),
                new ApproverSidecar.SidecarRule("second", "ROLE", 821L, 2));

        byte[] synthesized = ApproverSidecar.synthesize(original, "someFlow", rules);

        assertEquals(2, entryNames(synthesized).size());
        assertTrue(entryNames(synthesized).contains("flow.bpmn20.xml"));
        assertTrue(entryNames(synthesized).contains("oa-approvers.json"));
        assertArrayEquals("<definitions/>".getBytes(StandardCharsets.UTF_8), entry(synthesized, "flow.bpmn20.xml"));
        String sidecar = new String(entry(synthesized, "oa-approvers.json"), StandardCharsets.UTF_8);
        assertEquals("first", com.jayway.jsonpath.JsonPath.read(sidecar, "$.rules[0].taskDefinitionKey"));
        assertEquals("DEPT_LEADER", com.jayway.jsonpath.JsonPath.read(sidecar, "$.rules[0].sourceType"));
        assertEquals("second", com.jayway.jsonpath.JsonPath.read(sidecar, "$.rules[1].taskDefinitionKey"));
        assertEquals(821, (int) com.jayway.jsonpath.JsonPath.<Integer>read(sidecar, "$.rules[1].targetId"));
        assertEquals("someFlow", com.jayway.jsonpath.JsonPath.read(sidecar, "$.processDefinitionKey"));
    }

    @Test
    void synthesizeIsDeterministicForTheSameInputs() throws IOException {
        byte[] original = zip("flow.bpmn20.xml", "<definitions/>");
        List<ApproverSidecar.SidecarRule> rules = List.of(
                new ApproverSidecar.SidecarRule("first", "POST", 811L, 1));

        byte[] first = ApproverSidecar.synthesize(original, "someFlow", rules);
        byte[] second = ApproverSidecar.synthesize(original, "someFlow", rules);

        assertArrayEquals(first, second, "同输入必须得到同内容摘要（content-addressed）");
    }

    @Test
    void synthesizeReplacesAStraySidecarEntryFromTheOriginal() throws IOException {
        byte[] original = zip("oa-approvers.json", "{\"stale\":true}");
        List<ApproverSidecar.SidecarRule> rules = List.of(
                new ApproverSidecar.SidecarRule("first", "DEPT_LEADER", null, 1));

        byte[] synthesized = ApproverSidecar.synthesize(original, "someFlow", rules);

        assertEquals(1, entryNames(synthesized).size());
        String sidecar = new String(entry(synthesized, "oa-approvers.json"), StandardCharsets.UTF_8);
        assertTrue(sidecar.contains("DEPT_LEADER"));
    }

    @Test
    void parseRoundTripsAndRejectsBrokenSidecars() throws IOException {
        byte[] synthesized = ApproverSidecar.synthesize(zip("flow.bpmn20.xml", "<definitions/>"), "someFlow",
                List.of(new ApproverSidecar.SidecarRule("first", "ROLE", 821L, 1)));

        List<ApproverSidecar.SidecarRule> parsed = ApproverSidecar.parse(
                new ByteArrayInputStream(entry(synthesized, "oa-approvers.json")));

        assertEquals(1, parsed.size());
        assertEquals(new ApproverSidecar.SidecarRule("first", "ROLE", 821L, 1), parsed.get(0));

        assertThrows(IllegalArgumentException.class,
                () -> ApproverSidecar.parse(new ByteArrayInputStream("{\"noRules\":1}".getBytes(StandardCharsets.UTF_8))));
        assertThrows(IllegalArgumentException.class,
                () -> ApproverSidecar.parse(new ByteArrayInputStream("不是 JSON".getBytes(StandardCharsets.UTF_8))));
    }

    private static byte[] zip(String entryName, String content) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry(entryName));
            zip.write(content.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    private static List<String> entryNames(byte[] zipBytes) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            List<String> names = new java.util.ArrayList<>();
            while ((entry = zip.getNextEntry()) != null) {
                names.add(entry.getName());
            }
            return names;
        }
    }

    private static byte[] entry(byte[] zipBytes, String entryName) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entryName.equals(entry.getName())) {
                    return zip.readAllBytes();
                }
            }
        }
        throw new IllegalStateException("缺少条目 " + entryName);
    }
}
