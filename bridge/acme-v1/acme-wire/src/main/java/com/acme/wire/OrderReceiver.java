package com.acme.wire;

import com.acme.model.Order;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Accepts TCP connections and decodes every frame into an {@link Order}. */
public final class OrderReceiver implements Closeable {

    private final ServerSocket server;
    private final OrderCodec codec;
    private final OrderListener listener;
    private final List<Socket> connections = new CopyOnWriteArrayList<>();

    private OrderReceiver(ServerSocket server, OrderCodec codec, OrderListener listener) {
        this.server = server;
        this.codec = codec;
        this.listener = listener;
    }

    /** Listens on localhost; port 0 picks a free port (see {@link #port()}). */
    public static OrderReceiver listen(int port, OrderListener listener) throws IOException {
        ServerSocket server = new ServerSocket(port, 50, InetAddress.getLoopbackAddress());
        OrderReceiver receiver = new OrderReceiver(server, Codecs.load(), listener);
        Thread acceptor = new Thread(receiver::acceptLoop, "acme-" + WireProtocol.version() + "-accept");
        acceptor.setDaemon(true);
        acceptor.start();
        return receiver;
    }

    public int port() {
        return server.getLocalPort();
    }

    private void acceptLoop() {
        while (!server.isClosed()) {
            try {
                Socket socket = server.accept();
                connections.add(socket);
                Thread reader = new Thread(() -> readLoop(socket), "acme-" + WireProtocol.version() + "-rx");
                reader.setDaemon(true);
                reader.start();
            } catch (SocketException closed) {
                return;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void readLoop(Socket socket) {
        try (socket; InputStream in = socket.getInputStream()) {
            while (true) {
                byte[] payload = Framing.read(in);
                listener.onOrder(codec.decode(payload), payload);
            }
        } catch (EOFException | SocketException closed) {
            // Peer disconnected or receiver closed.
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void close() throws IOException {
        server.close();
        for (Socket socket : connections) {
            socket.close();
        }
    }
}
