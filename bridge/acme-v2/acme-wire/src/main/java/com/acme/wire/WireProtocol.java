package com.acme.wire;

public final class WireProtocol {
    private WireProtocol() {
    }

    /** A method, not a constant: javac would copy a constant into callers. */
    public static String version() {
        return "2.0";
    }
}
