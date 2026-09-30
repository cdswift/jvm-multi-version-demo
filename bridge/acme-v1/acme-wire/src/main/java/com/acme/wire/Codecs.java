package com.acme.wire;

import java.util.ServiceLoader;

public final class Codecs {
    private Codecs() {
    }

    /** The codec registered in META-INF/services/com.acme.wire.OrderCodec. */
    public static OrderCodec load() {
        // Use this library's own ClassLoader, not the thread's context loader,
        // so the lookup finds this version's codec.
        return ServiceLoader.load(OrderCodec.class, OrderCodec.class.getClassLoader())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No OrderCodec registered"));
    }
}
