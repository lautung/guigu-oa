package com.atguigu.observability;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

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
@TestPropertySource(properties = "management.health.redis.enabled=false")
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
}
