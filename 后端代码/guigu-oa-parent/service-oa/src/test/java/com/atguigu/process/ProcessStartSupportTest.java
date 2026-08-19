package com.atguigu.process;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProcessStartSupportTest {

    @Test
    public void formDataVariablesReadTheInnerFormDataMapForEngineStart() {
        String formValues = "{\"formData\":{\"days\":3,\"reason\":\"家中有事\"},\"formShowData\":{\"请假天数\":3}}";

        Map<String, Object> formData = ProcessStartSupport.formDataVariables(formValues);
        assertEquals(3, ((Number) formData.get("days")).intValue());
        assertEquals("家中有事", formData.get("reason"));

        Map<String, Object> variables = ProcessStartSupport.engineVariables(formValues);
        assertEquals(formData, variables.get("data"));
    }

    @Test
    public void formDataVariablesTreatMissingOrInvalidJsonAsEmpty() {
        assertTrue(ProcessStartSupport.formDataVariables(null).isEmpty());
        assertTrue(ProcessStartSupport.formDataVariables("{}").isEmpty());
        assertTrue(ProcessStartSupport.formDataVariables("{not-json").isEmpty());
    }

    @Test
    public void titleNamesTheStarterAndTemplate() {
        assertEquals("管理员发起请假申请申请", ProcessStartSupport.title("管理员", "请假申请"));
    }

    @Test
    public void waitingDescriptionListsNextApprovers() {
        assertEquals("等待张三,李四审批", ProcessStartSupport.waitingDescription(Arrays.asList("张三", "李四")));
        assertEquals("等待审批", ProcessStartSupport.waitingDescription(Collections.emptyList()));
    }
}
