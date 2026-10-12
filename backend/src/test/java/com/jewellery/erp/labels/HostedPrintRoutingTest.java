package com.jewellery.erp.labels;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jewellery.erp.labels.entity.LabelSettings;
import com.jewellery.erp.labels.entity.PrintMode;
import com.jewellery.erp.labels.service.LabelPrinterService;
import com.jewellery.erp.labels.service.LabelService;
import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Where a page goes when the application is not on the counter PC.
 *
 * <p>DIRECT means "the printer is attached to the computer running the
 * application". Hosted on a server that is not true and cannot be made true: a
 * data centre has no printers and no route to the one on the shop's USB cable.
 * Honouring DIRECT there produced the one error a shop cannot act on - "No
 * printer is set up. Choose the label printer in label settings" - pointing at a
 * settings screen whose printer list is necessarily empty.
 *
 * <p>So the routing asks the machine as well as the setting. The machine these
 * tests run on has printers and a server has none, which is exactly the
 * difference that matters, so the printer service is stubbed rather than real.
 */
class HostedPrintRoutingTest {

    /**
     * Calls the service's own rule.
     *
     * <p>Private on the service because nothing outside it should be making this
     * decision, and a test is not a reason to widen that. Only the settings and
     * the printer service are reached, so the other collaborators stay null -
     * passing nine mocks would suggest this depends on more than it does.
     */
    private static boolean routesToAgent(PrintMode mode, boolean machineHasPrinters) throws Exception {
        LabelPrinterService printers = mock(LabelPrinterService.class);
        when(printers.hasAnyPrinter()).thenReturn(machineHasPrinters);

        LabelService service = new LabelService(
                null, null, null, null, null, null, printers, null, null);
        LabelSettings settings = new LabelSettings();
        settings.setPrintMode(mode);

        Method rule = LabelService.class.getDeclaredMethod("printsThroughAgent", LabelSettings.class);
        rule.setAccessible(true);
        return (boolean) rule.invoke(service, settings);
    }

    @Test
    @DisplayName("hosted on a server with no printers: the page goes to the shop's agent")
    void hostedServerRoutesToTheAgent() throws Exception {
        // The case the shop hit. The setting still said DIRECT, because that is
        // the default and nothing had made it clear that it mattered.
        assertThat(routesToAgent(PrintMode.DIRECT, false)).isTrue();
    }

    @Test
    @DisplayName("the counter PC running the application keeps printing directly")
    void counterPcStaysDirect() throws Exception {
        // The original deployment. It has printers, so nothing changes for it -
        // this is what stops the fix from being a behaviour change for the shop
        // that runs the application on the till.
        assertThat(routesToAgent(PrintMode.DIRECT, true)).isFalse();
    }

    @Test
    @DisplayName("AGENT is honoured even where the machine does have printers")
    void agentIsHonouredOnAMachineWithPrinters() throws Exception {
        // A shop hosting the application on a PC that also has an office
        // printer still wants its tags at the counter.
        assertThat(routesToAgent(PrintMode.AGENT, true)).isTrue();
    }

    @Test
    @DisplayName("AGENT on a printerless server is the ordinary hosted case")
    void agentOnAPrinterlessServer() throws Exception {
        assertThat(routesToAgent(PrintMode.AGENT, false)).isTrue();
    }
}
