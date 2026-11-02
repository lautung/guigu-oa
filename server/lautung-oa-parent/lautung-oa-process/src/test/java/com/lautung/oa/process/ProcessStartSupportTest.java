package com.lautung.oa.process;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 动态必填的解释语义（ADR 0035）：必填形状、空值定义、隐藏与条件字段的静态判定、fail-open。
 * HTTP 层的 201 信封与「两种模板都过」见 FormRequiredContractTest。
 */
public class ProcessStartSupportTest {

    private static String rule(String field, String title, String required) {
        String titlePart = title == null ? "" : ",\"title\":\"" + title + "\"";
        String requiredPart = required == null ? "" : ",\"$required\":" + required;
        return "{\"type\":\"input\",\"field\":\"" + field + "\"" + titlePart + requiredPart + "}";
    }

    @Test
    public void booleanRequiredReportsMissingFieldByTitle() {
        String formProps = "[" + rule("reason", "请假原因", "true") + "]";
        assertEquals(
                List.of("请假原因不能为空"),
                ProcessStartSupport.missingRequiredMessages(formProps, "{\"formData\":{}}"));
    }

    @Test
    public void missingFormValuesCountsEveryRequiredFieldAsEmpty() {
        String formProps = "[" + rule("reason", "请假原因", "true") + "," + rule("days", "请假天数", "true") + "]";
        assertEquals(
                List.of("请假原因不能为空", "请假天数不能为空"),
                ProcessStartSupport.missingRequiredMessages(formProps, null));
        assertEquals(
                List.of("请假原因不能为空", "请假天数不能为空"),
                ProcessStartSupport.missingRequiredMessages(formProps, ""));
    }

    @Test
    public void emptySemanticsMirrorFormCreate() {
        String formProps = "[" + rule("reason", "请假原因", "true") + "]";
        // 算空：空串、显式 null、空数组；不算空：纯空白串、0、false、空对象、非空数组
        assertEquals(List.of("请假原因不能为空"),
                ProcessStartSupport.missingRequiredMessages(formProps, "{\"formData\":{\"reason\":\"\"}}"));
        assertEquals(List.of("请假原因不能为空"),
                ProcessStartSupport.missingRequiredMessages(formProps, "{\"formData\":{\"reason\":null}}"));
        assertEquals(List.of("请假原因不能为空"),
                ProcessStartSupport.missingRequiredMessages(formProps, "{\"formData\":{\"reason\":[]}}"));
        assertEquals(List.of(),
                ProcessStartSupport.missingRequiredMessages(formProps, "{\"formData\":{\"reason\":\"  \"}}"));
        assertEquals(List.of(),
                ProcessStartSupport.missingRequiredMessages(formProps, "{\"formData\":{\"reason\":0}}"));
        assertEquals(List.of(),
                ProcessStartSupport.missingRequiredMessages(formProps, "{\"formData\":{\"reason\":false}}"));
        assertEquals(List.of(),
                ProcessStartSupport.missingRequiredMessages(formProps, "{\"formData\":{\"reason\":{}}}"));
        assertEquals(List.of(),
                ProcessStartSupport.missingRequiredMessages(formProps, "{\"formData\":{\"reason\":[\"x\"]}}"));
    }

    @Test
    public void stringRequiredUsesTheCustomMessage() {
        String formProps = "[" + rule("reason", "请假原因", "\"必须说明请假事由\"") + "]";
        assertEquals(
                List.of("必须说明请假事由"),
                ProcessStartSupport.missingRequiredMessages(formProps, "{\"formData\":{}}"));
    }

    @Test
    public void effectRequiredCountsAsRequired() {
        String formProps = "[{\"type\":\"input\",\"field\":\"reason\",\"title\":\"请假原因\","
                + "\"effect\":{\"required\":true}}]";
        assertEquals(
                List.of("请假原因不能为空"),
                ProcessStartSupport.missingRequiredMessages(formProps, "{\"formData\":{}}"));
    }

    @Test
    public void falseOrAbsentRequiredIsNotChecked() {
        String formProps = "[" + rule("reason", "请假原因", "false") + ","
                + rule("days", "请假天数", null) + "]";
        assertEquals(
                List.of(),
                ProcessStartSupport.missingRequiredMessages(formProps, "{\"formData\":{}}"));
    }

    @Test
    public void childrenAreWalkedRecursively() {
        String formProps = "[{\"type\":\"row\",\"children\":[" + rule("reason", "请假原因", "true") + "]}]";
        assertEquals(
                List.of("请假原因不能为空"),
                ProcessStartSupport.missingRequiredMessages(formProps, "{\"formData\":{}}"));
    }

    @Test
    public void hiddenAndConditionalRulesAreSkippedButDisplayNoneIsNot() {
        String hidden = rule("reason", "请假原因", "true").replace("}", ",\"hidden\":true}");
        String typedHidden = "{\"type\":\"hidden\",\"field\":\"reason\",\"title\":\"请假原因\",\"$required\":true}";
        String conditional = rule("reason", "请假原因", "true").replace("}", ",\"control\":[]}");
        String displayNone = rule("reason", "请假原因", "true").replace("}", ",\"display\":false}");
        String emptyForm = "{\"formData\":{}}";

        assertEquals(List.of(), ProcessStartSupport.missingRequiredMessages("[" + hidden + "]", emptyForm));
        assertEquals(List.of(), ProcessStartSupport.missingRequiredMessages("[" + typedHidden + "]", emptyForm));
        assertEquals(List.of(), ProcessStartSupport.missingRequiredMessages("[" + conditional + "]", emptyForm));
        assertEquals(List.of("请假原因不能为空"),
                ProcessStartSupport.missingRequiredMessages("[" + displayNone + "]", emptyForm));
    }

    @Test
    public void missingTitleFallsBackToTheField() {
        String formProps = "[" + rule("reason", null, "true") + "]";
        assertEquals(
                List.of("reason不能为空"),
                ProcessStartSupport.missingRequiredMessages(formProps, "{\"formData\":{}}"));
    }

    @Test
    public void unparsableFormPropsIsTreatedAsUnconstrained() {
        String required = "[" + rule("reason", "请假原因", "true") + "]";
        assertEquals(List.of(), ProcessStartSupport.missingRequiredMessages(null, "{\"formData\":{}}"));
        assertEquals(List.of(), ProcessStartSupport.missingRequiredMessages("", "{\"formData\":{}}"));
        assertEquals(List.of(), ProcessStartSupport.missingRequiredMessages("{not-json", "{\"formData\":{}}"));
        assertEquals(List.of(), ProcessStartSupport.missingRequiredMessages("{\"formData\":{}}", "{\"formData\":{}}"));
        assertEquals(List.of(), ProcessStartSupport.missingRequiredMessages("[]", null));
    }

    @Test
    public void filledRequiredFieldsProduceNoMessages() {
        String formProps = "[" + rule("reason", "请假原因", "true") + "," + rule("days", "请假天数", "true") + "]";
        assertEquals(
                List.of(),
                ProcessStartSupport.missingRequiredMessages(
                        formProps, "{\"formData\":{\"reason\":\"家庭事务\",\"days\":1}}"));
    }
}
