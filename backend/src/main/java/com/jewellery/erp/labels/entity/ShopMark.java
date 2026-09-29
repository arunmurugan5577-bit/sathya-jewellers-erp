package com.jewellery.erp.labels.entity;

/** What stands for the shop on a printed tag, beside the barcode. */
public enum ShopMark {

    /** The shop's short name, e.g. "SJ". */
    TEXT,

    /** The shop logo, drawn in a square box. */
    LOGO,

    /** Neither - the barcode block starts at the left margin. */
    NONE
}
