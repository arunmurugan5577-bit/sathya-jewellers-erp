package com.jewellery.erp.numbering;

import com.jewellery.erp.common.exception.BusinessRuleException;
import com.jewellery.erp.common.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues consecutive, gap-free document numbers.
 *
 * <p>How gap-free is achieved: the counter row is locked {@code FOR UPDATE} and
 * incremented inside the <em>caller's</em> transaction ({@link Propagation#MANDATORY}
 * - this service refuses to run on its own). If the sale fails after taking a
 * number, the increment rolls back with it and the next sale gets the same
 * number. A PostgreSQL sequence cannot give that guarantee.
 *
 * <p>The cost is that concurrent documents of the same series serialise on the
 * counter row. Callers therefore take their number as the <em>last</em> step
 * before commit, so the lock is held for milliseconds rather than for the whole
 * sale.
 */
@Service
public class DocumentNumberService {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(propagation = Propagation.MANDATORY)
    public String next(DocumentSeries series, LocalDate documentDate) {
        Object[] config = loadSeries(series);
        String prefix = (String) config[0];
        // A one-character column comes back from a native query as Character, not String.
        String separator = config[1] == null ? "" : config[1].toString();
        String periodFormat = (String) config[2];
        int padWidth = ((Number) config[3]).intValue();
        int maxLength = ((Number) config[4]).intValue();

        String periodKey = DocumentNumberFormat.periodKey(periodFormat, documentDate);

        // Create the bucket on first use. ON CONFLICT makes two transactions that
        // both find it missing harmless; the SELECT FOR UPDATE below then serialises them.
        entityManager.createNativeQuery("""
                INSERT INTO document_counters (series_code, period_key, next_value)
                VALUES (:series, :period, 1)
                ON CONFLICT (series_code, period_key) DO NOTHING
                """)
                .setParameter("series", series.name())
                .setParameter("period", periodKey)
                .executeUpdate();

        long value = ((Number) entityManager.createNativeQuery("""
                SELECT next_value FROM document_counters
                WHERE series_code = :series AND period_key = :period
                FOR UPDATE
                """)
                .setParameter("series", series.name())
                .setParameter("period", periodKey)
                .getSingleResult()).longValue();

        entityManager.createNativeQuery("""
                UPDATE document_counters SET next_value = next_value + 1, updated_at = now()
                WHERE series_code = :series AND period_key = :period
                """)
                .setParameter("series", series.name())
                .setParameter("period", periodKey)
                .executeUpdate();

        String number = DocumentNumberFormat.format(prefix, separator, periodKey, padWidth, value);
        if (number.length() > maxLength) {
            // Reported rather than truncated: a truncated invoice number could
            // collide with an earlier one.
            throw new BusinessRuleException(ErrorCode.DOCUMENT_NUMBER_TOO_LONG, null,
                    "Document number %s exceeds the configured maximum of %d characters. Shorten the %s series format."
                            .formatted(number, maxLength, series.name()));
        }
        return number;
    }

    /**
     * Gives a number back, so the next document of the series takes it again.
     *
     * <p>Only the number that was issued last can be given back. The counter
     * holds one figure - what to issue next - and that is enough to undo the
     * most recent step and nothing else. Returning the number from the middle
     * of a run would mean issuing it again later, out of order, behind
     * documents that already carry higher numbers.
     *
     * <p>So deleting the invoice you have just raised frees its number, and
     * deleting an older one leaves the number spent. The caller is told which
     * happened rather than left to assume.
     *
     * <p>{@code MANDATORY} and {@code FOR UPDATE} for the same reason as
     * {@link #next}: the decrement has to stand or fall with the delete that
     * asked for it.
     *
     * @return true when the counter was wound back
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean release(DocumentSeries series, LocalDate documentDate, String number) {
        Object[] config = loadSeries(series);
        String prefix = (String) config[0];
        String separator = config[1] == null ? "" : config[1].toString();
        String periodFormat = (String) config[2];
        int padWidth = ((Number) config[3]).intValue();
        String periodKey = DocumentNumberFormat.periodKey(periodFormat, documentDate);

        List<?> rows = entityManager.createNativeQuery("""
                SELECT next_value FROM document_counters
                WHERE series_code = :series AND period_key = :period
                FOR UPDATE
                """)
                .setParameter("series", series.name())
                .setParameter("period", periodKey)
                .getResultList();
        if (rows.isEmpty()) {
            return false;
        }
        long next = ((Number) rows.get(0)).longValue();
        if (next <= 1) {
            return false;
        }

        // Rebuilt rather than parsed: the number is assembled from the series
        // configuration, so the only safe way to ask "was this the last one"
        // is to assemble it again and compare.
        String lastIssued = DocumentNumberFormat.format(prefix, separator, periodKey, padWidth, next - 1);
        if (!lastIssued.equals(number)) {
            return false;
        }

        entityManager.createNativeQuery("""
                UPDATE document_counters SET next_value = next_value - 1, updated_at = now()
                WHERE series_code = :series AND period_key = :period
                """)
                .setParameter("series", series.name())
                .setParameter("period", periodKey)
                .executeUpdate();
        return true;
    }

    private Object[] loadSeries(DocumentSeries series) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT prefix, separator_char, period_format, pad_width, max_length
                FROM document_series WHERE code = :code
                """)
                .setParameter("code", series.name())
                .getResultList();
        if (rows.isEmpty()) {
            throw new IllegalStateException(
                    "Document series " + series + " is not configured. Migration V6 has not been applied.");
        }
        return rows.get(0);
    }
}
