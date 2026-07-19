package com.mailorbit.entity;

/**
 * Lifecycle of an imported contact.
 * NEW -> (verification) -> VALID / RISKY / INVALID / SUPPRESSED
 */
public enum ContactStatus {
    NEW,
    VALID,
    RISKY,
    INVALID,
    SUPPRESSED
}
