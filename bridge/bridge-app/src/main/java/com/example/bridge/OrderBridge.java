package com.example.bridge;

import com.example.bridge.mapping.OrderV1ToV2;

import java.io.Closeable;
import java.io.IOException;
import java.util.function.Consumer;

/**
 * The bridge itself: listens for ACME 1.0 orders, translates each one to
 * ACME 2.0, and forwards it. It uses each version's own support libraries:
 *
 * <pre>
 *   v1 OrderReceiver (TCP, newline frames, TextCodec)
 *        -> OrderV1ToV2 (MapStruct)
 *        -> v2 OrderSender (TCP, length-prefixed frames, BinaryCodec)
 * </pre>
 *
 * Every class from both versions is referenced by its relocated name, so the
 * compiler checks all of it.
 */
public final class OrderBridge implements Closeable {

    /** What happened to one message, reported to the tracer. */
    public record Hop(byte[] v1Payload,
                      v1.com.acme.model.Order v1Order,
                      v2.com.acme.model.Order v2Order,
                      byte[] v2Payload,
                      Exception error) {
    }

    private final v1.com.acme.wire.OrderReceiver inbound;
    private final v2.com.acme.wire.OrderSender outbound;

    private OrderBridge(v1.com.acme.wire.OrderReceiver inbound, v2.com.acme.wire.OrderSender outbound) {
        this.inbound = inbound;
        this.outbound = outbound;
    }

    /**
     * Connects to the 2.0 system at {@code host:port}, then starts listening for
     * 1.0 traffic on a free local port (see {@link #port()}).
     */
    public static OrderBridge start(String host, int port, Consumer<Hop> tracer) throws IOException {
        v2.com.acme.wire.OrderSender outbound = v2.com.acme.wire.OrderSender.connect(host, port);
        OrderBridge[] self = new OrderBridge[1];
        v1.com.acme.wire.OrderReceiver inbound = v1.com.acme.wire.OrderReceiver.listen(0,
                (v1Order, v1Payload) -> self[0].forward(v1Order, v1Payload, tracer));
        self[0] = new OrderBridge(inbound, outbound);
        return self[0];
    }

    public int port() {
        return inbound.port();
    }

    private void forward(v1.com.acme.model.Order v1Order, byte[] v1Payload, Consumer<Hop> tracer) {
        v2.com.acme.model.Order v2Order = null;
        try {
            v2Order = OrderV1ToV2.INSTANCE.toV2(v1Order);
            byte[] v2Payload = outbound.send(v2Order);
            tracer.accept(new Hop(v1Payload, v1Order, v2Order, v2Payload, null));
        } catch (Exception e) {
            tracer.accept(new Hop(v1Payload, v1Order, v2Order, null, e));
        }
    }

    @Override
    public void close() throws IOException {
        inbound.close();
        outbound.close();
    }
}
