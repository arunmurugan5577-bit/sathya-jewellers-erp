package com.jewellery.erp.labels.entity;

/** Where a queued page has got to. */
public enum QueuedPageStatus {

    /** Waiting for the agent to collect it. */
    PENDING,

    /** Handed to the agent; it has not said what happened yet. */
    CLAIMED,

    /** The agent spooled it to the printer. */
    DONE,

    /** The agent could not print it, and said why. */
    FAILED
}
