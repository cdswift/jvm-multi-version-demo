package com.acme.model;

/** Order status in protocol 2.0. SENT was renamed SHIPPED; CANCELLED is new. */
public enum Status {
    NEW,
    SHIPPED,
    CANCELLED
}
