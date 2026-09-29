package com.jewellery.erp.security;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jewellery.erp.report.controller.ReportController;
import com.jewellery.erp.report.service.ReportService;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Deactivating a user has to end their sessions, not just stop new logins.
 *
 * <p>A signed token cannot be withdrawn, so without a check against the account
 * a user removed mid-shift kept working until their access token ran out - up
 * to fifteen minutes of full access after being switched off.
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
class DeactivatedUserTokenTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider tokenProvider;

    @MockBean private ReportService reportService;
    @MockBean private AppUserDetailsService appUserDetailsService;

    private String tokenFor(long userId) {
        UserPrincipal principal = new UserPrincipal(
                userId, "cashier", "A Cashier", null, true, false, false,
                Set.of("ROLE_USER"), Set.of("REPORT_SALES_VIEW"),
                java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority(
                        "REPORT_SALES_VIEW")));
        return tokenProvider.createAccessToken(principal).token();
    }

    @Test
    @DisplayName("a token for an active account is accepted")
    void activeAccountIsLetIn() throws Exception {
        given(appUserDetailsService.isActive(anyLong())).willReturn(true);

        mockMvc.perform(get("/api/reports/sales/preview")
                        .param("startDate", "not-a-date")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(7L)))
                // Past the filter: it fails on the date, not on authentication.
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a still-valid token stops working the moment the account is switched off")
    void deactivatedAccountIsRejected() throws Exception {
        given(appUserDetailsService.isActive(anyLong())).willReturn(false);

        mockMvc.perform(get("/api/reports/sales/preview")
                        .param("startDate", "2026-01-01")
                        .param("endDate", "2026-01-31")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(7L)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a token for an account that no longer exists is rejected")
    void deletedAccountIsRejected() throws Exception {
        given(appUserDetailsService.isActive(anyLong())).willReturn(false);

        mockMvc.perform(get("/api/reports/stock/preview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(999L)))
                .andExpect(status().isUnauthorized());
    }
}
