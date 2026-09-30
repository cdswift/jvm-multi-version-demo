package com.acme.wire;

import com.acme.model.Order;

/**
 * Turns orders into bytes and back. Implementations are discovered with
 * {@link java.util.ServiceLoader} (see {@link Codecs}), like many real
 * protocol libraries do.
 */
public interface OrderCodec {
    String name();

    byte[] encode(Order order);

    Order decode(byte[] payload);
}
