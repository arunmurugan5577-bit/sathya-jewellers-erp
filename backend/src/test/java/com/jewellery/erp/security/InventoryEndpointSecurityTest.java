package com.jewellery.erp.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jewellery.erp.common.dto.PageResponse;
import com.jewellery.erp.inventory.controller.InventoryItemController;
import com.jewellery.erp.inventory.service.InventoryItemService;
import com.jewellery.erp.numbering.SerialCounterService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Proof that the permission model is enforced by the server.
 *
 * <p>The Angular client hides actions a user cannot perform, but that is a
 * convenience. These tests call the endpoints directly, the way anything other
 * than the official client would, and assert that the {@code @PreAuthorize}
 * annotations are what actually decide.
 */
@WebMvcTest(controllers = InventoryItemController.class)
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
class InventoryEndpointSecurityTest {

    private static final String VALID_ITEM_JSON = """
            {
              "serialNumber": "000123",
              "itemTypeId": 1,
              "purityId": 2,
              "categoryId": 3,
              "weightGrams": 5.250
            }""";

    @Autowired private MockMvc mockMvc;

    @MockBean private InventoryItemService inventoryItemService;
    @MockBean private SerialCounterService serialCounterService;

    /** Required by the authentication provider; never exercised by these tests. */
    @MockBean private AppUserDetailsService appUserDetailsService;

    @Test
    @DisplayName("an unauthenticated request is refused with 401, not redirected to a login page")
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/inventory/items"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));

        verify(inventoryItemService, never()).findAll(any(), any());
    }

    @Test
    @WithMockUser(username = "staff1", authorities = {"INVENTORY_VIEW"})
    @DisplayName("INVENTORY_VIEW is enough to list items")
    void viewPermissionAllowsListing() throws Exception {
        when(inventoryItemService.findAll(any(), any()))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0, true, true));

        mockMvc.perform(get("/api/inventory/items")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "staff1", authorities = {"INVENTORY_VIEW"})
    @DisplayName("a user with only VIEW cannot delete, and the service is never reached")
    void viewPermissionDoesNotAllowDeleting() throws Exception {
        mockMvc.perform(delete("/api/inventory/items/1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));

        verify(inventoryItemService, never()).delete(anyLong());
    }

    @Test
    @WithMockUser(username = "manager", authorities = {"INVENTORY_VIEW", "INVENTORY_DELETE"})
    @DisplayName("INVENTORY_DELETE allows deleting")
    void deletePermissionAllowsDeleting() throws Exception {
        mockMvc.perform(delete("/api/inventory/items/1")).andExpect(status().isNoContent());

        verify(inventoryItemService).delete(1L);
    }

    @Test
    @WithMockUser(username = "staff1", authorities = {"INVENTORY_VIEW"})
    @DisplayName("a user with only VIEW cannot create")
    void viewPermissionDoesNotAllowCreating() throws Exception {
        mockMvc.perform(post("/api/inventory/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_ITEM_JSON))
                .andExpect(status().isForbidden());

        verify(inventoryItemService, never()).create(any());
    }

    @Test
    @WithMockUser(username = "staff1", authorities = {"INVENTORY_CREATE"})
    @DisplayName("a too-short serial number is rejected with a field level message")
    void rejectsMalformedSerialNumber() throws Exception {
        String body = """
                {
                  "serialNumber": "12",
                  "itemTypeId": 1,
                  "purityId": 2,
                  "categoryId": 3,
                  "weightGrams": 5.250
                }""";

        mockMvc.perform(post("/api/inventory/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Error"))
                .andExpect(jsonPath("$.fieldErrors.serialNumber").value("Serial number must be 3 to 6 digits"));

        verify(inventoryItemService, never()).create(any());
    }

    @Test
    @WithMockUser(username = "staff1", authorities = {"INVENTORY_CREATE"})
    @DisplayName("a zero weight is rejected - a jewellery piece always weighs something")
    void rejectsZeroWeight() throws Exception {
        String body = """
                {
                  "serialNumber": "000123",
                  "itemTypeId": 1,
                  "purityId": 2,
                  "categoryId": 3,
                  "weightGrams": 0
                }""";

        mockMvc.perform(post("/api/inventory/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.weightGrams").value("Weight must be greater than zero"));

        verify(inventoryItemService, never()).create(any());
    }

    @Test
    @WithMockUser(username = "staff1", authorities = {"INVENTORY_CREATE"})
    @DisplayName("a missing mandatory reference is reported per field")
    void rejectsMissingMandatoryReferences() throws Exception {
        String body = """
                {
                  "serialNumber": "000123",
                  "weightGrams": 5.250
                }""";

        mockMvc.perform(post("/api/inventory/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.itemTypeId").value("Item type is required"))
                .andExpect(jsonPath("$.fieldErrors.purityId").value("Purity is required"))
                .andExpect(jsonPath("$.fieldErrors.categoryId").value("Category is required"));
    }
}
