package com.lautung.oa.observability;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ObservabilityContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthIsAvailableWithoutBusinessToken() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void readinessProbeIsAvailableWithoutBusinessTokenAndHidesDetails() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void suppliedTraceIdIsReturnedAndWrittenToStructuredRequestLog(CapturedOutput output) throws Exception {
        String traceId = "request-83-correlation";

        mockMvc.perform(get("/actuator/health")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", traceId));

        assertTrue(output.toString().contains("\"traceId\":\"" + traceId + "\""));
    }

    @Test
    void invalidTraceIdIsReplacedWithGeneratedUuid() throws Exception {
        String invalidTraceId = "invalid trace id";
        String returnedTraceId = mockMvc.perform(get("/actuator/health")
                        .header("X-Trace-Id", invalidTraceId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getHeader("X-Trace-Id");

        assertNotNull(returnedTraceId);
        assertNotEquals(invalidTraceId, returnedTraceId);
        UUID.fromString(returnedTraceId);
    }

    /** The wired-in customizer (application.yml) must mask phones in every logger's message. */
    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void structuredConsoleOutputMasksPhoneNumbersInMessages(CapturedOutput output) {
        LoggerFactory.getLogger("com.lautung.oa.masking-contract").info(
                "login audit event=failed username=13800138000 source=203.0.113.5 failures=1");

        String logs = output.toString();
        assertTrue(logs.contains("username=138****8000"));
        assertFalse(logs.contains("13800138000"));
    }

    /** Exception stacks carry rejected values; the stack_trace member is masked too. */
    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void structuredConsoleOutputMasksPhoneNumbersInStackTrace(CapturedOutput output) {
        LoggerFactory.getLogger("com.lautung.oa.masking-contract").warn(
                "Business request failed with code 201", new RuntimeException("rejected value [13912345678]"));

        String logs = output.toString();
        assertTrue(logs.contains("139****5678"));
        assertFalse(logs.contains("13912345678"));
    }
}
