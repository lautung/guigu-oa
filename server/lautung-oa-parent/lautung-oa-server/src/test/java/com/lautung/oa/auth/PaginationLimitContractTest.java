package com.lautung.oa.auth;

import com.lautung.oa.common.jwt.JwtHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:guigu_oa_pagination_contract;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;INIT=CREATE DOMAIN IF NOT EXISTS DATETIME AS TIMESTAMP")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(LoginAuthContractTest.InMemoryRedisConfig.class)
class PaginationLimitContractTest {

    private static final String MAX_LIMIT_MESSAGE = "每页记录数必须在 1 到 500 之间";
    private static final String PAGE_MESSAGE = "页码必须大于等于 1";

    private static final Route SYS_USER =
            new Route("/admin/system/sysUser/%d/%d", 1L, "admin");
    private static final Route FIND_PENDING =
            new Route("/admin/process/findPending/%d/%d", 2L, "user");

    private static final List<Route> CONFIGURATION_ROUTES = List.of(
            SYS_USER,
            new Route("/admin/system/sysRole/%d/%d", 1L, "admin"),
            new Route("/admin/process/processType/%d/%d", 1L, "admin"),
            new Route("/admin/process/processTemplate/%d/%d", 1L, "admin"),
            new Route("/admin/process/%d/%d", 1L, "admin"));
    private static final List<Route> WORK_ROUTES = List.of(
            FIND_PENDING,
            new Route("/admin/process/findProcessed/%d/%d", 2L, "user"),
            new Route("/admin/process/findStarted/%d/%d", 2L, "user"),
            new Route("/admin/process/findCarbonCopied/%d/%d", 2L, "user"));
    private static final List<Route> ALL_ROUTES = Stream.concat(
            CONFIGURATION_ROUTES.stream(), WORK_ROUTES.stream()).toList();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtHelper jwtHelper;

    @Test
    void theReproducedOversizedLimitsAreRejected() throws Exception {
        assertRejected(SYS_USER, 1, 100000, MAX_LIMIT_MESSAGE);
        assertRejected(FIND_PENDING, 1, 100000, MAX_LIMIT_MESSAGE);
    }

    @Test
    void everyPaginatedRouteRejectsAPageSizeAboveTheServerMaximum() throws Exception {
        for (Route route : ALL_ROUTES) {
            assertRejected(route, 1, 501, MAX_LIMIT_MESSAGE);
        }
    }

    @Test
    void everyPaginatedRouteRejectsAPageNumberBelowOne() throws Exception {
        for (Route route : ALL_ROUTES) {
            assertRejected(route, 0, 10, PAGE_MESSAGE);
            assertRejected(route, -1, 10, PAGE_MESSAGE);
        }
    }

    @Test
    void theServerMaximumAndTheFirstPageAreAccepted() throws Exception {
        assertAccepted(SYS_USER, 1, 1);
        assertAccepted(SYS_USER, 1, 500);
        assertAccepted(FIND_PENDING, 1, 500);
    }

    private void assertRejected(Route route, long page, long limit, String message)
            throws Exception {
        mockMvc.perform(get(route.at(page, limit)).header("token", storeToken(route)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    private void assertAccepted(Route route, long page, long limit) throws Exception {
        mockMvc.perform(get(route.at(page, limit)).header("token", storeToken(route)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.current").value(page))
                .andExpect(jsonPath("$.data.size").value(limit));
    }

    private String storeToken(Route route) {
        return jwtHelper.createTokenPair(route.userId(), route.username(), 0L).token();
    }

    private record Route(String template, long userId, String username) {

        String at(long page, long limit) {
            return template.formatted(page, limit);
        }
    }
}
