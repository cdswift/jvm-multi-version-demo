package com.acme.model;

/** In 2.0 the customer became an immutable record. */
public record Customer(String name, String email) {
}
