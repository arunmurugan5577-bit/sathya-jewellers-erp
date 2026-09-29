package com.jewellery.erp.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Converts a rupee amount to words in the Indian numbering system.
 *
 * <pre>
 *   7000.00    -> "Seven Thousand Rupees Only"
 *   114960.00  -> "One Lakh Fourteen Thousand Nine Hundred Sixty Rupees Only"
 *   1250.50    -> "One Thousand Two Hundred Fifty Rupees and Fifty Paise Only"
 * </pre>
 *
 * <p>Lakh and crore, not million: "One Lakh Fourteen Thousand" is how the shop
 * writes it on its own bills, and "One Hundred Fourteen Thousand" would read as
 * wrong to every customer who sees it.
 *
 * <p>Generated on the server so the words can never disagree with the figures - a
 * hand-typed "amount in words" is exactly the field a tampered invoice changes.
 */
public final class AmountInWords {

    private static final String[] UNITS = {
        "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
        "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"
    };

    private static final String[] TENS = {
        "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"
    };

    private AmountInWords() {}

    public static String rupees(BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Amount in words is not defined for negative amounts.");
        }

        BigDecimal rounded = amount.setScale(2, RoundingMode.HALF_UP);
        long rupees = rounded.longValue();
        int paise = rounded.subtract(BigDecimal.valueOf(rupees)).movePointRight(2).intValue();

        StringBuilder words = new StringBuilder();
        words.append(rupees == 0 ? "Zero" : convert(rupees)).append(" Rupees");
        if (paise > 0) {
            words.append(" and ").append(convert(paise)).append(" Paise");
        }
        return words.append(" Only").toString();
    }

    /** Indian grouping: crore (10^7), lakh (10^5), thousand, hundred, then the last two digits. */
    private static String convert(long number) {
        StringBuilder words = new StringBuilder();

        long crores = number / 10_000_000;
        number %= 10_000_000;
        long lakhs = number / 100_000;
        number %= 100_000;
        long thousands = number / 1_000;
        number %= 1_000;
        long hundreds = number / 100;
        long remainder = number % 100;

        // Above 99 crore the crore part is itself expressed in Indian grouping
        // ("One Hundred Crore"), which convert() handles recursively.
        if (crores > 0) {
            append(words, convert(crores) + " Crore");
        }
        if (lakhs > 0) {
            append(words, belowHundred(lakhs) + " Lakh");
        }
        if (thousands > 0) {
            append(words, belowHundred(thousands) + " Thousand");
        }
        if (hundreds > 0) {
            append(words, UNITS[(int) hundreds] + " Hundred");
        }
        if (remainder > 0) {
            append(words, belowHundred(remainder));
        }
        return words.toString();
    }

    private static String belowHundred(long number) {
        if (number < 20) {
            return UNITS[(int) number];
        }
        String tens = TENS[(int) (number / 10)];
        long units = number % 10;
        return units == 0 ? tens : tens + " " + UNITS[(int) units];
    }

    private static void append(StringBuilder words, String part) {
        if (!words.isEmpty()) {
            words.append(' ');
        }
        words.append(part);
    }
}
