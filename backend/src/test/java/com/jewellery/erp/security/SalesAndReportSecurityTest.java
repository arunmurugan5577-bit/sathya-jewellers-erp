package com.jewellery.erp.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jewellery.erp.report.controller.ReportController;
import com.jewellery.erp.report.service.ReportService;
import com.jewellery.erp.sales.controller.SaleController;
import com.jewellery.erp.sales.service.SaleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Sales and report permissions are decided by the server, whatever the client shows. */
@WebMvcTest(controllers = {SaleController.class, ReportController.class})
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
class SalesAndReportSecurityTest {

    private static final String SALE_JSON = """
            {"customerId": 1, "items": [{"serialNumber": "000123", "wastagePercentage": 30,
              "ratePerGram": 235, "makingCharge": 550}], "discountAmount": 111}""";

    @Autowired private MockMvc mockMvc;

    @MockBean private SaleService saleService;
    @MockBean private ReportService reportService;
    @MockBean private AppUserDetailsService appUserDetailsService;

    @Test
    @WithMockUser(authorities = "SALES_VIEW")
    void viewingSalesDoesNotAllowCreatingOne() throws Exception {
        mockMvc.perform(post("/api/sales").contentType(MediaType.APPLICATION_JSON).content(SALE_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        verify(saleService, never()).create(any());
    }

    @Test
    @WithMockUser(authorities = "SALES_CREATE")
    void creatingSalesDoesNotAllowCancellingOne() throws Exception {
        mockMvc.perform(post("/api/sales/1/cancel").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"test\"}"))
                .andExpect(status().isForbidden());
        verify(saleService, never()).cancel(any(), any());
    }

    @Test
    @WithMockUser(authorities = "SALES_CREATE")
    void aSaleWithCreatePermissionIsAccepted() throws Exception {
        mockMvc.perform(post("/api/sales/calculate").contentType(MediaType.APPLICATION_JSON).content(SALE_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "REPORT_SALES_VIEW")
    void previewingAReportDoesNotAllowExportingIt() throws Exception {
        mockMvc.perform(get("/api/reports/sales/preview?startDate=2026-04-01&endDate=2026-04-30"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/reports/sales/export?startDate=2026-04-01&endDate=2026-04-30"))
                .andExpect(status().isForbidden());
        verify(reportService, never()).exportSales(any());
    }

    @Test
    @WithMockUser(authorities = "REPORT_SALES_EXPORT")
    void salesReportPermissionsDoNotOpenTheStockReport() throws Exception {
        mockMvc.perform(get("/api/reports/stock/export?startDate=2026-04-01&endDate=2026-04-30"))
                .andExpect(status().isForbidden());
    }
}
