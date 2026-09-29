package com.jewellery.erp.labels.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * What the shop's print agent last told us about itself.
 *
 * <p>Hosted, the server cannot see the shop's printers, so the settings screen
 * has to be told what they are. The agent reports them on every heartbeat, and
 * {@code lastSeenAt} is what lets the screen say whether the shop PC is
 * actually listening - otherwise a print would just sit in the queue with no
 * sign of why nothing came out.
 */
@Entity
@Table(name = "label_print_agent")
@Getter
@Setter
@NoArgsConstructor
public class LabelPrintAgent {

    public static final short SINGLETON_ID = 1;

    @Id
    @Column(name = "id", nullable = false)
    private Short id = SINGLETON_ID;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "agent_version", length = 40)
    private String agentVersion;

    @Column(name = "host_name", length = 160)
    private String hostName;

    /** Newline separated, as reported by the shop PC. */
    @Column(name = "printers")
    private String printers;

    @Column(name = "default_printer", length = 160)
    private String defaultPrinter;
}
