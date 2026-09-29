package com.jewellery.erp.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jewellery.erp.report.controller.ReportController;
import com.jewellery.erp.report.service.ReportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * An error comes back as the error, whatever the caller asked to be sent.
 *
 * <p>The report downloads are fetched with {@code Accept: <spreadsheet>}. Nothing
 * can write a JSON error body as a spreadsheet, so content negotiation failed,
 * the request fell through to {@code /error}, and the browser was answered 401 -
 * signing the user out over what was only a missing date range.
 */
@WebMvcTest(controllers = ReportController.class)
@Import({
    SecurityConfig.class,
    JwtAuthenticationFilter.class,
    JwtTokenProvider.class,
    RestAuthenticationEntryPoint.class,
    RestAccessDeniedHandler.class
})
@EnableConfigurationProperties({JwtProperties.class, CorsProperties.class})
@TestPropertySource(properties = {
    "app.jwt.secret=dGVzdC1zZWNyZXQtdGhhdC1pcy1sb25nLWVub3VnaC1mb3ItaHMyNTYtc2lnbmluZw==",
    "app.jwt.issuer=jewellery-erp-test",
    "app.jwt.access-token-validity=PT15M",
    "app.jwt.refresh-token-validity=P7D",
    "app.cors.allowed-origins=http://localhost:4200"
})
class ErrorResponseContentTypeTest {

    private static final String XLSX =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Autowired private MockMvc mockMvc;

    @MockBean private ReportService reportService;
    @MockBean private AppUserDetailsService appUserDetailsService;

    @Test
    @WithMockUser(authorities = "REPORT_SALES_EXPORT")
    @DisplayName("a download asked for as a spreadsheet still reports its error as JSON")
    void downloadErrorIsJsonNotUnauthorised() throws Exception {
        // A date the binder cannot parse: the error is raised in the web layer,
        // which is where the content type of the reply is decided.
        mockMvc.perform(get("/api/reports/sales/export")
                        .param("startDate", "not-a-date")
                        .header(HttpHeaders.ACCEPT, XLSX))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(result -> {
                    String type = result.getResponse().getContentType();
                    if (type == null || !type.startsWith(MediaType.APPLICATION_JSON_VALUE)) {
                        throw new AssertionError("expected a JSON error body, got " + type);
                    }
                });
    }

    @Test
    @WithMockUser(authorities = "REPORT_STOCK_EXPORT")
    @DisplayName("the same request with a plain Accept behaves identically")
    void plainAcceptGetsTheSameError() throws Exception {
        mockMvc.perform(get("/api/reports/stock/export")
                        .param("startDate", "not-a-date")
                        .header(HttpHeaders.ACCEPT, "*/*"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @WithMockUser(authorities = "SALES_VIEW")
    @DisplayName("a genuine permission failure is still 403, not swallowed")
    void missingPermissionIsStillForbidden() throws Exception {
        mockMvc.perform(get("/api/reports/sales/export").header(HttpHeaders.ACCEPT, XLSX))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
