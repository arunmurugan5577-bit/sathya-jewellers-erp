package com.jewellery.erp.common.config;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The shop's notion of "today".
 *
 * <p>Every business date - an invoice date, the start of a report range, the
 * financial year a document is numbered in - is resolved in the shop's zone
 * ({@code app.business-timezone}, default Asia/Kolkata), never in the server's
 * zone or UTC. A server in a data centre elsewhere must not move a 00:30 sale onto
 * the previous day.
 *
 * <p>Wraps a {@link Clock} so tests can pin the date.
 */
@Component
public class BusinessClock {

    private final ZoneId zone;
    private final Clock clock;

    // Spring needs to be told which constructor to use once a second one exists.
    @Autowired
    public BusinessClock(@Value("${app.business-timezone:Asia/Kolkata}") String zone) {
        this(ZoneId.of(zone), Clock.systemUTC());
    }

    BusinessClock(ZoneId zone, Clock clock) {
        this.zone = zone;
        this.clock = clock;
    }

    public static BusinessClock fixed(LocalDate date, ZoneId zone) {
        return new BusinessClock(zone, Clock.fixed(date.atStartOfDay(zone).toInstant(), zone));
    }

    public ZoneId zone() {
        return zone;
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(zone));
    }

    /** Midnight at the start of {@code date}, in the shop's zone - an inclusive lower bound. */
    public Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(zone).toInstant();
    }

    /** Midnight after {@code date} - an exclusive upper bound that includes the whole day. */
    public Instant startOfNextDay(LocalDate date) {
        return date.plusDays(1).atStartOfDay(zone).toInstant();
    }
}
