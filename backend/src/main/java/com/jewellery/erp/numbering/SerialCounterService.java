package com.jewellery.erp.numbering;

import com.jewellery.erp.common.exception.BusinessRuleException;
import com.jewellery.erp.common.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and moves the inventory serial counter.
 *
 * <p>Separate from {@link DocumentNumberService}, which only ever issues the
 * next number and refuses to run outside a caller's transaction. This is the
 * administrator's end: where the run starts, which is a decision about the
 * shop's tags rather than part of saving a piece.
 */
@Service
public class SerialCounterService {

    private static final Logger log = LoggerFactory.getLogger(SerialCounterService.class);
    private static final String SERIES = DocumentSeries.INVENTORY_SERIAL.name();
    private static final String PERIOD = "ALL";
    /** Six digits, so 999999 is the last piece the format can carry. */
    private static final long MAX_SERIAL = 999_999L;

    @PersistenceContext
    private EntityManager entityManager;

    /** The number the next piece will take, without taking it. */
    @Transactional(readOnly = true)
    public long peek() {
        Object value = entityManager.createNativeQuery("""
                SELECT next_value FROM document_counters
                WHERE series_code = :series AND period_key = :period
                """)
                .setParameter("series", SERIES)
                .setParameter("period", PERIOD)
                .getResultStream()
                .findFirst()
                .orElse(null);
        return value == null ? 1L : ((Number) value).longValue();
    }

    /**
     * Moves the counter.
     *
     * <p>Moving it backwards is allowed on purpose - a shop that has just
     * started numbering may want to begin again at 1 - but numbers already on a
     * piece are stepped over when the next one is issued, so an overlap costs a
     * few skipped values rather than a duplicate tag.
     */
    @Transactional
    public long setNext(long nextValue) {
        if (nextValue < 1 || nextValue > MAX_SERIAL) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "nextValue",
                    "The starting number must be between 1 and %d.".formatted(MAX_SERIAL));
        }
        entityManager.createNativeQuery("""
                INSERT INTO document_counters (series_code, period_key, next_value)
                VALUES (:series, :period, :value)
                ON CONFLICT (series_code, period_key)
                DO UPDATE SET next_value = :value, updated_at = now()
                """)
                .setParameter("series", SERIES)
                .setParameter("period", PERIOD)
                .setParameter("value", nextValue)
                .executeUpdate();
        log.info("Inventory serial numbering now starts at {}", nextValue);
        return nextValue;
    }
}
