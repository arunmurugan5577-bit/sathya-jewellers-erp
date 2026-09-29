package com.jewellery.erp.labels.entity;

/** Where the labels are actually spooled. */
public enum PrintMode {

    /** The printer is attached to the machine running the application. */
    DIRECT,

    /**
     * The application is hosted elsewhere, so pages are queued and a small agent
     * on the shop PC collects them and spools them to the real printer.
     */
    AGENT
}
