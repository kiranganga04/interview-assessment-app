package com.interview.assessment.entity;

/** Lifecycle of an interview slot: open for booking, booked by an interview, or withdrawn. */
public enum SlotStatus {
    AVAILABLE,
    BOOKED,
    CANCELLED
}
