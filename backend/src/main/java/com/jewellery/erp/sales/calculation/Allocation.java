package com.jewellery.erp.sales.calculation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Splits an amount across lines in proportion to weights, to the paisa, so that
 * the parts always add back up to the whole.
 *
 * <p>Needed because tax and discount are worked out on the invoice total (as the
 * shop's receipt does), yet each line must carry its own share - a future return
 * of one piece has to reverse exactly that piece's tax. Naive per-line rounding
 * would leave the lines a paisa or two away from the header, which the header's
 * check constraints would then (rightly) reject.
 *
 * <p>Method: largest remainder. Each line gets the floor of its exact share; the
 * paise left over go to the lines with the largest fractional parts, ties broken by
 * line order so the result is deterministic.
 */
public final class Allocation {

    private Allocation() {}

    public static List<BigDecimal> distribute(BigDecimal total, List<BigDecimal> weights) {
        int size = weights.size();
        List<BigDecimal> result = new ArrayList<>(size);
        if (size == 0) {
            return result;
        }

        long totalPaise = total.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact();
        BigDecimal weightSum = weights.stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalPaise == 0) {
            IntStream.range(0, size).forEach(i -> result.add(BigDecimal.ZERO.setScale(2)));
            return result;
        }
        if (weightSum.signum() == 0) {
            // Nothing to be proportional to - place it all on the first line.
            result.add(total.setScale(2));
            IntStream.range(1, size).forEach(i -> result.add(BigDecimal.ZERO.setScale(2)));
            return result;
        }

        long[] floors = new long[size];
        BigDecimal[] fractions = new BigDecimal[size];
        long allocated = 0;
        for (int i = 0; i < size; i++) {
            BigDecimal exact = BigDecimal.valueOf(totalPaise)
                    .multiply(weights.get(i))
                    .divide(weightSum, 12, RoundingMode.HALF_UP);
            floors[i] = exact.setScale(0, RoundingMode.FLOOR).longValueExact();
            fractions[i] = exact.subtract(BigDecimal.valueOf(floors[i]));
            allocated += floors[i];
        }

        long leftover = totalPaise - allocated;
        List<Integer> order = IntStream.range(0, size).boxed()
                .sorted(Comparator.<Integer, BigDecimal>comparing(i -> fractions[i]).reversed()
                        .thenComparing(Comparator.naturalOrder()))
                .toList();
        for (int k = 0; k < leftover; k++) {
            floors[order.get(k % size)]++;
        }

        for (long paise : floors) {
            result.add(BigDecimal.valueOf(paise).movePointLeft(2).setScale(2));
        }
        return result;
    }
}
