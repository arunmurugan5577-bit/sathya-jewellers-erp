package com.jewellery.erp.labels.controller;

import com.jewellery.erp.common.dto.ApiErrorResponse;
import com.jewellery.erp.labels.dto.LabelDtos;
import com.jewellery.erp.labels.service.LabelQueueService;
import com.jewellery.erp.labels.service.LabelService;
import com.jewellery.erp.permission.PermissionCatalog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Barcode labels for inventory pieces.
 *
 * <p>The pieces themselves come from the existing inventory endpoint; this
 * controller only turns a selection of them into a printable document.
 */
@RestController
@RequestMapping("/api/labels")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Label Printing", description = "Barcode labels for jewellery tags")
public class LabelController {

    private static final MediaType HTML = MediaType.valueOf("text/html; charset=UTF-8");

    /*
     * Note: neither document endpoint declares produces = text/html. It would stop
     * Spring writing the JSON error body when a label cannot be built - the response
     * would fall through to /error, which the security chain answers with 401, and a
     * validation failure would sign the user out.
     */

    private final LabelService labelService;
    private final LabelQueueService queueService;

    public LabelController(LabelService labelService, LabelQueueService queueService) {
        this.labelService = labelService;
        this.queueService = queueService;
    }

    @GetMapping("/settings")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.LABEL_VIEW + "')")
    @Operation(summary = "Label and printer settings",
            description = "Shop short name and every label dimension. Requires LABEL_VIEW.")
    public ResponseEntity<LabelDtos.Settings> settings() {
        return ResponseEntity.ok(labelService.findSettings());
    }

    @PutMapping("/settings")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.LABEL_EDIT + "')")
    @Operation(
            summary = "Change label settings",
            description = "The short name is saved here once and used on every label afterwards. "
                    + "Sizes are in millimetres and font sizes in points. Requires LABEL_EDIT.")
    @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public ResponseEntity<LabelDtos.Settings> updateSettings(@Valid @RequestBody LabelDtos.SettingsRequest request) {
        return ResponseEntity.ok(labelService.updateSettings(request));
    }

    @PostMapping("/preview")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.LABEL_VIEW + "')")
    @Operation(
            summary = "Check a selection",
            description = "Returns what each label will say, plus anything that cannot be printed - a piece with "
                    + "no serial number or no purity. Requires LABEL_VIEW.")
    public ResponseEntity<LabelDtos.Preview> preview(@Valid @RequestBody LabelDtos.Request request) {
        return ResponseEntity.ok(labelService.preview(request));
    }

    // --- the shop print agent ----------------------------------------------
    //
    // These are the agent's own endpoints. It signs in as an ordinary user with
    // LABEL_CREATE, so nothing new is exposed: a caller who could already print
    // can also collect queued pages. There is no anonymous route in or out.

    @GetMapping("/agent/status")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.LABEL_VIEW + "')")
    @Operation(summary = "Whether the shop's print agent is running",
            description = "Its last contact, the printers it can see and what is waiting. Requires LABEL_VIEW.")
    public ResponseEntity<LabelDtos.AgentStatus> agentStatus() {
        return ResponseEntity.ok(labelService.agentStatus());
    }

    @PostMapping("/agent/heartbeat")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.LABEL_CREATE + "')")
    @Operation(summary = "The agent checking in",
            description = "Reports the shop PC's printers and asks how much is waiting. Requires LABEL_CREATE.")
    public ResponseEntity<LabelDtos.AgentPoll> heartbeat(@Valid @RequestBody LabelDtos.AgentHello hello) {
        return ResponseEntity.ok(queueService.heartbeat(hello));
    }

    @PostMapping("/agent/claim")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.LABEL_CREATE + "')")
    @Operation(summary = "Collect the next pages to print",
            description = "Hands over the oldest waiting pages as bitmaps, and marks them taken. "
                    + "Anything not reported on within a few minutes is offered again. Requires LABEL_CREATE.")
    public ResponseEntity<List<LabelDtos.QueuedPage>> claim(
            @RequestParam(defaultValue = "5") int max) {
        return ResponseEntity.ok(queueService.claim(max));
    }

    @PostMapping("/agent/pages/{pageId}/result")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.LABEL_CREATE + "')")
    @Operation(summary = "Say what happened to a page",
            description = "A page reported as not printed goes back in the queue, up to three attempts. "
                    + "Requires LABEL_CREATE.")
    public ResponseEntity<Void> pageResult(
            @PathVariable Long pageId, @Valid @RequestBody LabelDtos.PageResult result) {
        queueService.report(pageId, result.printed(), result.error());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/printers")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.LABEL_VIEW + "')")
    @Operation(summary = "Printers on this computer",
            description = "Windows printer names, for choosing the label printer. Requires LABEL_VIEW.")
    public ResponseEntity<LabelDtos.Printers> printers() {
        return ResponseEntity.ok(labelService.printers());
    }

    @PostMapping("/print")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.LABEL_CREATE + "')")
    @Operation(
            summary = "Print labels",
            description = "Sends the labels straight to the configured label printer - no browser print dialog, "
                    + "and the page is the tag's own size rather than whatever the dialog was left on. "
                    + "Requires LABEL_CREATE.")
    @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED - nothing printable, or the printer is not reachable",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public ResponseEntity<LabelDtos.PrintResult> print(@Valid @RequestBody LabelDtos.Request request) {
        return ResponseEntity.ok(labelService.printDirect(request));
    }

    @PostMapping("/preview-document")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.LABEL_VIEW + "')")
    @Operation(
            summary = "The label document, for on-screen preview",
            description = "Exactly what would be printed, at true size, with the tag edge outlined. "
                    + "Nothing is recorded. Requires LABEL_VIEW.")
    public ResponseEntity<String> previewDocument(@Valid @RequestBody LabelDtos.Request request) {
        return ResponseEntity.ok().contentType(HTML).body(labelService.previewDocument(request));
    }

    @PostMapping("/print-document")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.LABEL_CREATE + "')")
    @Operation(
            summary = "The label document, for printing",
            description = "One page per label, each the size of the physical tag, for the browser to send to the "
                    + "label printer. The run is recorded against the signed-in user. Requires LABEL_CREATE.")
    @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED - a selected piece cannot be printed",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public ResponseEntity<String> printDocument(@Valid @RequestBody LabelDtos.Request request) {
        return ResponseEntity.ok().contentType(HTML).body(labelService.printDocument(request));
    }
}
