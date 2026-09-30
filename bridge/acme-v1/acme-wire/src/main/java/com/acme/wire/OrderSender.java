package com.acme.wire;

import com.acme.model.Order;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

/** Encodes orders and writes them as frames to a TCP connection. */
public final class OrderSender implements Closeable {

    private final Socket socket;
    private final OutputStream out;
    private final OrderCodec codec;

    private OrderSender(Socket socket, OrderCodec codec) throws IOException {
        this.socket = socket;
        this.out = socket.getOutputStream();
        this.codec = codec;
    }

    public static OrderSender connect(String host, int port) throws IOException {
        return new OrderSender(new Socket(host, port), Codecs.load());
    }

    /** Sends one order and returns the encoded payload that went on the wire. */
    public synchronized byte[] send(Order order) throws IOException {
        byte[] payload = codec.encode(order);
        Framing.write(out, payload);
        out.flush();
        return payload;
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
