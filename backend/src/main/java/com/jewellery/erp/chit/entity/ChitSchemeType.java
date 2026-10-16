package com.jewellery.erp.chit.entity;

/**
 * What a chit accumulates.
 *
 * <p>The difference runs through the whole module. A {@code MONEY} chit is a
 * savings pot: the customer puts in rupees and takes out rupees, or spends
 * them. A {@code GOLD} chit buys metal every month at that day's rate, so what
 * it is worth at the end is what gold is worth at the end - which is the
 * reason a customer picks one.
 */
public enum ChitSchemeType {
    MONEY,
    GOLD
}
